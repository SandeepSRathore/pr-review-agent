package com.sandeeprathore.reviewagent.orchestration;

import java.util.List;
import java.util.Optional;

import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import com.sandeeprathore.reviewagent.llm.ModelRouter;
import com.sandeeprathore.reviewagent.llm.PromptCatalog;
import com.sandeeprathore.reviewagent.llm.RoutedModel;
import com.sandeeprathore.reviewagent.review.diff.AnnotatedDiffRenderer;
import com.sandeeprathore.reviewagent.review.diff.FileDiff;
import com.sandeeprathore.reviewagent.review.finding.ReviewFindings;

/**
 * Reviews one batch of a diff through the eyes of one {@link ReviewerProfile}.
 *
 * <p>The PR text and diff go into the prompt inside untrusted-content blocks (see
 * {@code prompts/review-user.st}); the model answers with {@link ReviewFindings} as structured output.
 * {@link StructuredOutputValidationAdvisor} re-prompts with the schema error when the answer doesn't
 * parse, which is cheaper than failing the whole review over one malformed response.
 */
@Component
public class ReviewerAgent {

	private final ModelRouter router;

	private final PromptCatalog prompts;

	private final AnnotatedDiffRenderer renderer;

	private final ReviewProperties properties;

	public ReviewerAgent(ModelRouter router, PromptCatalog prompts, AnnotatedDiffRenderer renderer,
			ReviewProperties properties) {
		this.router = router;
		this.prompts = prompts;
		this.renderer = renderer;
		this.properties = properties;
	}

	public Result review(ReviewerProfile profile, ReviewRequest request, List<FileDiff> batch, int batchNumber,
			int batchCount) {
		RoutedModel model = router.route(profile.tier());
		ResponseEntity<ChatResponse, ReviewFindings> response = model.client()
			.prompt()
			.system(system -> system.text(prompts.template("reviewer-system"))
				.param("reviewer_name", profile.displayName())
				.param("focus", profile.focus()))
			.user(user -> user.text(prompts.template("review-user"))
				.param("title", AnnotatedDiffRenderer.neutralise(request.title()))
				.param("description", AnnotatedDiffRenderer.neutralise(request.description()))
				.param("diff", renderer.render(batch))
				.param("batch", batchNumber)
				.param("batch_count", batchCount))
			.advisors(StructuredOutputValidationAdvisor.builder()
				.outputType(ReviewFindings.class)
				.maxRepeatAttempts(properties.maxRepairAttempts())
				.build())
			.call()
			.responseEntity(ReviewFindings.class);

		ReviewFindings findings = Optional.ofNullable(response.entity()).orElseGet(() -> new ReviewFindings(List.of(), ""));
		Optional<Usage> usage = Optional.ofNullable(response.response()).map(r -> r.getMetadata().getUsage());
		return new Result(findings, model.label(), usage.map(Usage::getPromptTokens).map(Integer::longValue).orElse(0L),
				usage.map(Usage::getCompletionTokens).map(Integer::longValue).orElse(0L));
	}

	/**
	 * @param findings the model's structured answer, before validation
	 * @param model provider/model label that answered
	 * @param inputTokens prompt tokens reported by the provider
	 * @param outputTokens completion tokens reported by the provider
	 */
	public record Result(ReviewFindings findings, String model, long inputTokens, long outputTokens) {
	}

}
