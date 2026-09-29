package com.sandeeprathore.reviewagent.review.finding;

/** A finding the validator dropped, kept for the report so prompt regressions are visible. */
public record RejectedFinding(Finding finding, String reason) {
}
