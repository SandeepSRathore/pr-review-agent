package com.sandeeprathore.reviewagent.review.diff;

import java.util.List;
import java.util.Optional;

/** A parsed unified diff: every file the change touches. */
public record PatchSet(List<FileDiff> files) {

	public PatchSet {
		files = List.copyOf(files);
	}

	public Optional<FileDiff> file(String path) {
		return files.stream().filter(file -> file.path().equals(path)).findFirst();
	}

	public List<FileDiff> reviewableFiles() {
		return files.stream().filter(FileDiff::isReviewable).toList();
	}

}
