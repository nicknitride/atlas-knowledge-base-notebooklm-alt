# Quickstart: Reconcile Frontend With New Backend

**Feature**: `005-reconcile-frontend-backend` | **Date**: 2026-08-01

Manual and automated validation after implementation. Contracts:
[api-client-errors.md](./contracts/api-client-errors.md),
[workspace-mutations.md](./contracts/workspace-mutations.md),
[sse-chat-stream.md](./contracts/sse-chat-stream.md).

## Prerequisites

- Docker Compose stack healthy (`postgres`, `api` `:8080`, `web` `:3000`)
- Optional: Ollama on host for chat success path; provider-down tests work
  with Ollama stopped
- Node deps: `pnpm install`

```bash
docker compose up --build -d
# wait for api health
curl -s http://localhost:8080/actuator/health
```

## Automated tests

```bash
pnpm test
```

Expect new/updated suites covering:

- Workspace delete: no page reload / submit suppressed; modal closes; list
  updates; last workspace deletable; failure shows banner; 404 → success
- API error parsing for mutation helpers
- SSE `error` → `onError` not `onComplete`

## Manual scenarios

### 1. Workspace delete (P1)

1. Open `http://localhost:3000`; create two workspaces.
2. Delete one via trash → Confirm.
3. **Expect**: No full page reload; modal closes; workspace gone from list;
   other workspace selected with its content.
4. Hard-refresh the browser.
5. **Expect**: Deleted workspace still absent.
6. Delete the remaining workspace.
7. **Expect**: Empty state with create-workspace path; no leftover docs/chat.

### 2. Delete failure feedback (P1/P2)

1. Stop the API container: `docker compose stop api`
2. Confirm delete on a workspace.
3. **Expect**: Workspace remains; error message visible; no reload.
4. `docker compose start api` and retry → success.

### 3. Mutation errors (P2)

1. Attempt create/rename with blank or overlong name if UI allows submit.
2. **Expect**: Validation message; modal stays open with text preserved.
3. With API up, delete document/conversation and confirm list updates; force
   a failure (API stopped) and confirm banner, not silent no-op.

### 4. Chat stream errors (P2)

1. With a workspace that has at least one COMPLETE document, ask a question
   (Ollama running) → answer + citations or honest empty.
2. Stop Ollama (or mis-set model) and ask again.
3. **Expect**: Loading ends; error explains AI unavailable; not an empty
   “success.”
4. (If embedding mismatch can be induced via env) expect re-process messaging
   per `EMBEDDING_CONFIG_MISMATCH`.

### 5. Upload states (P3)

1. Upload a small `.md` or `.pdf`.
2. **Expect**: Appears as queued/processing then ready without manual refresh.
3. Upload an unsupported type or oversized file.
4. **Expect**: Rejection message; file not listed.

## Done criteria

- [ ] `pnpm test` green including new regression tests
- [ ] Manual P1 delete path verified (no reload, persisted)
- [ ] Chat mid-failure shows error (not empty success)
- [ ] No new backend endpoints required
