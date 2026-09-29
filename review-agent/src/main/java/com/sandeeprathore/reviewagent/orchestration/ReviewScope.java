package com.sandeeprathore.reviewagent.orchestration;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;

import com.sandeeprathore.reviewagent.review.diff.FileDiff;

/**
 * Decides which files are worth a model's attention. Lock files, minified bundles and build output
 * cost tokens and produce noise, so they never reach a reviewer.
 */
public final class ReviewScope {

	private final List<PathMatcher> ignored;

	public ReviewScope(List<String> ignoreGlobs) {
		this.ignored = ignoreGlobs.stream().map(glob -> FileSystems.getDefault().getPathMatcher("glob:" + glob)).toList();
	}

	public boolean includes(FileDiff file) {
		// Leading "/" so "**/x" patterns also match files at the repository root.
		Path path = Path.of("/" + file.path());
		return file.isReviewable() && ignored.stream().noneMatch(matcher -> matcher.matches(path));
	}

}
