package store.mailstock.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TicketReplyRequest(
        @NotBlank @Size(max = 10000) String body,
        // http(s) only — rendered as a raw <a href> in the ticket thread to admins/staff.
        @Size(max = 2000) @Pattern(regexp = "^https?://.+", message = "Must be a valid http(s) URL") String attachmentUrl
) {}
