package com.sandeeprathore.reviewagent.orchestration;

import com.sandeeprathore.reviewagent.llm.ModelTier;

/**
 * A reviewer persona: the same agent code, pointed at a different concern. The specialist reviewers
 * (security, performance, correctness, conventions) are further profiles run in parallel.
 *
 * @param id stable identifier, recorded with each finding
 * @param displayName how the reviewer introduces itself in the system prompt
 * @param focus one or two sentences telling the reviewer what to concentrate on
 * @param tier model tier the reviewer runs on
 */
public record ReviewerProfile(String id, String displayName, String focus, ModelTier tier) {

	public static final ReviewerProfile GENERAL = new ReviewerProfile("general", "the lead reviewer",
			"Cover every category: security, correctness, performance, concurrency, resource management, "
					+ "error handling, testing and maintainability.",
			ModelTier.DEEP);

}
