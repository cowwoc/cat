<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Batch Write Skill

## Purpose

Issue all independent Write/Edit tool calls in a single LLM response when 2+ files need to be written or edited,
reducing write round-trips from N to 1.

**Independence criterion**: Two writes are independent if neither file's content depends on the write result of the
other. When claiming writes are dependent, state which file depends on which other file's write result and why.
Multiple operations on the same file (e.g., Write then Edit) are dependent and count as one file.

**Scope**: Controls write batching only. Does not override existing write restrictions (hooks, worktree isolation,
file-type rules). Every file in the batch must be a legitimate write target under the current workflow's rules.

---

## Procedure

### Step 1: Count pending independent writes

Identify all distinct file paths to be written or edited. Count each path once, regardless of how many operations
target it. Count only mutually independent writes.

### Step 2: Check batch threshold

- If count < 2: proceed with normal Write/Edit calls. Do NOT use this skill.
- If count >= 2: continue to Step 3. Include all independent writes in a single batch; do not split across responses.

### Step 3: Collect all write targets

Before issuing any Write or Edit call, determine the complete set of file paths and their full content. Read each
file to be edited (not newly created) using the Read tool before this step. All content must be complete and final
before the first tool call — no placeholders, TODO comments, or empty stubs.

### Step 4: Issue all Write/Edit calls in a single response

In a **single LLM response**, issue every Write or Edit tool call for the batch. All calls in the same response
execute concurrently. Never batch two operations targeting the same file path in one response (e.g., Write to create
then Edit to modify); sequential operations on the same file must be issued in separate responses.

### Step 5: Review results and retry failures

After the batch returns, review per-call results. Retry only failed calls, up to 2 times each. Do not re-issue
successful writes. If a call still fails after 2 retries, stop and report the error to the user.

### Step 6: Verify

Confirm every item in the Verification checklist below before proceeding with other work.

---

## Verification

- [ ] All 2+ independent writes were issued within a single LLM response.
- [ ] No write call was issued in a separate response that could have been batched.
- [ ] Failed writes were retried individually without re-issuing successful ones.
