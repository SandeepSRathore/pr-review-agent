package com.sandeeprathore.reviewagent.llm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.anthropic.models.messages.OutputConfig;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;

import com.sandeeprathore.reviewagent.llm.ModelProperties.ModelSpec;

/**
 * Resolves a {@link ModelTier} to a concrete provider and model: the first provider in
 * {@code review.models.provider-order} that has credentials and a model configured for the tier.
 *
 * <p>Clients are built once per provider and tier and share the application's
 * {@link ObservationRegistry}, so every call is traced and its token usage recorded.
 */
public class ModelRouter {

	private final Map<ModelProvider, ChatModel> availableModels;

	private final ModelProperties properties;

	private final ObservationRegistry observationRegistry;

	private final Map<String, RoutedModel> cache = new ConcurrentHashMap<>();

	public ModelRouter(Map<ModelProvider, ChatModel> availableModels, ModelProperties properties,
			ObservationRegistry observationRegistry) {
		this.availableModels = Map.copyOf(availableModels);
		this.properties = properties;
		this.observationRegistry = observationRegistry;
	}

	public RoutedModel route(ModelTier tier) {
		for (ModelProvider provider : properties.providerOrder()) {
			ChatModel chatModel = availableModels.get(provider);
			var spec = properties.spec(tier, provider);
			if (chatModel != null && spec.isPresent()) {
				return cache.computeIfAbsent(provider + ":" + tier,
						key -> new RoutedModel(provider, tier, spec.get().model(), build(chatModel, provider, spec.get())));
			}
		}
		throw new NoModelAvailableException(tier);
	}

	private ChatClient build(ChatModel chatModel, ModelProvider provider, ModelSpec spec) {
		return ChatClient.builder(chatModel, observationRegistry, null, null)
			.defaultOptions(options(provider, spec))
			.build();
	}

	// Timeout and retries are set here, not only in spring.ai.* properties: per-request options carry their
	// own defaults (60s for OpenAI), and those win over the model-level configuration.
	private ChatOptions.Builder<?> options(ModelProvider provider, ModelSpec spec) {
		return switch (provider) {
			case ANTHROPIC -> {
				var builder = AnthropicChatOptions.builder()
					.model(spec.model())
					.maxTokens(spec.maxTokens())
					.timeout(properties.requestTimeout())
					.maxRetries(properties.maxRetries());
				if (spec.effort() != null) {
					builder.effort(OutputConfig.Effort.of(spec.effort()));
				}
				yield builder;
			}
			case OPENAI -> {
				var builder = OpenAiChatOptions.builder()
					.model(spec.model())
					.maxTokens(spec.maxTokens())
					.timeout(properties.requestTimeout())
					.maxRetries(properties.maxRetries());
				if (spec.effort() != null) {
					builder.reasoningEffort(spec.effort());
				}
				yield builder;
			}
		};
	}

}
