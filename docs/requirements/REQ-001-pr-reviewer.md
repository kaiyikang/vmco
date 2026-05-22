# REQ-001: PR Reviewer Requirements

- Requirement ID: `REQ-001`
- Status: draft
- Scope: PR review prompt generation

## Background

VMCO provides external context files for IntelliJ Copilot. Because the Copilot
plugin cannot reliably call external tools by itself, VMCO runs outside the IDE,
collects repository information, and writes a prompt file that Copilot can read
in agent mode.

The PR Reviewer use case packages branch changes into a focused review request.
It should help Copilot review production behavior changes without wasting context
on broad style feedback or unrelated files.

## Goal

Generate one timestamped prompt file that lets Copilot produce a concise pull
request review report.

The report must answer:

- What core production behavior changed?
- Are those changes protected by tests?
- Are configuration changes correct and covered?
- Which risks are blocker, major, minor, or nit?
- What exact code or test changes should be made?

## User Workflow

1. The user opens a target Git repository root.
2. The user runs the PR Reviewer script from the repository root.
3. The user optionally passes a base branch; if omitted, the default is
   `master`.
4. VMCO fetches the latest base branch state before building the prompt.
5. VMCO writes a timestamped prompt file into `.llm/`.
6. The user opens IntelliJ Copilot in agent mode.
7. Copilot reads the generated prompt file and inspects the referenced files and
   diffs.
8. Copilot outputs one review report only.

## Inputs

- Required: current Git repository.
- Optional: base branch name.
- Default base branch: `master`.
- Optional future extension: output directory override, while keeping `.llm/` as
  the default.

## Git And Diff Requirements

- VMCO must verify that the current working directory is inside a Git repository.
- VMCO must resolve and operate from the repository root.
- VMCO must fetch the latest remote state for the selected base branch when a
  remote is available.
- VMCO must compare the current branch against the base branch using PR-style
  merge-base semantics, equivalent to `<base>...HEAD`.
- VMCO must collect a changed-file overview before collecting detailed context.
- VMCO must avoid embedding full diffs for every changed file by default, because
  large diffs can exceed the model context budget.
- VMCO must include enough commands or references for Copilot to inspect exact
  diffs on demand.

## File Selection Requirements

The generated prompt should prioritize core production and configuration changes.

Include:

- Production source files.
- Application configuration files.
- Build files that can affect runtime behavior.
- Deployment or Helm configuration files.
- Mapper, DTO, entity, controller, resource, adapter, use case, and service
  files when present.

Initial Java/Maven MVP behavior:

- Treat Java, XML, YAML, YML, and properties files under `src/main/` as core
  production or configuration candidates.
- Treat `pom.xml`, `application.yaml`, `application.yml`, and
  `application.properties` as core configuration or build candidates.
- Treat Helm and deployment YAML/YML files, including `values.*`,
  `helm/*.yaml`, `helm/*.yml`, `templates/*.yaml`, and `templates/*.yml`, as
  core configuration candidates.

Exclude by default:

- Test files from the primary changed-file list.
- Build output directories such as `target/`, `build/`, `.gradle/`, and
  generated sources.
- Dependency directories such as `node_modules/`.
- Binary or media files such as `.class`, `.jar`, `.war`, `.zip`, `.png`,
  `.jpg`, `.jpeg`, `.gif`, and `.pdf`.
- Pure formatting or documentation-only changes unless they affect generated
  behavior.

## Context Requirements

Each selected production or configuration file should include:

- File path.
- Changed-symbol hint when the file type supports it.
- Possible related test files.
- Suggested command for inspecting the exact diff.
- Any context needed to evaluate behavior, configuration impact, and regression
  risk.

Java projects should detect related tests by filename conventions such
as:

- `FooTest`
- `FooTests`
- `FooIT`
- `FooITCase`
- `FooIntegrationTest`

Related-test detection should be Java-only, best-effort, and filename-based.
For non-Java files, the prompt should state that automatic test matching is not
available. For Java files, VMCO should search Git-tracked `src/test/**/*.java`
files by exact class name and by a normalized keyword. The normalized keyword may
strip common production suffixes such as `Resource`, `Controller`, `Mapper`,
`Adapter`, and `UseCase`.

Changed-symbol hints should also be Java-only and heuristic. They may be derived
from changed diff lines that mention Java declarations, visibility modifiers,
common return or field types, or common Spring mapping annotations. The prompt
must make clear that Copilot should inspect the exact diff when the hint is
missing, incomplete, or ambiguous.

## Prompt Output Requirements

The generated prompt file must include:

- Repository root.
- Base branch.
- Changed-file overview.
- Diff stat.
- Core production/configuration files changed.
- Related-test hints.
- Explicit review instructions.
- Explicit output constraints.

The file name must be timestamped to avoid overwriting previous generated
prompts.

The default output location must be `.llm/`.

If no core production or configuration files are detected, VMCO must still
generate the prompt file and explicitly state that the primary review target
list is empty.

## Copilot Output Constraints

The prompt must instruct Copilot to output one concise Markdown review report.

Each finding must include:

- Severity: `blocker`, `major`, `minor`, or `nit`.
- Location: exact file and class, method, or field when possible.
- Changed behavior.
- Problem.
- Existing test coverage.
- Missing or weak test coverage.
- Suggested fix or suggested test.

The prompt must also instruct Copilot to:

- Focus on behavior changes, missing tests, weak assertions, regression risk,
  API compatibility, mapper/DTO/entity field mistakes, null or default behavior,
  and configuration impact.
- Prefer concrete findings over generic advice.
- Avoid broad style review.
- Avoid comments on formatting, import order, or naming unless they hide a real
  defect.
- Explain missing evidence before raising speculative concerns.
- Briefly say so when a changed behavior is already well covered by tests.
- State explicitly when no blocker or major issue is found.

## Implementation Requirements

- The MVP implementation should be written in Python.
- The implementation should prefer the Python standard library and Git CLI
  commands, and should avoid external runtime dependencies unless they are
  explicitly justified.

## Acceptance Criteria

- Running the script from a Git repository root creates one timestamped file in
  `.llm/`.
- The generated file contains the selected base branch, repository root,
  changed-file overview, and diff stat.
- Non-core files are filtered out of the primary review target list,
  and related tests are listed when they can be inferred by filename.
- If no core production or configuration files are detected, the prompt is still
  generated and clearly states that no primary review targets were found.
- The generated instructions constrain Copilot to one stable review
  report format.

## Open Decisions

- Whether the default base branch should remain `master` or become configurable
  per repository.
- Whether VMCO should support both `main` and `master` fallback detection.
- Whether full diffs should ever be embedded automatically for small changes.
- Whether test files should be excluded entirely or shown in a separate
  supporting section.
