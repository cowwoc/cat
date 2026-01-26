---
name: shrink-doc
description: Compress documentation while preserving execution equivalence (validation-driven approach)
---

# Validation-Driven Document Compression

**Task**: Compress the documentation file: `{{arg}}`

**Goal**: Reduce document size while preserving execution equivalence using
objective validation instead of prescriptive rules.

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

When compressing `CLAUDE.md`, use **content reorganization** instead of standard compression:

**Step 1: Analyze Content Location**
Before compressing, categorize ALL content into:

| Category | Action |
|----------|--------|
| **Duplicates skills** | REMOVE - reference skill instead |
| **Main-agent-specific** | MOVE to main-agent-specific file |
| **Sub-agent-specific** | MOVE to sub-agent-specific file |
| **Universal (all agents)** | KEEP in CLAUDE.md |

**Step 2: Check for Duplication**
```bash
# Check if content already exists in skills
ls .claude/skills/

# Check if procedural content duplicates a skill
grep -l "pattern" .claude/skills/*/SKILL.md
```

**Step 3: Content Categories**

*Examples are illustrative; specific categories vary by project.*

**REMOVE (duplicates existing):**
- Procedural content that exists in skills
- Content already documented in agent-specific files

**MOVE (agent-specific):**
- Main-agent-only content (e.g., multi-agent coordination, repository structure)
- Sub-agent-only content (e.g., specific workflow steps only they perform)

**KEEP (universal guidance):**
- Tone/style, error handling, security policies
- Content that applies equally to ALL agent types

**Step 4: Result Structure**

CLAUDE.md should be a **slim reference document** (~200 lines) that:
- Contains ONLY universal guidance for ALL agents
- **Instructs agents to read their agent-specific files** (e.g., "MAIN AGENT: Read {file}.md")
- References skills for procedural content (not duplicate them)

**Hub-and-Spoke Pattern**:
```
CLAUDE.md (universal, ~200 lines)
  ├── "MAIN AGENT: Read {main-agent-file}.md"
  └── "SUB-AGENTS: Read {sub-agent-file}.md"
```

Agent-specific files contain the detailed content moved out of CLAUDE.md. Create these files if they
don't exist. CLAUDE.md becomes a routing document that directs agents to their specialized guidance.

**Why This Approach**: Standard compression preserves all content in place. CLAUDE.md benefits from
**reorganization** because much content is duplicated elsewhere or is agent-specific. Moving content
to appropriate locations reduces redundancy across the entire documentation system.

**Validation**: After reorganization, verify:
```bash
# CLAUDE.md should be ~200-250 lines (not 800+)
wc -l CLAUDE.md

# No procedural duplication with skills
grep -c "Step 1:" CLAUDE.md  # Should be minimal
```

---

**⚠️ SPECIAL HANDLING: Style Documentation Files**

When compressing `.claude/rules/*.md` or `docs/code-style/*-claude.md`:

**Preserve style rule sections** (lines starting with `### `). These are intentionally-added
detection patterns and rules. Compression can:
- ✅ Condense explanatory text within sections
- ✅ Shorten verbose rationale paragraphs
- ✅ Combine redundant examples
- ❌ Deleting entire `### Section Name` blocks breaks detection
- ❌ Removing detection patterns or code examples breaks detection

**Verification Required**: After compression, count section headers:
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

Use Task tool with `subagent_type: "general-purpose"` and simple outcome-based prompt:

**Agent Prompt Template**:
```
**Document Compression Task**

**File**: {{arg}}

**Goal**: Compress while preserving **perfect execution equivalence** (score = 1.0).

**Compression Target**: ~50% reduction is ideal, but lesser compression is acceptable. Perfect equivalence (1.0) is mandatory; compression amount is secondary.

---

## What is Execution Equivalence?

**Execution Equivalence** means: A reader following the compressed version will achieve the same results as someone following the original.

**Preserve**:
- **YAML frontmatter** (between `---` delimiters) - REQUIRED for slash commands
- **Decision-affecting information**: Claims, requirements, constraints that affect what to do
- **Relationship structure**: Temporal ordering (A before B), conditionals (IF-THEN), prerequisites, exclusions (A ⊥ B), escalations
- **Control flow**: Explicit sequences, blocking checkpoints (STOP, WAIT), branching logic
- **Executable details**: Commands, file paths, thresholds, specific values

**Safe to remove**:
- **Redundancy**: Repeated explanations of same concept
- **Verbose explanations**: Long-winded descriptions that can be condensed
- **Meta-commentary**: Explanatory comments about the document (NOT structural metadata like YAML frontmatter)
- **Non-essential examples**: Examples that don't add new information
- **Elaboration**: Extended justifications or background that don't affect decisions

---

## Compression Approach

**Focus on relationships**:
- Keep explicit relationship statements (Prerequisites, Dependencies, Exclusions, Escalations)
- Preserve temporal ordering (Step A→B)
- Maintain conditional logic (IF-THEN-ELSE)
- Keep constraint declarations (CANNOT coexist, MUST occur after)

**Condense explanations**:
- Remove "Why This Ordering Matters" verbose sections → keep ordering statement
- Remove "Definition" sections that explain obvious terms
- Combine related claims into single statements where possible
- Use high-level principle statements instead of exhaustive enumeration (when appropriate)

---

## Output

Read `{{arg}}`, compress it, and **USE THE WRITE TOOL** to save the compressed version.

**⚠️ CRITICAL**: You MUST actually write the file using the Write tool. Do NOT just describe
or summarize the compressed content - physically create the file.

**Target**: ~50% word reduction while maintaining execution equivalence.
```

