package com.sandeeprathore.reviewagent.llm;

import org.springframework.ai.chat.client.ChatClient;

/** A ready-to-use client plus what it resolved to, for reports, traces and cost accounting. */
public record RoutedModel(ModelProvider provider, ModelTier tier, String model, ChatClient client) {

	public String label() {
		return provider.name().toLowerCase() + "/" + model;
	}

}
