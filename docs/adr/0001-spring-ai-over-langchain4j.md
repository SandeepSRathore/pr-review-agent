# 1. Spring AI over LangChain4j

- Status: accepted
- Date: 2026-09-29

## Context

The review agent needs:
- chat models from more than one provider (Anthropic, OpenAI, and later Ollama);
- structured output;
- tool calling, and MCP as both client and server;
- a vector store (pgvector);
- tracing and token metrics for every model call.

It runs as a Spring Boot 4 service next to Postgres. The two mature JVM options are Spring AI 2.0 and LangChain4j 1.x.

## Decision

Use Spring AI 2.0.

- **Fits the stack we already run.** Spring AI is Spring Boot auto-configuration. Models, vector stores and MCP clients are beans configured with `spring.ai.*` properties. They're tested with the usual Boot test slices and Testcontainers `@ServiceConnection`. There is no second configuration model to learn.
- **Observability comes built in.** `ChatClient`, advisors, tool calls and vector store queries each emit Micrometer observations. That gives traces and `gen_ai.*` token metrics without extra code, and the cost dashboard (M9) depends on them.
- **MCP on both sides.** One set of starters covers both consuming external MCP servers (the GitHub MCP server) and publishing our own (`review-tools-mcp-server`, and the agent itself as a tool).
- **The advisor chain maps to our cross-cutting concerns.** Structured-output validation and retry, RAG, memory, guardrails and cost tracking are advisors. The request path stays readable and each concern is testable on its own.

## Consequences

- LangChain4j's `AiServices` interfaces and its agentic module (supervisor and sequential agent workflows) are more declarative than hand-written orchestration. We accept writing the orchestration ourselves: the review is a fixed workflow (ADR 0002 will record that), and explicit code is easier to test and to explain.
- Spring AI 2.0 moved the tool loop into `ToolCallingAdvisor` and uses Jackson 3. Examples written for 1.x often need adapting.
- Per-request `ChatOptions` carry their own timeout and retry defaults, and those override the model-level `spring.ai.*` properties. `ModelRouter` therefore sets timeouts on the options it builds.
