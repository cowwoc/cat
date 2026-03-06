<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Semantic Document Comparison

## Invocation Restriction

**MAIN AGENT ONLY**: This skill spawns subagents internally. It CANNOT be invoked by
a subagent (subagents cannot spawn nested subagents or invoke skills).

If you need this skill's functionality within delegated work:
1. Main agent invokes this skill directly
2. Pass results to the implementation subagent
3. See: plugin/skills/delegate/SKILL.md § "Model Selection for Subagents"

---

**Task:** Compare two documents semantically: `{{arg1}}` vs `{{arg2}}`

**Goal:** Determine if documents are EXECUTION-EQUIVALENT (no behavioral loss) or NOT, with rich diagnostics
showing exactly what was lost, how severe it is, and where it appeared in both documents.

---

## Architecture: Three-Agent Model

```
┌─────────────────────┐  ┌─────────────────────┐
│ Extraction Agent 1  │  │ Extraction Agent 2  │
│ (sees ONLY arg1)    │  │ (sees ONLY arg2)    │
└──────────┬──────────┘  └──────────┬──────────┘
           │                        │
           └───────────┬────────────┘
                       ▼
            ┌─────────────────────┐
            │  Comparison Agent   │
            │ (sees extractions   │
            │   only, not docs)   │
            └─────────────────────┘
```

**Why this avoids bias:**
- Extraction agents don't see the other document
- Comparison agent doesn't see raw documents

---

## Procedure

### Step 1: Extract Units from Both Documents

**Spawn BOTH extraction agents in a single message:**

```
Task tool #1 (Doc A):
  subagent_type: "general-purpose"
  model: "sonnet"
  description: "Extract units from Document A"
  prompt: |
    Read the instructions at: plugin/skills/compare-docs-agent/EXTRACTION-AGENT.md

    Extract all semantic units from:
    - DOCUMENT: {{arg1}}

    Return the JSON output as specified.

Task tool #2 (Doc B):
  subagent_type: "general-purpose"
  model: "sonnet"
  description: "Extract units from Document B"
  prompt: |
    Read the instructions at: plugin/skills/compare-docs-agent/EXTRACTION-AGENT.md

    Extract all semantic units from:
    - DOCUMENT: {{arg2}}

    Return the JSON output as specified.
```

**After both complete**, save results:
- `/tmp/compare-doc-a-extraction.json`
- `/tmp/compare-doc-b-extraction.json`

**⚠️ ENCAPSULATION:** The extraction algorithm is in EXTRACTION-AGENT.md.
Do NOT attempt extraction manually.

### Step 2: Compare Extractions

**Spawn comparison agent:**

```
Task tool:
  subagent_type: "general-purpose"
  model: "sonnet"
  description: "Compare document extractions"
  prompt: |
    Read the instructions at: plugin/skills/compare-docs-agent/COMPARISON-AGENT.md

    Compare these two extraction outputs:
    - Document A: /tmp/compare-doc-a-extraction.json
    - Document B: /tmp/compare-doc-b-extraction.json

    Generate the COMPARISON RESULT output as specified.
```

**⚠️ ENCAPSULATION:** The comparison algorithm is in COMPARISON-AGENT.md.

### Step 3: Relay Result

Relay the comparison result verbatim to the caller.

---

## Output Format

The comparison output includes:
- **`Status`** — EQUIVALENT (0 units lost) or NOT_EQUIVALENT (1+ units lost)
- **`Execution Equivalent`** — the authoritative gate verdict: `true` if no HIGH or MEDIUM findings exist;
  `false` if any HIGH or MEDIUM finding exists. LOW-only losses yield `true` — they are informational only.
- **Per LOST unit:**
  - **`[CATEGORY] [SEVERITY]`** — semantic category and severity, followed by the unit's action/content (without
    repeating the category prefix, since the tag already provides it)
  - **`Original:`** — verbatim quote from the original document where the unit appeared
  - **`Compressed:`** — verbatim quote from the compressed document (or `(absent)` if no related unit exists)

**`execution_equivalent` is the authoritative gate.** All other fields (Status, severity, evidence) are
diagnostic information to help understand and iterate on failures. The caller must check `Execution Equivalent`
to determine whether the compression passed or failed.

