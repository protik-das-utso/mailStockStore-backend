package store.mailstock.submission.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SubmissionBulkRequest(
        @NotEmpty @Size(max = 200) @Valid List<SubmissionCreateRequest> items
) {}
