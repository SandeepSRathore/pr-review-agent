package com.sandeeprathore.reviewagent.orchestration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

import com.sandeeprathore.reviewagent.llm.PromptCatalog;
import com.sandeeprathore.reviewagent.review.diff.DiffBatcher;
import com.sandeeprathore.reviewagent.review.diff.FileDiff;
import com.sandeeprathore.reviewagent.review.diff.PatchSet;
import com.sandeeprathore.reviewagent.review.finding.Finding;
import com.sandeeprathore.reviewagent.review.finding.FindingValidator;
import com.sandeeprathore.reviewagent.review.finding.ValidatedFindings;

/**
 * Runs a review end to end: scope the diff, split it into token-bounded batches, review each batch,
 * then validate and rank what came back.
 *
 * <p>This is deliberately a fixed workflow rather than an autonomous agent. The steps are known in
 * advance, so code sequences them and the model is only asked for judgement: what is wrong and why.
 */
@Service
public class ReviewService {

	private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

	private final ReviewerAgent reviewer;

	private final DiffBatcher batcher;

	private final FindingValidator validator;

	private final ReviewScope scope;

	private final PromptCatalog prompts;

	private final ReviewProperties properties;

	public ReviewService(ReviewerAgent reviewer, DiffBatcher batcher, FindingValidator validator, ReviewScope scope,
			PromptCatalog prompts, ReviewProperties properties) {
		this.reviewer = reviewer;
		this.batcher = batcher;
		this.validator = validator;
		this.scope = scope;
		this.prompts = prompts;
		this.properties = properties;
	}

	public ReviewReport review(ReviewRequest request) {
		long started = System.nanoTime();
		String reviewId = UUID.randomUUID().toString();
		List<FileDiff> inScope = request.patch().files().stream().filter(scope::includes).toList();
		int skipped = request.patch().files().size() - inScope.size();
		if (inScope.isEmpty()) {
			return new ReviewReport(reviewId, "Nothing to review: the change only touches deleted, binary or ignored files.",
					List.of(), List.of(), new ReviewReport.Stats(0, skipped, 0, 0, 0, elapsedMs(started), "none",
							prompts.version()));
		}

		List<List<FileDiff>> batches = batcher.batch(inScope, properties.maxInputTokensPerBatch());
		List<Finding> raw = new ArrayList<>();
		List<String> summaries = new ArrayList<>();
		long inputTokens = 0;
		long outputTokens = 0;
		String model = "none";
		for (int i = 0; i < batches.size(); i++) {
			ReviewerAgent.Result result = reviewer.review(ReviewerProfile.GENERAL, request, batches.get(i), i + 1,
					batches.size());
			raw.addAll(result.findings().findings());
			if (!result.findings().summary().isBlank()) {
				summaries.add(result.findings().summary());
			}
			inputTokens += result.inputTokens();
			outputTokens += result.outputTokens();
			model = result.model();
		}

		ValidatedFindings validated = validator.validate(new PatchSet(inScope), raw);
		long durationMs = elapsedMs(started);
		log.info("Review {} done: {} files in {} batch(es), {} findings kept, {} rejected, {} in / {} out tokens, {} ms",
				reviewId, inScope.size(), batches.size(), validated.accepted().size(), validated.rejected().size(),
				inputTokens, outputTokens, durationMs);
		return new ReviewReport(reviewId, String.join("\n\n", summaries), validated.accepted(), validated.rejected(),
				new ReviewReport.Stats(inScope.size(), skipped, batches.size(), inputTokens, outputTokens, durationMs,
						model, prompts.version()));
	}

	private static long elapsedMs(long startedNanos) {
		return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
	}

}
