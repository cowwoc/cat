<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Validation-Driven Document Compression

## Invocation Restriction

**MAIN AGENT ONLY**: This skill spawns subagents internally. It CANNOT be invoked by
a subagent (subagents cannot spawn nested subagents or invoke skills).

If you need this skill's functionality within delegated work:
1. Main agent invokes this skill directly
2. Pass results to the implementation subagent
3. See: plugin/skills/delegate/SKILL.md § "Model Selection for Subagents"

---

**Issue**: Compress the documentation file: `{{arg}}`

**Goal**: Reduce document size while preserving execution equivalence using
objective validation instead of prescriptive rules.

---

## CRITICAL: Always Use This Skill

**NEVER manually compress files and validate with /compare-docs directly.**

Manual compression bypasses:
1. The **iteration loop** that automatically retries when status = NOT_EQUIVALENT
2. The **EQUIVALENT requirement** enforced for optimize-doc
3. The **structured feedback** (LOST units list) that guides compression improvements

If you compress manually and get NOT_EQUIVALENT, you must manually iterate.
If you use this skill, iteration happens automatically until status = EQUIVALENT.

**MANDATORY: Report validation status**

When compressing files (even partially), you MUST report:
- Validation status per file (EQUIVALENT or NOT_EQUIVALENT from /compare-docs)
- Token counts per file (before and after)

If you compressed files without reporting these metrics, you violated this requirement.
Go back and run /compare-docs validation for each file, then report results.

---

## Workflow

### Step 1: Validate Document Type

**BEFORE compression**, verify this is a Claude-facing document:

**ALLOWED** (Claude-facing):
- `.claude/` configuration files:
  - `.claude/agents/` - Agent definitions (prompts for sub-agents)
  - `.claude/commands/` - **Slash commands** (prompts that expand when invoked)
  - `.claude/hooks/` - Hook scripts (execute on events)
  - `.claude/settings.json` - Claude Code settings
- `CLAUDE.md` and project instructions
- `docs/project/` development protocol documentation
- `docs/code-style/*-claude.md` style detection patterns

**Why slash commands are Claude-facing**: When you invoke `/optimize-doc`, the
contents of `.claude/commands/optimize-doc.md` expand into a prompt for Claude
to execute. The file is NOT for users to read - it's a configuration that
defines what Claude does when the command is invoked.

**FORBIDDEN** (Human-facing):
- `README.md`, `changelog.md`, `CHANGELOG.md`
- `docs/studies/`, `docs/decisions/`, `docs/performance/`
- `docs/optional-modules/` (potentially user-facing)
- `todo.md`, `docs/code-style/*-human.md`

**⚠️ SPECIAL HANDLING: CLAUDE.md**

When compressing `CLAUDE.md`, the compression agent uses **content reorganization** instead of
standard compression. The detailed reorganization algorithm is in COMPRESSION-AGENT.md.

**Validation after compression:**
```bash
# CLAUDE.md should be ~200-250 lines (not 800+)
wc -l CLAUDE.md

# No procedural duplication with skills
grep -c "Step 1:" CLAUDE.md  # Should be minimal
```

---

**⚠️ SPECIAL HANDLING: Style Documentation Files**

When compressing `.claude/rules/*.md` or `docs/code-style/*-claude.md`, the compression agent
follows special rules defined in COMPRESSION-AGENT.md. The orchestrator does NOT need to know
what to preserve/remove - just invoke the subagent and validate the result.

**Verification Required (orchestrator runs AFTER compression)**: Count section headers:
```bash
ORIGINAL_SECTIONS=$(grep -c "^### " /tmp/original-{filename})
COMPRESSED_SECTIONS=$(grep -c "^### " /tmp/compressed-{filename}-v${VERSION}.md)
if [ "$COMPRESSED_SECTIONS" -lt "$ORIGINAL_SECTIONS" ]; then
  echo "❌ ERROR: Section(s) removed! Original: $ORIGINAL_SECTIONS, Compressed: $COMPRESSED_SECTIONS"
  echo "   Style rule sections must be preserved. Iterate to restore missing sections."
fi
```

**Why This Protection Exists**: Session from 2025-12-19 had documentation update remove
intentionally-added "Use 'empty' Not 'blank'" style rule section, causing repeated data loss
during subsequent rebases.

**If forbidden**, respond:
```
This compression process only applies to Claude-facing documentation.
The file `{{arg}}` appears to be human-facing documentation.
```

