# reviewm

Review Prompt Packager for Java projects.

The default command fetches `origin`, packages the committed current branch
diff against the latest `origin/main` or `origin/master`, adds lightweight Java
context, renders a review prompt, and copies it to the clipboard. Staged and
unstaged working tree changes are ignored by default, matching the code that
would be compared when opening a pull request from the branch.

```bash
mvn -q -DskipTests package
./bin/reviewm
```

The script can be called from another repository without changing directories
to the tool checkout:

```bash
cd /path/to/java-project
bash /path/to/reviewm/bin/reviewm
```

`reviewm` uses the caller's current working directory to find the target Git
repository.

Default behavior:

```text
latest origin/main or origin/master vs current branch HEAD
-> collect git diff
-> collect changed Java method/class context
-> render prompt
-> copy prompt to clipboard
```

Common examples:

```bash
./bin/reviewm --console
./bin/reviewm --copy
./bin/reviewm --base main
./bin/reviewm --current feature/my-branch
./bin/reviewm --template default-review
./bin/reviewm --language en-US
./bin/reviewm --focus correctness,regression,tests
./bin/reviewm --max-context-chars 24000
./bin/reviewm --no-context
./bin/reviewm --include-callers
./bin/reviewm --output file --file /tmp/review-prompt.md
```

`reviewm prompt` is also accepted and behaves the same as `reviewm`.

## CLI reference

```text
reviewm [options]
reviewm prompt [options]
```

Commands:

| Command | Description |
| --- | --- |
| `reviewm` | Default command. Packages a PR-style review prompt and copies it to the clipboard. |
| `reviewm prompt` | Explicit form of the default command. Behaves the same as `reviewm`. |
| `reviewm review` | Reserved for future direct LLM client execution. Not available in the first version. |

Options:

| Option | Default | Description |
| --- | --- | --- |
| `--copy` | enabled | Copy the rendered prompt to the clipboard. |
| `--console` | disabled | Print the rendered prompt to stdout. |
| `--output <clipboard\|console\|file>` | `clipboard` | Select the output target. |
| `--file <path>` | none | Output file path. Required when `--output file` is used. |
| `--base <branch>` | `auto` | Base branch/ref. Auto tries `origin/main`, `origin/master`, `main`, then `master`. |
| `--current <branch>` | current HEAD branch | Current branch/ref to compare. |
| `--template <name>` | `default-review` | Prompt template name from `src/main/resources/prompts/<name>.md`. |
| `--language <language>` | `en-US` | Language instruction passed into the prompt. |
| `--focus <a,b,c>` | `correctness,regression,tests,null-safety` | Comma-separated review focus list. |
| `--max-context-chars <n>` | `16000` | Maximum character budget for collected Java context. |
| `--no-context` | disabled | Disable Java method/class context collection. |
| `--include-callers` | disabled | Request caller context. In v0.1 this is reserved and emits a warning. |
| `-h`, `--help` | disabled | Show CLI help. |

Focus values:

```text
correctness
regression
tests
null-safety
concurrency
performance
security
api-compatibility
```

First-version scope:

- Git CLI based diff against the merge base.
- Automatic base branch detection.
- Clipboard, console, and file output.
- Lightweight Java method/class extraction around changed lines.
- Hexagonal/Clean package boundaries for future adapters.

Future adapters can add JavaParser/Spoon context, Ollama review execution, and
OpenAI-compatible review clients without changing the core use case.
