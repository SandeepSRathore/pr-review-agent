package com.sandeeprathore.reviewagent.review.diff;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses unified diffs as produced by {@code git diff}, {@code git format-patch} and GitHub's
 * {@code .diff}/{@code .patch} URLs.
 *
 * <p>Hunk bodies are consumed by the line counts in their {@code @@} header rather than by prefix alone,
 * so a removed line whose text starts with {@code "-- "} or a {@code format-patch} signature after the
 * last hunk can't be mistaken for file headers.
 */
public final class UnifiedDiffParser {

	private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@(.*)$");

	private static final Pattern GIT_HEADER = Pattern.compile("^diff --git (\"?a/.+?\"?) (\"?b/.+\"?)$");

	private static final String DEV_NULL = "/dev/null";

	public PatchSet parse(String patch) {
		if (patch == null || patch.isBlank()) {
			throw new InvalidPatchException("Patch is empty");
		}
		var state = new State();
		for (String line : patch.split("\r?\n", -1)) {
			state.accept(line);
		}
		state.finishFile();
		if (state.files.isEmpty()) {
			throw new InvalidPatchException("No file changes found; expected a unified diff");
		}
		return new PatchSet(state.files);
	}

	private static final class State {

		private final List<FileDiff> files = new ArrayList<>();

		private FileBuilder file;

		private HunkBuilder hunk;

		void accept(String line) {
			if (line.startsWith("diff --git ")) {
				finishFile();
				file = FileBuilder.fromGitHeader(line);
				return;
			}
			if (hunk != null) {
				if (hunk.accept(line)) {
					if (!hunk.expectsMoreLines()) {
						finishHunk();
					}
					return;
				}
				// A line that doesn't fit the hunk means the counts were wrong; close it and re-read the line.
				finishHunk();
			}
			if (line.startsWith("\\")) {
				return; // "\ No newline at end of file" after a hunk's last line
			}
			Matcher hunkHeader = HUNK_HEADER.matcher(line);
			if (hunkHeader.matches()) {
				if (file == null) {
					throw new InvalidPatchException("Hunk header without a file header: " + line);
				}
				hunk = HunkBuilder.from(hunkHeader);
				if (!hunk.expectsMoreLines()) {
					finishHunk();
				}
				return;
			}
			if (line.startsWith("--- ")) {
				if (file == null || file.hasHunks()) {
					// Plain "diff -u" output has no "diff --git" line; "---" starts the next file.
					finishFile();
					file = new FileBuilder();
				}
				file.oldPath = stripPrefix(line.substring(4));
				return;
			}
			if (file == null) {
				return; // e-mail headers of a format-patch, or other preamble
			}
			if (line.startsWith("+++ ")) {
				file.newPath = stripPrefix(line.substring(4));
			}
			else if (line.startsWith("new file mode")) {
				file.changeType = ChangeType.ADDED;
			}
			else if (line.startsWith("deleted file mode")) {
				file.changeType = ChangeType.DELETED;
			}
			else if (line.startsWith("rename from ")) {
				file.changeType = ChangeType.RENAMED;
				file.oldPath = unquote(line.substring("rename from ".length()));
			}
			else if (line.startsWith("rename to ")) {
				file.changeType = ChangeType.RENAMED;
				file.newPath = unquote(line.substring("rename to ".length()));
			}
			else if (line.startsWith("Binary files ") || line.equals("GIT binary patch")) {
				file.binary = true;
			}
		}

		void finishHunk() {
			if (hunk != null) {
				file.hunks.add(hunk.build());
				hunk = null;
			}
		}

		void finishFile() {
			if (file != null) {
				finishHunk();
				file.build().ifPresent(files::add);
				file = null;
			}
		}

	}

	private static final class FileBuilder {

		private String oldPath;

		private String newPath;

		private ChangeType changeType;

		private boolean binary;

		private final List<DiffHunk> hunks = new ArrayList<>();

		static FileBuilder fromGitHeader(String line) {
			var builder = new FileBuilder();
			Matcher matcher = GIT_HEADER.matcher(line);
			if (matcher.matches()) {
				// Fallback only: "---"/"+++" or "rename" lines override these when present.
				builder.oldPath = stripPrefix(matcher.group(1));
				builder.newPath = stripPrefix(matcher.group(2));
			}
			return builder;
		}

		boolean hasHunks() {
			return !hunks.isEmpty();
		}

		Optional<FileDiff> build() {
			if (oldPath == null && newPath == null) {
				return Optional.empty();
			}
			ChangeType type = changeType;
			if (type == null) {
				type = DEV_NULL.equals(oldPath) ? ChangeType.ADDED
						: DEV_NULL.equals(newPath) ? ChangeType.DELETED : ChangeType.MODIFIED;
			}
			String path = (type == ChangeType.DELETED || DEV_NULL.equals(newPath) || newPath == null) ? oldPath : newPath;
			String previousPath = type == ChangeType.RENAMED ? oldPath : null;
			return Optional.of(new FileDiff(path, previousPath, type, binary, hunks));
		}

	}

	private static final class HunkBuilder {

		private final int oldStart;

		private final int oldCount;

		private final int newStart;

		private final int newCount;

		private final String sectionHeader;

		private final List<DiffLine> lines = new ArrayList<>();

		private int nextOld;

		private int nextNew;

		private HunkBuilder(int oldStart, int oldCount, int newStart, int newCount, String sectionHeader) {
			this.oldStart = oldStart;
			this.oldCount = oldCount;
			this.newStart = newStart;
			this.newCount = newCount;
			this.sectionHeader = sectionHeader;
			this.nextOld = oldStart;
			this.nextNew = newStart;
		}

		static HunkBuilder from(Matcher header) {
			return new HunkBuilder(Integer.parseInt(header.group(1)), count(header.group(2)),
					Integer.parseInt(header.group(3)), count(header.group(4)), header.group(5));
		}

		private static int count(String group) {
			return group == null ? 1 : Integer.parseInt(group);
		}

		boolean expectsMoreLines() {
			return nextOld < oldStart + oldCount || nextNew < newStart + newCount;
		}

		/** Returns false when the line can't belong to this hunk. */
		boolean accept(String line) {
			if (line.isEmpty()) {
				// Some editors and mail clients strip the single space of an empty context line.
				lines.add(DiffLine.context(nextOld++, nextNew++, ""));
				return true;
			}
			char marker = line.charAt(0);
			String content = line.substring(1);
			switch (marker) {
				case ' ' -> lines.add(DiffLine.context(nextOld++, nextNew++, content));
				case '+' -> lines.add(DiffLine.added(nextNew++, content));
				case '-' -> lines.add(DiffLine.removed(nextOld++, content));
				case '\\' -> {
					// "\ No newline at end of file": metadata, not a line of either version
				}
				default -> {
					return false;
				}
			}
			return true;
		}

		DiffHunk build() {
			return new DiffHunk(oldStart, oldCount, newStart, newCount, sectionHeader, lines);
		}

	}

	private static String stripPrefix(String rawPath) {
		String path = unquote(rawPath.strip());
		int tab = path.indexOf('\t');
		if (tab >= 0) {
			path = path.substring(0, tab); // "diff -u" appends a timestamp after a tab
		}
		if (path.equals(DEV_NULL)) {
			return DEV_NULL;
		}
		if (path.startsWith("a/") || path.startsWith("b/")) {
			return path.substring(2);
		}
		return path;
	}

	private static String unquote(String path) {
		String trimmed = path.strip();
		if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
			return trimmed.substring(1, trimmed.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
		}
		return trimmed;
	}

}
