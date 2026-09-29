package com.sandeeprathore.reviewagent.review.finding;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sandeeprathore.reviewagent.review.diff.PatchSet;
import com.sandeeprathore.reviewagent.review.diff.UnifiedDiffParser;

import static org.assertj.core.api.Assertions.assertThat;

class FindingValidatorTest {

	// New-file lines 1-3 are in the first hunk, 20-21 in the second; line 10 is outside the diff.
	private static final PatchSet PATCH = new UnifiedDiffParser().parse("""
			diff --git a/Svc.java b/Svc.java
			--- a/Svc.java
			+++ b/Svc.java
			@@ -1,2 +1,3 @@
			 a
			+b
			 c
			@@ -19,2 +20,2 @@
			-x
			+y
			 z
			diff --git a/Gone.java b/Gone.java
			deleted file mode 100644
			--- a/Gone.java
			+++ /dev/null
			@@ -1 +0,0 @@
			-gone
			""");

	private final FindingValidator validator = new FindingValidator(0.6, 3);

	@Test
	void acceptsWellFormedFindingsAndRanksThemBySeverity() {
		Finding minor = finding("Svc.java", 2, 2, Severity.MINOR, 0.9);
		Finding critical = finding("Svc.java", 20, 21, Severity.CRITICAL, 0.8);

		ValidatedFindings result = validator.validate(PATCH, List.of(minor, critical));

		assertThat(result.accepted()).containsExactly(critical, minor);
		assertThat(result.rejected()).isEmpty();
	}

	@Test
	void rejectsFindingsGitHubCouldNotAnchorOrThatAreTooUncertain() {
		List<Finding> bad = List.of(finding("Nope.java", 2, 2, Severity.MAJOR, 0.9),
				finding("Gone.java", 1, 1, Severity.MAJOR, 0.9), finding("Svc.java", 10, 10, Severity.MAJOR, 0.9),
				finding("Svc.java", 2, 20, Severity.MAJOR, 0.9), finding("Svc.java", 3, 2, Severity.MAJOR, 0.9),
				finding("Svc.java", 2, 2, Severity.MAJOR, 0.4), finding("Svc.java", 2, 2, Severity.MAJOR, 1.5));

		ValidatedFindings result = validator.validate(PATCH, bad);

		assertThat(result.accepted()).isEmpty();
		assertThat(result.rejected()).extracting(RejectedFinding::reason)
			.containsExactly("file is not part of the reviewable diff", "file is not part of the reviewable diff",
					"line 10 is not in the new side of the diff", "lines 2-20 span more than one hunk",
					"invalid line range 3-2", "confidence 0.40 below threshold 0.60", "confidence outside 0..1");
	}

	@Test
	void keepsTheMostConfidentOfDuplicateFindings() {
		Finding weaker = finding("Svc.java", 2, 2, Severity.MAJOR, 0.7);
		Finding stronger = finding("Svc.java", 2, 3, Severity.MAJOR, 0.95);

		ValidatedFindings result = validator.validate(PATCH, List.of(weaker, stronger));

		assertThat(result.accepted()).containsExactly(stronger);
		assertThat(result.rejected()).extracting(RejectedFinding::finding).containsExactly(weaker);
	}

	@Test
	void capsTheNumberOfFindingsKeepingTheMostImportant() {
		List<Finding> many = List.of(finding("Svc.java", 1, 1, Severity.NIT, 0.9),
				finding("Svc.java", 2, 2, Severity.CRITICAL, 0.9), finding("Svc.java", 3, 3, Severity.MINOR, 0.9),
				finding("Svc.java", 20, 20, Severity.MAJOR, 0.9));

		ValidatedFindings result = validator.validate(PATCH, many);

		assertThat(result.accepted()).extracting(Finding::severity)
			.containsExactly(Severity.CRITICAL, Severity.MAJOR, Severity.MINOR);
		assertThat(result.rejected()).singleElement().satisfies(r -> assertThat(r.reason()).startsWith("over the per-review limit"));
	}

	private static Finding finding(String file, int start, int end, Severity severity, double confidence) {
		return new Finding(file, start, end, severity, Category.CORRECTNESS, "Title", "Rationale", null, confidence);
	}

}
