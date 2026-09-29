package com.sandeeprathore.reviewagent.orchestration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ClassPathResource;

import com.sandeeprathore.reviewagent.llm.ModelProperties;
import com.sandeeprathore.reviewagent.llm.ModelProperties.ModelSpec;
import com.sandeeprathore.reviewagent.llm.ModelProvider;
import com.sandeeprathore.reviewagent.llm.ModelRouter;
import com.sandeeprathore.reviewagent.llm.ModelTier;
import com.sandeeprathore.reviewagent.llm.PromptCatalog;
import com.sandeeprathore.reviewagent.review.diff.AnnotatedDiffRenderer;
import com.sandeeprathore.reviewagent.review.diff.DiffBatcher;
import com.sandeeprathore.reviewagent.review.diff.UnifiedDiffParser;
import com.sandeeprathore.reviewagent.review.finding.Category;
import com.sandeeprathore.reviewagent.review.finding.FindingValidator;
import com.sandeeprathore.reviewagent.review.finding.Severity;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the real pipeline (prompts, structured output, validation) against a scripted model. */
class ReviewServiceTest {

	private static final String MODEL_ANSWER = """
			{
			  "findings": [
			    {
			      "file": "src/main/java/com/example/shop/UserRepository.java",
			      "startLine": 15, "endLine": 15,
			      "severity": "CRITICAL", "category": "SECURITY",
			      "title": "SQL injection through the name parameter",
			      "rationale": "name is concatenated into the SQL string passed to executeQuery.",
			      "suggestedFix": "",
			      "confidence": 0.97
			    },
			    {
			      "file": "src/main/java/com/example/shop/UserRepository.java",
			      "startLine": 7, "endLine": 7,
			      "severity": "NIT", "category": "MAINTAINABILITY",
			      "title": "Anchored on a line the model made up",
			      "rationale": "Line 7 is blank context, but line 40 below is not in the diff at all.",
			      "suggestedFix": "",
			      "confidence": 0.3
			    },
			    {
			      "file": "src/main/java/com/example/shop/UserRepository.java",
			      "startLine": 40, "endLine": 41,
			      "severity": "MAJOR", "category": "CORRECTNESS",
			      "title": "Outside the diff",
			      "rationale": "Should be rejected by the validator.",
			      "suggestedFix": "",
			      "confidence": 0.9
			    }
			  ],
			  "summary": "One critical SQL injection."
			}
			""";

	@Test
	void reviewsAPatchAndKeepsOnlyFindingsThatCanBePosted() throws IOException {
		var model = new ScriptedChatModel(MODEL_ANSWER);
		ReviewService service = service(model);
		String patch = new ClassPathResource("patches/sql-injection.patch").getContentAsString(StandardCharsets.UTF_8);

		ReviewReport report = service
			.review(new ReviewRequest("Add user search", "Adds findByName.", new UnifiedDiffParser().parse(patch)));

		assertThat(report.findings()).singleElement().satisfies(finding -> {
			assertThat(finding.severity()).isEqualTo(Severity.CRITICAL);
			assertThat(finding.category()).isEqualTo(Category.SECURITY);
			assertThat(finding.startLine()).isEqualTo(15);
		});
		assertThat(report.rejected()).hasSize(2);
		assertThat(report.summary()).isEqualTo("One critical SQL injection.");
		assertThat(report.stats().batches()).isEqualTo(1);
		assertThat(report.stats().model()).isEqualTo("anthropic/test-deep-model");
		assertThat(report.stats().promptVersion()).hasSize(8);

		// An answer that follows the field descriptions must pass schema validation without a repair round-trip.
		assertThat(model.prompts).hasSize(1);
		String prompt = model.prompts.getFirst();
		assertThat(prompt).contains("<untrusted_diff>")
			.contains("  15 | +\t\tResultSet rows = statement.executeQuery(")
			.contains("Title: Add user search");
	}

	@Test
	void skipsTheModelWhenNothingIsReviewable() {
		var model = new ScriptedChatModel(MODEL_ANSWER);
		String deletion = """
				diff --git a/Old.java b/Old.java
				deleted file mode 100644
				--- a/Old.java
				+++ /dev/null
				@@ -1 +0,0 @@
				-class Old {}
				""";

		ReviewReport report = service(model).review(new ReviewRequest(null, null, new UnifiedDiffParser().parse(deletion)));

		assertThat(report.findings()).isEmpty();
		assertThat(report.stats().filesSkipped()).isEqualTo(1);
		assertThat(model.prompts).isEmpty();
	}

	private static ReviewService service(ChatModel model) {
		var properties = new ReviewProperties(0.6, 25, 60_000, 2, 1_000_000, List.of("**/package-lock.json"));
		var modelProperties = new ModelProperties(List.of(ModelProvider.ANTHROPIC),
				Map.of(ModelTier.DEEP, Map.of(ModelProvider.ANTHROPIC, new ModelSpec("test-deep-model", null, 1000))), java.time.Duration.ofSeconds(30), 0);
		var router = new ModelRouter(Map.of(ModelProvider.ANTHROPIC, model), modelProperties, ObservationRegistry.NOOP);
		var renderer = new AnnotatedDiffRenderer();
		var prompts = new PromptCatalog();
		return new ReviewService(new ReviewerAgent(router, prompts, renderer, properties),
				new DiffBatcher(renderer, String::length), new FindingValidator(0.6, 25),
				new ReviewScope(properties.ignorePaths()), prompts, properties);
	}

	/** Records every prompt it receives and always answers with the same text. */
	static final class ScriptedChatModel implements ChatModel {

		final List<String> prompts = new ArrayList<>();

		private final String answer;

		ScriptedChatModel(String answer) {
			this.answer = answer;
		}

		@Override
		public ChatResponse call(Prompt prompt) {
			prompts.add(prompt.getContents());
			return new ChatResponse(List.of(new Generation(new AssistantMessage(answer))));
		}

	}

}
