---
description: "Task list for 006-fix-rebuild-duplicate-chunk"
---

# Tasks: Fix Rebuild Duplicate-Chunk Violation

**Input**: Design documents from `specs/006-fix-rebuild-duplicate-chunk/`

**Prerequisites**: [plan.md](./plan.md) ✅ | [spec.md](./spec.md) ✅ | [research.md](./research.md) ✅ | [data-model.md](./data-model.md) ✅ | [contracts/rebuild-api-contract.md](./contracts/rebuild-api-contract.md) ✅

**Tests**: MANDATORY per Atlas constitution (I. Test-First). Every user-story phase includes failing test tasks before implementation tasks.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no incomplete task dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Exact file paths included in each description

---

## Phase 1: Setup

**Purpose**: Confirm the working baseline before making any changes.

- [x] T001 Verify the bug is reproducible: run `cd backend && ./mvnw test -Dtest="RebuildServiceTest"` and confirm all existing tests pass; note any currently failing tests
- [x] T002 Run the full backend test suite to capture baseline: `cd backend && ./mvnw test` — record pass/fail counts

**Checkpoint**: Baseline captured — all existing tests pass before any code changes

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: No user-story work can begin until foundational tests are written and failing, and the shared `ApiException` 409 path is confirmed available.

**⚠️ CRITICAL**: All tests in this phase must be written and must **FAIL** before any implementation task in Phase 3+ begins.

- [x] T003 Confirm `dev.atlas.support.ApiException` supports `HttpStatus.CONFLICT` (read `backend/src/main/java/dev/atlas/support/ApiException.java` — no code change needed, just verify the constructor accepts any `HttpStatus`)
- [x] T004 Confirm `dev.atlas.support.ApiErrorHandler` maps `ApiException` with CONFLICT to an HTTP 409 response (read `backend/src/main/java/dev/atlas/support/ApiErrorHandler.java` — verify, no change needed)

**Checkpoint**: Foundation confirmed — existing error infrastructure supports the new 409 path

---

## Phase 3: User Story 1 — Rebuild Completes Without Error (Priority: P1) 🎯 MVP

**Goal**: A rebuild of a previously indexed workspace (existing chunks in DB) completes without any `DuplicateKeyException` or `UnexpectedRollbackException`.

**Independent Test**: Trigger rebuild on a workspace that already has chunks → response is `{"status":"COMPLETED","failedCount":0}` with no constraint errors in logs.

### Tests for User Story 1 (REQUIRED — write first, ensure FAIL before implementing) ⚠️

- [x] T005 [P] [US1] Add failing test `executeJobDeletesExistingChunksBeforeInserting` to `backend/src/test/java/dev/atlas/documents/IngestionWorkflowTest.java`:
  - Set up a document with `existsById` returning true
  - Stub `extractor.extract()` to return one section with content
  - Stub `embeddingProvider.embed()` to return `new float[768]`
  - Call `ingestionService.executeJob(jobId)`
  - Assert `verify(jdbc).update(startsWith("DELETE FROM document_chunks"), eq(doc.id()))` is called **before** any `INSERT` call using `InOrder`
  - **This test MUST fail (DELETE is not yet called in success path)**

- [x] T006 [P] [US1] Add failing test `rebuildAlreadyIndexedWorkspaceSucceeds` to `backend/src/test/java/dev/atlas/documents/RebuildServiceTest.java`:
  - Stub a document already in COMPLETE state with existing model
  - Stub `ingestionService.executeJob()` to complete successfully (doc → COMPLETE with new model)
  - Call `rebuildService.rebuildWorkspace(workspaceId)` **twice**
  - Assert both calls return `"COMPLETED"` with `failedCount == 0`
  - **This test MUST fail (second call would currently throw on duplicate chunks)**

### Implementation for User Story 1

- [x] T007 [US1] In `backend/src/main/java/dev/atlas/documents/IngestionService.java`, add `jdbc.update("DELETE FROM document_chunks WHERE document_id = ?", document.id())` immediately before `int ordinal = 0;` (line ~150) on the success path of `executeJob()` — inside the existing `try` block, after `workspaces.requireCompatibleEmbeddingConfig()` and after the `existsById` guard, but before the chunk-insert loop

- [x] T008 [US1] In `backend/src/main/java/dev/atlas/documents/RebuildService.java`, remove the `@Transactional` annotation from the `rebuildWorkspace(UUID workspaceId)` method (line ~105) so that each per-document `executeJob()` call runs in its own independent Spring-managed transaction rather than joining one outer transaction

- [x] T009 [US1] Run the US1 tests to confirm they now pass: `cd backend && ./mvnw test -Dtest="IngestionWorkflowTest,RebuildServiceTest"`

**Checkpoint**: US1 complete — rebuild no longer throws `DuplicateKeyException`; second rebuild succeeds

---

## Phase 4: User Story 2 — Rebuild Is Idempotent (Priority: P2)

