package com.sandeeprathore.reviewagent.review.finding;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** What one reviewer call returns: the model's structured output. */
public record ReviewFindings(
		@JsonPropertyDescription("Problems found in this diff, most severe first; an empty list when the change is clean") List<Finding> findings,
		@JsonPropertyDescription("Two or three sentences for the pull request author on the overall risk of this change; say plainly when nothing needs fixing") String summary) {

	public ReviewFindings {
		findings = findings == null ? List.of() : List.copyOf(findings);
		summary = summary == null ? "" : summary;
	}

}