**Execute compression**:
```bash
# Invoke agent
Task tool: general-purpose agent with above prompt
```

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

4. **Run validation AGAINST ORIGINAL**:
   ```bash
   # ALWAYS compare against original baseline, NOT current file state
   /compare-docs /tmp/original-{{filename}} /tmp/compressed-{{filename}}-v${VERSION}.md
   ```

   **⚠️ IMPORTANT**: The validation score reflects execution equivalence between:
   - **Document A**: Original document state BEFORE /shrink-doc was invoked in this session
   - **Document B**: Newly compressed candidate version

   **NOT** a comparison against any intermediate compressed versions.

5. **Parse validation result**:
   - Extract execution_equivalence_score
   - Extract warnings and lost_relationships
   - Extract structural_changes

**Scoring Context**: When reporting the score to the user, explicitly state:
```
Score {score}/1.0 compares the compressed version against the ORIGINAL document
state from before /shrink-doc was invoked (not against any intermediate versions).
```

**⚠️ CRITICAL REMINDER**: On second, third, etc. invocations:
- ✅ **REUSE** `/tmp/original-{{filename}}` from first invocation
- ✅ Always compare against original baseline (not intermediate versions)
- The baseline is set ONCE on first invocation and REUSED for all subsequent invocations

---

### Step 5: Decision Logic

**Threshold**: 1.0

**Report Format** (for approval):
1. What was preserved
2. What was removed
3. Validation Details (claim/relationship/graph scores)
4. **Results** (original size, compressed size, reduction %, **execution equivalence score**)
5. **Version Comparison Table** (showing all versions generated in this session)

**⚠️ CRITICAL**: List execution equivalence score at bottom for easy visibility.

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

**Expected output format:**

| Version      | Tokens | Reduction | Score | Status      |
|--------------|--------|-----------|-------|-------------|
| **Original** | {n}    | baseline  | N/A   | Reference   |
| **V1**       | {n}    | {n}%      | {n}   | Rejected    |
| **V2**       | {n}    | {n}%      | {n}   | Applied     |

**Status values**:
- Approved = Score equals 1.0
- Rejected = Score below 1.0
- Applied = Currently applied to original file

**Example**:

| Version      | Tokens | Reduction | Score | Status    |
|--------------|--------|-----------|-------|-----------|
| **Original** | 12,480 | baseline  | N/A   | Reference |
| **V1**       | 6,760  | 46%       | 0.89  | Rejected  |
| **V2**       | 5,590  | 55%       | 1.0   | Applied   |

---

**If score = 1.0**: ✅ **APPROVE**
```
Validation passed! Execution equivalence: {score}/1.0

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

**If score < 1.0**: ❌ **ITERATE**
```
Validation requires improvement. Score: {score}/1.0 (threshold: 1.0)

Components:
- Claim preservation: {claim_score}
- Relationship preservation: {relationship_score}
- Graph structure: {graph_score}

**Why < 1.0 requires iteration**:
Scores below 1.0 indicate relationship abstraction or loss that creates
interpretation vulnerabilities. See /compare-docs § Score Interpretation
for detailed vulnerability analysis.

**Common issues at this score range**:
- Abstraction ambiguity (e.g., "ALL of X" → separate statements)
- Lost mutual exclusivity constraints
- Conditional logic flattening (IF-THEN-ELSE → flat list)
- Temporal dependencies implicit rather than explicit

Issues found:
{list warnings from /compare-docs}

Specific relationship losses:
{list lost_relationships with details}

Re-invoking agent with feedback to fix issues...
```
→ Go to Step 6 (Iteration)

**⚠️ MANDATORY: Score Validation Gate (M254)**

**BLOCKING REQUIREMENT**: Complete this validation BEFORE making any approval decision.

**Step 1: Extract exact score value**
```
SCORE={exact decimal from /compare-docs execution_equivalence_score}
```

**Step 2: Perform explicit comparison**
```
THRESHOLD=1.0
IS_EXACTLY_ONE=$(echo "$SCORE == $THRESHOLD" | bc -l)
```

**Step 3: Decision based ONLY on comparison result**
```
if [ "$IS_EXACTLY_ONE" -eq 1 ]; then
  DECISION="APPROVE"
else
  DECISION="ITERATE"  # ANY value less than 1.0, including 0.99, 0.999, etc.