**Goal**: Triggering a rebuild N times produces the same final chunk state each time; a partially failed previous rebuild does not prevent a subsequent rebuild from succeeding.

**Independent Test**: Call `rebuildWorkspace()` three times in sequence on a workspace with documents → all three return `"COMPLETED"` with identical `rebuiltCount`.

### Tests for User Story 2 (REQUIRED — write first, ensure FAIL before implementing) ⚠️

- [x] T010 [P] [US2] Create new file `backend/src/test/java/dev/atlas/documents/RebuildIdempotencyTest.java` with test `threeConsecutiveRebuildsAllSucceed`:
  - Set up `documentRepository`, `jobRepository`, `ingestionService`, `embeddingProvider`, `workspaceLookup` mocks
  - Stub `documentRepository.findByWorkspaceIdOrderByCreatedAtDesc()` to return one completed document
  - Stub `jobRepository.save()` to return its argument
  - Stub `ingestionService.executeJob()` to mark the document COMPLETE with active model
  - Call `rebuildService.rebuildWorkspace(workspaceId)` three times
  - Assert all three results have `status == "COMPLETED"` and `failedCount == 0`
  - **This test MUST fail before T008 (outer @Transactional causes cascade abort on 2nd+ run)**

- [x] T011 [P] [US2] Add test `rebuildAfterPartialFailureSucceeds` to `RebuildIdempotencyTest.java`:
  - First call to `ingestionService.executeJob()` throws `RuntimeException` (simulates partial failure)
  - Second call succeeds (doc marked COMPLETE)
  - Assert second `rebuildWorkspace()` returns `"COMPLETED"` (not blocked by first failure)
  - **This test MUST fail before T008**

### Implementation for User Story 2

- [x] T012 [US2] Confirm that T008's `@Transactional` removal already satisfies idempotency (no additional code change needed — the fix applied for US1 is the same mechanism). Run `cd backend && ./mvnw test -Dtest="RebuildIdempotencyTest"` to confirm both tests now pass.

**Checkpoint**: US2 complete — rebuild is idempotent across N sequential invocations

---

## Phase 5: User Story 3 — Concurrent Rebuild Safety (Priority: P3)

**Goal**: Two simultaneous `POST /api/workspaces/{id}/rebuild` requests for the same workspace do not produce a duplicate-key violation; one proceeds and the other receives HTTP 409.

**Independent Test**: Two concurrent calls to `rebuildWorkspace()` → one completes successfully, the other throws `ApiException` with `CONFLICT` / `REBUILD_IN_PROGRESS`.

### Tests for User Story 3 (REQUIRED — write first, ensure FAIL before implementing) ⚠️

- [x] T013 [P] [US3] Create new file `backend/src/test/java/dev/atlas/documents/RebuildConcurrencyTest.java` with test `concurrentRebuildReturnConflict`:
  - Use a `CountDownLatch` to hold the first `rebuildWorkspace()` inside `ingestionService.executeJob()` while the second call arrives
  - First call: started on a background thread, latch awaited inside the `executeJob` stub
  - Second call: invoked on main thread while first is blocked
  - Assert second call throws `ApiException` with message containing `"REBUILD_IN_PROGRESS"` and HTTP status `409 CONFLICT`
  - **This test MUST fail (no concurrency guard exists yet)**

- [x] T014 [P] [US3] Add test `singleRebuildCompletesNormally` to `RebuildConcurrencyTest.java`:
  - Verify that a single (non-concurrent) rebuild with the lock guard still completes and returns `"COMPLETED"`
  - **This test MUST pass after guard is added (regression guard)**

### Implementation for User Story 3

- [x] T015 [US3] In `backend/src/main/java/dev/atlas/documents/RebuildService.java`, add a private field:
  ```java
  private final ConcurrentHashMap<UUID, ReentrantLock> workspaceRebuildLocks = new ConcurrentHashMap<>();
  ```
  Add imports: `java.util.concurrent.ConcurrentHashMap`, `java.util.concurrent.locks.ReentrantLock`

- [x] T016 [US3] In `backend/src/main/java/dev/atlas/documents/RebuildService.java`, wrap the body of `rebuildWorkspace(UUID workspaceId)` with a `tryLock` guard:
  ```java
  ReentrantLock lock = workspaceRebuildLocks.computeIfAbsent(workspaceId, id -> new ReentrantLock());
  if (!lock.tryLock()) {
      throw new ApiException(HttpStatus.CONFLICT, "REBUILD_IN_PROGRESS",
          "A rebuild is already in progress for this workspace. Please wait and retry.");
  }
  try {
      // existing method body
  } finally {
      lock.unlock();
  }
  ```
  Place the guard immediately after `workspaceLookup.requireExists(workspaceId)`.

- [x] T017 [US3] Run the US3 tests: `cd backend && ./mvnw test -Dtest="RebuildConcurrencyTest"` — confirm both tests pass

**Checkpoint**: US3 complete — concurrent rebuilds are safely guarded; no duplicate-key race possible

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final validation, log review, and documentation.