**Examples**:
- ✅ ALLOWED: `.claude/commands/optimize-doc.md` (slash command prompt)
- ✅ ALLOWED: `.claude/agents/architect.md` (agent prompt)
- ❌ FORBIDDEN: `README.md` (user-facing project description)
- ❌ FORBIDDEN: `changelog.md` (user-facing change history)

---

### Step 2: Check for Existing Baseline

**Check baseline and git history in parallel**:

```bash
BASELINE="/tmp/original-{{filename}}"
if [ -f "$BASELINE" ]; then
  BASELINE_LINES=$(wc -l < "$BASELINE")
  CURRENT_LINES=$(wc -l < "{{arg}}")
  echo "✅ Found existing baseline: $BASELINE ($BASELINE_LINES lines)"
  echo "   Current file: $CURRENT_LINES lines"
  echo "   Scores will compare against original baseline."
else
  RECENT_SHRINK=$(git log --oneline -5 -- {{arg}} 2>/dev/null | grep -iE "compress|shrink|reduction" | head -1)
  if [ -n "$RECENT_SHRINK" ]; then
    echo "ℹ️ Note: File was previously compressed (commit: $RECENT_SHRINK)"
    echo "   No baseline preserved. Starting fresh with current version as baseline."
  fi
fi
```

---

### Step 3: Invoke Compression Agent

**⚠️ ENCAPSULATION**: The compression algorithm is in a separate internal document.
Do NOT attempt to compress manually - invoke the subagent which will read its own instructions.

**Subagent invocation** (use Task tool, not TaskCreate - see M372 in subagent-delegation.md):

```
Task tool:
  subagent_type: "general-purpose"
  description: "Compress {{arg}}"
  prompt: |
    Read the instructions at: plugin/skills/optimize-doc/COMPRESSION-AGENT.md

    Then compress the following file according to those instructions:
    - FILE_PATH: {{arg}}
    - OUTPUT_PATH: /tmp/compressed-{{filename}}-v${VERSION}.md

    Use the Write tool to save the compressed version.
```

**Why separate documents**: The compression algorithm is intentionally NOT in this file.
If you can see HOW to compress, you might bypass the skill and do it manually - which skips
validation. The subagent reads COMPRESSION-AGENT.md; you (the orchestrator) only invoke and validate.

---

### Step 4: Validate with /compare-docs

**⚠️ CRITICAL**: Before saving compressed version, read and save the ORIGINAL
document state to use as baseline for validation.

After agent completes:

