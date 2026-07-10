package store.mailstock.support.dto;

import java.time.Instant;

import store.mailstock.support.entity.TicketMessage;

/** A message with its author's role (USER = ticket owner, STAFF = anyone else) and display name. */
public record TicketMessageResponse(
        Long id,
        Long ticketId,
        Long senderId,
        String senderRole,   // "USER" | "STAFF"
        String senderName,
        String body,
        String attachmentUrl,
        Instant createdAt
) {
    public static TicketMessageResponse of(TicketMessage m, boolean staff, String senderName) {
        return new TicketMessageResponse(m.getId(), m.getTicketId(), m.getSenderId(),
                staff ? "STAFF" : "USER", senderName, m.getBody(), m.getAttachmentUrl(), m.getCreatedAt());
    }
}