### Severity Classification

| Severity | Categories |
|----------|-----------|
| **HIGH** | PROHIBITION, REQUIREMENT, CONDITIONAL, EXCLUSION |
| **MEDIUM** | CONSEQUENCE, DEPENDENCY, SEQUENCE |
| **LOW** | CONJUNCTION, REFERENCE |

**Special rule:** EXCLUSION units are always HIGH — they define execution boundaries that cannot be lost
without changing behavior.

**Gate rule:** `execution_equivalent = false` if any LOST unit has HIGH or MEDIUM severity. LOW findings
do not fail the gate.

### Single File — NOT_EQUIVALENT Example

```
═══════════════════════════════════════════════════════════════════════════════
                              COMPARISON RESULT
═══════════════════════════════════════════════════════════════════════════════

Status: NOT_EQUIVALENT (44/47 preserved, 3 lost)
Execution Equivalent: false

───────────────────────────────────────────────────────────────────────────────
LOST (in original, missing in compressed)
───────────────────────────────────────────────────────────────────────────────
- [SEQUENCE] [MEDIUM] verify not inside target → cleanup
  Original:   "verify not inside target directory before cleanup"
  Compressed: (absent)

- [REFERENCE] [LOW] ops/recovery.md
  Original:   "See recovery procedures in ops/recovery.md"
  Compressed: (absent)

- [REQUIREMENT] [HIGH] use parent directory as working dir for build artifacts
  Original:   "Use parent directory as working dir for build artifacts"
  Compressed: (absent)

───────────────────────────────────────────────────────────────────────────────
ADDED (in compressed, not in original)
───────────────────────────────────────────────────────────────────────────────
- (none)

═══════════════════════════════════════════════════════════════════════════════
```

### Single File — EQUIVALENT Example

```
═══════════════════════════════════════════════════════════════════════════════
                              COMPARISON RESULT
═══════════════════════════════════════════════════════════════════════════════

Status: EQUIVALENT (47/47 preserved, 0 lost)
Execution Equivalent: true

───────────────────────────────────────────────────────────────────────────────
LOST (in original, missing in compressed)
───────────────────────────────────────────────────────────────────────────────
- (none)

───────────────────────────────────────────────────────────────────────────────
ADDED (in compressed, not in original)
───────────────────────────────────────────────────────────────────────────────
- [REQUIREMENT] "Additional clarification about edge case"

═══════════════════════════════════════════════════════════════════════════════
```

### Single File — LOW-only losses (execution equivalent despite semantic loss)

When all LOST units are LOW severity, the compressed document passes the gate even though semantic units
were lost. LOW findings are informational — they report context loss (e.g., missing cross-references or
conjunction grouping hints) that is unlikely to change execution behavior.

```
═══════════════════════════════════════════════════════════════════════════════
                              COMPARISON RESULT
═══════════════════════════════════════════════════════════════════════════════

Status: NOT_EQUIVALENT (46/47 preserved, 1 lost)
Execution Equivalent: true

───────────────────────────────────────────────────────────────────────────────
LOST (in original, missing in compressed)
───────────────────────────────────────────────────────────────────────────────
- [REFERENCE] [LOW] ops/recovery.md
  Original:   "See ops/recovery.md for recovery steps"
  Compressed: (absent)

───────────────────────────────────────────────────────────────────────────────
ADDED (in compressed, not in original)
───────────────────────────────────────────────────────────────────────────────
- (none)

═══════════════════════════════════════════════════════════════════════════════
```

### Batch (Multiple Files)

