package com.sandeeprathore.reviewagent.llm;

/**
 * Capability/cost classes rather than model names, so agents ask for "a fast model" and configuration
 * decides which one. FAST handles classification, triage and judging; DEEP handles the review itself.
 */
public enum ModelTier {
	FAST, DEEP
}
