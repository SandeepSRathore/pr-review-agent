package com.sandeeprathore.reviewagent.review.diff;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Packs file diffs into batches that each fit a token budget, so a large pull request becomes several
 * model calls instead of one that overflows the context window or dilutes attention. Files stay whole
 * where possible (the reviewer sees related hunks together); a file too big for one batch is split
 * at hunk boundaries.
 */
public final class DiffBatcher {

	private final AnnotatedDiffRenderer renderer;

	private final ToIntFunction<String> tokenCounter;

	public DiffBatcher(AnnotatedDiffRenderer renderer, ToIntFunction<String> tokenCounter) {
		this.renderer = renderer;
		this.tokenCounter = tokenCounter;
	}

	public List<List<FileDiff>> batch(List<FileDiff> files, int maxTokensPerBatch) {
		List<List<FileDiff>> batches = new ArrayList<>();
		List<FileDiff> current = new ArrayList<>();
		int currentTokens = 0;
		for (FileDiff file : files) {
			for (FileDiff piece : splitToFit(file, maxTokensPerBatch)) {
				int tokens = tokens(piece);
				if (!current.isEmpty() && currentTokens + tokens > maxTokensPerBatch) {
					batches.add(current);
					current = new ArrayList<>();
					currentTokens = 0;
				}
				current.add(piece);
				currentTokens += tokens;
			}
		}
		if (!current.isEmpty()) {
			batches.add(current);
		}
		return batches;
	}

	/** One piece if the file fits; otherwise consecutive runs of hunks. A single oversized hunk stays whole. */
	private List<FileDiff> splitToFit(FileDiff file, int maxTokens) {
		if (tokens(file) <= maxTokens || file.hunks().size() <= 1) {
			return List.of(file);
		}
		List<FileDiff> pieces = new ArrayList<>();
		List<DiffHunk> run = new ArrayList<>();
		for (DiffHunk hunk : file.hunks()) {
			List<DiffHunk> candidate = new ArrayList<>(run);
			candidate.add(hunk);
			if (!run.isEmpty() && tokens(file.withHunks(candidate)) > maxTokens) {
				pieces.add(file.withHunks(run));
				run = new ArrayList<>();
			}
			run.add(hunk);
		}
		pieces.add(file.withHunks(run));
		return pieces;
	}

	private int tokens(FileDiff file) {
		return tokenCounter.applyAsInt(renderer.render(List.of(file)));
	}

}
