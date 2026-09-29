# PR Review Agent

A code reviewer for GitHub pull requests, built with Spring Boot 4.1, Spring AI 2.0 and Java 25. You give it a diff; it returns review findings that are anchored to real lines, validated and ranked, ready to post as inline comments.

> **Status:** M1 of 10 is done: the core review engine, run offline against a local patch. The GitHub integration, code-aware RAG, multi-agent review, MCP tools, guardrails, dashboard and evals are on the [roadmap](#roadmap).

## Try it

Requires JDK 25, Docker, and an `ANTHROPIC_API_KEY` or `OPENAI_API_KEY` (or both).

```bash
./mvnw -pl review-agent spring-boot:run          # also starts Postgres/pgvector from compose.yaml

git diff main | curl -s -H 'Content-Type: text/x-diff' --data-binary @- \
  'localhost:8080/api/reviews/local?title=My%20change' | jq
```

Set `PORT=18080` if 8080 is taken. A sample patch with two planted bugs is in `review-agent/src/test/resources/patches/sql-injection.patch`.

Here's an excerpt of a real response for that patch:

```json
{
  "findings": [
    {
      "file": "src/main/java/com/example/shop/UserRepository.java",
      "startLine": 15, "endLine": 15,
      "severity": "CRITICAL", "category": "SECURITY",
      "title": "SQL injection via string concatenation in query",
      "confidence": 0.98
    },
    {
      "startLine": 13, "endLine": 16,
      "severity": "MAJOR", "category": "RESOURCE_MANAGEMENT",
      "title": "Connection/Statement/ResultSet are not closed (resource leak)"
    }
  ],
  "stats": { "batches": 1, "inputTokens": 3345, "outputTokens": 11816, "model": "openai/gpt-5", "promptVersion": "a3f63ee9" }
}
```

## How a review works

```
patch ─► UnifiedDiffParser ─► ReviewScope ─► DiffBatcher ─► ReviewerAgent (per batch) ─► FindingValidator ─► ReviewReport
          (format-patch,       (skip lock    (token budget,   (annotated diff in an        (file in diff? line on the
           git diff, diff -u)   files, build  split at hunk    untrusted block, structured   new side? one hunk? confidence?
                                output)       boundaries)      output + schema retry)        dedupe, rank, cap)
```

The design decisions that matter:

- **The model gets line numbers; it doesn't count them.** `AnnotatedDiffRenderer` prefixes every diff line with its new-file line number. Models are unreliable at computing positions in a raw unified diff, and GitHub rejects comments on lines that aren't in the diff.
- **Deterministic code checks model output.** `FindingValidator` drops findings on files or lines GitHub couldn't anchor, ranges that span hunks, low-confidence guesses and duplicates. Rejected findings stay in the report with a reason, so prompt regressions are visible.
- **PR content is untrusted.** The title, description and diff sit inside `<untrusted_*>` blocks. Any text that could close those blocks early is neutralised. The system prompt tells the model to report injection attempts, not follow them.
- **Precision over recall.** Developers mute noisy reviewers. The prompt says an empty list is the right answer for a clean change, and a confidence threshold plus a per-review cap keep the output short.
- **Prompts are versioned by content.** `PromptCatalog` hashes `prompts/*.st`, and every report carries that hash, so a change in quality can be traced to the prompt edit that caused it.
- **Tiers, not model names.** Agents ask `ModelRouter` for a `FAST` or `DEEP` model. Configuration maps each tier to a model per provider, and providers without an API key are skipped.

| Tier | Anthropic | OpenAI |
|---|---|---|
| `DEEP` (review) | `claude-opus-5-5`, effort high | `gpt-5`, effort medium |
| `FAST` (triage, judging) | `claude-haiku-4-5` | `gpt-5-mini`, effort low |

## Project layout

```
review-agent/                 Spring Boot app
  review/diff, review/finding   framework-free domain: parser, renderer, batcher, validator (enforced by ArchUnit)
  llm/                          ModelRouter, model tiers, PromptCatalog
  orchestration/                ReviewService workflow, ReviewerAgent, reviewer profiles
  api/                          REST endpoint and RFC 9457 error mapping
review-tools-mcp-server/      MCP server for deterministic analysis tools (skeleton; tools arrive in M6)
docs/adr/                     architecture decision records
compose.yaml                  local infrastructure
```

## Tests

```bash
./mvnw verify
```

The suite needs no API keys: the model is replaced by a scripted `ChatModel`, and the real prompts, structured-output parsing and validation all still run. The integration test boots the full context against pgvector with Testcontainers, so it needs Docker. ArchUnit rules keep the domain free of Spring and model dependencies.

## Roadmap

| Milestone | Scope | Status |
|---|---|---|
| M0 | Multi-module scaffold, compose, Flyway, CI, ADR 0001 | done |
| M1 | Core review engine on a local patch | done |
| M2 | Eval harness: golden dataset of seeded PRs, LLM-as-judge, precision/recall gate in CI | next |
| M3 | GitHub webhook (HMAC, idempotent), durable job queue, inline review comments | |
| M4 | Code-aware RAG: JavaParser chunking, call graph, hybrid pgvector + full-text search, rerank | |
| M5 | Multi-agent: triage/router, parallel specialists, critic/verifier, incremental re-review | |
| M6 | MCP: analysis-tools server, GitHub MCP client, the agent exposed as an MCP tool | |
| M7 | Guardrails, human-in-the-loop approval, feedback memory, PR-thread chat | |
| M8 | React dashboard with live SSE timeline | |
| M9 | OpenTelemetry → Grafana, cost dashboard, resilience | |
| M10 | Polish, demo, write-up | |

## Known issues (found in the first live runs; M2 evals will measure them)

- A suggested fix sometimes rewrites more than its `startLine..endLine` range. That would be wrong if posted as a GitHub suggestion block. The M5 critic will verify suggestions before they're posted.
- `gpt-5` at medium effort took about 2.5 minutes and about 12k output tokens (mostly reasoning) for one small file. Effort per tier will be tuned against the eval set.
