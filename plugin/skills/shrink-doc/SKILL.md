---
description: >
  Use to reduce Claude-facing document size while maintaining execution equivalence.
  Validates with /compare-docs to ensure no semantic loss.
---

# Validation-Driven Document Compression

**Issue**: Compress the documentation file: `{{arg}}`

**Goal**: Reduce document size while preserving execution equivalence using
objective validation instead of prescriptive rules.

---

## CRITICAL: Always Use This Skill (M294, M296)

**NEVER manually compress files and validate with /compare-docs directly.**

Manual compression bypasses:
1. The **iteration loop** that automatically retries when score < 1.0
2. The **1.0 threshold enforcement** (compare-docs defaults to 0.95)
3. The **structured feedback** that guides compression improvements

If you compress manually and get score 0.995, you must manually iterate.
If you use this skill, iteration happens automatically until score = 1.0.

**MANDATORY: Report execution equivalence score (M296)**

When compressing files (even partially), you MUST report:
- Execution equivalence score per file (from /compare-docs)
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

**Why slash commands are Claude-facing**: When you invoke `/shrink-doc`, the
contents of `.claude/commands/shrink-doc.md` expand into a prompt for Claude
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
- ✅ ALLOWED: `.claude/commands/shrink-doc.md` (slash command prompt)
- ✅ ALLOWED: `.claude/agents/architect.md` (agent prompt)
- ❌ FORBIDDEN: `README.md` (user-facing project description)
- ❌ FORBIDDEN: `changelog.md` (user-facing change history)

---

### Step 2: Check for Existing Baseline

**Check if baseline exists from prior iteration**:

```bash
BASELINE="/tmp/original-{{filename}}"
if [ -f "$BASELINE" ]; then
  BASELINE_LINES=$(wc -l < "$BASELINE")
  CURRENT_LINES=$(wc -l < "{{arg}}")
  echo "✅ Found existing baseline: $BASELINE ($BASELINE_LINES lines)"
  echo "   Current file: $CURRENT_LINES lines"
  echo "   Scores will compare against original baseline."
fi
```

**If NO baseline exists**, optionally check git history for prior compression:

```bash
if [ ! -f "$BASELINE" ]; then
  RECENT_SHRINK=$(git log --oneline -5 -- {{arg}} 2>/dev/null | grep -iE "compress|shrink|reduction" | head -1)
  if [ -n "$RECENT_SHRINK" ]; then
    echo "ℹ️ Note: File was previously compressed (commit: $RECENT_SHRINK)"
    echo "   No baseline preserved. Starting fresh with current version as baseline."
  fi
fi
```

---

### Step 3: Invoke Compression Agent

**⚠️ ENCAPSULATION (M269)**: The compression algorithm is in a separate internal document.
Do NOT attempt to compress manually - invoke the subagent which will read its own instructions.

**Subagent invocation**:

```
Task tool:
  subagent_type: "general-purpose"
  description: "Compress {{arg}}"
  prompt: |
    Read the instructions at: plugin/skills/shrink-doc/COMPRESSION-AGENT.md

    Then compress the following file according to those instructions:
    - FILE_PATH: {{arg}}
    - OUTPUT_PATH: /tmp/compressed-{{filename}}-v${VERSION}.md

    Use the Write tool to save the compressed version.
```

**Why separate documents (M269)**: The compression algorithm is intentionally NOT in this file.
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
   ```bash
   VERSION_FILE="/tmp/shrink-doc-{{filename}}-version.txt"

   # Get next version number from persistent counter (survives across sessions)
   if [ -f "$VERSION_FILE" ]; then
     LAST_VERSION=$(cat "$VERSION_FILE")
     VERSION=$((LAST_VERSION + 1))
   else
     # First time: check for existing version files to continue numbering
     HIGHEST=$(ls /tmp/compressed-{{filename}}-v*.md 2>/dev/null | sed 's/.*-v\([0-9]*\)\.md/\1/' | sort -n | tail -1)
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
   head -5 /tmp/compressed-{{filename}}-v${VERSION}.md | grep -q "^---$" || echo "⚠️ WARNING: YAML frontmatter missing!"
   ```

