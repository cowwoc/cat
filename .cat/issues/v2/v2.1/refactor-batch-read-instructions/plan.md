# Plan

## Goal

Delete `cat:batch-read-agent` skill. The skill is redundant: parallel `Read` calls handle known
paths naturally (Claude parallelizes them by default), and `cat:grep-and-read-agent` handles the
search+read scenario. No skill is needed for plain batch reads.

## Pre-conditions

(none)

## Post-conditions

- [ ] `plugin/skills/batch-read-agent/` directory is deleted
- [ ] `tests/bats/batch-read-invocation.bats` is deleted
- [ ] No remaining references to `cat:batch-read-agent` exist in any file under `plugin/`
- [ ] `.cat/issues/v2/v2.1/refactor-batch-read-instructions/index.json` `status` field is set to `"closed"`

## Files Modified

- `plugin/skills/batch-read-agent/` — deleted
- `tests/bats/batch-read-invocation.bats` — deleted
- `.cat/issues/v2/v2.1/refactor-batch-read-instructions/index.json` — status set to `"closed"`

## Execution Steps

### Step 1 — Search for references to cat:batch-read-agent

```bash
grep -r "batch-read-agent" plugin/ --include="*.md" --include="*.json" -l
```

List all files that reference `cat:batch-read-agent` or `batch-read-agent`.

### Step 2 — Remove references

For each file found in Step 1 (excluding the batch-read-agent skill directory itself), remove or
replace the reference to `cat:batch-read-agent`.

### Step 3 — Delete the skill directory

```bash
rm -rf plugin/skills/batch-read-agent/
```

### Step 4 — Delete the Bats test

```bash
rm tests/bats/batch-read-invocation.bats
```

### Step 5 — Commit deletions

```bash
git add -A
git commit -m "refactor: delete cat:batch-read-agent skill — redundant with parallel Read and grep-and-read-agent"
```

### Step 7 — Remove stale cat:batch-read-agent references from instruction-builder-agent/first-use.md

`plugin/skills/instruction-builder-agent/first-use.md` contains negative-example clauses that mention `cat:batch-read-agent`
by name (lines 151, 527, 650, 959, 1191). Because the skill no longer exists, these references are stale and must be
removed so that `grep -r "batch-read-agent" plugin/` returns no results.

For each occurrence, remove `cat:batch-read-agent` from the parenthesised "do not invoke" example list. For example,
change:

```
Do NOT invoke any skill (e.g., cat:grep-and-read-agent, cat:batch-read-agent, or any other cat: skill)
```

to:

```
Do NOT invoke any skill (e.g., cat:grep-and-read-agent, or any other cat: skill)
```

After editing, verify no references remain:

```bash
grep -r "batch-read-agent" plugin/
```

Commit the change:

```bash
git add plugin/skills/instruction-builder-agent/first-use.md
git commit -m "refactor: remove stale cat:batch-read-agent references from instruction-builder-agent"
```

### Step 6 — Close the issue

Update `.cat/issues/v2/v2.1/refactor-batch-read-instructions/index.json` to set `status` to `"closed"`:

```json
{
  "status" : "closed",
  "target_branch" : "v2.1"
}
```

```bash
git add .cat/issues/v2/v2.1/refactor-batch-read-instructions/index.json
git commit -m "planning: close refactor-batch-read-instructions"
```

## Success Criteria

- `plugin/skills/batch-read-agent/` does not exist
- `grep -r "batch-read-agent" plugin/` returns no results
- `tests/bats/batch-read-invocation.bats` does not exist
- `index.json` status is `"closed"`
