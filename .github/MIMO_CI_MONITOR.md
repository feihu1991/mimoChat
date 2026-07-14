# MIMO CI Monitor

## 1. Purpose

This file defines CI acceptance and failure handling for the Issue-driven workflow.

- MiMo Claw edits code and pushes.
- ChatGPT reads GitHub Actions through the GitHub plugin.
- CI results are written back to the corresponding task Issue.
- MiMo Claw never uses `gh` and does not wait indefinitely for CI.

## 2. Trigger

A task enters CI handling after the Issue contains:

```text
[MIMO_CLAW_RESULT]
status: PUSHED
head_sha: <sha>
requires_chatgpt:
- CHECK_CI
```

ChatGPT verifies the task ID, branch, PR, and exact head SHA.

## 3. Required CI checks

CI passes only when all are true:

1. PR head SHA equals workflow head SHA;
2. workflow status is completed;
3. workflow conclusion is success;
4. Android debug compilation succeeds;
5. JVM unit tests succeed;
6. Android Lint succeeds;
7. an APK artifact is uploaded;
8. the artifact is not expired and belongs to the same run;
9. no other blocking job failed, timed out, or was cancelled.

Equivalent job names are acceptable, but the actual work must be performed.

## 4. Running or missing workflow

If no run exists yet:

- verify the push and current PR head;
- verify workflow triggers and YAML;
- report `WORKFLOW_NOT_TRIGGERED` only after a reasonable delay;
- never push an empty commit merely to trigger CI.

If a run is queued or in progress:

- report the current state in the Issue;
- do not claim success;
- do not request Codex review;
- check again when ChatGPT is invoked or by a configured scheduled monitor.

## 5. Artifact rules

An artifact counts as APK only if its name and run indicate an Android APK, such as:

```text
apk
debug-apk
app-debug
android-debug
mimoChat-debug
```

Test reports, Lint reports, logs, or coverage files do not satisfy the APK requirement.

## 6. Success action

When CI succeeds, ChatGPT posts to the task Issue:

```text
[CHATGPT_CI_RESULT]
task_id: <id>
status: PASSED
pr: <number>
head_sha: <sha>
workflow_run: <id>
assembleDebug: PASSED
testDebugUnitTest: PASSED
lintDebug: PASSED
apk_artifact: PRESENT
next: CODEX_REVIEW
```

Then ChatGPT comments on the PR:

```text
Current GitHub CI passed for commit <HEAD_SHA>.

- assembleDebug: passed
- testDebugUnitTest: passed
- lintDebug: passed
- APK artifact: generated

@codex review

Please review the latest commit and focus on real P0/P1 defects, concurrency, state transitions, Room consistency, lifecycle, security, and regression tests.
```

Do not request Codex review more than once for the same head SHA.

## 7. Failure extraction

ChatGPT must identify:

- workflow;
- failed job;
- failed step;
- first meaningful error;
- file and line when available;
- probable root cause;
- whether it is caused by current code or infrastructure.

Do not paste complete logs into the Issue.

## 8. Failure classes

```text
CODE_FAILURE
TEST_FAILURE
LINT_FAILURE
WORKFLOW_FAILURE
WORKFLOW_NOT_TRIGGERED
INFRASTRUCTURE_FAILURE
CANCELLED_OR_SUPERSEDED
UNCERTAIN
```

### CODE_FAILURE

Examples: Kotlin/Java compile error, missing import, type mismatch, Android resources, Room/KSP/Gradle code configuration.

Create or update a `CI_FIX` task Issue.

### TEST_FAILURE

Prefer fixing production code. Never delete, skip, ignore, or weaken tests.

Create or update a `CI_FIX` task Issue.

### LINT_FAILURE

Fix the real problem. Never globally disable Lint or add broad suppressions.

Create or update a `CI_FIX` task Issue.

### WORKFLOW_FAILURE

Fix YAML, working directory, JDK/Android setup, command, or artifact path without removing real checks.

Create or update a `CI_FIX` task Issue.

### WORKFLOW_NOT_TRIGGERED

Inspect event, branch, and path filters. Do not use empty commits.

### INFRASTRUCTURE_FAILURE

ChatGPT may rerun failed jobs. Maximum two infrastructure retries. Do not ask MiMo Claw to change business code.

### CANCELLED_OR_SUPERSEDED

Ignore the old run and inspect the newest PR head SHA.

### UNCERTAIN

Set the task Issue to `BLOCKED` and request a human decision.

## 9. CI repair Issue

ChatGPT creates or updates a structured task:

```text
<!-- MIMO_CLAW_TASK_V1 -->
task_id: <original-id>-ci-<round>
status: READY
type: CI_FIX
repository: feihu1991/mimoChat
base_branch: master
work_branch: <same claw branch>
pr_number: <same PR>
base_sha: <current head SHA>
review_round: <unchanged>
ci_fix_round: <1-3>
created_by: ChatGPT
```

The body must include the exact failed job, step, first meaningful error, affected file, root-cause assessment, required change, required test, and prohibitions.

## 10. Limits

- CI repair rounds: maximum 3.
- Infrastructure retries: maximum 2.

After the limit, post:

```text
[CHATGPT_CI_RESULT]
status: BLOCKED
reason: automatic repair limit reached
```

Do not generate further automatic fixes.

## 11. MiMo Claw local checks

At minimum:

```bash
git status
git diff --check
git diff --stat
git branch --show-current
```

If feasible:

```bash
cd android
chmod +x gradlew
./gradlew testDebugUnitTest
```

GitHub Actions remains the final source of truth.

## 12. Prohibited actions

- no `gh` in MiMo Claw;
- no direct push to `master`;
- no force-push;
- no auto-merge;
- no duplicate PR;
- no empty commit for CI;
- no skipped tests or Lint;
- no `continue-on-error` for required checks;
- no use of old SHA results;
- no success claim while CI is running;
- no secret disclosure;
- no execution of untrusted artifacts.

## 13. Report format

ChatGPT reports to the user and Issue:

```text
Issue:
PR:
branch:
head SHA:
workflow run:
CI state:
assembleDebug:
testDebugUnitTest:
lintDebug:
APK artifact:
failure class:
failed job:
failed step:
next task Issue:
next action:
```