- [x] T018 [P] Run the full backend test suite: `cd backend && ./mvnw test` — confirm all tests pass and no regressions introduced
- [x] T019 [P] Verify the `failDocument()` cleanup path in `IngestionService` still works correctly: the existing `DELETE` in `failDocument()` is now redundant for the success path but is still correct for the failure path — confirm no double-delete issue by tracing the code path in `backend/src/main/java/dev/atlas/documents/IngestionService.java` lines 207–221
- [x] T020 Perform quickstart validation per `specs/006-fix-rebuild-duplicate-chunk/quickstart.md` Scenario 1: trigger rebuild twice against the running Docker stack and confirm `"status":"COMPLETED"` both times with no errors in `docker logs atlas_knowledge_base-api-1`
- [x] T021 [P] Check logs for absence of constraint violations after double rebuild: `docker logs atlas_knowledge_base-api-1 2>&1 | grep -i "duplicate\|constraint\|rollback"` — expect zero matches
- [x] T022 Privacy/simplicity review: confirm the new `DELETE` statement logs document ID only (not content); confirm the lock map does not leak workspace state; confirm no new config knobs or cloud dependencies were introduced

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 — BLOCKS all user stories
- **US1 (Phase 3)**: Depends on Phase 2; tests written first (T005–T006 fail), then implemented (T007–T009)
- **US2 (Phase 4)**: Depends on Phase 3 complete (T008 fix is the same mechanism); tests T010–T011 can be written in parallel with US1 implementation
- **US3 (Phase 5)**: Depends on Phase 3 (lock guard builds on same `RebuildService`); tests T013–T014 can be written in parallel with US1/US2
- **Polish (Phase 6)**: Depends on all user stories complete

### User Story Dependencies

- **US1 (P1)**: Independent — the core fix, no dependencies on US2 or US3
- **US2 (P2)**: Mechanically satisfied by US1's `@Transactional` removal; test-only addition
- **US3 (P3)**: Independent of US1/US2 business logic; modifies same `RebuildService` file (serial with US1 edits)

### Within Each User Story

- Tests MUST be written and MUST FAIL before implementation tasks
- For US1: T005–T006 (tests) → T007–T008 (implementation) → T009 (verify)
- For US2: T010–T011 (tests) → T012 (verify) — no new production code
- For US3: T013–T014 (tests) → T015–T016 (implementation) → T017 (verify)

### Parallel Opportunities

- T005 and T006 can be written in parallel (different test classes)
- T007 (IngestionService edit) and lock map field (T015 prep) are different files — can be parallelized if two developers
- T013 and T014 can be written in parallel
- T018, T019, T021, T022 (Polish) can all run in parallel

---

## Parallel Example: User Story 1

```bash
# Write both failing tests in parallel:
Task: "T005 — IngestionWorkflowTest: executeJobDeletesExistingChunksBeforeInserting"
Task: "T006 — RebuildServiceTest: rebuildAlreadyIndexedWorkspaceSucceeds"

# Then implement (serial — same file for T007, different file for T008):
Task: "T007 — Add DELETE before INSERT in IngestionService.executeJob()"
Task: "T008 — Remove @Transactional from RebuildService.rebuildWorkspace()"

# Then verify:
Task: "T009 — Run IngestionWorkflowTest + RebuildServiceTest"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (baseline)
2. Complete Phase 2: Foundational (verify error infrastructure)
3. Write T005–T006 (failing tests), confirm they FAIL
4. Complete Phase 3: US1 (T007–T008)
5. Run T009 — **STOP and VALIDATE**: confirm no `DuplicateKeyException` on repeat rebuild
6. **This alone fixes the production crash** — US2 and US3 are correctness improvements

### Incremental Delivery

1. Phases 1–3 (US1) → core bug fixed → production-safe
2. Phase 4 (US2) → idempotency confirmed by tests → confidence +
3. Phase 5 (US3) → concurrency guard added → fully hardened
4. Phase 6 (Polish) → validated end-to-end

### Single Developer Order

```
T001 → T002 → T003 → T004
→ T005 → T006 (both FAIL ✓)
→ T007 → T008 → T009 (both PASS ✓)
→ T010 → T011 (both FAIL ✓) → T012 (PASS ✓)
→ T013 → T014 (T013 FAIL ✓, T014 PASS ✓ after guard) → T015 → T016 → T017 (both PASS ✓)
→ T018 → T019 → T020 → T021 → T022
```

---

## Notes

- [P] tasks = different files, no incomplete task dependencies within the same story
- [Story] label maps each task to its user story for traceability
- T008 (`@Transactional` removal from `rebuildWorkspace`) also satisfies US2 — no duplicate production code change
- The `DELETE` added in T007 is already present on the failure path (`failDocument()`); it's only missing on the success path — a one-line addition
- Chunk-level concurrency (two rebuild jobs for different workspaces) is already safe; the lock in T015–T016 is keyed by `workspaceId`
- Commit after T009 (US1 complete) as the minimum shippable fix

