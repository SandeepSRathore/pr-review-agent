package com.sandeeprathore.reviewagent;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

import com.sandeeprathore.reviewagent.llm.ModelRouter;
import com.sandeeprathore.reviewagent.llm.ModelTier;

import static org.assertj.core.api.Assertions.assertThat;

/** Boots the full context against a real pgvector database, so Flyway migrations are exercised too. */
@Testcontainers
@SpringBootTest(properties = { "spring.docker.compose.enabled=false", "spring.ai.openai.api-key=test-key",
		"spring.ai.anthropic.api-key=" })
class ReviewAgentApplicationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer(
			DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

	@Autowired
	private ModelRouter router;

	@Test
	void routesToTheProviderThatHasAnApiKey() {
		// Only OpenAI has a key here, so both tiers fall through Anthropic to OpenAI.
		assertThat(router.route(ModelTier.DEEP).label()).isEqualTo("openai/gpt-5");
		assertThat(router.route(ModelTier.FAST).label()).isEqualTo("openai/gpt-5-mini");
	}

}
