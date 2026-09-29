package com.sandeeprathore.reviewagent.review.diff;

import java.util.List;
import java.util.Optional;

/**
 * The changes to one file. {@code path} is the file's path after the change (its old path when the
 * file was deleted); {@code previousPath} is only set for renames.
 */
public record FileDiff(String path, String previousPath, ChangeType changeType, boolean binary, List<DiffHunk> hunks) {

	public FileDiff {
		hunks = List.copyOf(hunks);
	}

	/** Deleted and binary files have nothing a reviewer can anchor a comment on. */
	public boolean isReviewable() {
		return !binary && changeType != ChangeType.DELETED && !hunks.isEmpty();
	}

	public Optional<DiffHunk> hunkContaining(int newLine) {
		return hunks.stream().filter(hunk -> hunk.containsNewLine(newLine)).findFirst();
	}

	public FileDiff withHunks(List<DiffHunk> subset) {
		return new FileDiff(path, previousPath, changeType, binary, subset);
	}

}
