package store.mailstock.wallet.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import store.mailstock.audit.service.AuditService;
import store.mailstock.common.exception.ApiException;
import store.mailstock.notification.service.NotificationService;
import store.mailstock.wallet.dto.DepositCreateRequest;
import store.mailstock.wallet.dto.DepositDecisionRequest;
import store.mailstock.wallet.dto.WithdrawCreateRequest;
import store.mailstock.wallet.dto.WithdrawDecisionRequest;
import store.mailstock.wallet.entity.Wallet;
import store.mailstock.wallet.entity.WalletDeposit;
import store.mailstock.wallet.entity.WalletTransaction;
import store.mailstock.wallet.entity.WithdrawRequest;
import store.mailstock.wallet.repo.WalletDepositRepository;
import store.mailstock.wallet.repo.WalletRepository;
import store.mailstock.wallet.repo.WalletTransactionRepository;
import store.mailstock.wallet.repo.WithdrawRequestRepository;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository wallets;
    private final WalletTransactionRepository txRepo;
    private final WithdrawRequestRepository withdraws;
    private final WalletDepositRepository deposits;
    private final NotificationService notifications;
    private final AuditService audit;
    private final store.mailstock.abuse.service.AbuseService abuse;

    @Transactional
    public Wallet createForSeller(Long userId) {
        return wallets.findByUserId(userId).orElseGet(() ->
                wallets.save(Wallet.builder().userId(userId).build()));
    }

    @Transactional(readOnly = true)
    public Wallet get(Long userId) {
        return wallets.findByUserId(userId).orElseGet(() -> createForSeller(userId));
    }

    /**
     * Fetch the wallet with a pessimistic write lock for a balance mutation. Ensures the row
     * exists first, then re-reads it FOR UPDATE so concurrent debits/credits serialize instead
     * of racing (no lost updates, no negative balance). Must be called inside a write transaction.
     */
    private Wallet lockWallet(Long userId) {
        get(userId); // ensure the row exists so the locked read below always finds it
        return wallets.findByUserIdForUpdate(userId)
                .orElseThrow(() -> ApiException.notFound("Wallet not found"));
    }

    @Transactional
    public void creditSale(Long sellerId, BigDecimal amount, String reference) {
        Wallet w = lockWallet(sellerId);
        w.setAvailableBalance(w.getAvailableBalance().add(amount));
        w.setTotalEarnings(w.getTotalEarnings().add(amount));
        wallets.save(w);
        txRepo.save(WalletTransaction.builder()
                .walletId(w.getId()).type("CREDIT_SALE").amount(amount)
                .balanceAfter(w.getAvailableBalance()).reference(reference).build());
    }

    /** Admin tops up any user's balance directly. Records a ledger transaction + audit log as proof. */
    @Transactional
    public Wallet adminCredit(Long adminId, Long userId, BigDecimal amount, String note) {
        if (amount == null || amount.signum() <= 0)
            throw ApiException.badRequest("Amount must be positive");
        Wallet w = lockWallet(userId);
        w.setAvailableBalance(w.getAvailableBalance().add(amount));
        wallets.save(w);
        txRepo.save(WalletTransaction.builder()
                .walletId(w.getId()).type("CREDIT_ADMIN").amount(amount)
                .balanceAfter(w.getAvailableBalance()).reference("admin:credit").note(note).build());
        notifications.notify(userId, "BALANCE_CREDITED", "Balance added",
                "An admin added " + amount + " to your balance." + (note != null && !note.isBlank() ? " Note: " + note : ""));
        audit.log(adminId, "ADMIN_CREDIT", "wallet", String.valueOf(userId),
                "{\"amount\":\"" + amount + "\",\"note\":" + jsonStr(note) + "}", null);
        return w;
    }

    /**
     * Write a zero-amount ledger entry as proof for an event that moves no money
     * (e.g. a free manual delivery). Keeps every delivery auditable in the wallet ledger.
     */
    @Transactional
    public void recordProof(Long userId, String type, String reference, String note) {
        Wallet w = get(userId);
        txRepo.save(WalletTransaction.builder()
                .walletId(w.getId()).type(type).amount(BigDecimal.ZERO)
                .balanceAfter(w.getAvailableBalance()).reference(reference).note(note).build());
    }

    /** Refund a buyer to their wallet balance (warranty resolution). */
    @Transactional
    public void refundBuyer(Long buyerId, BigDecimal amount, String reference) {
        if (amount == null || amount.signum() <= 0) return;
        Wallet w = lockWallet(buyerId);
        w.setAvailableBalance(w.getAvailableBalance().add(amount));
        wallets.save(w);
        txRepo.save(WalletTransaction.builder()
                .walletId(w.getId()).type("REFUND_WARRANTY").amount(amount)
                .balanceAfter(w.getAvailableBalance()).reference(reference).build());
    }

    /** Claw back a seller's payout for a dead account. Balance is allowed to go negative (a debt owed). */
    @Transactional
    public void clawbackSeller(Long sellerId, BigDecimal amount, String reference, String note) {
        if (amount == null || amount.signum() <= 0) return;
        Wallet w = lockWallet(sellerId);
        w.setAvailableBalance(w.getAvailableBalance().subtract(amount));
        wallets.save(w);
        txRepo.save(WalletTransaction.builder()
                .walletId(w.getId()).type("CLAWBACK_WARRANTY").amount(amount.negate())
                .balanceAfter(w.getAvailableBalance()).reference(reference).note(note).build());
    }

    private static String jsonStr(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    @Transactional(readOnly = true)
    public Page<WalletTransaction> transactions(Long userId, Pageable p) {
        return txRepo.findByWalletIdOrderByIdDesc(get(userId).getId(), p);
    }

    // ---- Buyer balance: deposits & purchases ----

    /** Deduct the buyer's available balance for a purchase; throws if funds are insufficient. */
    @Transactional
    public void debitPurchase(Long buyerId, BigDecimal amount, String reference) {
        Wallet w = lockWallet(buyerId);
        if (w.getAvailableBalance().compareTo(amount) < 0)
            throw ApiException.badRequest("Insufficient balance. Please deposit funds before buying.");
        w.setAvailableBalance(w.getAvailableBalance().subtract(amount));
        wallets.save(w);
        txRepo.save(WalletTransaction.builder()
                .walletId(w.getId()).type("DEBIT_PURCHASE").amount(amount.negate())
                .balanceAfter(w.getAvailableBalance()).reference(reference).build());
    }

    @Transactional
    public WalletDeposit requestDeposit(Long userId, DepositCreateRequest req) {
        get(userId); // ensure wallet exists
        String txid = (req.txid() == null || req.txid().isBlank()) ? null : req.txid().trim();
        // Reject a re-used transaction id up front — a given txid may only ever be submitted once,
        // regardless of who submitted it or whether it is still pending, approved, or rejected.
        if (txid != null && deposits.existsByTxidIgnoreCase(txid))
            throw ApiException.badRequest("This transaction ID has already been submitted and cannot be used again.");
        WalletDeposit d = deposits.save(WalletDeposit.builder()
                .userId(userId).amount(req.amount()).txid(txid).build());
        notifications.notifyAdmins("NEW_DEPOSIT", "New balance deposit",
                "Deposit #" + d.getId() + " of " + req.amount() + (txid != null ? " (txid " + txid + ")" : "")
                        + " awaiting validation");
        return d;
    }

    @Transactional(readOnly = true)
    public WalletDeposit getDeposit(Long id) {
        return deposits.findById(id).orElseThrow(() -> ApiException.notFound("Deposit not found"));
    }

    @Transactional(readOnly = true)
    public Page<WalletDeposit> myDeposits(Long userId, Pageable p) {
        return deposits.findByUserIdOrderByIdDesc(userId, p);
    }

    @Transactional(readOnly = true)
    public Page<WalletDeposit> adminDeposits(WalletDeposit.Status status, String q, Pageable p) {
        return deposits.adminSearch(status, (q != null && !q.isBlank()) ? q.trim() : null, p);
    }

    @Transactional
    public WalletDeposit decideDeposit(Long adminId, Long id, DepositDecisionRequest req) {
        WalletDeposit d = deposits.findById(id).orElseThrow(() -> ApiException.notFound("Deposit not found"));
        if (d.getStatus() != WalletDeposit.Status.PENDING)
            throw ApiException.badRequest("Already processed");
        if (req.approve()) {
            Wallet w = lockWallet(d.getUserId());
            w.setAvailableBalance(w.getAvailableBalance().add(d.getAmount()));
            wallets.save(w);
            txRepo.save(WalletTransaction.builder()
                    .walletId(w.getId()).type("CREDIT_DEPOSIT").amount(d.getAmount())
                    .balanceAfter(w.getAvailableBalance()).reference("deposit:" + d.getId()).build());
            d.setStatus(WalletDeposit.Status.APPROVED);
            notifications.notify(d.getUserId(), "DEPOSIT_APPROVED",
                    "Deposit approved", "Your balance was topped up by " + d.getAmount());
        } else {
            d.setStatus(WalletDeposit.Status.REJECTED);
            notifications.notify(d.getUserId(), "DEPOSIT_REJECTED", "Deposit rejected", req.adminNote());
        }
        d.setAdminNote(req.adminNote());
        d.setProcessedBy(adminId);
        d.setProcessedAt(Instant.now());
        WalletDeposit saved = deposits.save(d);
        // Auto-flag the user for admin review if this rejection pushes them over the abuse threshold.
        if (!req.approve()) abuse.onFailedDeposit(saved.getUserId());
        return saved;
    }

    /**
     * Auto-credit a pending deposit once its Binance Pay transaction is confirmed (reconciler/validator).
     * Credits the ACTUAL settled amount (not the buyer-typed one) and marks the deposit APPROVED.
     * Returns false (no-op) if the deposit is gone/already processed or the txid was already credited.
     */
    @Transactional
    public boolean autoCreditDeposit(Long depositId, BigDecimal actualAmount, String txid, String note) {
        // Lock the deposit row FOR UPDATE so the synchronous validateNow and the scheduled
        // reconciler can't both credit the same pending deposit: the second caller blocks,
        // then re-reads status = APPROVED below and returns false.
        WalletDeposit d = deposits.findByIdForUpdate(depositId).orElse(null);
        if (d == null || d.getStatus() != WalletDeposit.Status.PENDING) return false;
        if (deposits.existsByTxidAndStatus(txid, WalletDeposit.Status.APPROVED)) return false; // already credited elsewhere
        BigDecimal amount = actualAmount.setScale(2, java.math.RoundingMode.HALF_UP); // ledger is 2-dp
        Wallet w = lockWallet(d.getUserId());
        w.setAvailableBalance(w.getAvailableBalance().add(amount));
        wallets.save(w);
        txRepo.save(WalletTransaction.builder()
                .walletId(w.getId()).type("CREDIT_DEPOSIT").amount(amount)
                .balanceAfter(w.getAvailableBalance()).reference("deposit:" + d.getId()).note(note).build());
        d.setAmount(amount);
        d.setTxid(txid);
        d.setMethod("BINANCE_AUTO");
        d.setStatus(WalletDeposit.Status.APPROVED);
        d.setAdminNote(note);
        d.setProcessedAt(Instant.now());
        deposits.save(d);
        notifications.notify(d.getUserId(), "DEPOSIT_APPROVED",
                "Deposit credited", "Your balance was topped up by " + amount + " (auto-confirmed via Binance Pay).");
        return true;
    }

    @Transactional
    public WithdrawRequest requestWithdraw(Long userId, WithdrawCreateRequest req) {
        Wallet w = lockWallet(userId);
        if (w.getAvailableBalance().compareTo(req.amount()) < 0)
            throw ApiException.badRequest("Insufficient balance");
        w.setAvailableBalance(w.getAvailableBalance().subtract(req.amount()));
        w.setPendingBalance(w.getPendingBalance().add(req.amount()));
        wallets.save(w);
        txRepo.save(WalletTransaction.builder()
                .walletId(w.getId()).type("HOLD_WITHDRAW").amount(req.amount().negate())
                .balanceAfter(w.getAvailableBalance()).reference("withdraw:pending").build());
        return withdraws.save(WithdrawRequest.builder()
                .userId(userId).amount(req.amount()).destination(req.destination()).build());
    }

    @Transactional(readOnly = true)
    public Page<WithdrawRequest> myWithdrawals(Long userId, Pageable p) {
        return withdraws.findByUserIdOrderByIdDesc(userId, p);
    }

    @Transactional(readOnly = true)
    public Page<WithdrawRequest> adminList(WithdrawRequest.Status status, String q, Pageable p) {
        return withdraws.adminSearch(status, (q != null && !q.isBlank()) ? q.trim() : null, p);
    }

    @Transactional
    public WithdrawRequest decide(Long adminId, Long id, WithdrawDecisionRequest req) {
        WithdrawRequest wr = withdraws.findById(id)
                .orElseThrow(() -> ApiException.notFound("Withdrawal not found"));
        if (wr.getStatus() != WithdrawRequest.Status.PENDING)
            throw ApiException.badRequest("Already processed");
        Wallet w = lockWallet(wr.getUserId());

        if (req.approve()) {
            w.setPendingBalance(w.getPendingBalance().subtract(wr.getAmount()));
            wr.setStatus(WithdrawRequest.Status.PAID);
            wr.setPayoutTxid(req.payoutTxid());
            txRepo.save(WalletTransaction.builder()
                    .walletId(w.getId()).type("DEBIT_WITHDRAW").amount(wr.getAmount().negate())
                    .balanceAfter(w.getAvailableBalance()).reference("withdraw:" + wr.getId()).build());
            notifications.notify(wr.getUserId(), "WITHDRAW_APPROVED",
                    "Withdrawal approved", "Your withdrawal of " + wr.getAmount() + " has been paid.");
        } else {
            w.setPendingBalance(w.getPendingBalance().subtract(wr.getAmount()));
            w.setAvailableBalance(w.getAvailableBalance().add(wr.getAmount()));
            wr.setStatus(WithdrawRequest.Status.REJECTED);
            txRepo.save(WalletTransaction.builder()
                    .walletId(w.getId()).type("RELEASE_WITHDRAW").amount(wr.getAmount())
                    .balanceAfter(w.getAvailableBalance()).reference("withdraw:reject:" + wr.getId()).build());
            notifications.notify(wr.getUserId(), "WITHDRAW_REJECTED",
                    "Withdrawal rejected", req.adminNote());
        }
        wallets.save(w);
        wr.setAdminNote(req.adminNote());
        wr.setProcessedBy(adminId);
        wr.setProcessedAt(Instant.now());
        return withdraws.save(wr);
    }
}
