package store.mailstock.support.dto;

import java.time.Instant;

import store.mailstock.support.entity.SupportTicket;

/** Enriched ticket view — carries computed {@code unread} for the current viewer so the UI needn't derive it. */
public record TicketResponse(
        Long id,
        Long userId,
        String subject,
        String category,
        Long orderId,
        SupportTicket.Status status,
        SupportTicket.Priority priority,
        Instant createdAt,
        Instant lastMessageAt,
        boolean lastSenderStaff,
        boolean unread,
        long messageCount
) {
    public static TicketResponse of(SupportTicket t, boolean unread, long messageCount) {
        return new TicketResponse(t.getId(), t.getUserId(), t.getSubject(), t.getCategory(), t.getOrderId(),
                t.getStatus(), t.getPriority(), t.getCreatedAt(), t.getLastMessageAt(),
                t.isLastSenderStaff(), unread, messageCount);
    }
}
