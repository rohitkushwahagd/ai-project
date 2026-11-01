# Day 12: Evaluation and honest quality reporting

This is a learning milestone, not a claim that the work happened on a separate date. Implementation provenance and observed checks are tracked in the [daily index](README.md) and [verification ledger](../VERIFICATION.md).

## Goal

Measure retrieval and answer behavior instead of selecting one attractive demo.

## Implementation and code to read

The reused harness seeds synthetic documents under a fresh tenant, sends questions, parses complete SSE responses, measures latency/token usage, tests cache behavior, and writes timestamped reports. This session updates its token audience and endpoint defaults.

The [first actual baseline](../benchmarks/2026-09-22-baseline.md) is now available. Read its limitations before interpreting the perfect recall/fact proxies: the set has only ten questions and one response took 116 seconds.

Read: `evaluation/golden.json`, `evaluation/evaluate.py`, `evaluation/gates.json`, `evaluation/test_metrics.py`, `evaluate.ps1`. Paths are relative to the project root.

## Concepts with a project example

Precision and recall measure retrieval against labels; neither proves answer correctness. Expected-fact string matching is a cheap proxy. A model judge is an estimate and may share a model's mistakes. Human-supported claim review remains separate.

## Walkthrough

Run metric tests with `./evaluate.ps1 -MetricsOnly`. Once real inference works, run `./evaluate.ps1 -SkipJudge -Hardware '<actual hardware>'`. Review generated results before publishing them. The first set is a small development fixture, not a held-out enterprise benchmark.

## Exercise to do yourself

Manually inspect one answer where expected words appear but the relationship is wrong. Explain why a keyword gate could pass it. Label supporting chunks before expanding to the planned 60+ questions.

## Interview explanation

Why no p95-under-900ms claim? The original brief supplied an example, not an observation. Measure retrieval, first token, and full answer separately on stated hardware.

## Troubleshooting and tradeoff

The harness labels relevant source IDs and deduplicates source hits among top-five chunks. Its precision is therefore source-level with a fixed denominator of five, not the planned chunk-level precision. Do not compare the two without relabeling.

Worked example: suppose the only relevant source is Orion, and five returned chunks include Orion once. Precision@5 is 1/5 = 0.20; recall@5 is 1/1 = 1.00. Even perfect recovery of that single labeled source cannot achieve 0.87 precision under this definition. Choose quality gates after inspecting label density and the exact metric, rather than copying a resume example.

## Verification and reflection

Local Ollama has no per-token provider invoice. Token counts are measured; cloud token-rate arithmetic is an estimate only when actual rates are supplied.

Record your own observations here after working through the milestone:

- Command or experiment I ran:
- Result I observed:
- Concept I can now explain:
- Question I still have:

[Back to day-wise plan](README.md)
