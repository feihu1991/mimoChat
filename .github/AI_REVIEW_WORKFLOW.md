# AI Review Workflow

## 1. Purpose

This file defines the Issue-driven collaboration workflow for:

- the user;
- ChatGPT;
- MiMo Claw;
- Codex GitHub Review.

The user should not copy task prompts between ChatGPT and MiMo Claw. GitHub Issues are the task queue.

## 2. Practical limitation

ChatGPT can perform GitHub operations through the connected GitHub plugin, but a chat session is not automatically awakened by GitHub events.

Therefore:

- ChatGPT creates and maintains task Issues;
- MiMo Claw automatically scans and executes ready Issues;
- MiMo Claw reports results in the Issue;
- the user may need to tell ChatGPT to inspect completed tasks, unless a separate ChatGPT scheduled monitor is configured.

No prompt copying is required.

## 3. Issue states

The machine-readable `status` in the Issue body uses:

```text
READY
IN_PROGRESS
PUSHED
WAITING_FOR_CI
CI_FAILED
WAITING_FOR_CODEX
CHANGES_REQUIRED
APPROVED
BLOCKED
DONE
```

The canonical source of task status is:

1. the latest valid structured Issue comment;
2. then the body status if no structured comment exists.

## 4. Task Issue format

Title:

```text
[MIMO_CLAW_TASK] <task title>
```

Required body header:

```text
<!-- MIMO_CLAW_TASK_V1 -->
task_id: <unique id>
status: READY
type: <FEATURE|BUGFIX|CI_FIX|CODEX_FIX|TEST_FIX>
repository: feihu1991/mimoChat
base_branch: master
work_branch: claw/<branch>
pr_number: <number or NONE>
base_sha: <sha or LATEST_MASTER>
review_round: <0-3>
ci_fix_round: <0-3>
created_by: ChatGPT
```

The rest of the Issue body must include:

- goal;
- current problem and evidence;
- exact allowed change scope;
- implementation requirements;
- tests;
- prohibited actions;
- expected result format;
- manual acceptance steps where relevant.

## 5. ChatGPT responsibilities

When the user requests a change, ChatGPT:

1. reads the current repository and open Issues/PRs;
2. checks for an existing task for the same problem;
3. avoids duplicate Issues;
4. creates or updates one structured task Issue;
5. chooses `work_branch`;
6. records `base_sha`;
7. tells the user the Issue number, but does not require prompt copying.

When MiMo Claw reports `PUSHED`, ChatGPT:

1. verifies the Issue and task ID;
2. verifies the branch and head SHA;
3. creates or updates the same PR;
4. updates the PR description with the task Issue;
5. checks CI according to `.github/MIMO_CI_MONITOR.md`;
6. posts CI results back to the task Issue.

When CI succeeds, ChatGPT:

1. posts `@codex review` on the PR;
2. records `WAITING_FOR_CODEX` in the task Issue;
3. later reads the latest Codex review when invoked or by a configured scheduled monitor.

When Codex requests changes, ChatGPT:

1. deduplicates all P0/P1 and requested changes;
2. updates the same Issue if the change is still the same task, or creates a linked follow-up Issue for an independent fix;
3. sets type `CODEX_FIX`;
4. keeps the same PR and branch unless isolation is required;
5. increments `review_round`;
6. sets status `READY`, allowing MiMo Claw to scan it again.

## 6. MiMo Claw scanning

MiMo Claw cron scans open Issues using GitHub REST API.

It must fetch open Issues and filter:

- title starts with `[MIMO_CLAW_TASK]`;
- body contains `<!-- MIMO_CLAW_TASK_V1 -->`;
- the Issue is not a pull request;
- latest structured status is `READY`;
- task ID has not already been completed;
- no active non-expired claim exists.

Suggested REST call:

```bash
curl \
  --fail-with-body \
  --silent \
  --show-error \
  --location \
  --header "Authorization: Bearer ${GITHUB_TOKEN}" \
  --header "Accept: application/vnd.github+json" \
  --header "X-GitHub-Api-Version: 2022-11-28" \
  "https://api.github.com/repos/feihu1991/mimoChat/issues?state=open&per_page=100"
```

Filter with `jq`, excluding entries that contain `.pull_request`.

