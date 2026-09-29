package com.sandeeprathore.reviewagent.llm;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Which model serves each tier on each provider, and the order providers are tried in. A provider is
 * skipped when its API key isn't configured, so the app runs with whichever keys are present.
 *
 * @param providerOrder providers in preference order
 * @param tiers per tier, the model to use on each provider
 * @param requestTimeout how long one model call may take; reasoning models reviewing a large diff can
 * think for minutes, and a timed-out call is paid for without producing anything
 * @param maxRetries retries on transient provider errors
 */
@Validated
@ConfigurationProperties("review.models")
public record ModelProperties(@NotEmpty List<ModelProvider> providerOrder,
		@NotEmpty Map<ModelTier, Map<ModelProvider, ModelSpec>> tiers, @DefaultValue("5m") Duration requestTimeout,
		@DefaultValue("1") @Min(0) int maxRetries) {

	/**
	 * @param model provider model id
	 * @param effort reasoning effort (low, medium, high); leave unset for models that don't accept it
	 * @param maxTokens output token cap; provider default when unset
	 */
	public record ModelSpec(String model, String effort, Integer maxTokens) {
	}

	public Optional<ModelSpec> spec(ModelTier tier, ModelProvider provider) {
		return Optional.ofNullable(tiers.get(tier)).map(byProvider -> byProvider.get(provider));
	}

}
