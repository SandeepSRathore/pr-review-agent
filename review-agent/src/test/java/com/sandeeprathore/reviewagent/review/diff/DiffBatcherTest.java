package com.sandeeprathore.reviewagent.review.diff;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiffBatcherTest {

	// One "token" per rendered character keeps the arithmetic in these tests obvious.
	private final DiffBatcher batcher = new DiffBatcher(new AnnotatedDiffRenderer(), String::length);

	private final AnnotatedDiffRenderer renderer = new AnnotatedDiffRenderer();

	@Test
	void keepsSmallFilesTogetherInOneBatch() {
		List<FileDiff> files = List.of(file("A.java", 1), file("B.java", 1));

		List<List<FileDiff>> batches = batcher.batch(files, 10_000);

		assertThat(batches).hasSize(1);
		assertThat(batches.getFirst()).hasSize(2);
	}

	@Test
	void startsANewBatchWhenTheBudgetWouldBeExceeded() {
		FileDiff a = file("A.java", 1);
		FileDiff b = file("B.java", 1);
		int oneFile = renderer.render(List.of(a)).length();

		List<List<FileDiff>> batches = batcher.batch(List.of(a, b), oneFile + 5);

		assertThat(batches).hasSize(2);
	}

	@Test
	void splitsAnOversizedFileAtHunkBoundaries() {
		FileDiff big = file("Big.java", 3);
		int oneHunk = renderer.render(List.of(big.withHunks(List.of(big.hunks().getFirst())))).length();

		List<List<FileDiff>> batches = batcher.batch(List.of(big), oneHunk + 5);

		assertThat(batches).hasSize(3).allSatisfy(batch -> {
			assertThat(batch).singleElement().satisfies(piece -> {
				assertThat(piece.path()).isEqualTo("Big.java");
				assertThat(piece.hunks()).hasSize(1);
			});
		});
	}

	private static FileDiff file(String path, int hunkCount) {
		List<DiffHunk> hunks = java.util.stream.IntStream.range(0, hunkCount).mapToObj(i -> {
			int start = 1 + i * 100;
			return new DiffHunk(start, 1, start, 2, "",
					List.of(DiffLine.context(start, start, "int a" + i + " = 0;"), DiffLine.added(start + 1, "int b" + i + " = 1;")));
		}).toList();
		return new FileDiff(path, null, ChangeType.MODIFIED, false, hunks);
	}

}
