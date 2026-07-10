package store.mailstock.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TicketReplyRequest(
        @NotBlank @Size(max = 10000) String body,
        @Size(max = 2000) String attachmentUrl
) {}
