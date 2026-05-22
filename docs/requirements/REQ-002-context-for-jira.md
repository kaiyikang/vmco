# REQ-002: Context For JIRA Requirements

- Requirement ID: `REQ-002`
- Status: draft
- Scope: JIRA ticket context prompt generation

## Background

VMCO provides prompt files that let IntelliJ Copilot perform repository work with
more context than the plugin can gather by itself. The Context for JIRA use case
starts from an issue ID, fetches the original ticket JSON, and asks Copilot to
collect the relevant code or documentation inside the repository.

## Goal

Generate one timestamped prompt file from a JIRA ticket so Copilot can inspect
the repository and gather context related to that ticket.

The generated prompt should help Copilot answer:

- What does the ticket ask for?
- Which code areas are likely related?
- Which documents, configs, or tests should be inspected?
- What existing behavior or constraints matter before later work?

## User Workflow

1. The user opens a target repository root.
2. The user runs `python3 bin/context-for-jira.py <ticket-id>`.
3. VMCO reads the JIRA token and JIRA URL template from environment
   configuration or the repository root `.env` file.
4. VMCO builds the ticket URL from the template and ticket ID.
5. VMCO fetches the ticket JSON.
6. VMCO writes a timestamped prompt file into `.llm/`.
7. The user opens IntelliJ Copilot in agent mode.
8. Copilot reads the generated prompt file and collects relevant repository
   context.

## Inputs

- Required: JIRA ticket ID.
- Required: current repository root.
- Required: JIRA URL template from environment configuration.
- Required: JIRA bearer token from environment configuration.
- Optional future extension: output directory override, while keeping `.llm/` as
  the default.

## Implementation Requirements

- The MVP implementation must be a Python script.
- The Python script must use only the Python standard library.
- The script must use Python standard library HTTP support for the JIRA request,
  such as `urllib.request`.
- The script must not require third-party Python packages.
- The script must load the Copilot instruction text from a versioned prompt
  template file instead of embedding the full prompt inline in Python code.
- The CLI entry point must be:

```bash
python3 bin/context-for-jira.py <ticket-id>
```

- On success, the script should print the generated prompt file path.
- On failure, the script should print a clear error to stderr and exit non-zero.

## Environment Requirements

VMCO must read configuration from environment variables, not hard-coded values.

Required configuration:

- `JIRA_TOKEN`: bearer token used to access JIRA.
- `JIRA_URL_TEMPLATE`: URL template containing a placeholder for the ticket ID.

The script must read these values from process environment variables first. If a
value is missing from the process environment, the script may read it from a
repository root `.env` file.

The MVP `.env` parser only needs to support:

- `KEY=VALUE`
- `export KEY=VALUE`
- Blank lines.
- Full-line comments starting with `#`.
- Single-quoted or double-quoted values.

The URL template must use `{ticket}` as the ticket ID placeholder.

Potential future configuration:

- HTTP timeout.
- Proxy configuration.

The script must print a clear error and exit non-zero when `JIRA_TOKEN` or
`JIRA_URL_TEMPLATE` is missing from both the process environment and `.env`.
The error should identify the missing configuration name without printing any
secret values.

The script must not print or write `JIRA_TOKEN` into generated prompt files,
error messages, logs, or metadata.

## JIRA Fetch Requirements

- VMCO must construct the ticket URL from the configured template and the user
  supplied ticket ID.
- VMCO must fetch the ticket with Python standard library HTTP support using
  bearer token authentication and a JSON accept header:

```python
request = urllib.request.Request(
    url,
    headers={
        "Authorization": f"Bearer {jira_token}",
        "Accept": "application/json",
    },
)
```

- VMCO must use a fixed 30 second request timeout in the MVP.
- VMCO must treat the response as JSON.
- VMCO must parse the JSON with the Python standard library.
- VMCO must fail clearly when the ticket cannot be fetched or the response is not
  valid JSON.
- VMCO must exit non-zero and must not generate a prompt file when the HTTP
  request fails.
- VMCO must preserve the original JIRA JSON response in the generated prompt
  instead of selecting or transforming fields in the MVP.
- VMCO does not need to support HTML scraping in the MVP.

## Prompt Output Requirements

Prompt templates must live outside the script code under a dedicated prompt
template directory:

```text
prompts/context-for-jira/v1.md
```

The template version must be explicit and easy to inspect. The generated prompt
file must include:

- Prompt template name.
- Prompt template version.
- Prompt template path.

This makes prompt updates easier to review and makes generated files easier to
debug.

The generated prompt file must start with a fixed metadata block containing:

- Ticket ID.
- Ticket URL.
- Generated timestamp.
- Repository root.
- Prompt template name.
- Prompt template version.
- Prompt template path.

The generated prompt file must include:

- Repository root.
- Ticket ID.
- Ticket URL.
- Original JIRA JSON response, formatted for readability.
- Explicit instructions for Copilot to search related code and documents.
- Output constraints for the context collection result.

The embedded JIRA JSON content must be limited to 20,000 characters. If the
content is truncated, the generated prompt must state that truncation happened.

The output file name must be:

```text
.llm/context-for-jira-{ticket}-{timestamp}.md
```

The timestamp prevents overwriting previous generated prompts.

The default output location must be `.llm/`.

## Copilot Task Requirements

The prompt must instruct Copilot to:

- Read the generated prompt first.
- Identify likely affected modules, packages, configs, and tests.
- Search the repository for domain terms from the ticket.
- Inspect existing code and documents before summarizing context.
- List concrete files that are relevant to the ticket.
- Separate confirmed context from assumptions.
- Avoid editing files.
- Avoid producing an implementation plan.
- If useful, list likely entry points discovered during context collection, but
  do not expand them into implementation steps.

## Copilot Output Constraints

Copilot should produce one concise Markdown context report.

The report should include:

- Ticket summary.
- Relevant files and why each file matters.
- Existing behavior inferred from the repository.
- Tests or test gaps related to the ticket.
- Questions or missing information.
- Likely implementation entry points when appropriate.

The report should not include:

- Generic project commentary.
- Unverified assumptions presented as facts.
- Broad rewrites unrelated to the ticket.
- Code edits.

## Acceptance Criteria

- Running the script with a ticket ID creates one timestamped file in `.llm/`.
- The script fails clearly when the ticket ID, `JIRA_TOKEN`, or
  `JIRA_URL_TEMPLATE` is missing.
- The generated file contains the ticket ID, source ticket URL, and fetched
  original JIRA JSON response.
- The script exits non-zero and does not generate a prompt file when the JIRA
  request fails or returns invalid JSON.
- The JIRA request uses a fixed 30 second timeout.
- The embedded JIRA JSON content is capped at 20,000 characters and clearly
  marked when truncated.
- The generated file starts with the required metadata block.
- The generated file identifies the prompt template name, version, and path used
  to create it.
- The generated instructions constrain Copilot to context collection
  and explicitly forbid code edits during this step.
- The script prints the generated prompt path on success.
- The script never writes `JIRA_TOKEN` into generated files or command output.

## Open Decisions

- Whether fetched ticket JSON should be cached under `.llm/`.
- Whether Context for JIRA should later hand off directly into an implementation
  prompt.
