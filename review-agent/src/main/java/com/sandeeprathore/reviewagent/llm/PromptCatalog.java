package com.sandeeprathore.reviewagent.llm;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Loads the prompt templates in {@code classpath:prompts/*.st} and derives a version from their
 * content. The version is stamped on every review and eval run, so a change in quality can be traced
 * to the prompt edit that caused it without anyone remembering to bump a number.
 */
@Component
public class PromptCatalog {

	private final Map<String, String> templates;

	private final String version;

	public PromptCatalog() {
		this.templates = load();
		this.version = hash(templates);
	}

	public String template(String name) {
		String template = templates.get(name);
		if (template == null) {
			throw new IllegalArgumentException("No prompt template named " + name + "; have " + templates.keySet());
		}
		return template;
	}

	public String version() {
		return version;
	}

	private static Map<String, String> load() {
		try {
			Map<String, String> loaded = new TreeMap<>();
			for (Resource resource : new PathMatchingResourcePatternResolver().getResources("classpath*:prompts/*.st")) {
				String name = resource.getFilename().replaceFirst("\\.st$", "");
				loaded.put(name, resource.getContentAsString(StandardCharsets.UTF_8));
			}
			return Map.copyOf(loaded);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not load prompt templates", ex);
		}
	}

	private static String hash(Map<String, String> templates) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			new TreeMap<>(templates).forEach((name, text) -> {
				digest.update(name.getBytes(StandardCharsets.UTF_8));
				digest.update(text.getBytes(StandardCharsets.UTF_8));
			});
			return HexFormat.of().formatHex(digest.digest()).substring(0, 8);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
