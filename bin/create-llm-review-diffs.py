#!/usr/bin/env python3
import re
import subprocess
import sys
from datetime import datetime
from pathlib import Path, PurePosixPath
from string import Template


TEMPLATE_NAME = "pr-reviewer"
TEMPLATE_VERSION = "v1"
TOOL_ROOT = Path(__file__).resolve().parents[1]
TEMPLATE_PATH = TOOL_ROOT / "prompts" / TEMPLATE_NAME / f"{TEMPLATE_VERSION}.md"
DEFAULT_BASE_BRANCH = "master"
DEFAULT_OUTPUT_DIR = ".llm"
OUTPUT_FILE_NAME = "review-instruction.md"

MAX_RELATED_TESTS = 40
MAX_SYMBOL_HINTS = 60
MAX_SNIPPET_HUNKS = 8
MAX_SNIPPET_LINES = 80

IGNORED_DIRS = {"target", "build", ".gradle", "generated", "node_modules"}
BINARY_SUFFIXES = {
    ".class",
    ".jar",
    ".war",
    ".zip",
    ".png",
    ".jpg",
    ".jpeg",
    ".gif",
    ".pdf",
}
CORE_SUFFIXES = {".java", ".xml", ".yaml", ".yml", ".properties"}
APPLICATION_FILES = {"application.yaml", "application.yml", "application.properties"}
TEST_SUFFIXES = ("Test", "Tests", "IT", "ITCase", "IntegrationTest")
NORMALIZED_SUFFIXES = ("Resource", "Controller", "Mapper", "Adapter", "UseCase")

SYMBOL_HINT_RE = re.compile(
    r"(class |interface |enum |record |public |protected |private |static |final |"
    r"void |String |boolean |Boolean |int |Integer |long |Long |List<|Set<|Map<|"
    r"Optional<|ResponseEntity|@GetMapping|@PostMapping|@PutMapping|@DeleteMapping|"
    r"@PatchMapping)"
)
HUNK_RE = re.compile(r"^@@ -(?P<start>\d+)(?:,(?P<count>\d+))? \+\d+(?:,\d+)? @@")


def fail(message):
    print(f"Error: {message}", file=sys.stderr)
    return 1


def usage():
    print(
        "Usage: python3 bin/create-llm-review-diffs.py [base-branch] [output-dir]",
        file=sys.stderr,
    )


def resolve_repo_root():
    result = subprocess.run(
        ["git", "rev-parse", "--show-toplevel"],
        check=False,
        capture_output=True,
        encoding="utf-8",
        errors="replace",
    )
    if result.returncode != 0:
        raise RuntimeError("current directory is not inside a Git repository")
    return Path(result.stdout.strip()).resolve()


def run_git(repo_root, args, check=True):
    result = subprocess.run(
        ["git", *args],
        cwd=repo_root,
        check=False,
        capture_output=True,
        encoding="utf-8",
        errors="replace",
    )
    if check and result.returncode != 0:
        message = result.stderr.strip() or result.stdout.strip() or "git command failed"
        raise RuntimeError(message)
    return result.stdout


def posix_parts(path):
    return PurePosixPath(path).parts


def is_ignored_path(path):
    parts = set(posix_parts(path))
    suffix = PurePosixPath(path).suffix.lower()
    return bool(parts & IGNORED_DIRS) or suffix in BINARY_SUFFIXES


def is_test_file(path):
    if is_ignored_path(path):
        return False

    parts = posix_parts(path)
    filename = PurePosixPath(path).name
    in_src_test = any(
        parts[index] == "src" and parts[index + 1] == "test"
        for index in range(len(parts) - 1)
    )
    java_test_name = filename.endswith(
        tuple(f"{suffix}.java" for suffix in TEST_SUFFIXES)
    )
    return in_src_test or java_test_name


def is_core_file(path):
    if is_test_file(path) or is_ignored_path(path):
        return False

    posix_path = PurePosixPath(path)
    parts = posix_parts(path)
    filename = posix_path.name
    suffix = posix_path.suffix.lower()

    in_src_main = any(
        parts[index] == "src" and parts[index + 1] == "main"
        for index in range(len(parts) - 1)
    )
    if in_src_main and suffix in CORE_SUFFIXES:
        return True

    if filename == "pom.xml" or filename in APPLICATION_FILES:
        return True

    if filename in {"values.yaml", "values.yml", "values.xml"}:
        return True

    if suffix in {".yaml", ".yml"} and ("helm" in parts or "templates" in parts):
        return True

    return False


