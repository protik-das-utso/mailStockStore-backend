package store.mailstock.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import store.mailstock.support.entity.SupportTicket;

public record TicketCreateRequest(
        @NotBlank @Size(max = 200) String subject,
        @Size(max = 50) String category,
        @NotBlank @Size(max = 10000) String body,
        @Size(max = 2000) String attachmentUrl,
        SupportTicket.Priority priority,   // optional; defaults to NORMAL
        Long orderId                        // optional; must belong to the requester
) {}