4. **Run validation with Best-2-of-3 (M346)**:

   Due to ±10-35% variance in /compare-docs scores across invocations, use consensus validation:

   **Step 4a: Run two validations in parallel**
   ```
   Task tool #1:
     subagent_type: "general-purpose"
     model: "opus"
     description: "Validate compression (run 1)"
     prompt: |
       Run /compare-docs to validate:
       - Original: /tmp/original-{{filename}}
       - Compressed: /tmp/compressed-{{filename}}-v${VERSION}.md

       MANDATORY: Invoke the Skill tool with skill="cat:compare-docs".
       Include the COMPLETE output including:
       - EXECUTION EQUIVALENCE score
       - Component Scores table
       - Warnings section
       - Summary section

       Do NOT summarize or fabricate - return the actual skill output.

   Task tool #2:
     subagent_type: "general-purpose"
     model: "opus"
     description: "Validate compression (run 2)"
     prompt: |
       Run /compare-docs to validate:
       - Original: /tmp/original-{{filename}}
       - Compressed: /tmp/compressed-{{filename}}-v${VERSION}.md

       MANDATORY: Invoke the Skill tool with skill="cat:compare-docs".
       Include the COMPLETE output including:
       - EXECUTION EQUIVALENCE score
       - Component Scores table
       - Warnings section
       - Summary section

       Do NOT summarize or fabricate - return the actual skill output.
   ```

   **Step 4b: Check for consensus on 1.0**

   Extract `execution_equivalence_score` from both reports.

   **For shrink-doc, only 1.0 is acceptable.** Check if both runs agree on 1.0:

   | Score 1 | Score 2 | Action |
   |---------|---------|--------|
   | 1.0 | 1.0 | **PASS** - both agree on 1.0, approved |
   | 1.0 | 0.95 | **Disagree** - run tiebreaker |
   | 0.95 | 0.93 | **FAIL** - neither is 1.0, iterate |

   **Step 4c: If one is 1.0 and other is not, run tiebreaker**
   ```
   Task tool:
     subagent_type: "general-purpose"
     model: "opus"
     description: "Validate compression (tiebreaker)"
     prompt: |
       Run /compare-docs to validate:
       - Original: /tmp/original-{{filename}}
       - Compressed: /tmp/compressed-{{filename}}-v${VERSION}.md

       MANDATORY: Invoke the Skill tool with skill="cat:compare-docs".
       Include the COMPLETE output including:
       - EXECUTION EQUIVALENCE score
       - Component Scores table
       - Warnings section
       - Summary section

       Do NOT summarize or fabricate - return the actual skill output.
   ```

   **Step 4d: Determine if 2 of 3 agree on 1.0**

   | Score 1 | Score 2 | Score 3 | Result |
   |---------|---------|---------|--------|
   | 1.0 | 0.95 | 1.0 | **PASS** - 2 of 3 = 1.0 |
   | 1.0 | 0.95 | 0.93 | **FAIL** - only 1 of 3 = 1.0, iterate |
   | 0.95 | 0.93 | - | **FAIL** - 0 of 2 = 1.0, iterate (no tiebreaker needed) |

   **Step 4e: Verify validation evidence (M349)**

   **MANDATORY**: Before accepting any score, verify the subagent output contains ACTUAL
   /compare-docs evidence, not just a claimed score.

   **Evidence requirements** - subagent output MUST contain:
   1. EXECUTION EQUIVALENCE score line
   2. Component Scores table (Claim Preservation, Relationship Preservation, Graph Structure)
   3. Summary section with Shared Claims count

   **Verification check**:
   ```yaml
   evidence_check:
     has_score: true|false           # Look for "EXECUTION EQUIVALENCE:"
     has_component_table: true|false # Look for "| Component | Score |"
     has_summary: true|false         # Look for "Shared Claims:"

     # If ANY is false → subagent likely fabricated the score
     # REJECT and re-run validation with explicit instruction to include full output
   ```

   **If evidence missing**: Do NOT accept the score. Re-run validation with prompt:
   "Include the COMPLETE /compare-docs output including the EXECUTION EQUIVALENCE score, component table, and summary."

   **Why this matters (M349)**: Subagents may report expected scores without actually running
   validation. The 1.0 threshold creates strong priming for fabrication. Requiring evidence
   proves the skill was actually invoked.

   **Step 4f: Fabrication Detection (M354)**

   **MANDATORY**: After collecting scores, check for fabrication indicators:

   ```yaml
   fabrication_check:
     all_scores_identical: true|false  # If all runs return exactly 1.0, suspicious
     high_variance: true|false         # If runs differ by >0.3, extraction unstable

     # If all_scores_identical AND score is 1.0:
     action: "WARN: All validation runs returned identical 1.0 scores"
     recommendation: "Request independent re-validation from user or different session"

     # If high_variance (>0.3 difference between runs):
     action: "WARN: High variance suggests extraction instability"
     recommendation: "Run additional validation, investigate document structure"
   ```

   **Why this matters (M354)**: Subagents primed for 1.0 may construct evidence to match.
   Real /compare-docs runs show natural variance (±0.05-0.10). Identical 1.0 scores across
   multiple runs suggests fabrication rather than actual validation.

   **Report validation result**:
   ```
   Validation (best 2 of 3):
   - Run 1: {score1} [evidence: {verified|missing}]
   - Run 2: {score2} [evidence: {verified|missing}]
   - Run 3: {score3} (if needed) [evidence: {verified|missing}]
   - Fabrication check: {PASS|WARN: all identical|WARN: high variance}
   - Result: {PASS if ≥2 runs = 1.0 WITH verified evidence AND fabrication check passes, else FAIL}
   ```

   **If FAIL**: Proceed to Step 6 (Iteration). After creating new version, run
   Steps 4a-4e again on the NEW compressed version.