MiMo Claw must never run `gh`.

## 7. Claim protocol

Before editing, MiMo Claw posts:

```text
[MIMO_CLAW_CLAIM]
task_id: <id>
status: IN_PROGRESS
agent: MiMo Claw
claimed_at: <UTC timestamp>
base_sha: <resolved sha>
```

An active claim expires after 60 minutes unless a later result exists.

MiMo Claw must also use a local persistent lock keyed by repository + Issue number.

If another active claim exists, skip the Issue.

## 8. Code execution

MiMo Claw:

1. rereads all three rule files;
2. resolves `LATEST_MASTER` to the current `origin/master`;
3. verifies `base_sha`;
4. checks out or creates the specified `claw/*` branch;
5. confirms a clean worktree;
6. reads the full relevant call chain;
7. changes only allowed files;
8. adds regression tests;
9. runs available checks;
10. commits and pushes.

Minimum pre-push checks:

```bash
git status
git diff --check
git diff --stat
git branch --show-current
```

Never push to `master`, force-push, create a duplicate PR, or auto-merge.

## 9. Result protocol

After a successful push, MiMo Claw comments on the Issue:

```text
[MIMO_CLAW_RESULT]
task_id: <id>
status: PUSHED
issue: <number>
branch: <branch>
head_sha: <sha>
commit: <message>

summary:
- ...

changed_files:
- ...

tests:
- ...

local_checks:
- ...

requires_chatgpt:
- CREATE_OR_UPDATE_PR
- CHECK_CI
```

If the task cannot be safely completed:

```text
[MIMO_CLAW_RESULT]
task_id: <id>
status: BLOCKED
reason: <specific blocker>
evidence:
- ...
requires_chatgpt:
- HUMAN_DECISION
```

MiMo Claw then stops. It does not wait indefinitely for CI and does not request Codex review.

## 10. Pull request management

ChatGPT creates or updates a PR:

- base: `master`;
- head: the Issue `work_branch`;
- title references the Issue;
- body links `Closes #<issue>` only when closing on merge is appropriate;
- otherwise use `Related to #<issue>`.

The same task must reuse the same PR.

## 11. CI flow

ChatGPT checks the exact current PR head SHA.

- Running: report and wait for a later check.
- Failure: classify and create/update an Issue task of type `CI_FIX`.
- Success with APK artifact: request Codex review.

CI repair does not increase Codex review round.

CI repair is limited to three rounds.

## 12. Codex review flow

ChatGPT reads:

- review submissions;
- PR conversation comments;
- inline review comments;
- unresolved review threads;
- review commit SHA.

Classify:

```text
PASS
CHANGES_REQUIRED
UNCERTAIN
```

### PASS

Requirements:

- no P0/P1;
- no requested changes;
- no unresolved blocking thread;
- current CI still succeeds;
- APK artifact exists.

Then:

- mark the task Issue `APPROVED`;
- post the result to the Issue and PR;
- ask the user to perform device testing;
- never auto-merge.

### CHANGES_REQUIRED

- increment `review_round`;
- if round <= 3, update or create a structured `CODEX_FIX` Issue with `status: READY`;
- keep the same PR and branch where practical;
- MiMo Claw scans it automatically.

### UNCERTAIN

- set Issue status `BLOCKED`;
- state the decision needed;
- stop automatic modification.

## 13. Round limits

Maximum:

- CI fix rounds: 3;
- Codex fix rounds: 3;
- infrastructure retries: 2.

After a limit is reached, set `BLOCKED` and require human review.

## 14. Idempotency

Never duplicate:

- task Issues for the same root problem;
- task ID execution;
- claims;
- PRs;
- CI-fix rounds;
- Codex review triggers for the same SHA;
- result comments;
- processing of old review SHAs.

## 15. User commands

The normal user flow becomes:

```text
Create a task Issue for <change>.
```

Then MiMo Claw automatically scans and executes.

At checkpoints the user can say:

```text
Check the MiMo Claw task queue.
Check completed MiMo Claw Issues and handle their PR/CI.
Read the latest Codex review and continue the Issue workflow.
I tested the APK; here are the results.
Merge PR #N after verifying the latest SHA.
```

The user does not copy implementation prompts.