fi
```

**Step 4: State decision with explicit score verification**
```
Score: {SCORE}/1.0
Comparison: {SCORE} == 1.0 → {true|false}
Decision: {DECISION}
```

**FAIL-FAST**: If DECISION=ITERATE, STOP. Do not ask user for approval. Proceed directly to Step 6 (Iteration Loop).

**Why this gate exists (M254)**: Completion bias causes agents to rationalize "close enough" scores. Only exact equality (score == 1.0) permits approval. No exceptions.

**Anti-patterns (blocked by this gate)**:
- ❌ "Score 0.97, close enough to 1.0" → BLOCKED (0.97 ≠ 1.0)
- ❌ "Score 0.99, good enough" → BLOCKED (0.99 ≠ 1.0)
- ❌ "Score 0.999, essentially perfect" → BLOCKED (0.999 ≠ 1.0)
- ✅ "Score 1.0" → APPROVED (1.0 == 1.0)

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

**Your Task**:

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

**CRITICAL**: You MUST invoke `/compare-docs` (SlashCommand tool) for EVERY version validation.
There are NO exceptions. Manual validation, estimation, or checklist-based scoring is PROHIBITED.

**Why This Is Non-Negotiable**:
- Session 7937e222: Agent manually validated v2/v3 with self-created checklist → score 0.97
- Independent re-analysis: Actual score was 0.72 (25% inflation)
- Result: Compression approved that lost critical content

**Validation Anti-Patterns** (ALL are violations):

❌ **WRONG #1**: Manual checklist validation
```
"Let me assess v2 improvements..."
| Category | Original | v2 | Preserved |
| State machine | ✅ | ✅ | 100% |
[creates own checklist, assigns 100% to all]
"Estimated Score: 0.97"
```
**Why wrong**: Agent knows what SHOULD be there, confirms it exists (confirmation bias)

❌ **WRONG #2**: Estimation without /compare-docs
```
"Good progress on v2. Estimated Score: ~0.88"
```
**Why wrong**: No independent extraction, just subjective assessment

❌ **WRONG #3**: Custom Task prompt with items to verify
```
Task: "Verify these 6 improvements are present: 1. X, 2. Y..."
```
**Why wrong**: Primes validator to confirm checklist, misses other losses

✅ **CORRECT**: Invoke /compare-docs for EVERY version
```bash
# v1 validation
/compare-docs /tmp/original-{filename} /tmp/compressed-{filename}-v1.md

# v2 validation (after iteration)
/compare-docs /tmp/original-{filename} /tmp/compressed-{filename}-v2.md

# v3 validation (after iteration)
/compare-docs /tmp/original-{filename} /tmp/compressed-{filename}-v3.md
```

**Enforcement**: Score is ONLY valid if it comes from /compare-docs output.
Any score derived from manual assessment, estimation, or targeted validation is INVALID.

**Self-Check Before Reporting Score**:
1. Did I invoke /compare-docs (SlashCommand tool) for this version? YES/NO
2. Is the score from /compare-docs output, not my own calculation? YES/NO
3. If either is NO → STOP and invoke /compare-docs

**Maximum iterations**: 3
- If still < 1.0 after 3 attempts, report to user and ask for guidance
- All versions preserved in /tmp for rollback
- User may choose to accept best attempt or abandon compression

---

## Batch Processing (Multiple Files)

When compressing multiple files in a single task (e.g., "compress all files in plugin/commands/"):

### Per-File Validation Required (M265)

Each file MUST be validated individually with `/compare-docs`. Report results as a per-file table:

| File | execution_equivalence_score | Status |
|------|----------------------------|--------|
| commands/add.md | 1.0 | PASS |
| commands/work.md | 1.0 | PASS |
| commands/status.md | 0.94 | FAIL - iterate |

**Anti-pattern (M265):** Summarizing batch results as "all files passed" or "all files scored 1.0" without showing per-file evidence. Aggregate statements are unverifiable.

### Execution Models

**Sequential** (single agent processes files one at a time):
1. Process each file through full workflow (Steps 1-6)
2. Track per-file scores in the table
3. Iterate failed files before moving to next file
4. Present final table after all files complete

**Parallel** (multiple subagents via `/cat:parallel-execute`):
1. Spawn one subagent per file, each running full shrink-doc workflow
2. Each subagent manages its own baseline (`/tmp/original-{filename}`) and versions
3. Collect results from all subagents
4. Combine into single per-file table
5. Files that fail validation iterate independently within their subagent

**Parallel is preferred** for batch operations - files are independent and can be compressed concurrently. Use sequential only when resource constraints require it.

### Reporting to Parent Agent

When batch compression is delegated via subagent:

```
BATCH RESULTS:

| File | execution_equivalence_score | Status |
|------|----------------------------|--------|
| {file1} | {score} | {PASS/FAIL} |
| {file2} | {score} | {PASS/FAIL} |

Summary: {N} files processed, {M} passed (score = 1.0), {K} failed
```

**Parent agent responsibility:** When presenting approval gate after batch compression, include the per-file table. Do NOT summarize as "all passed" - show each file's actual score.

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
- Execution equivalence score = 1.0

✅ **Compression quality** metrics:
- Word reduction: ~50% (target)
- Execution equivalence: = 1.0
- Claim preservation: = 1.0
- Relationship preservation: = 1.0
- Graph structure: = 1.0
- No critical relationship losses

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
