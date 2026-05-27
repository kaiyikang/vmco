# REQ-002: Context For JIRA Requirements

- Requirement ID: `REQ-002`
- Status: draft
- Scope: JIRA ticket context prompt generation

## Purpose

Context for JIRA generates a timestamped prompt from a JIRA ticket so IntelliJ
Copilot Agent can collect related repository context before implementation work.
The prompt must ask Copilot to inspect code, configuration, tests, and documents
without editing files or producing an implementation plan.

## Workflow

1. The user opens any directory inside a target Git repository.
2. The user runs `python3 bin/context-for-jira.py <ticket-id>`.
3. VMCO reads JIRA configuration from the process environment or repository
   root `.env`.
4. VMCO builds the ticket URL, fetches the ticket JSON, and renders the prompt
   template.
5. VMCO writes `.llm/context-for-jira-{ticket}-{timestamp}.md`.
6. The user asks Copilot Agent to read the generated file first.
7. Copilot returns one concise Markdown context report.

## Inputs

- Required: JIRA ticket ID.
- Required: current Git repository.
- Required: `JIRA_TOKEN`.
- Required: `JIRA_URL_TEMPLATE` containing `{ticket}`.

## Implementation And Configuration

- The MVP entry point is `python3 bin/context-for-jira.py <ticket-id>`.
- The script must use only the Python standard library, including
  `urllib.request` for HTTP.
- The script must load instruction text from `prompts/context-for-jira/v1.md`
  instead of embedding the full prompt in Python code.
- On success, the script prints the generated prompt path.
- On failure, it prints a clear stderr error and exits non-zero.

Configuration lookup order:

1. Process environment.
2. Repository root `.env`.

The MVP `.env` parser supports `KEY=VALUE`, `export KEY=VALUE`, blank lines,
full-line comments starting with `#`, and single-quoted or double-quoted values.

The script must never print or write `JIRA_TOKEN` into generated prompts, error
messages, logs, or metadata.

## JIRA Fetch Requirements

VMCO must:

- Construct the ticket URL by replacing `{ticket}` with the URL-encoded ticket
  ID.
- Fetch the ticket with bearer token authentication and
  `Accept: application/json`.
- Use a fixed 30 second timeout in the MVP.
- Parse the response as JSON with the Python standard library.
- Fail clearly and avoid generating a prompt when the request fails or the
  response is not valid JSON.
- Preserve the original JIRA JSON response in the generated prompt, formatted for
  readability.
- Skip HTML scraping in the MVP.

## Prompt File Requirements

The generated prompt must start with a frontmatter metadata block containing:

- `ticket_id`
- `ticket_url`
- `generated_at`
- `repo_root`
- `template_name`
- `template_version`
- `template_path`
- `jira_json_truncated`

The body must not repeat these metadata fields in a separate metadata section.
It should contain only the formatted JIRA JSON, truncation note, task
instructions, and report constraints.

The embedded JIRA JSON must be capped at 20,000 characters and clearly marked
when truncated.

The timestamped output path prevents overwriting previous generated prompts:

```text
.llm/context-for-jira-{ticket}-{timestamp}.md
```

## Copilot Report Constraints

The prompt must instruct Copilot to:

- Read the generated prompt first.
- Search the repository for domain terms from the ticket.
- Identify relevant modules, packages, configs, tests, and documents.
- Separate confirmed context from assumptions.
- Avoid code edits and implementation plans.
- List likely entry points only when useful, without expanding them into steps.

Copilot should produce one concise Markdown context report with:

- Ticket summary.
- Relevant files and why each file matters.
- Existing behavior inferred from the repository.
- Tests or test gaps related to the ticket.
- Questions or missing information.
- Likely implementation entry points when appropriate.

The report must not include generic project commentary, unverified assumptions as
facts, broad rewrites unrelated to the ticket, or code edits.

## Acceptance Criteria

- Running the script with a ticket ID creates one timestamped file in `.llm/`.
- The script fails clearly when the ticket ID, `JIRA_TOKEN`, or
  `JIRA_URL_TEMPLATE` is missing.
- The generated file contains the ticket ID, source ticket URL, and fetched
  original JIRA JSON response.
- The script exits non-zero and does not generate a prompt when the JIRA request
  fails or returns invalid JSON.
- The JIRA request uses a fixed 30 second timeout.
- The embedded JIRA JSON is capped at 20,000 characters and marked when
  truncated.
- The generated file starts with the required frontmatter and does not repeat it
  in the body.
- The generated file identifies the prompt template name, version, and path.
- The generated instructions constrain Copilot to context collection and forbid
  code edits.
- The script prints the generated prompt path on success.
- The script never writes `JIRA_TOKEN` into generated files or command output.

## Open Decisions

- Whether fetched ticket JSON should be cached under `.llm/`.
- Whether Context for JIRA should later hand off directly into an implementation
  prompt.
- Whether output directory override should be supported.
- Whether HTTP timeout and proxy configuration should become configurable.
