package store.mailstock.support.dto;

import store.mailstock.support.entity.SupportTicket;

/** Admin ticket management — both fields optional; only non-null ones are applied. */
public record AdminTicketUpdateRequest(
        SupportTicket.Status status,
        SupportTicket.Priority priority
) {}
