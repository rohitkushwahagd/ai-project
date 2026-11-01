# Day 15: an Angular workspace for the RAG service

New implementation, 22–23 September 2026. This is a learning milestone over code created in this session, not a claim that you personally completed a separate day of work.

## Goal and implemented behavior

Use the backend through a browser: connect with a local JWT, ingest synthetic text or Markdown, inspect indexing status, replace/delete a source, ask questions, read evidence, and rate completed answers. The interface is named Fieldnotes and is built with Angular standalone components, TypeScript, signals, and template-driven forms.

Visual previews: [desktop](../images/frontend-desktop.png) and [mobile](../images/frontend-mobile.png).

## Start and explore

From the project root, with Docker Desktop running and local secrets/models already configured:

```powershell
docker compose --profile app up -d --build app
.\token.ps1 | Set-Clipboard
```

Open [the workspace](http://localhost:8081/). Choose **Connect workspace**, paste the token, and connect. The default token allows reading and writing synthetic documents for tenant `demo`. No JWT signing secret is delivered to the browser. This is a developer connection flow, not a production login system.

1. Open Documents and add a synthetic runbook: “Orion P1 incidents must be acknowledged within 17 minutes. The Aurora support team owns escalation.”
2. Select Refresh status until READY. QUEUED means the worker has not finished; FAILED requires checking the backend logs.
3. Ask: “How many minutes to acknowledge an Orion P1 incident?” Watch the answer and expand the retrieved evidence. Evidence is retrieved context, not a guarantee that every generated sentence is supported.
4. Read the full source, then send a rating and optional comment.
5. Replace the runbook with an 18-minute rule. The old version remains searchable until the replacement is READY. A pending version cannot be replaced again.
6. Delete the document and confirm that the library no longer shows it. Deletion removes retrieval access but retains historical raw text in the database.
7. Reload the page. Connection state, conversation, and token are cleared; documents remain on the server.

## Trace the implementation

- [Angular component](../../frontend/src/app/app.component.ts): signals, forms, pending/error states, abort controller, document actions, feedback, workspace reset.
- [Template](../../frontend/src/app/app.component.html): conditional rendering, loops, bindings, accessible form labels, native modal dialogs.
- [API service](../../frontend/src/app/api.service.ts): injectable service, typed API records, in-memory token, bearer headers, status-specific errors.
- [Stream parser](../../frontend/src/app/stream.js): UTF-8 decoding and SSE framing across arbitrary network chunks.
- [Document repository](../../src/main/java/dev/rohit/rag/SourceRepository.java): tenant-filtered listing, pagination, active/pending versions, and deletion.

## Concepts to learn

**Signals and templates.** Signals hold observable screen state such as the current view, evidence, and request status. Angular updates the template when a signal changes. `ngModel` connects editable form fields to the component. Learn how server-owned records differ from temporary draft state.

**Dependency injection.** `ApiService` centralizes HTTP calls and credentials. Components describe the user interaction; the service owns transport details. Types document the Java API shapes, but the backend still validates requests and permissions.

**Authenticated streaming.** The query API uses POST and a bearer header, so the app uses Fetch with a response reader. A network chunk may end in the middle of a UTF-8 character, line, or event. The parser buffers incomplete lines, decodes incrementally, and dispatches complete events. A stream without `done` or `error` is reported as incomplete; incomplete answers cannot receive feedback.

**Same-origin packaging.** Docker builds Angular first and copies the output into Spring Boot resources under `/ui`. The public shell has no data or secret. The JSON APIs retain their tenant/scope checks. A development proxy forwards API calls during Angular development; no permissive CORS configuration is needed.

The Docker Java stage caches `/root/.m2` with BuildKit and limits the build heap to 384 MB. This avoids a separate prefetch step and reuses downloaded dependencies after source changes. A fresh Docker engine still needs its container images and both Ollama models downloaded; source code being available does not mean the runtime is ready.

**Safe rendering.** Answers and source text use Angular interpolation. They are displayed as literal text, including HTML-like input. There is no untrusted HTML or Markdown rendering. The source cards describe retrieval scores as retrieval scores, not model confidence.

**Versioned document management.** `GET /api/documents?page=0` returns up to 20 tenant-owned, nondeleted document summaries. The pending version is shown while indexing; active and pending IDs tell the UI when replacement is possible. Stable UUID ordering is adequate for this local library, but offset pages can shift during concurrent mutations.

## Development and verification commands

Use Node 24 (or compatible Node 22.12+) and Java 21. Angular 21 compatibility is documented in [Angular's official version matrix](https://angular.dev/reference/versions).

```powershell
.\frontend.ps1                 # install locked dependencies and build Angular
.\frontend.ps1 -Action test    # stream parser edge cases
.\frontend.ps1 -Action start   # dev server, API proxy to localhost:8081
.\build.ps1 -Integration       # Java tests, real infrastructure required
cd frontend
npx playwright install chromium --only-shell
npm run test:e2e              # running app on 8081; live test reads local .env
```

The frontend helper accepts `-NodePath` for another installed Node executable. It does not change global installations. For a local JVM, build Angular before `run-local.ps1` so Maven packages the assets. The development frontend uses `/ui/index.html` on port 4200. Browser tests use the Chromium headless shell installed by Playwright. Set `UI_BROWSER_CHANNEL=msedge` to explicitly test installed Edge instead.

## Exercise and interview explanation

Exercise: use a token generated with `-Scope 'rag.read'`, try to add a document, and explain why a hidden or disabled button would not replace the server's permission check. Then compare a normal completed stream with the truncated-stream browser test.

Illustrative interview answer: “I added an Angular client to a Java RAG service. The UI streams answers over authenticated Fetch, keeps retrieved evidence visible, and uses server-owned active/pending version IDs for document management. The browser never receives the signing secret. I tested both browser failure states with controlled responses and the live local flow separately.”

## Troubleshooting and limits

- Expired/invalid token: generate a fresh token and reconnect. A 403 means the token lacks the required scope.
- Slow answers: local inference can be slow; Stop cancels the client request. It cannot guarantee immediate shutdown of a model operation already running.
- Indexing pending: refresh status manually; there is no background polling or push notification yet.
- Source already changed: refresh the library and edit the current active version.
- No full login, persistent chat history, rich document editor, PDF parsing, or external connector is included. Lookup tools are optional; the UI does not opt into synthetic escalation.

See [the verification ledger](../VERIFICATION.md) for observed results. Prior backend benchmarks do not measure frontend responsiveness.

Observed: production build, 3 stream-parser tests, 5 controlled-response browser scenarios, and the expanded Java suite passed. The full browser flow with real models remains pending because Docker failed startup on 23 September. The live test is implemented, but an implemented test is not a passing result.

## Your learning reflection

- What I traced in the code:
- What I ran myself and observed:
- A failure I reproduced and understood:
- What I can explain without notes:
