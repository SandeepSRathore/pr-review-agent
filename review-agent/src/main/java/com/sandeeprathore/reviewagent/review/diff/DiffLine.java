package com.sandeeprathore.reviewagent.review.diff;

/**
 * One line inside a hunk. Line numbers are 1-based; {@code 0} means the line does not exist on that
 * side (an added line has no old number, a removed line has no new number).
 */
public record DiffLine(Kind kind, int oldLine, int newLine, String content) {

	public enum Kind {
		CONTEXT, ADDED, REMOVED
	}

	public static DiffLine context(int oldLine, int newLine, String content) {
		return new DiffLine(Kind.CONTEXT, oldLine, newLine, content);
	}

	public static DiffLine added(int newLine, String content) {
		return new DiffLine(Kind.ADDED, 0, newLine, content);
	}

	public static DiffLine removed(int oldLine, String content) {
		return new DiffLine(Kind.REMOVED, oldLine, 0, content);
	}

	/** GitHub only accepts review comments on lines that exist in the new version of the file. */
	public boolean existsInNewFile() {
		return newLine > 0;
	}

}
