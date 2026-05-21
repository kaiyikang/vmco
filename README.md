# reviewm

Review Prompt Packager for Java projects.

```bash
./bin/reviewm
```

`reviewm` fetches `origin` when available, compares the current branch HEAD
against `master` or `main`, adds lightweight Java context, renders the default
English review prompt, and copies it to the clipboard.

Uncommitted working tree changes are ignored.
