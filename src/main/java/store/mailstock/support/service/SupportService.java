package store.mailstock.support.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import store.mailstock.auth.entity.Role;
import store.mailstock.auth.entity.User;
import store.mailstock.auth.repo.UserRepository;
import store.mailstock.common.exception.ApiException;
import store.mailstock.email.EmailService;
import store.mailstock.notification.service.NotificationService;
import store.mailstock.order.repo.OrderRepository;
import store.mailstock.support.dto.AdminTicketUpdateRequest;
import store.mailstock.support.dto.TicketCreateRequest;
import store.mailstock.support.dto.TicketMessageResponse;
import store.mailstock.support.dto.TicketReplyRequest;
import store.mailstock.support.dto.TicketResponse;
import store.mailstock.support.entity.SupportTicket;
import store.mailstock.support.entity.SupportTicket.Priority;
import store.mailstock.support.entity.SupportTicket.Status;
import store.mailstock.support.entity.TicketMessage;
import store.mailstock.support.repo.SupportTicketRepository;
import store.mailstock.support.repo.TicketMessageRepository;

@Service
@RequiredArgsConstructor
public class SupportService {

    private final SupportTicketRepository tickets;
    private final TicketMessageRepository messages;
    private final NotificationService notifications;
    private final EmailService email;
    private final UserRepository users;
    private final OrderRepository orders;

    @Value("${app.frontend-url}") private String frontendUrl;

    // ---------------- commands ----------------

    @Transactional
    public TicketResponse create(Long userId, TicketCreateRequest req) {
        if (req.orderId() != null) {
            var o = orders.findById(req.orderId())
                    .orElseThrow(() -> ApiException.badRequest("That order doesn't exist."));
            if (!o.getBuyerId().equals(userId))
                throw ApiException.forbidden("That order isn't yours.");
        }
        Instant now = Instant.now();
        SupportTicket t = tickets.save(SupportTicket.builder()
                .userId(userId).subject(req.subject()).category(req.category()).orderId(req.orderId())
                .priority(req.priority() != null ? req.priority() : Priority.NORMAL)
                .status(Status.OPEN).lastMessageAt(now).lastSenderStaff(false).userReadAt(now)
                .build());
        messages.save(TicketMessage.builder()
                .ticketId(t.getId()).senderId(userId).body(req.body()).attachmentUrl(req.attachmentUrl()).build());

        notifyAdmins("SUPPORT_NEW", "New support ticket #" + t.getId(),
                "\"" + t.getSubject() + "\" (" + t.getPriority() + ")", t.getId(), req.body());
        return toResponse(t, false, 1);
    }

    @Transactional
    public TicketMessageResponse reply(User user, Long ticketId, TicketReplyRequest req) {
        SupportTicket t = getTicket(user, ticketId);
        if (t.getStatus() == Status.CLOSED)
            throw ApiException.badRequest("This ticket is closed. Reopen it to continue the conversation.");
        boolean staff = isStaff(t, user);
        Instant now = Instant.now();
        t.setStatus(staff ? Status.ANSWERED : Status.PENDING);
        t.setLastMessageAt(now);
        t.setLastSenderStaff(staff);
        if (staff) t.setAdminReadAt(now); else t.setUserReadAt(now);
        tickets.save(t);
        TicketMessage m = messages.save(TicketMessage.builder()
                .ticketId(ticketId).senderId(user.getId())
                .body(req.body()).attachmentUrl(req.attachmentUrl()).build());

        if (staff)
            notifyOwner(t, "SUPPORT_REPLY", "Support replied to ticket #" + t.getId(), t.getSubject(), req.body());
        else
            notifyAdmins("SUPPORT_REPLY", "New reply on ticket #" + t.getId(), t.getSubject(), t.getId(), req.body());

        return TicketMessageResponse.of(m, staff, staff ? "Support" : displayName(user.getId()));
    }

    @Transactional
    public TicketResponse close(User user, Long id) {
        SupportTicket t = getTicket(user, id);
        boolean staff = isStaff(t, user);
        t.setStatus(Status.CLOSED);
        t.setClosedAt(Instant.now());
        tickets.save(t);
        if (staff)
            notifyOwner(t, "SUPPORT_CLOSED", "Ticket #" + t.getId() + " closed", t.getSubject(), null);
        return toResponse(t, staff, messages.countByTicketId(id));
    }

    @Transactional
    public TicketResponse reopen(User user, Long id) {
        SupportTicket t = getTicket(user, id);
        if (t.getStatus() != Status.CLOSED && t.getStatus() != Status.RESOLVED)
            throw ApiException.badRequest("Only closed or resolved tickets can be reopened.");
        boolean staff = isStaff(t, user);
        t.setStatus(Status.OPEN);
        t.setClosedAt(null);
        tickets.save(t);
        if (staff) notifyOwner(t, "SUPPORT_REOPENED", "Ticket #" + t.getId() + " reopened", t.getSubject(), null);
        else notifyAdmins("SUPPORT_REOPENED", "Ticket #" + t.getId() + " reopened by customer", t.getSubject(), t.getId(), null);
        return toResponse(t, staff, messages.countByTicketId(id));
    }

