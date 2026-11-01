# Day 5: Chunking and local embeddings

This is a learning milestone, not a claim that the work happened on a separate date. Implementation provenance and observed checks are tracked in the [daily index](README.md) and [verification ledger](../VERIFICATION.md).

## Goal

Turn source text into searchable numeric representations.

## Implementation and code to read

The baseline splits into 1,200 Unicode code-point windows with 200-point overlap and uses `nomic-embed-text:v1.5` for 768-dimensional embeddings. Embeddings are cached per tenant/model/text.

Read: `TextChunker.java`, `Embeddings.java`, `IngestionPipeline.java`, `CoreTest.java`. Paths are relative to the project root.

## Concepts with a project example

An embedding is a fixed-size vector that allows a distance function to rank similarity. A code point is not a model token. Overlap retains context near boundaries but duplicates storage and model work. Query and document vectors must use the same embedding model.

## Walkthrough

Read `TextChunker.split`, then run its Unicode test with repeated rocket characters. A 2,500-code-point string creates three chunks. Follow the worker from chunk text to `Embeddings.embed` and the `vector(768)` database column.

## Exercise to do yourself

Try one long paragraph where a useful fact crosses a boundary. Inspect both chunks. Compare this baseline with a heading-aware splitter on a held-out set before changing it.

## Interview explanation

Why reject an embedding-model change even if it still returns 768 dimensions? Equal dimensions do not mean the models share a semantic coordinate system; rebuild a separate index.

## Troubleshooting and tradeoff

The baseline chunker is not token-aware or heading-aware. This is a recorded limitation, not an implemented advanced chunk strategy.

## Verification and reflection

Mock embeddings test orchestration deterministically. Only real Ollama inference proves the model is installed and returns compatible vectors.

Record your own observations here after working through the milestone:

- Command or experiment I ran:
- Result I observed:
- Concept I can now explain:
- Question I still have:

[Back to day-wise plan](README.md)

