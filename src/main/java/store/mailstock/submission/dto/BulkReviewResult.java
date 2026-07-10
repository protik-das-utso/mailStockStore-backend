package store.mailstock.submission.dto;

import java.util.List;

/** Outcome of a bulk review: how many were attempted, which ids succeeded, and which failed with why. */
public record BulkReviewResult(int total, List<Long> succeeded, List<Failure> failed) {
    public record Failure(Long id, String error) {}
}
