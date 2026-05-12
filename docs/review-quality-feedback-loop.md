# Review Quality Feedback Loop

This document captures the long-term design direction for turning reviewm from a prompt packager into a quality-improving review system.

The core idea is that the tool's durable value should not only come from generating a diff prompt. It should come from a feedback loop where every review session helps improve prompt design, context selection, and review strategy.

## Core Loop

```text
Generate prompt
-> User sends prompt to Copilot or another LLM
-> User marks which findings are useful, noisy, wrong, or missing
-> Tool stores structured feedback
-> Feedback becomes a review case dataset
-> Offline evaluation compares prompt and context strategies
-> Better prompt profiles are published
-> Future review quality improves
```

## Quality Metrics

Avoid asking only whether an AI review is "good". Break quality into measurable signals:

```text
useful_findings_count       useful findings found
false_positive_count        incorrect or irrelevant findings
missed_issue_count          issues humans found but AI missed
actionability_score         whether suggestions are concrete enough to act on
severity_accuracy           whether severity is reasonable
context_sufficiency         whether provided context was enough
noise_score                 style, formatting, or naming noise
time_saved                  whether the tool reduced context preparation time
```

The most important ratio is:

```text
accepted_findings / total_findings
```

If the model produces 10 findings and only 1 is useful, the prompt or context strategy needs work.

## Feedback Capture

Start with local feedback files before building a platform.

Example run storage:

```text
.reviewm/runs/
  2026-05-12-branch-xyz/
    prompt.md
    metadata.json
    diff.patch
    context.json
    feedback.json
```

Useful feedback fields:

```text
finding_id
severity
category
accepted: true | false
reason:
  - real_bug
  - useful_test_gap
  - false_positive
  - already_handled
  - style_noise
  - insufficient_context
  - wrong_severity
```

For sensitive company code, default to local-only storage. Team-level collection should be opt-in and should consider redaction.

## Version Everything

Every generated prompt should record the versions and strategies used:

```text
promptTemplate: default-review
promptVersion: 2026.05.12
contextStrategy: changed-method-v1
baseBranch: origin/main
modelTarget: copilot
maxContextChars: 16000
```

Without this metadata, it is impossible to know whether quality changed because of the prompt, the context strategy, the model, or the diff itself.

Treat prompts like code: version them, evaluate them, and allow rollback.

## Golden Review Dataset

Over time, collect real review cases:

```text
case-001: null handling bug
case-002: transaction boundary bug
case-003: DTO/entity mismatch
case-004: security auth bypass
case-005: false positive caused by insufficient context
```

Each case should include:

```text
diff
context
expected_findings
known_false_positives
useful_tests
```

Every prompt or context strategy change should be evaluated against this dataset. Without a dataset, prompt optimization is mostly intuition.

## Offline Evaluation Architecture

A future evaluator can be structured as:

```text
ReviewCaseRepository
-> PromptStrategy
-> ContextStrategy
-> LlmClient
-> ReviewOutputParser
-> FindingMatcher
-> QualityScorer
-> EvaluationReport
```

Example report:

```text
prompt default-review-v3
cases: 42
accepted finding recall: 68%
false positive rate: 22%
critical/high miss rate: 8%
style noise rate: 5%
context insufficient rate: 17%
```

This gives a factual basis for saying:

```text
v4 is better than v3
changed-method-plus-callers is better than diff-only
security-review is more effective for controller changes
```

## Prompt Profiles

Do not expect one prompt to review every change well.

Potential prompt profiles:

```text
default-review
security-review
spring-review
data-layer-review
api-compatibility-review
test-gap-review
architecture-review
```

The tool can eventually recommend profiles based on changed files:

```text
pom.xml changed              -> dependency/security focus
Controller changed           -> security/api focus
Repository/Mapper changed    -> data-layer focus
Entity/DTO changed           -> consistency/migration focus
public interface changed     -> compatibility focus
test files missing           -> test-gap focus
```

The long-term value shifts from "generate a prompt" to "choose the right review strategy".

## Context Strategy Evaluation

Context should be evaluated, not blindly maximized.

Possible strategies:

```text
diff-only
changed-method
changed-class
changed-method-plus-callers
changed-method-plus-tests
spring-bean-context
api-contract-context
```

Evaluate:

```text
Which strategy improves useful findings?
Which strategy only adds tokens without improving quality?
Which strategy distracts the model?
Which strategy reduces INSUFFICIENT_CONTEXT findings?
```

Context strategy is one of the main differentiators of the tool.

## Human-in-the-Loop Workflow

Possible workflow:

```text
reviewm
-> copy prompt
-> user gets AI output
-> reviewm feedback paste/output.md
-> user marks accepted/rejected
-> reviewm eval update
```

Example future feedback command:

```bash
reviewm feedback --accepted 1,3 --rejected 2,4 --reason false-positive
```

User feedback is the core input for the quality loop.

## Team Knowledge Base

The system can eventually build a project-specific review memory.

Common real issues:

```text
transaction missing on service method
controller missing auth check
DTO field added but mapper not updated
Optional used as a field
logging sensitive token
```

Common false positives:

```text
generated code
framework magic
Lombok-generated methods
test fixtures
intentionally package-private API
```

These can feed back into the prompt as project conventions:

```text
Ignore generated files under ...
Lombok is used; do not flag missing getters.
MyBatis mapper XML must be checked with mapper interface.
Controllers require @PreAuthorize unless endpoint is public.
```

This is how reviewm can become a team-specific review assistant instead of a generic prompt wrapper.

## Suggested Evolution

1. Local feedback
   Save prompt, diff, context, AI output, and user feedback.

2. Golden cases
   Convert useful real review sessions into regression cases.

3. Prompt profiles
   Introduce specialized review prompts for security, data layer, API compatibility, architecture, and tests.

4. Context strategy evaluation
   Compare diff-only, changed-method, caller, test, dependency, and framework-aware context strategies.

5. Team knowledge base
   Capture project rules, false positive patterns, architecture boundaries, and framework conventions.

## Summary

```text
Prompt generation is the product surface.
Feedback dataset is the asset.
Evaluation loop is the engine.
Context strategy is the differentiator.
```

If reviewm can continuously collect which AI findings humans actually accept, it can evolve from a prompt packager into a code review assistant with team memory.
