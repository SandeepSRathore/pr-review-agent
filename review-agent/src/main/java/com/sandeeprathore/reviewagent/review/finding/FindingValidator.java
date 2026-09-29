package com.sandeeprathore.reviewagent.review.finding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.sandeeprathore.reviewagent.review.diff.DiffHunk;
import com.sandeeprathore.reviewagent.review.diff.FileDiff;
import com.sandeeprathore.reviewagent.review.diff.PatchSet;

/**
 * Deterministic checks on model output before anything reaches a developer. The model decides what is
 * wrong; this class decides whether the answer is well-formed enough to post: the file is in the diff,
 * the lines exist in the new file and sit in one hunk (GitHub rejects anything else), and confidence
 * clears the bar.
 */
public final class FindingValidator {

	private static final Comparator<Finding> MOST_IMPORTANT_FIRST = Comparator.comparing(Finding::severity)
		.thenComparing(Comparator.comparingDouble(Finding::confidence).reversed())
		.thenComparing(Finding::file)
		.thenComparingInt(Finding::startLine);

	private final double minConfidence;

	private final int maxFindings;

	public FindingValidator(double minConfidence, int maxFindings) {
		this.minConfidence = minConfidence;
		this.maxFindings = maxFindings;
	}

	public ValidatedFindings validate(PatchSet patch, List<Finding> findings) {
		List<Finding> accepted = new ArrayList<>();
		List<RejectedFinding> rejected = new ArrayList<>();
		for (Finding finding : findings) {
			Optional<String> problem = problemWith(patch, finding);
			if (problem.isPresent()) {
				rejected.add(new RejectedFinding(finding, problem.get()));
				continue;
			}
			Optional<Finding> duplicate = accepted.stream().filter(finding::sameIssueAs).findFirst();
			if (duplicate.isEmpty()) {
				accepted.add(finding);
			}
			else if (finding.confidence() > duplicate.get().confidence()) {
				accepted.remove(duplicate.get());
				accepted.add(finding);
				rejected.add(new RejectedFinding(duplicate.get(), "duplicate of a higher-confidence finding"));
			}
			else {
				rejected.add(new RejectedFinding(finding, "duplicate of a higher-confidence finding"));
			}
		}
		accepted.sort(MOST_IMPORTANT_FIRST);
		if (accepted.size() > maxFindings) {
			accepted.subList(maxFindings, accepted.size())
				.forEach(f -> rejected.add(new RejectedFinding(f, "over the per-review limit of " + maxFindings)));
			accepted = new ArrayList<>(accepted.subList(0, maxFindings));
		}
		return new ValidatedFindings(accepted, rejected);
	}

	private Optional<String> problemWith(PatchSet patch, Finding finding) {
		if (finding.file() == null || finding.severity() == null || finding.category() == null) {
			return Optional.of("missing file, severity or category");
		}
		if (isBlank(finding.title()) || isBlank(finding.rationale())) {
			return Optional.of("missing title or rationale");
		}
		if (Double.isNaN(finding.confidence()) || finding.confidence() < 0 || finding.confidence() > 1) {
			return Optional.of("confidence outside 0..1");
		}
		if (finding.confidence() < minConfidence) {
			return Optional.of("confidence %.2f below threshold %.2f".formatted(finding.confidence(), minConfidence));
		}
		Optional<FileDiff> file = patch.file(finding.file()).filter(FileDiff::isReviewable);
		if (file.isEmpty()) {
			return Optional.of("file is not part of the reviewable diff");
		}
		if (finding.startLine() < 1 || finding.endLine() < finding.startLine()) {
			return Optional.of("invalid line range %d-%d".formatted(finding.startLine(), finding.endLine()));
		}
		Optional<DiffHunk> hunk = file.get().hunkContaining(finding.startLine());
		if (hunk.isEmpty()) {
			return Optional.of("line %d is not in the new side of the diff".formatted(finding.startLine()));
		}
		if (!hunk.get().containsNewLine(finding.endLine())) {
			return Optional.of("lines %d-%d span more than one hunk".formatted(finding.startLine(), finding.endLine()));
		}
		return Optional.empty();
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

}
