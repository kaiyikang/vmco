# VMCO

Languages: English | [Deutsch](README.de.md) | [中文](README.zh-CN.md)

VMCO creates prompt files for IntelliJ Copilot Agent when the IDE plugin cannot
collect external repository, diff, or ticket context by itself.

## PR Reviewer

From the root directory of the project you want to analyze, call the VMCO script:

```bash
/path/to/vmco/bin/create-llm-review-diffs.sh [base-branch] [output-dir]
```

Defaults:

- `base-branch`: `master`
- `output-dir`: `.llm`
- output file: `<output-dir>/review-instruction.md`

After generation, switch IntelliJ Copilot to Agent mode and ask it to read the
output file first.

## Context For JIRA

Set `JIRA_TOKEN` and `JIRA_URL_TEMPLATE` in the environment or in the target
repository `.env` file. The URL template must contain `{ticket}`.

```bash
python3 /path/to/vmco/bin/context-for-jira.py <ticket-id>
```

The script writes `.llm/context-for-jira-{ticket}-{timestamp}.md`.

## Design Principles

- Scripts collect facts outside the IDE; Copilot analyzes inside the repository.
- Generated prompts are single-file, traceable, and explicit about constraints.
- Large diffs are referenced instead of embedded by default.
- Versioned prompt templates are used when prompts need stable review history.
