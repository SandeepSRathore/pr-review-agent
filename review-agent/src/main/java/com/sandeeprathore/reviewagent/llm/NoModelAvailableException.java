package com.sandeeprathore.reviewagent.llm;

public class NoModelAvailableException extends RuntimeException {

	public NoModelAvailableException(ModelTier tier) {
		super("No model is available for tier " + tier
				+ ": set ANTHROPIC_API_KEY or OPENAI_API_KEY and configure review.models.tiers");
	}

}
