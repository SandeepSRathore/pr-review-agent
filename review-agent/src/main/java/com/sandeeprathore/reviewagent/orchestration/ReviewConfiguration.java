package com.sandeeprathore.reviewagent.orchestration;

import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.sandeeprathore.reviewagent.review.diff.AnnotatedDiffRenderer;
import com.sandeeprathore.reviewagent.review.diff.DiffBatcher;
import com.sandeeprathore.reviewagent.review.diff.UnifiedDiffParser;
import com.sandeeprathore.reviewagent.review.finding.FindingValidator;

/** Wires the framework-free domain classes into the Spring context. */
@Configuration(proxyBeanMethods = false)
class ReviewConfiguration {

	@Bean
	UnifiedDiffParser unifiedDiffParser() {
		return new UnifiedDiffParser();
	}

	@Bean
	AnnotatedDiffRenderer annotatedDiffRenderer() {
		return new AnnotatedDiffRenderer();
	}

	@Bean
	DiffBatcher diffBatcher(AnnotatedDiffRenderer renderer) {
		// cl100k-style estimate: providers tokenize differently, but it's close enough for a budget.
		var estimator = new JTokkitTokenCountEstimator();
		return new DiffBatcher(renderer, estimator::estimate);
	}

	@Bean
	FindingValidator findingValidator(ReviewProperties properties) {
		return new FindingValidator(properties.minConfidence(), properties.maxFindings());
	}

	@Bean
	ReviewScope reviewScope(ReviewProperties properties) {
		return new ReviewScope(properties.ignorePaths());
	}

}
