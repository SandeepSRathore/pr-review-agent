package com.sandeeprathore.reviewagent.review.finding;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * One problem a reviewer found. This record doubles as the model's output schema, so the
 * {@link JsonPropertyDescription} texts are prompt: they tell the model what each field must contain.
 */
public record Finding(
		@JsonPropertyDescription("Path of the file, copied exactly from the path attribute of its <file> element") String file,
		@JsonPropertyDescription("First line of the problem: the new-file line number shown in the gutter, never a removed line") int startLine,
		@JsonPropertyDescription("Last line of the problem; equal to startLine for a single line, and in the same hunk as startLine") int endLine,
		Severity severity,
		Category category,
		@JsonPropertyDescription("Headline for the finding, under 80 characters") String title,
		@JsonPropertyDescription("Why this is a problem and what concretely goes wrong, naming the identifiers involved. Two to four sentences.") String rationale,
		@JsonPropertyDescription("Replacement for exactly lines startLine..endLine as they should read after the fix, with original indentation and no diff markers or line numbers; an empty string when the fix isn't a mechanical edit") String suggestedFix,
		@JsonPropertyDescription("0.0 to 1.0: how sure you are that this is a real problem in this change") double confidence) {

	/**
	 * The schema has no optional fields (strict structured-output modes reject them), so "no fix" arrives
	 * as an empty string.
	 */
	public boolean hasSuggestedFix() {
		return suggestedFix != null && !suggestedFix.isBlank();
	}

	/** Same place and same kind of problem: reviewers (or batches) reporting the same thing twice. */
	public boolean sameIssueAs(Finding other) {
		return file.equals(other.file) && startLine == other.startLine && category == other.category;
	}

}
