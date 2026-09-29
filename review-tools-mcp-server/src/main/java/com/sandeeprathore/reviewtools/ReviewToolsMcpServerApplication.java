package com.sandeeprathore.reviewtools;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Streamable-HTTP MCP server for the review agent's deterministic analysis tools. Kept in its own
 * process so tools that parse or execute against untrusted PR code are isolated from the agent.
 */
@SpringBootApplication
public class ReviewToolsMcpServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReviewToolsMcpServerApplication.class, args);
	}

}