def changed_files(repo_root, base_branch):
    output = run_git(
        repo_root,
        [
            "diff",
            "--name-only",
            f"{base_branch}...HEAD",
            "--",
            ".",
            ":(exclude)**/target/**",
            ":(exclude)**/build/**",
            ":(exclude)**/generated/**",
            ":(exclude)**/.gradle/**",
            ":(exclude)**/node_modules/**",
        ],
    )
    return [line for line in output.splitlines() if line.strip()]


def java_test_files(repo_root):
    output = run_git(
        repo_root,
        ["ls-files", "*/src/test/**/*.java", "src/test/**/*.java"],
        check=False,
    )
    return sorted({line for line in output.splitlines() if line.strip()})


def normalized_keyword(path):
    name = PurePosixPath(path).stem
    for suffix in NORMALIZED_SUFFIXES:
        if name.endswith(suffix):
            return name[: -len(suffix)]
    return name


def find_related_tests(repo_root, path):
    if not path.endswith(".java"):
        return "No automatic test matching for non-Java file."

    exact = PurePosixPath(path).stem
    keyword = normalized_keyword(path)
    matches = []

    for test_file in java_test_files(repo_root):
        stem = PurePosixPath(test_file).stem
        has_test_suffix = stem.endswith(TEST_SUFFIXES)
        matches_exact = stem == exact or stem.startswith(exact)
        matches_keyword = bool(keyword) and keyword != exact and keyword in stem
        if has_test_suffix and (matches_exact or matches_keyword):
            matches.append(test_file)

    if not matches:
        return "No obvious related tests found by filename matching. Please search tests manually."

    return "\n".join(matches[:MAX_RELATED_TESTS])


def changed_symbol_hints(repo_root, base_branch, path):
    if not path.endswith(".java"):
        return "No Java symbol hint for non-Java file."

    output = run_git(repo_root, ["diff", "-U0", f"{base_branch}...HEAD", "--", path])
    hints = []

    for line in output.splitlines():
        if line.startswith(("+++", "---")):
            continue
        if line.startswith(("+", "-")) and SYMBOL_HINT_RE.search(line):
            hints.append(f"    {line}")
        if len(hints) >= MAX_SYMBOL_HINTS:
            break

    if not hints:
        return "No obvious Java symbol hint extracted. Please inspect git diff directly."

    return "\n".join(hints)


def merge_base_file_lines(repo_root, merge_base, path):
    result = subprocess.run(
        ["git", "show", f"{merge_base}:{path}"],
        cwd=repo_root,
        check=False,
        capture_output=True,
        encoding="utf-8",
        errors="replace",
    )
    if result.returncode != 0:
        return None
    return result.stdout.splitlines()


def changed_old_ranges(repo_root, base_branch, path):
    output = run_git(repo_root, ["diff", "-U0", f"{base_branch}...HEAD", "--", path])
    ranges = []

    for line in output.splitlines():
        match = HUNK_RE.match(line)
        if not match:
            continue

        start = int(match.group("start"))
        count = int(match.group("count") or "1")
        if start == 0 or count == 0:
            continue

        first_line = max(start - 2, 1)
        last_line = start + count + 1
        ranges.append((first_line, last_line))

    return ranges


def merge_base_snippets(repo_root, base_branch, merge_base, path):
    lines = merge_base_file_lines(repo_root, merge_base, path)
    if lines is None:
        return (
            "No merge-base-side snippet available. The file may be new, renamed, "
            "or absent at the merge base."
        )

    ranges = changed_old_ranges(repo_root, base_branch, path)
    if not ranges:
        return (
            "No merge-base-side changed lines detected. The change may be add-only "
            "in the current branch."
        )

    snippets = []
    for start, end in ranges[:MAX_SNIPPET_HUNKS]:
        capped_end = min(end, start + MAX_SNIPPET_LINES - 1, len(lines))
        truncated = ""
        if end - start + 1 > MAX_SNIPPET_LINES:
            truncated = f" (truncated to {MAX_SNIPPET_LINES} lines)"
        snippet_lines = [
            f"{line_number:6}\t{lines[line_number - 1]}"
            for line_number in range(start, capped_end + 1)
        ]
        snippets.append(
            f"--- merge-base:{path} lines {start}-{capped_end}{truncated} ---\n"
            + "\n".join(snippet_lines)
        )

    if len(ranges) > MAX_SNIPPET_HUNKS:
        snippets.append(f"--- snippet list truncated to {MAX_SNIPPET_HUNKS} hunks ---")

    return "\n".join(snippets)


