# ADR 004: Local models and evidence-based evaluation

Status: accepted, 22 September 2026.

## Context

The project is for learning and portfolio evidence. A paid provider account is unnecessary for local iteration; advertised benchmark numbers from the source document have not been achieved here.

## Decision

Keep Ollama with a small chat model and a fixed embedding model/index. Separate deterministic tests from real-model experiments. Record actual results, failed quality gates, hardware, and metric definitions. Treat the current eight-document/ten-question set as a development fixture. Record source-level retrieval metrics accurately instead of calling them chunk-level scores.

## Consequences

First setup downloads large images/models, and CPU inference can be slow. No per-token invoice does not mean no compute cost. More labels, held-out questions, and manual faithfulness review are needed before credible large-scale claims. A future provider comparison must use the same corpus/questions and include cost and latency distributions.

