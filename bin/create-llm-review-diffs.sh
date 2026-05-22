#!/usr/bin/env bash
set -euo pipefail

BASE_BRANCH="${1:-main}"
OUT_DIR="${2:-.llm-review}"
OUT_FILE="$OUT_DIR/review-instruction.md"

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "Error: current directory is not inside a Git repository."
  echo "Please cd into the target project first."
  exit 1
fi

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

mkdir -p "$OUT_DIR"

is_core_file() {
  local file="$1"

  case "$file" in
    src/test/*|*/src/test/*)
      return 1
      ;;
    *Test.java|*Tests.java|*IT.java|*ITCase.java|*IntegrationTest.java)
      return 1
      ;;
    */target/*|target/*|*/build/*|build/*|*/generated/*|generated/*|*/node_modules/*)
      return 1
      ;;
    *.class|*.jar|*.war|*.zip|*.png|*.jpg|*.jpeg|*.gif|*.pdf)
      return 1
      ;;
  esac

  case "$file" in
    */src/main/*.java|src/main/*.java)
      return 0
      ;;
    */src/main/*.xml|src/main/*.xml)
      return 0
      ;;
    */src/main/*.yml|src/main/*.yml)
      return 0
      ;;
    */src/main/*.yaml|src/main/*.yaml)
      return 0
      ;;
    */src/main/*.properties|src/main/*.properties)
      return 0
      ;;
    pom.xml|*/pom.xml)
      return 0
      ;;
    */values.yaml|*/values.yml|*/values.xml)
      return 0
      ;;
    */application.yaml|*/application.yml|*/application.properties)
      return 0
      ;;
    */helm/*.yaml|*/helm/*.yml)
      return 0
      ;;
    */templates/*.yaml|*/templates/*.yml)
      return 0
      ;;
  esac

  return 1
}

base_keyword() {
  local file="$1"
  local name
  name="$(basename "$file")"
  name="${name%.*}"

  case "$name" in
    *Resource)
      echo "${name%Resource}"
      ;;
    *Controller)
      echo "${name%Controller}"
      ;;
    *Mapper)
      echo "${name%Mapper}"
      ;;
    *Adapter)
      echo "${name%Adapter}"
      ;;
    *UseCase)
      echo "${name%UseCase}"
      ;;
    *)
      echo "$name"
      ;;
  esac
}

find_related_tests_fast() {
  local file="$1"
  local exact
  local keyword

  exact="$(basename "$file" .java)"
  keyword="$(base_keyword "$file")"

  if [[ "$file" != *.java ]]; then
    echo "No automatic test matching for non-Java file."
    return 0
  fi

  git ls-files \
    '*/src/test/**/*.java' \
    'src/test/**/*.java' \
    2>/dev/null \
    | grep -E "(${exact}|${keyword}).*(Test|Tests|IT|ITCase|IntegrationTest)\.java$|(^|/)${exact}(Test|Tests|IT|ITCase|IntegrationTest)\.java$" \
    | sort -u \
    | head -40 || true
}

changed_symbols_hint_fast() {
  local file="$1"

  if [[ "$file" != *.java ]]; then
    echo "No Java symbol hint for non-Java file."
    return 0
  fi

  git diff -U0 "$BASE_BRANCH...HEAD" -- "$file" \
    | grep -E '^[+-].*(class |interface |enum |record |public |protected |private |static |final |void |String |boolean |Boolean |int |Integer |long |Long |List<|Set<|Map<|Optional<|ResponseEntity|@GetMapping|@PostMapping|@PutMapping|@DeleteMapping|@PatchMapping)' \
    | sed 's/^/    /' \
    | head -60 || true
}

CORE_FILES_TMP="$(mktemp)"

git diff --name-only "$BASE_BRANCH...HEAD" -- . \
  ':(exclude)**/target/**' \
  ':(exclude)**/build/**' \
  ':(exclude)**/generated/**' \
  ':(exclude)**/.gradle/**' \
  ':(exclude)**/node_modules/**' \
  | while IFS= read -r file; do
      if is_core_file "$file"; then
        echo "$file"
      fi
    done > "$CORE_FILES_TMP"

cat > "$OUT_FILE" <<EOF
# LLM Review Instruction

## Core goal

Summarize and review the core production behavior changes in this branch.

Main question:

> Are the production behavior changes protected by tests?

Do not perform a broad style review.

Focus on:

- behavior changes
- missing tests
- weak assertions
- regression risk
- API compatibility
- mapper / DTO / entity field mistakes
- null / default behavior
- configuration impact

## Repository

\`\`\`
$REPO_ROOT
\`\`\`

## Base branch

\`\`\`
$BASE_BRANCH
\`\`\`

## Changed files overview

\`\`\`
$(git diff --name-status "$BASE_BRANCH...HEAD")
\`\`\`

## Diff stat

\`\`\`
$(git diff --stat "$BASE_BRANCH...HEAD")
\`\`\`

## Core production/config files changed

EOF

if [ ! -s "$CORE_FILES_TMP" ]; then
  cat >> "$OUT_FILE" <<EOF
No core production/config files detected by the MVP script.

EOF
else
  while IFS= read -r file; do
    cat >> "$OUT_FILE" <<EOF
- \`$file\`

EOF
  done < "$CORE_FILES_TMP"
fi

cat >> "$OUT_FILE" <<EOF

## Core files and possible related tests

EOF

while IFS= read -r file; do
  [ -n "$file" ] || continue

  related_tests="$(find_related_tests_fast "$file" || true)"
  symbols="$(changed_symbols_hint_fast "$file" || true)"

  cat >> "$OUT_FILE" <<EOF
### $file

Changed symbols hint:

\`\`\`diff
${symbols:-No obvious Java symbol hint extracted. Please inspect git diff directly.}
\`\`\`

Possible related tests, fast filename-based match only:

\`\`\`
${related_tests:-No obvious related tests found by filename matching. Please search tests manually.}
\`\`\`

Suggested command for inspecting the exact diff:

\`\`\`bash
git diff -U2 $BASE_BRANCH...HEAD -- "$file"
\`\`\`

EOF
done < "$CORE_FILES_TMP"

cat >> "$OUT_FILE" <<EOF

## Output constraints for the LLM

Create one concise markdown review report.

The report should be short and list-based.

For each finding, include:

- Severity: blocker / major / minor / nit
- Location: exact file + class / method / field if possible
- Changed behavior
- Problem
- Existing test coverage
- Missing or weak test
- Suggested fix or suggested test

Rules:

1. Point to concrete code locations so the developer can jump to the code quickly.
2. Prefer precise findings over generic advice.
3. Do not comment on formatting, import order, or naming unless it hides a real bug.
4. Do not list issues that are only speculative without explaining what evidence is missing.
5. If a changed behavior is already well covered by tests, say so briefly.
6. If no blocker or major issue is found, say that explicitly.
7. Keep the final report concise.

## Recommended LLM task

Please read this file first.

Then inspect:

1. The changed production/config files listed above
2. Their git diffs
3. The possible related tests listed above

Produce one concise markdown review report answering:

1. What core behavior changed?
2. Is each behavior change protected by tests?
3. What tests are missing or weak?
4. Are there any blocker or major risks?
5. What exact code or test changes should be made?

EOF

rm -f "$CORE_FILES_TMP"

echo "Generated:"
echo "  $REPO_ROOT/$OUT_FILE"