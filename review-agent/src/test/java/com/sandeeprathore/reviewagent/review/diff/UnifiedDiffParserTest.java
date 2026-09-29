package com.sandeeprathore.reviewagent.review.diff;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import org.springframework.core.io.ClassPathResource;

import com.sandeeprathore.reviewagent.review.diff.DiffLine.Kind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnifiedDiffParserTest {

	private final UnifiedDiffParser parser = new UnifiedDiffParser();

	@Test
	void parsesFormatPatchIgnoringMailHeadersAndSignature() throws IOException {
		String patch = new ClassPathResource("patches/sql-injection.patch").getContentAsString(StandardCharsets.UTF_8);

		PatchSet set = parser.parse(patch);

		assertThat(set.files()).singleElement().satisfies(file -> {
			assertThat(file.path()).isEqualTo("src/main/java/com/example/shop/UserRepository.java");
			assertThat(file.changeType()).isEqualTo(ChangeType.MODIFIED);
			assertThat(file.hunks()).singleElement().satisfies(hunk -> {
				assertThat(hunk.lines()).hasSize(21); // 9 context + 3 removed + 9 added; the "-- " signature is not a line
				assertThat(hunk.lines()).filteredOn(l -> l.kind() == Kind.REMOVED).allMatch(l -> !l.existsInNewFile());
			});
			assertThat(file.hunkContaining(15)).isPresent();
			assertThat(file.hunkContaining(19)).isEmpty();
		});
		DiffLine injection = set.files().getFirst()
			.hunks()
			.getFirst()
			.lines()
			.stream()
			.filter(l -> l.newLine() == 15)
			.findFirst()
			.orElseThrow();
		assertThat(injection.kind()).isEqualTo(Kind.ADDED);
		assertThat(injection.content()).contains("executeQuery");
	}

	@Test
	void countsHunkLinesSoContentLookingLikeHeadersIsNotMisread() {
		String patch = """
				diff --git a/notes.sql b/notes.sql
				--- a/notes.sql
				+++ b/notes.sql
				@@ -1,3 +1,3 @@
				 SELECT 1;
				--- old comment
				+++ new comment
				 SELECT 2;
				""";

		FileDiff file = parser.parse(patch).files().getFirst();

		assertThat(file.path()).isEqualTo("notes.sql");
		assertThat(file.hunks().getFirst().lines()).extracting(DiffLine::kind, DiffLine::content)
			.containsExactly(org.assertj.core.groups.Tuple.tuple(Kind.CONTEXT, "SELECT 1;"),
					org.assertj.core.groups.Tuple.tuple(Kind.REMOVED, "-- old comment"),
					org.assertj.core.groups.Tuple.tuple(Kind.ADDED, "++ new comment"),
					org.assertj.core.groups.Tuple.tuple(Kind.CONTEXT, "SELECT 2;"));
	}

	@Test
	void recognisesAddedDeletedRenamedAndBinaryFiles() {
		String patch = """
				diff --git a/src/New.java b/src/New.java
				new file mode 100644
				index 0000000..1111111
				--- /dev/null
				+++ b/src/New.java
				@@ -0,0 +1,2 @@
				+class New {
				+}
				diff --git a/src/Old.java b/src/Old.java
				deleted file mode 100644
				index 2222222..0000000
				--- a/src/Old.java
				+++ /dev/null
				@@ -1,1 +0,0 @@
				-class Old {}
				diff --git a/src/A.java b/src/B.java
				similarity index 90%
				rename from src/A.java
				rename to src/B.java
				index 3333333..4444444 100644
				--- a/src/A.java
				+++ b/src/B.java
				@@ -1 +1 @@
				-class A {}
				+class B {}
				diff --git a/logo.png b/logo.png
				index 5555555..6666666 100644
				Binary files a/logo.png and b/logo.png differ
				""";

		PatchSet set = parser.parse(patch);

		assertThat(set.files()).extracting(FileDiff::path, FileDiff::changeType, FileDiff::isReviewable)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("src/New.java", ChangeType.ADDED, true),
					org.assertj.core.groups.Tuple.tuple("src/Old.java", ChangeType.DELETED, false),
					org.assertj.core.groups.Tuple.tuple("src/B.java", ChangeType.RENAMED, true),
					org.assertj.core.groups.Tuple.tuple("logo.png", ChangeType.MODIFIED, false));
		assertThat(set.file("src/B.java")).get().extracting(FileDiff::previousPath).isEqualTo("src/A.java");
		assertThat(set.reviewableFiles()).extracting(FileDiff::path).containsExactly("src/New.java", "src/B.java");
	}

	@Test
	void parsesPlainUnifiedDiffWithoutGitHeaders() {
		String patch = "--- a/one.txt\t2026-09-01 10:00:00\n+++ b/one.txt\t2026-09-02 10:00:00\n@@ -1 +1,2 @@\n x\n+y\n"
				+ "--- a/two.txt\n+++ b/two.txt\n@@ -5,2 +5 @@\n a\n-b\n";

		PatchSet set = parser.parse(patch);

		assertThat(set.files()).extracting(FileDiff::path).containsExactly("one.txt", "two.txt");
		assertThat(set.file("two.txt").orElseThrow().hunks().getFirst().lines()).hasSize(2);
	}

	@Test
	void ignoresNoNewlineMarkerAndTreatsStrippedBlankLinesAsContext() {
		String patch = "diff --git a/f.txt b/f.txt\n--- a/f.txt\n+++ b/f.txt\n@@ -1,3 +1,3 @@\n a\n\n-c\n\\ No newline at end of file\n+d\n\\ No newline at end of file\n";

		DiffHunk hunk = parser.parse(patch).files().getFirst().hunks().getFirst();

		assertThat(hunk.lines()).extracting(DiffLine::kind).containsExactly(Kind.CONTEXT, Kind.CONTEXT, Kind.REMOVED, Kind.ADDED);
		assertThat(hunk.lines().get(3).newLine()).isEqualTo(3);
	}

	@Test
	void rejectsInputThatIsNotADiff() {
		assertThatThrownBy(() -> parser.parse("  ")).isInstanceOf(InvalidPatchException.class);
		assertThatThrownBy(() -> parser.parse("hello world\nnot a diff")).isInstanceOf(InvalidPatchException.class);
		assertThatThrownBy(() -> parser.parse("@@ -1 +1 @@\n-a\n+b\n")).isInstanceOf(InvalidPatchException.class)
			.hasMessageContaining("without a file header");
	}

}
