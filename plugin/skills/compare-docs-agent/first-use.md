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

**Goal:** Determine if documents are EQUIVALENT (no semantic loss) or NOT_EQUIVALENT
(with list of what was lost).

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
    Read the instructions at: plugin/skills/compare-docs/EXTRACTION-AGENT.md

    Extract all semantic units from:
    - DOCUMENT: {{arg1}}

    Return the JSON output as specified.

Task tool #2 (Doc B):
  subagent_type: "general-purpose"
  model: "sonnet"
  description: "Extract units from Document B"
  prompt: |
    Read the instructions at: plugin/skills/compare-docs/EXTRACTION-AGENT.md

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
    Read the instructions at: plugin/skills/compare-docs/COMPARISON-AGENT.md

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

### Single File

```
═══════════════════════════════════════════════════════════════════════════════
                              COMPARISON RESULT
═══════════════════════════════════════════════════════════════════════════════

Status: EQUIVALENT | NOT_EQUIVALENT (X/Y preserved, Z lost)

───────────────────────────────────────────────────────────────────────────────
LOST (in original, missing in compressed)
───────────────────────────────────────────────────────────────────────────────
- [CATEGORY] "original text of lost unit"
- ...

───────────────────────────────────────────────────────────────────────────────
ADDED (in compressed, not in original)
───────────────────────────────────────────────────────────────────────────────
- [CATEGORY] "original text of added unit"
- ...

═══════════════════════════════════════════════════════════════════════════════
```

### Batch (Multiple Files)

```
═══════════════════════════════════════════════════════════════════════════════
                           BATCH COMPARISON RESULT
═══════════════════════════════════════════════════════════════════════════════

| File             | Status          | Preserved | Lost |
|------------------|-----------------|-----------|------|
| file1.md         | EQUIVALENT      | 23/23     | 0    |
| file2.md         | NOT_EQUIVALENT  | 44/47     | 3    |

───────────────────────────────────────────────────────────────────────────────
DETAILS: file2.md (3 lost)
───────────────────────────────────────────────────────────────────────────────
- [SEQUENCE] "lost unit text"
- ...

═══════════════════════════════════════════════════════════════════════════════
```

---

## Status Interpretation

| Status | Meaning | Action |
|--------|---------|--------|
| EQUIVALENT | No semantic loss | Compression approved |
| NOT_EQUIVALENT | Units lost | Iterate with LOST list as feedback |

**No numeric scores.** Binary status with counts provides clear decision.

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
- [ ] Result shows Status: EQUIVALENT or NOT_EQUIVALENT
- [ ] LOST list includes [CATEGORY] tags and original text

---

## Limitations

1. **Normalization consistency:** Matching depends on consistent normalization by extraction agents
2. **Extraction variance:** Some variance possible in unit boundary decisions
3. **Context qualifiers:** Complex context dependencies may not be fully captured
4. **Cross-document:** Cannot follow external references to verify their content

---

## Use Cases

**Best suited for:**
- Compression validation (shrink-doc)
- Documentation refactoring verification
- Procedure change impact analysis

**Binary output is intentional:**
- No ambiguous 0.91 scores to interpret
- Clear EQUIVALENT/NOT_EQUIVALENT decision
- Actionable LOST list for iteration
