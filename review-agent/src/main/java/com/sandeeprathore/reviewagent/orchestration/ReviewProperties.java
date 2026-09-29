package com.sandeeprathore.reviewagent.orchestration;

import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Budgets and quality gates for a review.
 *
 * @param minConfidence findings below this confidence are dropped
 * @param maxFindings most findings posted on one review; the least important are dropped beyond it
 * @param maxInputTokensPerBatch token budget for the diff in one model call
 * @param maxRepairAttempts re-prompts when the model's output doesn't match the findings schema
 * @param maxPatchBytes largest patch accepted, to bound cost and memory
 * @param ignorePaths globs (matched against "/" + path) for files never sent to a reviewer
 */
@Validated
@ConfigurationProperties("review.reviewer")
public record ReviewProperties(@DefaultValue("0.6") @DecimalMin("0.0") @DecimalMax("1.0") double minConfidence,
		@DefaultValue("25") @Min(1) int maxFindings, @DefaultValue("60000") @Min(1000) int maxInputTokensPerBatch,
		@DefaultValue("2") @Min(0) int maxRepairAttempts, @DefaultValue("1000000") @Min(1) int maxPatchBytes,
		@DefaultValue({ "**/package-lock.json", "**/*.min.js", "**/target/**", "**/build/**" }) List<String> ignorePaths) {
}