5. **Parse validation result**:
   - Use the **consensus score** from Step 4d
   - Extract warnings and lost_relationships from the agreeing runs
   - If runs disagree on warnings, include warnings from BOTH agreeing runs

**Scoring Context**: When reporting the score to the user, explicitly state:
```
Consensus score {score}/1.0 (best 2 of 3) compares the compressed version against
the ORIGINAL document state from before /shrink-doc was invoked.
```

**⚠️ CRITICAL REMINDER**: On second, third, etc. invocations:
- ✅ **REUSE** `/tmp/original-{{filename}}` from first invocation
- ✅ Always compare against original baseline (not intermediate versions)
- The baseline is set ONCE on first invocation and REUSED for all subsequent invocations

---

### Step 5: Decision Logic

**Threshold**: Exact equality required (no "close enough" - see M254)

**COMMIT GATE (M335)**: Files may ONLY be committed after validation passes:
- Score < 1.0 → File MUST NOT be applied or committed
- Score = 1.0 → File may be applied and committed
- Skipped validation → File MUST NOT be applied or committed

Rationalizations like "extraction variance" or "validation is broken" are completion bias.
If validation consistently fails, the compression is too aggressive - iterate or abandon.

**Report Format** (for approval):
1. Validation output from /compare-docs (copy verbatim)
2. **Version Comparison Table** (showing all versions generated in this session)

**⚠️ CRITICAL**: Report the ACTUAL score from /compare-docs. Do not summarize or interpret.

**Version Comparison Table Format**:

After presenting validation results for ANY version, show comparison table.

**Token Counting**: Use tiktoken for accurate token counts:

```bash
# Actual token count using tiktoken
TOKENS=$(python3 -c "
import tiktoken
enc = tiktoken.get_encoding('cl100k_base')
with open('$FILE', 'r') as f:
    print(len(enc.encode(f.read())))
")
```

**Table format:**

| Version      | Tokens | Reduction | Score | Status     |
|--------------|--------|-----------|-------|------------|
| **Original** | {n}    | baseline  | N/A   | Reference  |
| **V{n}**     | {n}    | {n}%      | {n}   | {status}   |

**Status values**:
- Approved = Score equals threshold
- Rejected = Score below threshold
- Applied = Currently applied to original file

---

**If score meets threshold**: ✅ **APPROVE**
```
Validation passed! Execution equivalence: {actual score from /compare-docs}

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
→ Future /shrink-doc invocations will reuse this baseline
→ Scores will reflect cumulative compression from true original
→ Go back to Step 3 with user's feedback

**If user says NO** (done iterating):
→ `rm /tmp/original-{{filename}}`
→ `rm /tmp/shrink-doc-{{filename}}-version.txt`
→ Note: Future /shrink-doc on this file will use compressed version as new baseline

**If score below threshold**: ❌ **ITERATE**
```
Validation requires improvement. Score: {actual score from /compare-docs}

{Copy /compare-docs output verbatim - includes component scores, warnings, lost_relationships}

Re-invoking agent with feedback to fix issues...
```
→ Go to Step 6 (Iteration)

**⚠️ MANDATORY: Score Validation Gate (M254)**

**BLOCKING REQUIREMENT**: Complete this validation BEFORE making any approval decision.

**Step 1: Run /compare-docs and capture output**
```bash
/compare-docs /tmp/original-{{filename}} /tmp/compressed-{{filename}}-v${VERSION}.md
```

**Step 2: Extract and report the ACTUAL score**
```
SCORE={exact decimal from /compare-docs execution_equivalence_score}
```

**Step 3: Perform explicit comparison**
```
IS_EXACT_MATCH=$(echo "$SCORE == 1.0" | bc -l)
if [ "$IS_EXACT_MATCH" -eq 1 ]; then
  DECISION="APPROVE"
else
  DECISION="ITERATE"
fi
```

**Step 4: State decision with actual score**
```
Score: {ACTUAL_SCORE} (from /compare-docs)
Decision: {DECISION}
```

**FAIL-FAST**: If DECISION=ITERATE, STOP. Do not ask user for approval. Proceed directly to Step 6 (Iteration Loop).

**Why this gate exists (M254)**: Completion bias causes agents to rationalize "close enough" scores. Only exact threshold match permits approval. No exceptions.

---

### Step 6: Iteration Loop

**If score < 1.0**, invoke agent again with specific feedback:

**Iteration Prompt Template**:
```
**Document Compression - Revision Attempt {iteration_number}**

