package com.sandeeprathore.reviewagent.review.diff;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Renders file diffs for a model to read. Models are unreliable at counting lines in a raw unified
 * diff, so every line gets an explicit gutter with its line number in the new file; findings are then
 * anchored on numbers the model can copy rather than compute.
 *
 * <pre>
 * &lt;file path="src/main/java/Foo.java" change="MODIFIED"&gt;
 * @@ -10,4 +10,5 @@ class Foo
 *   10 |   String find(String id) {
 *   11 | +   String sql = "SELECT * FROM t WHERE id = '" + id + "'";
 *      | -   return repo.find(id);
 *   12 |   }
 * &lt;/file&gt;
 * </pre>
 *
 * The output is meant to sit inside an {@code <untrusted_diff>} block. Any text in the diff that
 * could close that block early is neutralised so PR content can't break out of the data section.
 */
public final class AnnotatedDiffRenderer {

	public static final String UNTRUSTED_BLOCK = "untrusted_diff";

	private static final Pattern DELIMITER_BREAKOUT = Pattern
		.compile("</\\s*(" + UNTRUSTED_BLOCK + "|untrusted_pr_description|file)\\b", Pattern.CASE_INSENSITIVE);

	public String render(List<FileDiff> files) {
		var out = new StringBuilder();
		for (FileDiff file : files) {
			render(file, out);
		}
		return out.toString();
	}

	private void render(FileDiff file, StringBuilder out) {
		out.append("<file path=\"").append(file.path()).append("\" change=\"").append(file.changeType()).append('"');
		if (file.previousPath() != null) {
			out.append(" previous_path=\"").append(file.previousPath()).append('"');
		}
		out.append(">\n");
		int width = gutterWidth(file);
		for (DiffHunk hunk : file.hunks()) {
			out.append(neutralise(hunk.header())).append('\n');
			for (DiffLine line : hunk.lines()) {
				String number = line.existsInNewFile() ? Integer.toString(line.newLine()) : "";
				out.append(" ".repeat(width - number.length())).append(number).append(" | ");
				out.append(switch (line.kind()) {
					case ADDED -> '+';
					case REMOVED -> '-';
					case CONTEXT -> ' ';
				});
				out.append(neutralise(line.content())).append('\n');
			}
		}
		out.append("</file>\n");
	}

	private static int gutterWidth(FileDiff file) {
		int max = file.hunks().stream().mapToInt(h -> h.newStart() + h.newCount()).max().orElse(0);
		return Math.max(4, Integer.toString(max).length());
	}

	/** Public for the PR description, which is wrapped in its own untrusted block. */
	public static String neutralise(String text) {
		return DELIMITER_BREAKOUT.matcher(text).replaceAll(match -> "<\\\\/" + match.group(1));
	}

}
