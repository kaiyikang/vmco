#!/usr/bin/env python3
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime
from pathlib import Path
from string import Template


TEMPLATE_NAME = "context-for-jira"
TEMPLATE_VERSION = "v1"
TEMPLATE_RELATIVE_PATH = Path("prompts") / TEMPLATE_NAME / f"{TEMPLATE_VERSION}.md"
OUTPUT_DIR = ".llm"
REQUEST_TIMEOUT_SECONDS = 30
MAX_JIRA_JSON_CHARS = 20_000
DOTENV_FILE = ".env"


def fail(message):
    print(f"Error: {message}", file=sys.stderr)
    return 1


def usage():
    print("Usage: python3 bin/context-for-jira.py <ticket-id>", file=sys.stderr)


def resolve_repo_root():
    result = subprocess.run(
        ["git", "rev-parse", "--show-toplevel"],
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        raise RuntimeError("current directory is not inside a Git repository")
    return Path(result.stdout.strip()).resolve()


def load_dotenv(repo_root):
    dotenv_path = repo_root / DOTENV_FILE
    if not dotenv_path.is_file():
        return {}

    values = {}
    lines = dotenv_path.read_text(encoding="utf-8").splitlines()
    for line_number, raw_line in enumerate(lines, 1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[len("export ") :].lstrip()
        if "=" not in line:
            raise RuntimeError(f"invalid .env line {line_number}: expected KEY=VALUE")

        key, value = line.split("=", 1)
        key = key.strip()
        if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", key):
            raise RuntimeError(f"invalid .env line {line_number}: invalid variable name")

        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
            value = value[1:-1]
        values[key] = value

    return values


def require_config(name, dotenv_values):
    value = os.environ.get(name, "").strip()
    if not value:
        value = dotenv_values.get(name, "").strip()
    if not value:
        raise RuntimeError(f"required configuration {name} is missing from environment and .env")
    return value


def build_ticket_url(template, ticket_id):
    if "{ticket}" not in template:
        raise RuntimeError("JIRA_URL_TEMPLATE must contain the {ticket} placeholder")
    encoded_ticket = urllib.parse.quote(ticket_id, safe="")
    return template.replace("{ticket}", encoded_ticket)


def fetch_jira_json(url, jira_token):
    request = urllib.request.Request(
        url,
        headers={
            "Authorization": f"Bearer {jira_token}",
            "Accept": "application/json",
        },
    )

    try:
        with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
            charset = response.headers.get_content_charset() or "utf-8"
            body = response.read().decode(charset)
    except urllib.error.HTTPError as exc:
        raise RuntimeError(f"JIRA request failed with HTTP {exc.code}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"JIRA request failed: {exc.reason}") from exc
    except TimeoutError as exc:
        raise RuntimeError("JIRA request timed out") from exc
    except UnicodeDecodeError as exc:
        raise RuntimeError("JIRA response could not be decoded as text") from exc

    try:
        return json.loads(body)
    except json.JSONDecodeError as exc:
        raise RuntimeError(
            f"JIRA response was not valid JSON at line {exc.lineno}, column {exc.colno}"
        ) from exc


def sanitize_filename_part(value):
    sanitized = re.sub(r"[^A-Za-z0-9._-]+", "-", value.strip())
    return sanitized.strip("-") or "ticket"


def format_jira_json(jira_json):
    formatted = json.dumps(jira_json, ensure_ascii=False, indent=2)
    if len(formatted) <= MAX_JIRA_JSON_CHARS:
        return formatted, "false", "The JIRA JSON content was not truncated."

    truncated = formatted[:MAX_JIRA_JSON_CHARS]
    note = (
        "The JIRA JSON content was truncated from "
        f"{len(formatted)} characters to {MAX_JIRA_JSON_CHARS} characters."
    )
    return truncated, "true", note


def render_prompt(repo_root, ticket_id, ticket_url, jira_json):
    template_path = repo_root / TEMPLATE_RELATIVE_PATH
    if not template_path.is_file():
        raise RuntimeError(f"prompt template not found: {template_path}")

    template_text = template_path.read_text(encoding="utf-8")
    rendered_json, truncated, truncation_note = format_jira_json(jira_json)
    generated_at = datetime.now().astimezone().isoformat(timespec="seconds")

    return Template(template_text).substitute(
        ticket_id=ticket_id,
        ticket_url=ticket_url,
        generated_at=generated_at,
        repo_root=str(repo_root),
        template_name=TEMPLATE_NAME,
        template_version=TEMPLATE_VERSION,
        template_path=str(template_path),
        jira_json_truncated=truncated,
        jira_json_truncation_note=truncation_note,
        jira_json=rendered_json,
    )


def write_prompt(repo_root, ticket_id, content):
    output_dir = repo_root / OUTPUT_DIR
    output_dir.mkdir(parents=True, exist_ok=True)

    timestamp = datetime.now().astimezone().strftime("%Y%m%d-%H%M%S")
    safe_ticket = sanitize_filename_part(ticket_id)
    output_path = output_dir / f"context-for-jira-{safe_ticket}-{timestamp}.md"
    output_path.write_text(content, encoding="utf-8")
    return output_path


def main(argv):
    if len(argv) != 2 or argv[1] in {"-h", "--help"}:
        usage()
        return 1

    ticket_id = argv[1].strip()
    if not ticket_id:
        usage()
        return fail("ticket id is required")

    try:
        repo_root = resolve_repo_root()
        dotenv_values = load_dotenv(repo_root)
        jira_token = require_config("JIRA_TOKEN", dotenv_values)
        jira_url_template = require_config("JIRA_URL_TEMPLATE", dotenv_values)
        ticket_url = build_ticket_url(jira_url_template, ticket_id)
        jira_json = fetch_jira_json(ticket_url, jira_token)
        prompt = render_prompt(repo_root, ticket_id, ticket_url, jira_json)
        output_path = write_prompt(repo_root, ticket_id, prompt)
    except RuntimeError as exc:
        return fail(str(exc))

    print(output_path)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
