<!--
Sync Impact Report
- Version change: (unset / template) → 1.0.0
- Modified principles: all placeholders replaced with Atlas principles
  - [PRINCIPLE_1_NAME] → I. Test-First Development (NON-NEGOTIABLE)
  - [PRINCIPLE_2_NAME] → II. Local-First & Ollama-Native
  - [PRINCIPLE_3_NAME] → III. Performance & UX Excellence
  - [PRINCIPLE_4_NAME] → IV. Knowledge Organization & NotebookLM-Class Functionality
  - [PRINCIPLE_5_NAME] → V. Privacy by Design
  - Added: VI. User Configurability
  - Added: VII. Simplicity (YAGNI, KISS, DRY)
- Added sections: Runtime & Integration Constraints; Quality Gates & Review
- Removed sections: none (template placeholders only)
- Templates requiring updates:
  - .specify/templates/plan-template.md ✅ updated (Constitution Check gates)
  - .specify/templates/spec-template.md ✅ updated (NFR / constraint guidance)
  - .specify/templates/tasks-template.md ✅ updated (TDD mandatory)
  - .cursor/skills/speckit-tasks/SKILL.md ✅ updated (tests mandatory per constitution)
  - README.md ✅ no outdated principle refs (no change)
- Follow-up TODOs: none
-->

# Atlas Constitution

## Core Principles

### I. Test-First Development (NON-NEGOTIABLE)

Every behavior change MUST follow red–green–refactor: failing tests are written
first, then the minimal implementation that makes them pass, then refactor under
green tests. Specs, plans, and tasks MUST include test tasks before
implementation tasks for each user story. Merging without tests that cover the
changed behavior is forbidden.

**Rationale**: TDD keeps the knowledge workspace correct as retrieval, chat, and
organization features grow, and prevents regressions in citation and privacy
boundaries.

### II. Local-First & Ollama-Native

Atlas MUST run fully on the user's machine as the default path. AI features MUST
target local Ollama (or a compatible local OpenAI-style endpoint) as the primary
inference backend. Cloud or remote model providers MAY be added only as explicit,
opt-in configuration—never as a required dependency for core flows.

**Rationale**: Local-first preserves ownership of knowledge, reduces latency to
local hardware, and matches the self-hosted product intent.

### III. Performance & UX Excellence

Interactive paths MUST stay responsive under local hardware constraints. Plans
MUST state measurable performance targets (e.g. time-to-first-token, search
latency, navigation responsiveness). UI work MUST follow established UX
practices: clear hierarchy, accessible controls, predictable feedback for
loading/errors/empty states, and keyboard-friendly core flows. Premature visual
complexity without UX value is forbidden.

**Rationale**: A local AI notebook is only valuable if it feels fast and
trustworthy to use daily.

### IV. Knowledge Organization & NotebookLM-Class Functionality

Atlas MUST provide organization capabilities expected of modern note-taking apps
(workspaces/notebooks, hierarchical or tagged structure, search, and clear
document membership). It MUST also deliver NotebookLM-class functional value:
grounded Q&A over user sources, source-aware chat, and citation or provenance
back to source material. Features that cannot cite or scope to user knowledge
MUST NOT be presented as grounded answers.

**Rationale**: Organization without grounded synthesis is a notes app; synthesis
without organization is a chat toy—Atlas requires both.

### V. Privacy by Design

User content, embeddings, and derived artifacts MUST remain under user control by
default. The system MUST NOT phone home, train on user data, or send documents to
third parties unless the user explicitly configures and consents to that path.
Logs MUST avoid content payloads and secrets. Any telemetry, if ever introduced,
MUST be off by default and documented.

**Rationale**: Privacy is a product requirement, not an afterthought, for a
personal knowledge base.

### VI. User Configurability

Runtime behavior that varies by environment or preference—model name/endpoint,
generation parameters, workspace defaults, theme/UI preferences, and feature
toggles—MUST be user-configurable via documented settings or environment
configuration. Hard-coded values that block local setup or model choice are
forbidden for first-class features.

**Rationale**: Local deployments differ; configurability is how Atlas stays
usable across machines and models.

### VII. Simplicity (YAGNI, KISS, DRY)

Code MUST adhere to YAGNI, KISS, and DRY. Complexity (new services, abstractions,
frameworks, indirection) MUST be added only when an explicit requirement or
measured failure justifies it. Duplication is preferred over the wrong
abstraction until a clear third use case appears. Constitution Check violations
require a Complexity Tracking entry in the plan.

**Rationale**: Simple systems are easier to secure, test, and run locally.

## Runtime & Integration Constraints

- Default deployment target: local (Docker Compose or equivalent local stack).
- Primary LLM integration: Ollama-compatible HTTP API on localhost (or
  user-configured host).
- Data and vector stores used by the app MUST be local by default; remote stores
  require explicit configuration.
- Core API and UI MUST remain usable when only local services are available.
- New external network dependencies for core features require constitution
  review and an opt-in path.

## Quality Gates & Review

- Every feature plan MUST pass Constitution Check before Phase 0 research and
  again after Phase 1 design.
- Every user-story phase in tasks.md MUST list failing tests before
  implementation tasks.
- PRs and agent implementations MUST verify: TDD evidence, local/Ollama path,
  privacy non-exfiltration, configurability of new knobs, and no unjustified
  complexity.
- UX-facing changes MUST include acceptance criteria for loading, error, and
  empty states.
- Performance-sensitive changes MUST include a stated target or measurement
  method in the plan or tasks.

## Governance

This constitution supersedes conflicting informal practices for Atlas work.
Amendments MUST update `.specify/memory/constitution.md`, bump
`CONSTITUTION_VERSION` using semver (MAJOR: remove/redefine principles; MINOR:
add/expand principles or sections; PATCH: clarifications only), set
**Last Amended** to the amendment date, and propagate changes to dependent Spec
Kit templates and skills. Compliance is reviewed at plan Constitution Check,
task generation, and PR/implementation review. Runtime guidance lives in
`README.md` and feature specs under `specs/`.

**Version**: 1.0.0 | **Ratified**: 2026-08-01 | **Last Amended**: 2026-08-01