```
═══════════════════════════════════════════════════════════════════════════════
                           BATCH COMPARISON RESULT
═══════════════════════════════════════════════════════════════════════════════

| File                 | Status          | Preserved | Lost | Exec Equivalent |
|----------------------|-----------------|-----------|------|-----------------|
| safe-rm/SKILL.md     | NOT_EQUIVALENT  | 44/47     | 3    | false           |
| status/SKILL.md      | EQUIVALENT      | 23/23     | 0    | true            |
| deploy/SKILL.md      | NOT_EQUIVALENT  | 31/35     | 4    | false           |

───────────────────────────────────────────────────────────────────────────────
DETAILS: safe-rm/SKILL.md (3 lost)
───────────────────────────────────────────────────────────────────────────────
- [SEQUENCE] [MEDIUM] verify not inside target → cleanup
  Original:   "verify not inside target before cleanup"
  Compressed: (absent)

- [REFERENCE] [LOW] ops/recovery.md
  Original:   "See ops/recovery.md"
  Compressed: (absent)

- [REQUIREMENT] [HIGH] use parent directory as working dir
  Original:   "Use parent directory as working dir"
  Compressed: (absent)

───────────────────────────────────────────────────────────────────────────────
DETAILS: deploy/SKILL.md (4 lost)
───────────────────────────────────────────────────────────────────────────────
- [PROHIBITION] [HIGH] deploy without approval
  Original:   "NEVER deploy without approval"
  Compressed: (absent)

- [CONDITIONAL] [HIGH] if production then require sign-off
  Original:   "IF production THEN require sign-off"
  Compressed: (absent)

- [DEPENDENCY] [MEDIUM] valid credentials → deploy
  Original:   "Valid credentials required for deploy"
  Compressed: (absent)

- [SEQUENCE] [MEDIUM] run tests → deploy
  Original:   "Run tests before deploy"
  Compressed: (absent)

═══════════════════════════════════════════════════════════════════════════════
```

---

## Status and Verdict Interpretation

| `Status` | `Execution Equivalent` | Meaning | Action |
|----------|------------------------|---------|--------|
| EQUIVALENT | true | No semantic loss | Compression approved |
| NOT_EQUIVALENT | false | HIGH or MEDIUM units lost | Iterate with LOST list as feedback |
| NOT_EQUIVALENT | true | Only LOW units lost | Compression approved (informational losses only) |

**`execution_equivalent` is the authoritative gate.** Check it — not `Status` — to decide whether to
approve compression.

---

## Nine Semantic Categories

Units are classified into mutually exclusive categories:

| Priority | Category | Captures |
|----------|----------|----------|
| 1 | EXCLUSION | Mutual exclusivity constraints |
| 2 | CONJUNCTION | ALL-of requirements |
| 3 | PROHIBITION | Forbidden actions |
| 4 | CONDITIONAL | IF-THEN-ELSE logic |
| 5 | CONSEQUENCE | Causal relationships |
| 6 | DEPENDENCY | Non-temporal prerequisites |
| 7 | SEQUENCE | Temporal ordering |
| 8 | REFERENCE | Cross-document references |
| 9 | REQUIREMENT | Default requirements |

See EXTRACTION-AGENT.md for full marker definitions and disambiguation rules.

---

## Verification

- [ ] Both extraction agents spawned in single message
- [ ] Extraction outputs saved to /tmp/
- [ ] Comparison agent spawned after extractions complete
- [ ] Result shows `Status: EQUIVALENT` or `Status: NOT_EQUIVALENT`
- [ ] `Execution Equivalent:` line present in output header
- [ ] Each LOST unit includes `[CATEGORY]` and `[SEVERITY]` tags
- [ ] Each LOST unit includes `Original:` and `Compressed:` evidence quotes
- [ ] `Execution Equivalent: false` when any HIGH or MEDIUM LOST unit exists
- [ ] `Execution Equivalent: true` when all LOST units are LOW severity or 0 units lost
- [ ] Caller uses `execution_equivalent` (not `Status`) as the gate decision

---

## Limitations

1. **Normalization consistency:** Matching depends on consistent normalization by extraction agents
2. **Extraction variance:** Some variance possible in unit boundary decisions
3. **Context qualifiers:** Complex context dependencies may not be fully captured
4. **Cross-document:** Cannot follow external references to verify their content

---

## Use Cases

**Best suited for:**
- Compression validation (optimize-doc)
- Documentation refactoring verification
- Procedure change impact analysis

**Binary verdict is intentional:**
- `execution_equivalent` provides an unambiguous pass/fail decision
- Severity and evidence fields provide diagnostic context for iteration
- LOW findings surface potential context loss without blocking approval