    /** Admin: change status and/or priority. */
    @Transactional
    public TicketResponse adminUpdate(User admin, Long id, AdminTicketUpdateRequest req) {
        SupportTicket t = tickets.findById(id).orElseThrow(() -> ApiException.notFound("Ticket not found"));
        Status prev = t.getStatus();
        if (req.priority() != null) t.setPriority(req.priority());
        if (req.status() != null) {
            t.setStatus(req.status());
            t.setClosedAt(req.status() == Status.CLOSED ? Instant.now() : null);
        }
        tickets.save(t);
        if (req.status() != null && req.status() != prev)
            notifyOwner(t, "SUPPORT_STATUS", "Ticket #" + t.getId() + " is now " + t.getStatus(),
                    t.getSubject(), null);
        return toResponse(t, true, messages.countByTicketId(id));
    }

    // ---------------- queries ----------------

    @Transactional(readOnly = true)
    public Page<TicketResponse> mine(Long userId, Pageable p) {
        return tickets.findByUserIdOrderByIdDesc(userId, p)
                .map(t -> toResponse(t, false, messages.countByTicketId(t.getId())));
    }

    @Transactional(readOnly = true)
    public Page<TicketResponse> adminList(Status status, String q, Pageable p) {
        return tickets.adminSearch(status, (q != null && !q.isBlank()) ? q.trim() : null, p)
                .map(t -> toResponse(t, true, messages.countByTicketId(t.getId())));
    }

    @Transactional(readOnly = true)
    public TicketResponse get(User user, Long id) {
        SupportTicket t = getTicket(user, id);
        return toResponse(t, isStaff(t, user), messages.countByTicketId(id));
    }

    /** Returns the thread AND marks it read for the viewer (clears their unread). */
    @Transactional
    public List<TicketMessageResponse> messages(User user, Long ticketId) {
        SupportTicket t = getTicket(user, ticketId);
        boolean staff = isStaff(t, user);
        if (staff) t.setAdminReadAt(Instant.now()); else t.setUserReadAt(Instant.now());
        tickets.save(t);
        String ownerName = displayName(t.getUserId());
        return messages.findByTicketIdOrderByIdAsc(ticketId).stream()
                .map(m -> {
                    boolean fromStaff = !m.getSenderId().equals(t.getUserId());
                    return TicketMessageResponse.of(m, fromStaff, fromStaff ? "Support" : ownerName);
                }).toList();
    }

    // ---------------- helpers ----------------

    private SupportTicket getTicket(User user, Long id) {
        SupportTicket t = tickets.findById(id).orElseThrow(() -> ApiException.notFound("Ticket not found"));
        if (!user.hasRole(Role.ADMIN) && !t.getUserId().equals(user.getId()))
            throw ApiException.forbidden("Not your ticket");
        return t;
    }

    /** Staff = anyone who isn't the ticket owner (authz already guarantees they're an admin). */
    private boolean isStaff(SupportTicket t, User viewer) {
        return !t.getUserId().equals(viewer.getId());
    }

    private boolean unreadFor(SupportTicket t, boolean staffViewer) {
        Instant last = t.getLastMessageAt();
        if (last == null) return false;
        return staffViewer
                ? (!t.isLastSenderStaff() && (t.getAdminReadAt() == null || last.isAfter(t.getAdminReadAt())))
                : (t.isLastSenderStaff() && (t.getUserReadAt() == null || last.isAfter(t.getUserReadAt())));
    }

    private TicketResponse toResponse(SupportTicket t, boolean staffViewer, long messageCount) {
        return TicketResponse.of(t, unreadFor(t, staffViewer), messageCount);
    }

    private String displayName(Long userId) {
        return users.findById(userId).map(User::getFullName).orElse("User #" + userId);
    }

    private List<User> admins() {
        return users.findAll().stream().filter(u -> u.hasRole(Role.ADMIN)).toList();
    }

    private void notifyOwner(SupportTicket t, String type, String title, String body, String preview) {
        String link = "/support/" + t.getId();
        notifications.notify(t.getUserId(), type, title, body, link); // in-app + Telegram
        users.findById(t.getUserId()).ifPresent(u ->
                emailSupport(u.getEmail(), title, body, preview, frontendUrl + link));
    }

    private void notifyAdmins(String type, String title, String body, Long ticketId, String preview) {
        notifications.notifyAdmins(type, title, body); // in-app + Telegram to each admin
        String link = frontendUrl + "/admin/support/" + ticketId;
        for (User a : admins()) emailSupport(a.getEmail(), title, body, preview, link);
    }

    private void emailSupport(String to, String heading, String message, String preview, String link) {
        if (to == null || to.isBlank()) return;
        Map<String, Object> model = new HashMap<>();
        model.put("heading", heading);
        model.put("message", message);
        if (preview != null && !preview.isBlank())
            model.put("preview", preview.length() > 160 ? preview.substring(0, 160) + "…" : preview);
        model.put("link", link);
        email.sendGeneric(to, heading, "support", model); // @Async, best-effort
    }
}
