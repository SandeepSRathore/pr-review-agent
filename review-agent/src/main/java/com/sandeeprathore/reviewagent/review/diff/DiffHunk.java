package com.sandeeprathore.reviewagent.review.diff;

import java.util.List;

/** An {@code @@ -a,b +c,d @@} block of a unified diff. */
public record DiffHunk(int oldStart, int oldCount, int newStart, int newCount, String sectionHeader,
		List<DiffLine> lines) {

	public DiffHunk {
		sectionHeader = sectionHeader == null ? "" : sectionHeader;
		lines = List.copyOf(lines);
	}

	public boolean containsNewLine(int lineNumber) {
		return lines.stream().anyMatch(line -> line.existsInNewFile() && line.newLine() == lineNumber);
	}

	public String header() {
		String header = "@@ -%d,%d +%d,%d @@".formatted(oldStart, oldCount, newStart, newCount);
		return sectionHeader.isBlank() ? header : header + " " + sectionHeader.strip();
	}

}
