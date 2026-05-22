# VMCO

Languages: English | [Deutsch](README.de.md) | [中文](README.zh-CN.md)

VMCO generates context prompts that IntelliJ Copilot can read.

When the Copilot plugin cannot call external tools directly, VMCO collects repository, branch diff, or ticket information outside the IDE and writes a prompt file for Copilot Agent.

## Quick Start

From the root directory of the project you want to analyze, call the VMCO script:

```bash
/path/to/vmco/bin/create-llm-review-diffs.sh [base-branch] [output-dir]
```

Defaults:

- `base-branch`: `master`
- `output-dir`: `.llm`
- output file: `.llm/review-instruction.md`

After generation, switch IntelliJ Copilot to Agent mode and ask it to read the output file first.

## Use Cases

- PR Reviewer: generates a context prompt for pull request review.
- Context for JIRA: generates a context collection prompt from a JIRA ticket.

## Design Principles

- External scripts collect facts and generate prompts.
- Copilot reads the prompt and continues analysis inside the repository.
- Prompts should be single-file, traceable, and explicit about constraints.
- Large diffs should be avoided by default to reduce context waste.
- Prompts should include version information.
