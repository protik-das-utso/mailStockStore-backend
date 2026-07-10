package store.mailstock.wallet.binance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import store.mailstock.common.util.MaskUtil;
import store.mailstock.wallet.entity.WalletDeposit;
import store.mailstock.wallet.repo.WalletDepositRepository;
import store.mailstock.wallet.service.WalletService;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Validates buyer deposits against Binance Pay history and auto-credits them. A deposit is matched by
 * its txid (the Binance Pay transaction id the buyer pasted); the settled amount must also match what
 * the buyer claimed (within tolerance), and each txid can credit at most once.
 *
 * Two entry points share the same matcher:
 *  - {@link #validateNow(WalletDeposit)} — called synchronously right after a buyer submits, for instant credit.
 *  - {@link #reconcile()} — a scheduled fallback that retries deposits whose payment wasn't visible yet.
 *
 * No-ops unless {@code app.binance.enabled=true} with credentials set, so the manual admin flow is the default.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BinanceDepositReconciler {

    private final BinanceProperties props;
    private final BinanceClient binance;
    private final WalletDepositRepository deposits;
    private final WalletService wallet;

    /** Best-effort instant validation of a single freshly-submitted deposit. Returns true if credited now. */
    public boolean validateNow(WalletDeposit dep) {
        if (!props.isConfigured()) {
            log.warn("[BINANCE] auto-credit DISABLED (app.binance.enabled={} / keys set={}) — deposit #{} stays PENDING",
                    props.isEnabled(), (props.getApiKey() != null && !props.getApiKey().isBlank()),
                    dep != null ? dep.getId() : null);
            return false;
        }
        if (dep == null || dep.getStatus() != WalletDeposit.Status.PENDING) {
            log.info("[BINANCE] skip validation — deposit null or not PENDING");
            return false;
        }
        if (dep.getTxid() == null || dep.getTxid().isBlank()) {
            log.info("[BINANCE] deposit #{} has no txid — cannot auto-match, stays PENDING", dep.getId());
            return false;
        }
        log.info("[BINANCE] validating deposit #{} (txid={}, amount={}) against Binance Pay history…",
                dep.getId(), MaskUtil.maskId(dep.getTxid()), dep.getAmount());
        return matchAndCredit(dep, index(binance.recentPayIncome()));
    }

    @Scheduled(fixedDelayString = "${app.binance.poll-interval-ms:120000}",
            initialDelayString = "${app.binance.poll-interval-ms:120000}")
    public void reconcile() {
        if (!props.isConfigured()) return;
        List<WalletDeposit> pending = deposits.findByStatus(WalletDeposit.Status.PENDING);
        if (pending.isEmpty()) return;

        Map<String, BinanceClient.PayTxn> byTxid = index(binance.recentPayIncome());
        if (byTxid.isEmpty()) return;

        int credited = 0;
        for (WalletDeposit dep : pending) {
            if (dep.getTxid() == null || dep.getTxid().isBlank()) continue;
            try {
                if (matchAndCredit(dep, byTxid)) credited++;
            } catch (Exception e) {
                log.warn("Auto-credit failed for deposit #{}: {}", dep.getId(), e.toString());
            }
        }
        if (credited > 0) log.info("Binance Pay reconciler credited {} deposit(s)", credited);
    }

    /** Match one deposit to a confirmed Pay transaction by txid, verify coin+amount, then credit. */
    private boolean matchAndCredit(WalletDeposit dep, Map<String, BinanceClient.PayTxn> byOrderId) {
        log.info("[BINANCE] matching deposit #{} orderId={} against {} Binance order(s)",
                dep.getId(), MaskUtil.maskId(norm(dep.getTxid())), byOrderId.size());
        BinanceClient.PayTxn tx = byOrderId.get(norm(dep.getTxid()));
        if (tx == null) {
            log.info("[BINANCE] deposit #{}: orderId not found in Binance history yet — stays PENDING", dep.getId());
            return false;
        }
        if (props.getCoin() != null && tx.currency() != null
                && !props.getCoin().equalsIgnoreCase(tx.currency())) {
            log.warn("[BINANCE] deposit #{}: coin mismatch (want {} got {}) — stays PENDING",
                    dep.getId(), props.getCoin(), tx.currency());
            return false;
        }
        // The settled amount must match what the buyer said they sent (within tolerance).
        if (dep.getAmount() != null
                && tx.amount().subtract(dep.getAmount()).abs().compareTo(props.getAmountTolerance()) > 0) {
            log.warn("[BINANCE] deposit #{} txid matched but amount {} != claimed {} (tolerance {}) — left for review",
                    dep.getId(), tx.amount(), dep.getAmount(), props.getAmountTolerance());
            return false;
        }
        // A given Pay order may only ever credit once.
        if (deposits.existsByTxidAndStatus(tx.orderId(), WalletDeposit.Status.APPROVED)) {
            log.warn("[BINANCE] deposit #{}: order {} already credited a previous deposit — skipping",
                    dep.getId(), MaskUtil.maskId(tx.orderId()));
            return false;
        }

        String note = "Auto-credited via Binance Pay: " + tx.amount() + " " + tx.currency()
                + " (" + tx.orderType() + ") order=" + tx.orderId() + " txn=" + tx.transactionId();
        boolean credited = wallet.autoCreditDeposit(dep.getId(), tx.amount(), tx.orderId(), note);
        log.info("[BINANCE] deposit #{}: MATCH ✓ amount={} {} order={} -> autoCredit returned {}",
                dep.getId(), tx.amount(), tx.currency(), MaskUtil.maskId(tx.orderId()), credited);
        return credited;
    }

    private static Map<String, BinanceClient.PayTxn> index(List<BinanceClient.PayTxn> income) {
        Map<String, BinanceClient.PayTxn> m = new HashMap<>();
        for (BinanceClient.PayTxn t : income) m.putIfAbsent(norm(t.orderId()), t);
        return m;
    }

    private static String norm(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
