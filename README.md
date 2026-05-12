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

Useful options:

```bash
./bin/reviewm --console
./bin/reviewm --copy
./bin/reviewm --base main
./bin/reviewm --template default-review
./bin/reviewm --max-context-chars 24000
./bin/reviewm --no-context
./bin/reviewm --output file --file /tmp/review-prompt.md
```

`reviewm prompt` is also accepted and behaves the same as `reviewm`.

First-version scope:

- Git CLI based diff against the merge base.
- Automatic base branch detection.
- Clipboard, console, and file output.
- Lightweight Java method/class extraction around changed lines.
- Hexagonal/Clean package boundaries for future adapters.

Future adapters can add JavaParser/Spoon context, Ollama review execution, and
OpenAI-compatible review clients without changing the core use case.
