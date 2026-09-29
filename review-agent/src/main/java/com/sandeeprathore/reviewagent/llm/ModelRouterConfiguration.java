package com.sandeeprathore.reviewagent.llm;

import java.util.EnumMap;
import java.util.Map;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Both provider starters are on the classpath, so Spring AI creates a ChatModel for each. A provider only
 * joins the router when its API key is set. The default ChatClient.Builder is disabled
 * ({@code spring.ai.chat.client.enabled=false}) because it can't choose between two models.
 */
@Configuration(proxyBeanMethods = false)
class ModelRouterConfiguration {

	@Bean
	ModelRouter modelRouter(ObjectProvider<AnthropicChatModel> anthropic, ObjectProvider<OpenAiChatModel> openAi,
			ModelProperties properties, ObjectProvider<ObservationRegistry> observationRegistry, Environment env) {
		Map<ModelProvider, ChatModel> available = new EnumMap<>(ModelProvider.class);
		if (StringUtils.hasText(env.getProperty("spring.ai.anthropic.api-key"))) {
			anthropic.ifAvailable(model -> available.put(ModelProvider.ANTHROPIC, model));
		}
		if (StringUtils.hasText(env.getProperty("spring.ai.openai.api-key"))) {
			openAi.ifAvailable(model -> available.put(ModelProvider.OPENAI, model));
		}
		return new ModelRouter(available, properties, observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP));
	}

}
