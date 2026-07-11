package store.mailstock.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import store.mailstock.support.entity.SupportTicket;

public record TicketCreateRequest(
        @NotBlank @Size(max = 200) String subject,
        @Size(max = 50) String category,
        @NotBlank @Size(max = 10000) String body,
        // http(s) only — rendered as a raw <a href> in the ticket thread to admins/staff.
        @Size(max = 2000) @Pattern(regexp = "^https?://.+", message = "Must be a valid http(s) URL") String attachmentUrl,
        SupportTicket.Priority priority,   // optional; defaults to NORMAL
        Long orderId                        // optional; must belong to the requester
) {}