1. **Save original document** (ONLY if baseline doesn't exist):
   ```bash
   BASELINE="/tmp/original-{{filename}}"
   if [ ! -f "$BASELINE" ]; then
     cp {{arg}} "$BASELINE"
     echo "✅ Saved baseline: $BASELINE ($(wc -l < "$BASELINE") lines)"
   else
     echo "✅ Reusing existing baseline: $BASELINE"
   fi
   ```

   **Why baseline is preserved**: Baseline is kept until user explicitly confirms
   they're done iterating (see Step 5). This ensures scores always compare against
   the TRUE original, not intermediate compressed versions.

2. **Determine version number and save compressed version**:

   First, discover existing version files using the Glob tool:
   ```
   Glob tool:
     pattern: "/tmp/compressed-{{filename}}-v*.md"
   ```

   Then determine the next version number. If Glob returned files, extract the highest
   version number from the paths (e.g., `/tmp/compressed-foo-v3.md` → 3). If no files
   found, start at version 1.

   ```bash
   VERSION_FILE="/tmp/optimize-doc-{{filename}}-version.txt"

   # Get next version number from persistent counter (survives across sessions)
   if [ -f "$VERSION_FILE" ]; then
     LAST_VERSION=$(cat "$VERSION_FILE")
     VERSION=$((LAST_VERSION + 1))
   else
     # First time: use highest version from Glob results above
     # Replace <HIGHEST> with the number extracted from Glob output, or leave empty if no files found
     HIGHEST=<HIGHEST>
     if [ -n "$HIGHEST" ]; then
       VERSION=$((HIGHEST + 1))
     else
       VERSION=1
     fi
   fi

   # Save version counter for next iteration
   echo "$VERSION" > "$VERSION_FILE"

   # Save with version number for rollback capability
   # Agent output → /tmp/compressed-{{filename}}-v${VERSION}.md
   echo "📝 Saved as version ${VERSION}: /tmp/compressed-{{filename}}-v${VERSION}.md"
   ```

   **Why persistent versioning**: Version numbers continue across sessions (v1, v2 in session 1 →
   v3, v4 in session 2) so older revisions are never overwritten. This enables rollback to any
   previous version and maintains complete compression history.

3. **Verify YAML frontmatter preserved** (if compressing slash command):
   ```bash
   head -5 /tmp/compressed-{{filename}}-v${VERSION}.md | grep -q "^---$" || \
     echo "⚠️ WARNING: YAML frontmatter missing!"
   ```

4. **Run validation via /cat:delegate-agent**:

   Use `/cat:delegate-agent` for ALL validation (single or multiple files). The compare-docs skill
   handles extraction, comparison, and report generation.

   ```bash
   /cat:delegate-agent --skill compare-docs-agent /tmp/original-{{filename}} /tmp/compressed-{{filename}}-v${VERSION}.md
   ```

   **Why delegate**: Delegate handles:
   - Subagent spawning with appropriate model selection (opus for validation)
   - Isolation of validation context from main agent
   - Result collection and formatting

   **The compare-docs skill handles internally**:
   - Parallel extraction from both documents (3-agent model)
   - Binary equivalence determination (EQUIVALENT or NOT_EQUIVALENT)

   **Parse validation result from delegate output**:
   - Extract `Status:` line (EQUIVALENT or NOT_EQUIVALENT with counts)
   - Extract LOST section for iteration feedback
   - Extract ADDED section (informational only)

   **If Status = NOT_EQUIVALENT**: Proceed to Step 6 (Iteration). After creating new version,
   re-run validation on the NEW compressed version.

5. **Parse validation result**:
   - Find the `Status:` line in the COMPARISON RESULT
   - Extract the LOST section (needed for iteration feedback)

**Validation Context**: When reporting to the user, explicitly state:
```
Status: {EQUIVALENT|NOT_EQUIVALENT} compares the compressed version against
the ORIGINAL document state from before /optimize-doc was invoked.
```

**⚠️ CRITICAL REMINDER**: On second, third, etc. invocations:
- ✅ **REUSE** `/tmp/original-{{filename}}` from first invocation
- ✅ Always compare against original baseline (not intermediate versions)
- The baseline is set ONCE on first invocation and REUSED for all subsequent invocations

---

### Step 5: Decision Logic

**Threshold**: EQUIVALENT status required (no "close enough" - see M254)

**COMMIT GATE**: Files may ONLY be committed after validation passes:
- Status = NOT_EQUIVALENT → File MUST NOT be applied or committed
- Status = EQUIVALENT → File may be applied and committed
- Skipped validation → File MUST NOT be applied or committed

Rationalizations like "extraction variance" or "validation is broken" are completion bias.
If validation consistently fails, the compression is too aggressive - iterate or abandon.

**Report Format** (for approval):
1. Validation output from /compare-docs (copy verbatim)
2. **Version Comparison Table** (showing all versions generated in this session)

**⚠️ CRITICAL**: Report the ACTUAL score from /compare-docs. Do not summarize or interpret.

**Version Comparison Table Format**:

After presenting validation results for ANY version, show comparison table.

**Token Counting**: Use JTokkit for accurate token counts:

```bash
# Actual token count using JTokkit (Java tokenizer)
TOKENS=$("${CLAUDE_PLUGIN_ROOT}/hooks/java.sh" TokenCounter "$FILE" | jq -r ".\"$FILE\"")
```

**Table format:**

| Version      | Tokens | Reduction | Preserved | Status     |
|--------------|--------|-----------|-----------|------------|
| **Original** | {n}    | baseline  | N/A       | Reference  |
| **V{n}**     | {n}    | {n}%      | {X}/{Y}   | {status}   |

**Status values**:
- EQUIVALENT = All units preserved, approved for commit
- NOT_EQUIVALENT = Units lost, requires iteration
- Applied = Currently applied to original file

---

**If status = EQUIVALENT**: ✅ **APPROVE**
```
Validation passed! Status: EQUIVALENT ({X}/{Y} preserved)

✅ Approved version: /tmp/compressed-{{filename}}-v${VERSION}.md

Writing compressed version to {{arg}}...
```
→ Overwrite original with approved version
→ Clean up versioned compressions: `rm /tmp/compressed-{{filename}}-v*.md`
→ **KEEP baseline**: `/tmp/original-{{filename}}` preserved for potential future iterations

**After applying changes, ASK user**:
```
Changes applied successfully!

Would you like to try again to generate an even better version?
- YES → I'll keep the baseline and iterate with new compression targets
- NO → I'll clean up the baseline (compression complete)
```

**If user says YES** (wants to try again):
→ Keep `/tmp/original-{{filename}}`
→ Future /optimize-doc invocations will reuse this baseline
→ Scores will reflect cumulative compression from true original
→ Go back to Step 3 with user's feedback

**If user says NO** (done iterating):
→ `rm /tmp/original-{{filename}}`
→ `rm /tmp/optimize-doc-{{filename}}-version.txt`
→ Note: Future /optimize-doc on this file will use compressed version as new baseline

**If status = NOT_EQUIVALENT**: ❌ **ITERATE**
```
Validation requires improvement. Status: NOT_EQUIVALENT ({X}/{Y} preserved, {Z} lost)

{Copy /compare-docs LOST section verbatim - shows what units need to be restored}

Re-invoking agent with feedback to fix issues...
```
→ Go to Step 6 (Iteration)

**⚠️ MANDATORY: Validation Gate**

**BLOCKING REQUIREMENT**: Complete this validation BEFORE making any approval decision.

**Parse the COMPARISON RESULT from /compare-docs:**

The report contains this key line (exact format):
```
Status: EQUIVALENT | NOT_EQUIVALENT (X/Y preserved, Z lost)
```

**Extract the status:**
- Find the line starting with `Status:`
- Check if it says `EQUIVALENT` or `NOT_EQUIVALENT`

**Decision logic:**
```
if Status contains "EQUIVALENT" and NOT "NOT_EQUIVALENT":
  DECISION = "APPROVE"
else:
  DECISION = "ITERATE"
```

**Iteration required**: If DECISION=ITERATE:
1. **STOP** - do not ask user for approval
2. **Extract** the LOST section from the report (lists units that need restoration)
3. **Proceed** directly to Step 6 (Iteration Loop) with that feedback

**Why this gate exists**: Completion bias causes agents to rationalize "close enough". Only EQUIVALENT status
permits approval. No exceptions.

---

### Step 6: Iteration Loop

**If status = NOT_EQUIVALENT**, invoke agent again with specific feedback:

**Iteration Prompt Template**:
```
**Document Compression - Revision Attempt {iteration_number}**

**Previous Status**: NOT_EQUIVALENT ({X}/{Y} preserved, {Z} lost)

**Lost Semantic Units** (MUST be restored):

{for each lost unit from LOST section:}
- [{CATEGORY}] "{original text of lost unit}"

**Your Task**:

Revise the compressed document to restore the lost semantic units while maintaining compression.

**Original**: /tmp/original-{{filename}}
**Previous Attempt**: /tmp/compressed-{{filename}}-v${VERSION}.md

Focus on:
1. Restoring the exact semantic meaning of each lost unit
2. Maintaining conditional structure (IF-THEN-ELSE)
3. Preserving mutual exclusivity constraints (EXCLUSION category)
4. Keeping temporal ordering (SEQUENCE category)
5. Keeping prohibition constraints (PROHIBITION category)

**⚠️ CRITICAL**: USE THE WRITE TOOL to save the revised document to the specified path.
Do NOT just describe or return the content - you MUST physically write the file.
```

**After iteration**:
- Save revised version as next version number (v${VERSION+1})
- Re-run /compare-docs validation **AGAINST ORIGINAL BASELINE**
- Apply decision logic again (Step 5)

**🚨 MANDATORY: /compare-docs Required for EVERY Iteration**

**CRITICAL**: You MUST invoke `/compare-docs` for EVERY version validation.
No exceptions. Status is ONLY valid if it comes from /compare-docs output.

```bash
/compare-docs /tmp/original-{filename} /tmp/compressed-{filename}-v{N}.md
```

**Self-Check Before Reporting Status**:
1. Did I invoke /compare-docs for this version? YES/NO
2. Is the status from /compare-docs output? YES/NO
3. If either is NO → STOP and invoke /compare-docs

**Maximum iterations**: 3
- If still NOT_EQUIVALENT after 3 attempts, report to user and ask for guidance
- All versions preserved in /tmp for rollback
- User may choose to accept best attempt or abandon compression

---

## Multiple Files: Use /cat:delegate-agent

**For compressing multiple files**, use `/cat:delegate-agent`:

```bash
/cat:delegate-agent --skill optimize-doc-agent file1.md file2.md file3.md
```

Delegate handles:
- Wave-based parallel execution (all files in a single wave since they have no dependencies)
- Parallel subagent spawning (one per file, each running this full workflow)
- Fault-tolerant result collection
- Automatic retry of failed subagents
- Result aggregation into per-file table

**Do NOT manually spawn Task tools for batch operations** - delegate already implements
parallel execution, fault tolerance, and retry logic.

**Per-file validation:** Each file MUST be validated individually. Report results:

| File | Tokens Before | Tokens After | Reduction | Preserved | Status |
|------|---------------|--------------|-----------|-----------|--------|
| {filename} | {count} | {count} | {%} | {X}/{Y} | {EQUIVALENT/NOT_EQUIVALENT} |

**Validation separation:** Compression subagents must NOT validate their own work.
Each optimize-doc subagent spawns SEPARATE validation subagents per Step 4.

---

## Implementation Notes

**Agent Type**: MUST use `subagent_type: "general-purpose"`

**Validation Tool**: Use `/cat:delegate-agent --skill compare-docs-agent` - delegate handles subagent
spawning and result collection.

**Validation Baseline**: On first invocation, save original document to
`/tmp/original-{filename}` and use this as baseline for ALL subsequent
validation comparisons in the session.

**Versioning Scheme**: Each compression attempt is saved with incrementing
version numbers for rollback capability.

**File Operations**:
- Read original: `Read` tool
- Save original baseline: `Write` tool to `/tmp/original-{filename}` (once per session)
- Save versioned compressed: `Write` tool to `/tmp/compressed-{filename}-v1.md`,
  `/tmp/compressed-{filename}-v2.md`, etc.
- Overwrite original: `Write` tool to `{{arg}}` (only after approval)
- Cleanup after approval: `rm /tmp/compressed-{filename}-v*.md /tmp/original-{filename}`

**Rollback Capability**:
- If latest version unsatisfactory, previous versions available at `/tmp/compressed-{filename}-v{N}.md`
- Example: If v3 approved but later found problematic, can review v1 or v2
- Versions automatically cleaned up after successful approval

**Iteration State**:
- Track iteration count via version numbers
- Provide specific feedback from validation warnings
- ALWAYS validate against original baseline, not previous iteration

---

## Success Criteria

✅ **Compression approved** when:
- /compare-docs returns Status: EQUIVALENT

✅ **Compression quality** metrics:
- Word reduction: ~50% (target, secondary to equivalence)
- All semantic units preserved (X/X in status)
- No units in LOST section

---

## Edge Cases

**Abstraction vs Enumeration**: When compressed document uses high-level
constraint statements (e.g., "handlers are mutually exclusive") instead of
explicit pairwise enumerations, validation may return NOT_EQUIVALENT with
specific EXCLUSION units in the LOST section. System will automatically
iterate to restore explicit constraints.

**Persistent NOT_EQUIVALENT**: If multiple iterations needed but LOST count
doesn't decrease (e.g., v1=3 lost, v2=3 lost, v3=2 lost), compression
may be hitting fundamental limits. After 3 attempts with units still lost,
report best version to user and explain compression challenges encountered.

**Multiple Iterations**: Each iteration should reduce the LOST count. Monitor
progression toward EQUIVALENT status (0 lost).

**Large Documents**: For documents >10KB, consider breaking into logical sections
and compressing separately to improve iteration efficiency.

---

## Example Usage

```
/optimize-doc /workspace/main/.claude/commands/example-command.md
```

Expected flow:
1. Validate document type ✅
2. Save original to /tmp/original-example-command.md (baseline) ✅
3. Invoke compression agent
4. Save to /tmp/compressed-example-command-v1.md (version 1) ✅
5. Run /compare-docs /tmp/original-example-command.md /tmp/compressed-example-command-v1.md
6. Status = EQUIVALENT → Approve v1 and overwrite original ✅
7. Cleanup: Remove /tmp/compressed-example-command-v*.md and /tmp/original-example-command.md ✅

**If iteration needed**:
- v1 NOT_EQUIVALENT (3 lost) → Save v2, validate against original
- v2 NOT_EQUIVALENT (1 lost) → Save v3, validate against original
- v3 EQUIVALENT → Approve v3, cleanup v1/v2/v3 and original
- v3 NOT_EQUIVALENT (after max iterations) → Report to user with best version