def fenced_block(language, content):
    return f"```{language}\n{content}\n```"


def render_core_files(core_files):
    if not core_files:
        return "No core production/config files detected by the MVP script."
    return "\n".join(f"- `{path}`" for path in core_files)


def render_core_context(repo_root, base_branch, merge_base, core_files):
    sections = []

    for path in core_files:
        sections.append(
            "\n".join(
                [
                    f"### {path}",
                    "",
                    "Changed symbols hint:",
                    "",
                    fenced_block("diff", changed_symbol_hints(repo_root, base_branch, path)),
                    "",
                    "Merge-base-side changed hunk snippets:",
                    "",
                    fenced_block("", merge_base_snippets(repo_root, base_branch, merge_base, path)),
                    "",
                    "Possible related tests, fast filename-based match only:",
                    "",
                    fenced_block("", find_related_tests(repo_root, path)),
                    "",
                    "Suggested command for inspecting the exact diff:",
                    "",
                    fenced_block("bash", f'git diff -U2 {base_branch}...HEAD -- "{path}"'),
                ]
            )
        )

    return "\n\n".join(sections)


def render_changed_tests(repo_root, base_branch, merge_base, test_files):
    if not test_files:
        return "No changed test files detected by the MVP script."

    sections = []
    for path in test_files:
        sections.append(
            "\n".join(
                [
                    f"### {path}",
                    "",
                    "Merge-base-side changed hunk snippets:",
                    "",
                    fenced_block("", merge_base_snippets(repo_root, base_branch, merge_base, path)),
                    "",
                    "Suggested command for inspecting the exact test diff:",
                    "",
                    fenced_block("bash", f'git diff -U2 {base_branch}...HEAD -- "{path}"'),
                ]
            )
        )

    return "\n\n".join(sections)


def render_prompt(repo_root, base_branch):
    if not TEMPLATE_PATH.is_file():
        raise RuntimeError(f"prompt template not found: {TEMPLATE_PATH}")

    merge_base = run_git(repo_root, ["merge-base", base_branch, "HEAD"]).strip()
    files = changed_files(repo_root, base_branch)
    core_files = [path for path in files if is_core_file(path)]
    test_files = [path for path in files if is_test_file(path)]

    template_text = TEMPLATE_PATH.read_text(encoding="utf-8")
    generated_at = datetime.now().astimezone().isoformat(timespec="seconds")

    return Template(template_text).substitute(
        generated_at=generated_at,
        repo_root=str(repo_root),
        base_branch=base_branch,
        merge_base=merge_base,
        template_name=TEMPLATE_NAME,
        template_version=TEMPLATE_VERSION,
        template_path=str(TEMPLATE_PATH),
        changed_files_overview=run_git(
            repo_root, ["diff", "--name-status", f"{base_branch}...HEAD"]
        ).strip(),
        diff_stat=run_git(repo_root, ["diff", "--stat", f"{base_branch}...HEAD"]).strip(),
        core_files_section=render_core_files(core_files),
        core_context_section=render_core_context(repo_root, base_branch, merge_base, core_files),
        changed_test_files_section=render_changed_tests(
            repo_root, base_branch, merge_base, test_files
        ),
    )


def write_prompt(repo_root, output_dir, content):
    output_dir_path = Path(output_dir)
    if not output_dir_path.is_absolute():
        output_dir_path = repo_root / output_dir_path

    output_dir_path.mkdir(parents=True, exist_ok=True)
    output_path = output_dir_path / OUTPUT_FILE_NAME
    output_path.write_text(content, encoding="utf-8")
    return output_path


def main(argv):
    if len(argv) > 3 or (len(argv) > 1 and argv[1] in {"-h", "--help"}):
        usage()
        return 1

    base_branch = argv[1] if len(argv) >= 2 else DEFAULT_BASE_BRANCH
    output_dir = argv[2] if len(argv) >= 3 else DEFAULT_OUTPUT_DIR

    try:
        repo_root = resolve_repo_root()
        prompt = render_prompt(repo_root, base_branch)
        output_path = write_prompt(repo_root, output_dir, prompt)
    except RuntimeError as exc:
        return fail(str(exc))

    print("Generated:")
    print(f"  {output_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