**Previous Score**: {score}/1.0 (threshold: 1.0)

**Issues Identified by Validation**:

{warnings from /compare-docs}

**Lost Relationships**:

{for each lost_relationship:}
- **{type}**: {from_claim} → {to_claim}
  - Constraint: {constraint}
  - Evidence: {evidence}
  - Impact: {violation_consequence}
  - **Fix**: {specific recommendation}

**Your Issue**:

Revise the compressed document to restore the lost relationships while maintaining compression.

**Original**: /tmp/original-{{filename}}
**Previous Attempt**: /tmp/compressed-{{filename}}-v${VERSION}.md

Focus on:
1. Restoring explicit relationship statements identified above
2. Maintaining conditional structure (IF-THEN-ELSE)
3. Preserving mutual exclusivity constraints
4. Keeping escalation/fallback paths

**⚠️ CRITICAL**: USE THE WRITE TOOL to save the revised document to the specified path.
Do NOT just describe or return the content - you MUST physically write the file.
```

**After iteration**:
- Save revised version as next version number (v${VERSION+1})
- Re-run /compare-docs validation **AGAINST ORIGINAL BASELINE**
- Apply decision logic again (Step 5)

**🚨 MANDATORY: /compare-docs Required for EVERY Iteration**

**CRITICAL**: You MUST invoke `/compare-docs` for EVERY version validation.
No exceptions. Score is ONLY valid if it comes from /compare-docs output.

```bash
/compare-docs /tmp/original-{filename} /tmp/compressed-{filename}-v{N}.md
```

**Self-Check Before Reporting Score**:
1. Did I invoke /compare-docs for this version? YES/NO
2. Is the score from /compare-docs output? YES/NO
3. If either is NO → STOP and invoke /compare-docs

**Maximum iterations**: 3
- If still < 1.0 after 3 attempts, report to user and ask for guidance
- All versions preserved in /tmp for rollback
- User may choose to accept best attempt or abandon compression

---

## Batch Processing (Multiple Files)

**For batch operations**, read: [BATCH-PROCESSING.md](BATCH-PROCESSING.md)

Summary: When compressing multiple files, each must be validated individually with `/compare-docs`.
Use parallel subagents for efficiency, with fault-tolerant collection and selective retry logic.

---

## Implementation Notes

**Agent Type**: MUST use `subagent_type: "general-purpose"`

**Validation Tool**: Use /compare-docs (SlashCommand tool)

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
- /compare-docs execution_equivalence_score meets threshold exactly

✅ **Compression quality** metrics:
- Word reduction: ~50% (target, secondary to equivalence)
- All component scores from /compare-docs at maximum
- No critical relationship losses reported by /compare-docs

---

## Edge Cases

**Abstraction vs Enumeration**: When compressed document uses high-level
constraint statements (e.g., "handlers are mutually exclusive") instead of
explicit pairwise enumerations, validation may score 0.85-0.94. System will
automatically iterate to restore explicit relationships, as abstraction
creates interpretation vulnerabilities (see /compare-docs § Score Interpretation).

**Score Plateau**: If multiple iterations needed but score plateaus (no
improvement after 2 attempts, e.g., v1=0.87, v2=0.88, v3=0.89), compression
may be hitting fundamental limits. After 3 attempts below 1.0, report best
version to user and explain compression challenges encountered.

**Multiple Iterations**: Each iteration should show improvement. Monitor progression toward 1.0 threshold.

**Large Documents**: For documents >10KB, consider breaking into logical sections
and compressing separately to improve iteration efficiency.

---

## Example Usage

```
/shrink-doc /workspace/main/.claude/commands/example-command.md
```

Expected flow:
1. Validate document type ✅
2. Save original to /tmp/original-example-command.md (baseline) ✅
3. Invoke compression agent
4. Save to /tmp/compressed-example-command-v1.md (version 1) ✅
5. Run /compare-docs /tmp/original-example-command.md /tmp/compressed-example-command-v1.md
6. Score 1.0 → Approve v1 and overwrite original ✅
7. Cleanup: Remove /tmp/compressed-example-command-v*.md and /tmp/original-example-command.md ✅

**If iteration needed**:
- v1 score < 1.0 → Save v2, validate against original
- v2 score < 1.0 → Save v3, validate against original
- v3 score = 1.0 → Approve v3, cleanup v1/v1/v3 and original
- v3 score < 1.0 (after max iterations) → Report to user with best version
