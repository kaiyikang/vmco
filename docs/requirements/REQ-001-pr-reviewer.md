# REQ-001: PR Reviewer Requirements

- Requirement ID: `REQ-001`
- Status: draft
- Scope: PR review prompt generation

## Purpose

PR Reviewer generates a prompt file that lets IntelliJ Copilot Agent review
branch changes when the plugin cannot gather Git context itself. The prompt
keeps Copilot focused on production behavior, test coverage, configuration
impact, and concrete risk.

## Workflow

1. The user opens any directory inside a target Git repository.
2. The user runs `bin/create-llm-review-diffs.sh [base-branch] [output-dir]`.
3. VMCO resolves the repository root and compares `<base-branch>...HEAD`.
4. VMCO writes `<output-dir>/review-instruction.md`.
5. The user asks Copilot Agent to read that file first.
6. Copilot inspects referenced diffs/tests and returns one concise Markdown
   review report.

Defaults:

- `base-branch`: `master`
- `output-dir`: `.llm`

## Inputs

- Required: current Git repository.
- Optional: base branch.
- Optional: output directory.

## Diff And File Selection

VMCO must:

- Verify that the current working directory is inside a Git repository.
- Operate from the repository root.
- Use PR-style merge-base diff semantics, equivalent to `<base>...HEAD`.
- Include a changed-file overview and diff stat.
- Avoid embedding full diffs by default; include commands for exact diff
  inspection instead.

Primary review targets are changed production or configuration files. The
Java/Maven MVP includes:

- Java, XML, YAML, YML, and properties files under `src/main/`.
- `pom.xml` and `application.{yaml,yml,properties}`.
- Helm/deployment files such as `values.yaml`, `values.yml`, `values.xml`,
  `helm/*.yaml`, `helm/*.yml`, `templates/*.yaml`, and `templates/*.yml`.

Exclude from the primary target list:

- Test files.
- Build output or generated directories such as `target/`, `build/`, `.gradle/`,
  and `generated/`.
- Dependency directories such as `node_modules/`.
- Binary or media files such as `.class`, `.jar`, `.war`, `.zip`, `.png`,
  `.jpg`, `.jpeg`, `.gif`, and `.pdf`.
- Formatting-only or documentation-only changes unless they affect generated
  behavior.

## Context Hints

For each primary target, the prompt should include:

- File path.
- Changed Java symbol hint when available.
- Possible related Java tests when filename matching can infer them.
- Suggested command for inspecting the exact diff.

Java test matching is best-effort and filename-based. It should search
Git-tracked `src/test/**/*.java` files by exact class name and by a normalized
keyword that may strip suffixes such as `Resource`, `Controller`, `Mapper`,
`Adapter`, and `UseCase`. Common test suffixes include `Test`, `Tests`, `IT`,
`ITCase`, and `IntegrationTest`.

Non-Java files should state that automatic test matching and Java symbol hints
are unavailable.

## Prompt File Requirements

The generated prompt file must include:

- Repository root.
- Base branch.
- Changed-file overview.
- Diff stat.
- Primary production/configuration targets.
- Related-test hints.
- Review instructions and output constraints.

If no primary targets are detected, VMCO must still generate the prompt and state
that the primary review target list is empty.

## Copilot Report Constraints

The prompt must instruct Copilot to produce one concise Markdown report.

Each finding must include:

- Severity: `blocker`, `major`, `minor`, or `nit`.
- Location: exact file and class, method, or field when possible.
- Changed behavior.
- Problem.
- Existing test coverage.
- Missing or weak test coverage.
- Suggested fix or suggested test.

The report should focus on behavior changes, missing tests, weak assertions,
regression risk, API compatibility, mapper/DTO/entity field mistakes, null or
default behavior, and configuration impact. It should avoid broad style review
and formatting comments unless they hide a real defect.

## Implementation Requirements

- The MVP entry point is `bin/create-llm-review-diffs.sh`.
- The implementation should use Bash, Git CLI, and common Unix tools.
- Third-party runtime dependencies should be avoided unless explicitly
  justified.

## Acceptance Criteria

- Running the script from a Git repository creates or overwrites
  `<output-dir>/review-instruction.md`.
- The generated file contains the selected base branch, repository root,
  changed-file overview, and diff stat.
- Non-core files are filtered out of the primary review target list.
- Related tests are listed when they can be inferred by Java filename matching.
- If no primary targets are detected, the prompt clearly says so.
- The generated instructions constrain Copilot to one stable review report.

## Open Decisions

- Whether the default base branch should remain `master` or be auto-detected per
  repository.
- Whether VMCO should fetch the latest remote base branch before generating the
  prompt.
- Whether small diffs should ever be embedded automatically.
- Whether changed test files should appear in a separate supporting section.
- Whether PR Reviewer output should become timestamped like Context for JIRA.
