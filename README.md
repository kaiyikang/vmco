# reviewm

Review Prompt Packager for Java projects.

The default command packages the current working tree diff against `main` or
`master`, adds lightweight Java context, renders a review prompt, and copies it
to the clipboard. Staged and unstaged changes are included by default.

```bash
mvn -q -DskipTests package
./bin/reviewm
```

Default behavior:

```text
current working tree vs origin/main, origin/master, main, or master
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
