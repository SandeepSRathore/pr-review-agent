package com.sandeeprathore.reviewagent.orchestration;

import java.util.List;

import com.sandeeprathore.reviewagent.review.finding.Finding;
import com.sandeeprathore.reviewagent.review.finding.RejectedFinding;

/**
 * The outcome of a review: what would be posted, what was dropped and why, and what it cost.
 *
 * @param reviewId unique id for correlating logs, traces and eval runs
 * @param summary overall assessment for the author
 * @param findings validated findings, most important first
 * @param rejected findings the validator dropped, with reasons
 * @param stats size, token and timing figures
 */
public record ReviewReport(String reviewId, String summary, List<Finding> findings, List<RejectedFinding> rejected,
		Stats stats) {

	/**
	 * @param filesReviewed files sent to a reviewer
	 * @param filesSkipped deleted, binary or ignored files
	 * @param batches model calls the diff was split into
	 * @param inputTokens prompt tokens across all calls
	 * @param outputTokens completion tokens across all calls
	 * @param durationMs wall-clock time for the whole review
	 * @param model provider/model that did the review
	 * @param promptVersion content hash of the prompt templates
	 */
	public record Stats(int filesReviewed, int filesSkipped, int batches, long inputTokens, long outputTokens,
			long durationMs, String model, String promptVersion) {
	}

}
