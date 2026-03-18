# sprt-benchmarking-for-skill-builder

## Goal

Unify skill-builder, optimize-doc, and compare-docs into a single skill-builder workflow that produces the smallest
skill file achieving 95% compliance with 95% confidence, rename "eval" terminology to "benchmark" throughout, rename
`skill-builder-agent` → `instruction-builder-agent` and `consolidate-doc-agent` → `instruction-organizer-agent`, and
integrate SPRT benchmarking validation into instruction-organizer-agent.

## Background

The current skill-builder runs each benchmark test case once per configuration. A single passing run provides almost no
statistical confidence in the true compliance rate (95% CI lower bound ~2.5%). Additionally, "eval" terminology is used
inconsistently alongside "benchmark" — the aggregator, output file, and iteration language already use "benchmark."

Meanwhile, `/cat:optimize-doc` compresses skill files to reduce token cost but has a critical blind spot: it validates
semantic equivalence (same meaning) but not behavioral equivalence (same compliance rate). A compressed skill can be
semantically equivalent yet produce different agent behavior because repetition, emphasis, and word choice affect LLM
compliance in ways semantic extraction cannot detect.

This issue combines six changes:
1. SPRT-based multi-run benchmarking with hybrid grading (deterministic + LLM) for statistical confidence
2. Auto-generation of test cases from semantic extraction, replacing manual test case design
3. Absorption of optimize-doc and compare-docs functionality into skill-builder as a compression phase
4. Consistent "benchmark" terminology throughout
5. Rename `skill-builder-agent` → `instruction-builder-agent` and `consolidate-doc-agent` →
   `instruction-organizer-agent`
6. Add SPRT benchmarking validation to instruction-organizer-agent so organized documents are verified to still meet
   the 95% compliance threshold

## Design Decisions

### SPRT over fixed-sample testing
Wald's Sequential Probability Ratio Test allows early stopping when evidence is sufficient, reducing average sample
count by ~50% compared to fixed-sample tests (72 → ~35 runs on average). Each run uses a fresh subagent to ensure
statistical independence.

### Haiku for benchmark runs
Benchmark runs test instruction clarity, not model capability. A skill that Haiku follows correctly will be followed by
Opus. Haiku runs cost ~10-20x less than Opus, and automatic prompt caching gives 90% discount on input tokens for
subagents 2-N within each 5-minute window.

### Prefer deterministic grading, allow LLM grading
Assertions should be deterministic where feasible (string match, regex, structural checks) — these are graded inline
with no subagent overhead. However, since the skill-builder creates skills for arbitrary purposes, some assertions
require semantic judgment ("explanation is accurate," "code handles edge cases"). These assertions spawn Haiku grader
subagents. The skill-builder should maximize the ratio of deterministic to LLM-graded assertions when designing test
cases, but must not prohibit LLM-graded ones.

### Parallel waves of 4
Claude Code allows 4 concurrent background agents. SPRT decision logic runs after each agent completes (pipelined, not
batched per wave). If the likelihood ratio crosses a boundary mid-wave, remaining agents in that wave are wasted but
the cost is negligible with Haiku + caching.

### Early rejection across test cases
Each test case runs its own independent SPRT. As soon as any test case rejects (non-compliant), all remaining test
cases stop immediately and the workflow proceeds to hardening. For non-compliant skills, this typically catches the
problem within 3-5 runs of the weakest test case rather than completing ~35 runs across all cases.

### Inline deterministic grading
Eval-run subagents grade their own deterministic assertions (regex, string match, structural checks) before returning,
eliminating a separate grader subagent round-trip per run. Only semantic assertions that require judgment spawn a
separate Haiku grader subagent. The eval-run subagent returns pass/fail results for deterministic assertions alongside
its output metadata.

### Ephemeral run outputs
Individual run output files are written to temp files (no git commit), not committed per-run. Only the final
`benchmark.json` is committed after SPRT completes. This eliminates git lock contention across parallel agents and
reduces per-run latency. Run outputs are ephemeral — they exist only for grading, which happens immediately.

### Hardening + benchmarking + compression as atomic unit
These three operations form a single phase that always runs together — never one without the others. When `effort` is
`low`, skip this entire phase (accept the skill draft as-is after the single-run sanity check). When `effort` is
`medium` or `high`, run the full harden → benchmark → compress → re-benchmark loop.

### Sequential phases, never interleaved
Hardening only adds text. Compression only removes text. Interleaving them creates oscillation (hardening adds a
clause, compression removes it, hardening adds it back). The correct sequence is:
1. Harden until compliant (only add text)
2. Compress to minimize size (only remove text)
3. Re-benchmark to verify compression preserved compliance
4. If compliance dropped, the compression removed load-bearing text — mark it as protected, retry compression

### Compression replaces optimize-doc and compare-docs
Skill-builder absorbs the compression algorithm from `cat:compression-agent` and the semantic validation from
`cat:compare-docs-agent` as reference files in its own directory. The standalone optimize-doc and compare-docs skills
are deprecated — skill-builder is the single entry point for creating, hardening, benchmarking, and compressing skills.

### Behavioral validation over semantic-only validation
Optimize-doc's compare-docs validates semantic equivalence (same meaning) but not behavioral equivalence (same
compliance). Skill-builder replaces this with SPRT re-benchmarking after compression — the compressed version must
achieve the same compliance rate, not just preserve the same semantic units. Semantic validation may still run as a
fast pre-check, but SPRT is the authoritative gate.

### Auto-generate test cases from semantic extraction
The current skill-builder requires manual test case design ("Create 2-3 test cases with assertions"). This limits
coverage to whatever the skill-builder happens to think of. Instead, embed the Nine-Category Extraction Algorithm
(from `plugin/skills/compare-docs-agent/EXTRACTION-AGENT.md`) directly in skill-builder to auto-generate test cases:
1. Extract semantic units from the skill using the 9 categories: REQUIREMENT, PROHIBITION, CONDITIONAL, SEQUENCE,
   DEPENDENCY, EXCLUSION, CONSEQUENCE, CONJUNCTION, REFERENCE. Each unit is a JSON object with fields: `id`,
   `category`, `original`, `normalized`, `quote`, `location`.
2. Classify each unit as behaviorally testable or not (see Appendix A for full classification table):
   - Testable (7 categories): REQUIREMENT, PROHIBITION, CONDITIONAL, SEQUENCE, DEPENDENCY, EXCLUSION, CONSEQUENCE
   - Not testable (2 categories): REFERENCE, CONJUNCTION → skip
3. For each testable unit, generate a test case prompt and assertions (see Appendix A for generation template)
4. Present generated test cases to user for approval before benchmarking

The extraction logic is embedded in skill-builder (not invoked via subagent) to minimize overhead. The extraction
algorithm is copied from EXTRACTION-AGENT.md into `validation-protocol.md` as a reference file. This makes benchmark
coverage proportional to the skill's actual instruction surface area rather than a fixed 2-3 manually designed cases.

### Renaming rationale
`skill-builder-agent` and `consolidate-doc-agent` are renamed to `instruction-builder-agent` and
`instruction-organizer-agent` respectively. Both skills operate on "agent instructions" — the complete set of
Claude-facing documents including skills, rules, agents, concepts, CLAUDE.md, and `.claude/rules/`. The new names
reflect their actual purpose more accurately:
- `instruction-builder-agent`: builds and validates new agent instruction files
- `instruction-organizer-agent`: reorganizes existing agent instruction files for clarity

### instruction-organizer-agent SPRT integration
After reorganization (Phase 3 Verify), instruction-organizer-agent performs a Quality Verification phase using the
same SPRT benchmarking defined in instruction-builder-agent's `validation-protocol.md`. This ensures that
consolidation does not regress the instruction document's compliance rate below the 95% threshold. If the SPRT rejects,
the organizer reverts to the pre-consolidation version.

## Scope

### In scope
- `plugin/skills/skill-builder-agent/first-use.md` — main workflow (harden + benchmark + compress)
- `plugin/skills/skill-builder-agent/e2e-dispute-trace.md` — terminology updates
- New reference files in `plugin/skills/skill-builder-agent/`:
  - `compression-protocol.md` — compression algorithm (absorbed from `plugin/agents/compression-agent.md`)
  - `validation-protocol.md` — semantic equivalence validation (absorbed from compare-docs)
- SPRT decision logic (Java tool or inline in skill instructions)
- Hybrid assertion grading: deterministic (Java/Bash) for structural checks, Haiku LLM for semantic checks
- Removal of `plugin/skills/optimize-doc/`, `plugin/skills/compare-docs-agent/`, and `plugin/agents/compression-agent.md`
- Rename `plugin/skills/skill-builder-agent/` → `plugin/skills/instruction-builder-agent/`
- Rename `plugin/skills/consolidate-doc-agent/` → `plugin/skills/instruction-organizer-agent/`
- Update all cross-references to both skills throughout `plugin/`
- Add SPRT Quality Verification phase to instruction-organizer-agent

### Out of scope
- Changes to adversarial TDD loop logic (hardening itself)
- Changes to skill-analyzer-agent or skill-grader-agent beyond terminology

## Changes

### Terminology rename
1. Rename commit message prefix: `eval:` → `benchmark:`
2. Rename directory: `eval-artifacts/` → `benchmark-artifacts/`
3. Rename variables: `EVAL_ARTIFACTS_DIR` → `BENCHMARK_ARTIFACTS_DIR`, `EVAL_SET_SHA` → `BENCHMARK_SET_SHA`
4. Update step labels and compliance checklist

### SPRT benchmark workflow
5. Replace single-run benchmark with SPRT loop:
   - Spawn waves of 4 Haiku eval-run subagents (fresh, independent)
   - Each subagent grades deterministic assertions inline and returns pass/fail with output
   - Spawn Haiku grader subagents only for semantic assertions
   - Write run outputs to temp files (no per-run git commits)
   - Feed pass/fail into per-test-case SPRT decision function after each completion (pipelined)
   - SPRT parameters: H₀ ≥ 0.95, H₁ ≤ 0.85, α = 0.05, β = 0.05
   - Early rejection: if any test case rejects, stop all cases and proceed to hardening
   - Accept (all cases) → compliant, Reject (any case) → harden → re-benchmark
   - Entire phase skipped when effort = low
6. Design hybrid assertion format: deterministic type (regex, string match, structural) graded inline; semantic type
   graded by Haiku subagent. Skill-builder should prefer deterministic assertions where possible.

### Assertion and test case JSON schema

Test case schema (stored in `benchmark-artifacts/<SID>/test-cases.json`):
```json
{
  "test_cases": [
    {
      "test_case_id": "TC1",
      "semantic_unit_id": "unit_5",
      "category": "REQUIREMENT",
      "prompt": "Scenario text that exercises the behavior...",
      "assertions": [
        {
          "assertion_id": "TC1_det_1",
          "type": "deterministic",
          "method": "regex",
          "description": "output must contain table with 4 columns",
          "pattern": "\\|[^|]+\\|[^|]+\\|[^|]+\\|[^|]+\\|",
          "expected": true
        },
        {
          "assertion_id": "TC1_sem_1",
          "type": "semantic",
          "description": "explanation correctly identifies the root cause",
          "instruction": "Check if the output explanation correctly identifies...",
          "expected": true
        }
      ]
    }
  ]
}
```

Deterministic assertion methods:
- `regex`: `pattern` field contains regex; pass if output matches (or doesn't match when `expected: false`)
- `string_match`: `pattern` field contains literal string; pass if output contains it
- `structural`: `pattern` field contains a structural check description (e.g., "JSON with key 'status'")

Eval-run subagent return format:
```json
{
  "run_id": "TC1_run_3",
  "test_case_id": "TC1",
  "assertion_results": [
    {"assertion_id": "TC1_det_1", "passed": true},
    {"assertion_id": "TC1_sem_1", "passed": null}
  ],
  "semantic_pending": ["TC1_sem_1"],
  "output_path": "/tmp/benchmark-runs/TC1_run_3.txt"
}
```

Assertions with `passed: null` require a Haiku grader subagent. Grader subagent contract:

**Input:** The grader is spawned as a background Agent (Haiku model) with a prompt containing:
- The assertion object (type, description, instruction, expected)
- The output file path (grader reads the file itself via the Read tool)
- Prohibition: "Do NOT read the skill file, test-cases.json, or any file other than the specified output file."

**Output:** The grader returns a single JSON object: `{"assertion_id": "TC1_sem_1", "passed": true|false}`

**Concurrency:** Grader subagents count against the 4-agent concurrency limit. When an eval-run subagent completes
with semantic assertions pending, the main agent spawns grader(s) for those assertions. If multiple semantic
assertions exist for a single run, they are graded in parallel (each gets its own grader subagent). Freed eval-run
slots are used for graders before spawning new eval-runs, to avoid blocking SPRT progress on pending grades.

7. Implement SPRT decision function
8. Add re-benchmark step after hardening converges

### Auto-generated test cases
9. Extract semantic units from the skill file using the validation protocol's extraction algorithm
10. Classify each unit as behaviorally testable or not:
    - Behaviorally testable: requirements, prohibitions, conditionals, sequences
    - Not directly testable: references, conjunctions → skip
11. For each testable unit, generate:
    - A test case prompt that exercises the behavior described by the unit
    - One or more assertions (deterministic where possible, semantic where necessary)
12. Skill-builder may add, remove, or refine auto-generated test cases before benchmarking

### Compression phase
13. After hardening achieves compliance, compress the skill file:
    - Invoke compression protocol (absorbed from `cat:compression-agent`)
    - Preserve load-bearing text identified by prior hardening rounds
    - Target ~50% token reduction (secondary to compliance preservation)
    - Compression is invoked by spawning a background Agent (subagent_type: general-purpose) with:
      - The skill file path (worktree-relative)
      - The compression-protocol.md as reference instructions
      - If retrying: the protected-sections.txt constraint file path
      - Output: the compressed file written to `benchmark-artifacts/<SID>/compressed-<skill-name>.md` and committed
      - The compression subagent must NOT read test-cases.json, benchmark.json, or any benchmark artifacts other than
        protected-sections.txt
14. Re-benchmark the compressed version via SPRT:
    - Re-benchmark uses the same test cases and same SPRT parameters as the post-hardening benchmark
    - Acceptance criteria: ALL test cases must reach SPRT Accept (log_ratio ≥ A) — identical standard to post-hardening
    - If any test case reaches SPRT Reject (log_ratio ≤ B) → compression broke compliance
    - On rejection: identify load-bearing text via git diff (see Appendix C), mark as protected, retry compression
    - Cap compression retries at 3 attempts
    - After 3 failures: accept the post-hardening (uncompressed) version as final
15. Optional semantic pre-check (absorbed from compare-docs):
    - Run semantic equivalence validation as a fast gate before the full SPRT re-benchmark
    - If semantically NOT_EQUIVALENT with HIGH-severity losses → skip SPRT and retry compression immediately
    - If semantically EQUIVALENT → proceed to SPRT for behavioral confirmation

### Workflow termination

The full workflow is NOT a repeating outer loop. It executes once in sequence:
1. Auto-generate test cases → user approval
2. SPRT benchmark (initial)
3. If non-compliant → harden (adversarial TDD loop until convergence) → SPRT re-benchmark (post-hardening)
4. Compress → SPRT re-benchmark (post-compression), with up to 3 compression retries
5. Accept final version (compressed if compliant, uncompressed if compression failed)

There is no return from compression back to hardening. If compression cannot preserve compliance after 3 retries,
the uncompressed post-hardening version is the final output. The only loops are: (a) SPRT runs until decision
boundary, (b) adversarial TDD runs until convergence, (c) compression retries up to 3 times.

### Skill consolidation
16. Create `plugin/skills/skill-builder-agent/compression-protocol.md`:
    - Compression algorithm from `plugin/agents/compression-agent.md`
    - What to preserve (decision-affecting constraints, control flow, executable details)
    - What to remove (redundant explanations, meta-commentary, non-essential examples)
17. Create `plugin/skills/skill-builder-agent/validation-protocol.md` with two sections:
    - **Section 1 — Semantic Extraction Algorithm:** The Nine-Category extraction algorithm from
      `EXTRACTION-AGENT.md`. Used by skill-builder for two purposes: (a) auto-generating test cases from skill
      instructions, and (b) extracting units for the semantic pre-check during compression. Categories: REQUIREMENT,
      PROHIBITION, CONDITIONAL, SEQUENCE, DEPENDENCY, EXCLUSION, CONSEQUENCE, CONJUNCTION, REFERENCE.
    - **Section 2 — Semantic Comparison Algorithm:** The comparison algorithm from `COMPARISON-AGENT.md`. Used for
      the optional semantic pre-check gate during compression (Step 15). Includes severity classification (HIGH:
      PROHIBITION/REQUIREMENT/CONDITIONAL/EXCLUSION, MEDIUM: CONSEQUENCE/DEPENDENCY/SEQUENCE, LOW:
      CONJUNCTION/REFERENCE) and the EQUIVALENT/NOT_EQUIVALENT decision logic.
18. Remove `plugin/skills/optimize-doc/`, `plugin/skills/compare-docs-agent/`, and `plugin/agents/compression-agent.md`:
    - Delete skill directories and agent file entirely
    - Remove any references to these skills in other files (hook registrations, skill lists, etc.)

### Rename skill-builder-agent → instruction-builder-agent
19. Rename directory:
    ```bash
    git mv plugin/skills/skill-builder-agent plugin/skills/instruction-builder-agent
    ```
20. Update internal SKILL.md reference:
    - `plugin/skills/instruction-builder-agent/SKILL.md`: change `get-skill" skill-builder-agent` →
      `get-skill" instruction-builder-agent`
21. Search for all cross-references and update them:
    ```bash
    grep -r "skill-builder-agent" plugin/ --include="*.md" -l
    ```
    Files known to reference `skill-builder-agent` (as of issue creation):
    - `plugin/skills/work-merge-agent/first-use.md`
    - `plugin/skills/optimize-execution/first-use.md`
    - `plugin/concepts/subagent-context-minimization.md`
    - `plugin/concepts/adversarial-protocol.md`
    - `plugin/agents/blue-team-agent/SKILL.md`
    - `plugin/agents/red-team-agent/SKILL.md`
    - `plugin/agents/skill-validator-agent/SKILL.md`
    - `plugin/skills/learn/phase-prevent.md`
    - `plugin/skills/learn/phase-investigate.md`
    - `plugin/skills/learn/PRIMING-VERIFICATION.md`
    - `plugin/concepts/eval-patterns.md`
    - `plugin/concepts/skill-validation.md`
    - `plugin/skills/empirical-test/first-use.md`
    - `plugin/skills/consolidate-doc-agent/first-use.md` (also being renamed; update simultaneously)

    Re-run grep after renaming to confirm no remaining references.

### Rename consolidate-doc-agent → instruction-organizer-agent
22. Rename directory:
    ```bash
    git mv plugin/skills/consolidate-doc-agent plugin/skills/instruction-organizer-agent
    ```
23. Update internal SKILL.md reference:
    - `plugin/skills/instruction-organizer-agent/SKILL.md`: change `get-skill" consolidate-doc-agent` →
      `get-skill" instruction-organizer-agent`
24. Search for all cross-references and update them:
    ```bash
    grep -r "consolidate-doc-agent" plugin/ --include="*.md" -l
    ```
    Files known to reference `consolidate-doc-agent` (as of issue creation):
    - `plugin/concepts/doc-consolidation.md`

    Re-run grep after renaming to confirm no remaining references.

### Update terminology throughout
25. Replace all occurrences of `skill-builder` with `instruction-builder` and `consolidate-doc` with
    `instruction-organizer` in:
    - SKILL.md descriptions
    - first-use.md headers and overview text
    - Any concept files that describe these skills by their old names
26. Update the user-facing description string in `plugin/skills/instruction-builder-agent/SKILL.md` to reference
    "agent instructions" instead of "skill OR command"
27. Update `plugin/skills/instruction-organizer-agent/SKILL.md` description to reference "agent instructions"

### Add SPRT Quality Verification to instruction-organizer-agent
28. In `plugin/skills/instruction-organizer-agent/first-use.md`, after Phase 3 (Verify), add a **Phase 4: Quality
    Verification** section:
    - Read `validation-protocol.md` from `instruction-builder-agent` (the same SPRT benchmarking protocol)
    - Run SPRT benchmarking on the reorganized document using the same test cases used in the original instruction-
      builder validation (if available) or generate new test cases
    - SPRT parameters: H₀ ≥ 0.95, H₁ ≤ 0.85, α = 0.05, β = 0.05
    - If SPRT accepts: commit the reorganized document
    - If SPRT rejects: revert to the pre-consolidation version and report which test cases failed, with a summary of
      where the organized document introduced ambiguity or regression
    - Add to the existing compliance checklist in instruction-organizer-agent

## Post-conditions

### Terminology
- [ ] No occurrences of `eval-artifacts`, `EVAL_ARTIFACTS_DIR`, `EVAL_SET_SHA`, or `eval:` commit prefix in
  `plugin/skills/instruction-builder-agent/first-use.md`
- [ ] No occurrences of `eval-artifacts` or `eval:` in `plugin/skills/instruction-builder-agent/e2e-dispute-trace.md`
- [ ] All renamed terms use `benchmark-artifacts`, `BENCHMARK_ARTIFACTS_DIR`, `BENCHMARK_SET_SHA`, `benchmark:`

### SPRT benchmarking
- [ ] Benchmark runs use Haiku model for eval-run subagents
- [ ] Each benchmark run uses a fresh (non-resumed) subagent
- [ ] SPRT decision logic implemented with parameters: H₀ ≥ 0.95, H₁ ≤ 0.85, α = 0.05, β = 0.05
- [ ] Assertion format supports both deterministic (machine-checkable) and semantic (LLM-graded) types
- [ ] Deterministic assertions are graded inline without subagent overhead
- [ ] Semantic assertions use Haiku grader subagents
- [ ] Skill-builder maximizes deterministic-to-semantic assertion ratio when generating test cases
- [ ] SPRT check runs after each individual agent completion (pipelined), not batched per wave
- [ ] Each test case runs its own independent SPRT; rejection of any case stops all remaining cases
- [ ] Run outputs are written to temp files, not committed per-run; only benchmark.json is committed
- [ ] Assertion JSON schema defined with deterministic and semantic types per Appendix E
- [ ] Eval-run subagent return format includes per-assertion pass/fail with null for semantic assertions
- [ ] Re-benchmark after compression uses identical SPRT acceptance criteria as post-hardening benchmark

### Test case generation
- [ ] Semantic extraction produces testable units from skill file
- [ ] Each behaviorally testable unit (requirement, prohibition, conditional, sequence) generates a test case
- [ ] Non-testable units (reference, conjunction) are skipped
- [ ] Auto-generated assertions prefer deterministic type; use semantic type only when behavior requires judgment
- [ ] Skill-builder can add, remove, or refine auto-generated test cases before benchmarking
- [ ] Benchmark coverage is proportional to skill instruction surface area, not fixed at 2-3 manual cases
- [ ] Semantic extraction uses embedded Nine-Category algorithm (not subagent invocation)
- [ ] Extracted units include id, category, original, normalized, quote, and location fields

### Compression phase
- [ ] After hardening achieves compliance, compression phase runs to minimize skill file size
- [ ] Compressed version is re-benchmarked via SPRT before acceptance
- [ ] If compression breaks compliance, load-bearing text is marked as protected and compression retries
- [ ] Compression retries capped at 3 attempts
- [ ] Semantic pre-check gates compression before full SPRT re-benchmark
- [ ] Protected text identification cross-references SPRT failure data with diff hunks via semantic unit location

### Skill consolidation
- [ ] `compression-protocol.md` created in `plugin/skills/instruction-builder-agent/` with algorithm from compression-agent
- [ ] `validation-protocol.md` created in `plugin/skills/instruction-builder-agent/` with algorithm from compare-docs
- [ ] `plugin/skills/optimize-doc/` deleted entirely
- [ ] `plugin/skills/compare-docs-agent/` deleted entirely
- [ ] `plugin/agents/compression-agent.md` deleted entirely
- [ ] References to optimize-doc, compare-docs, and compression-agent removed from hook registrations and skill lists

### Workflow constraints
- [ ] Hardening + benchmarking + compression phase only runs when effort > low (skipped entirely at effort = low)
- [ ] Hardening, benchmarking, and compression are always run together — never one without the others
- [ ] Hardening and compression are never interleaved — harden first (add), then compress (remove)
- [ ] Compliance checklist updated to reflect all changes

### Rename
- [ ] `plugin/skills/skill-builder-agent/` directory no longer exists; replaced by
  `plugin/skills/instruction-builder-agent/`
- [ ] `plugin/skills/consolidate-doc-agent/` directory no longer exists; replaced by
  `plugin/skills/instruction-organizer-agent/`
- [ ] No remaining occurrences of `skill-builder-agent` anywhere in `plugin/` (verified by grep)
- [ ] No remaining occurrences of `consolidate-doc-agent` anywhere in `plugin/` (verified by grep)
- [ ] SKILL.md descriptions for both skills reference "agent instructions" terminology

### instruction-organizer-agent SPRT integration
- [ ] `plugin/skills/instruction-organizer-agent/first-use.md` contains a Phase 4 Quality Verification section
  that references `validation-protocol.md` from instruction-builder-agent
- [ ] instruction-organizer-agent Phase 4 rejects and reverts when SPRT compliance falls below threshold
- [ ] Phase 4 SPRT uses same parameters: H₀ ≥ 0.95, H₁ ≤ 0.85, α = 0.05, β = 0.05

## Fix Steps (Iteration 1)

29. In `plugin/skills/skill-builder-agent/first-use.md`, remove all checklist entries that reference `eval-artifacts`,
    `EVAL_ARTIFACTS_DIR`, `EVAL_SET_SHA`, or `eval:` as negative examples (e.g. lines containing "not eval:" or
    "uses benchmark: (not eval:)"). The compliance checklist must contain no occurrences of these old terms in any
    form — neither as positive nor negative references.

30. Delete the entire `plugin/skills/optimize-doc-agent/` directory, including the remaining `SKILL.md` stub file.
    The directory must not exist at all after this step.

31. In each of the following files, remove all references to `optimize-doc`, `compare-docs`, and `compression-agent`
    (including skill invocations, skill list entries, and instructional mentions):
    - `plugin/skills/consolidate-doc-agent/first-use.md`
    - `plugin/skills/learn/phase-prevent.md`
    - `plugin/skills/learn/documentation-priming.md`
    - `plugin/skills/work-implement-agent/first-use.md`

## Fix Steps (Iteration 2)

32. In `plugin/skills/skill-builder-agent/first-use.md`, rename all 6 occurrences of the prose phrase "eval-run subagent"
    to "benchmark-run subagent". These occur at lines: 186, 308, 318, 319, 331, 334, 342. Each occurrence is part of a
    sentence describing the subagent type and must be changed to use consistent terminology with the renamed system.

33. In `plugin/skills/skill-builder-agent/first-use.md`, fix the stale step cross-reference at line 186. Change the text
    "Proceed directly to Step 5 (Output Format)" to "Proceed directly to ## Output Format". Step 5 refers to the
    in-place hardening mode (line 268), while "Output Format" is an unnumbered section at line 645. The effort gate
    fallback should reference the correct output destination section.

## Appendix A: Test Case Auto-Generation Algorithm

### Testability Classification for All 9 Semantic Categories

| Category | Testable? | Rationale | Assertion Strategy |
|----------|-----------|-----------|-------------------|
| REQUIREMENT | Yes | Core behavior to verify | Deterministic if output is structural; semantic if judgment needed |
| PROHIBITION | Yes | Verify forbidden action is not taken | Deterministic: output must NOT match prohibited pattern |
| CONDITIONAL | Yes | Test both branches (condition met / not met) | Two test cases per conditional; deterministic where possible |
| SEQUENCE | Yes | Verify ordering constraint | Deterministic: check output order matches required sequence |
| DEPENDENCY | Yes | Verify Y fails or degrades without X | Two test cases: with dependency met, with dependency absent |
| EXCLUSION | Yes | Verify mutual exclusivity | Test case that attempts both; assert only one succeeds |
| CONSEQUENCE | Yes | Verify X triggers Y | Deterministic if consequence is observable; semantic if indirect |
| CONJUNCTION | No | ALL-of requirements — tested via individual REQUIREMENT units | Skip (covered by constituent requirements) |
| REFERENCE | No | Cross-document pointer — no behavior to test | Skip |

### Test Case Prompt Generation

For each testable unit, the skill-builder generates a test case using this template:

1. **Extract the constraint** from the semantic unit's `original` text
2. **Design a scenario** that exercises the constraint:
   - For REQUIREMENT: scenario where the requirement should be applied
   - For PROHIBITION: scenario where the forbidden action is tempting but must be avoided
   - For CONDITIONAL: two scenarios — one triggering the condition, one not
   - For SEQUENCE: scenario requiring multiple ordered steps
   - For DEPENDENCY: scenario with dependency present, scenario with dependency absent
   - For EXCLUSION: scenario attempting both mutually exclusive options
   - For CONSEQUENCE: scenario triggering the cause, assert the effect occurs
3. **Generate assertions**:
   - Prefer deterministic: regex match, string containment, structural check (e.g., "output contains table with 4
     columns")
   - Fall back to semantic: when the expected behavior requires judgment (e.g., "explanation correctly identifies the
     root cause")
   - Each test case must have at least one assertion

### Assertion Type Decision Heuristic

Use deterministic when the expected output has a concrete, observable property:
- Output format (table, list, JSON structure)
- Presence/absence of specific strings or patterns
- File operations (created file X, did not modify file Y)
- Ordering (step A appears before step B in output)

Use semantic when the expected behavior requires understanding:
- Correctness of explanations or analysis
- Appropriateness of chosen approach
- Quality of generated code beyond syntax

**Borderline resolution:** When uncertain whether an assertion should be deterministic or semantic, prefer
deterministic IF the expected output is amenable to structural checking (size, format, presence of keywords). Fall
back to semantic only if the property is genuinely subjective (tone, appropriateness, clarity). When in doubt, start
with deterministic; if the assertion always passes regardless of skill presence (non-discriminating), upgrade to
semantic in subsequent iterations.

### Quality Gate

After auto-generation, the skill-builder presents generated test cases to the user for approval before benchmarking.
The user may add, remove, or modify test cases. Auto-generated cases that are non-discriminating (pass equally with
and without the skill) are flagged by the benchmark analyzer for removal in subsequent iterations.

## Appendix B: SPRT Implementation Specification

### Likelihood Ratio Formula

For a sequence of n independent Bernoulli trials (pass=1, fail=0), the log-likelihood ratio after observation k is:

```
If observation k is PASS:
  log_ratio += log(p0 / p1)

If observation k is FAIL:
  log_ratio += log((1 - p0) / (1 - p1))
```

Where:
- p0 = 0.95 (pass rate under H₀ — skill is compliant)
- p1 = 0.85 (pass rate under H₁ — skill is non-compliant)
- log_ratio starts at 0

### Decision Boundaries

```
A = log((1 - β) / α) = log(0.95 / 0.05) = log(19) ≈ 2.944
B = log(β / (1 - α)) = log(0.05 / 0.95) = log(0.0526) ≈ -2.944
```

After each observation:
- If log_ratio ≥ A → **Accept H₀** (skill is compliant, stop testing)
- If log_ratio ≤ B → **Reject H₀** (skill is non-compliant, proceed to hardening)
- If B < log_ratio < A → **Inconclusive** (continue testing)

### Mixed Assertion Aggregation

A single benchmark run produces multiple assertion results (deterministic + semantic). Aggregation rule:

- A run **passes** if and only if ALL assertions pass (deterministic and semantic)
- A single failed assertion (of any type) fails the entire run
- The SPRT receives a single pass/fail per run, not per assertion

### Multiple Comparisons

Each test case runs its own independent SPRT. With k test cases at α=0.05 each, the family-wise error rate is
1 - (1-α)^k. For k=3, this is ~14%.

This is acceptable because:
- False acceptance (concluding compliant when not) is the risk — at 14%, this means ~14% chance of accepting a
  non-compliant skill on any given test case
- Early rejection mitigates: if any case rejects, all stop, so the family-wise rejection sensitivity is higher
- Bonferroni correction (α/k per test) would require ~3x more samples, which is disproportionate cost for marginal
  accuracy

If future experience shows false acceptance is a problem, apply Bonferroni correction by using α' = α/k per test case.

### Pipelining Control Flow

1. Main agent spawns up to 4 eval-run subagents in parallel
2. As each subagent completes, main agent immediately:
   a. Grades deterministic assertions from the subagent's return value
   b. If semantic assertions exist, spawns a Haiku grader (counts against the 4-agent limit)
   c. Once all assertions for the run are graded, updates the SPRT log_ratio for that test case
   d. Checks boundaries: if Accept or Reject, stops spawning new subagents for that test case
3. If any test case rejects, all remaining test cases stop immediately
4. Freed agent slots are used to spawn runs for test cases that are still inconclusive
5. Loop terminates when all test cases have accepted or any test case has rejected

## Appendix C: Protected Text Tracking

### Identification via Git Diff and SPRT Failure Analysis

When a compressed version fails SPRT re-benchmark:

1. Let `SHA_PASS` = the last committed version that passed SPRT (post-hardening, pre-compression)
2. Let `SHA_FAIL` = the compressed version that failed SPRT
3. Run `git diff SHA_PASS SHA_FAIL -- <skill-file-path>` to identify removed/changed hunks
4. Identify which test case(s) triggered the SPRT rejection and which assertions failed
5. Cross-reference failed assertions with their source semantic units (`semantic_unit_id` in test-cases.json)
6. Cross-reference source semantic units with the diff hunks (via `location` field in the extracted unit)
7. Mark only the hunks that correspond to failed semantic units as protected (targeted, not conservative)
8. If cross-referencing is ambiguous (multiple hunks could explain the failure), mark all candidate hunks as protected
   for this retry; narrow on subsequent retries based on new failure data

### Constraint File Format

Create `benchmark-artifacts/<SESSION_ID>/protected-sections.txt` with one entry per protected hunk:

```
## Protected Section 1
Reason: Compression removed this text and SPRT compliance dropped from ACCEPT to REJECT
Original location: lines 45-52
Text:
> [verbatim text that was removed]

## Protected Section 2
...
```

### Compression Agent Integration

On retry, pass the constraint file to the compression agent:

```
RESTRICTION: The following text sections are load-bearing and must NOT be removed, rephrased,
or merged with other content. They were identified by SPRT re-benchmarking as necessary for
compliance. See protected-sections.txt for the full list.
```

The compression agent reads the constraint file and treats listed text as mandatory preservation targets (same
category as "decision-affecting requirements" in the compression protocol).

### Retry Strategy

- Retry 1: Compress with protected sections from the first failure
- Retry 2: If still failing, diff again to find additional load-bearing text, add to protected sections
- Retry 3: Final attempt with all accumulated protected sections
- After 3 failures: Accept the post-hardening (uncompressed) version as final, report compression was not possible
  without compliance loss

## Appendix D: Phase State Diagram

### File State at Each Phase

| Phase | Source of Truth | Committed? | Commit Message |
|-------|----------------|------------|----------------|
| Design draft | `<skill-path>/SKILL.md` or `first-use.md` | Yes | `benchmark: write skill draft [session: ...]` |
| Test case generation | `benchmark-artifacts/<SID>/test-cases.json` | Yes | `benchmark: generate test cases [session: ...]` |
| Test case approval | `benchmark-artifacts/<SID>/test-cases.json` (updated) | Yes | `benchmark: approve test cases [session: ...]` |
| SPRT benchmark | `benchmark-artifacts/<SID>/benchmark.json` | Yes (final only) | `benchmark: SPRT result [session: ...]` |
| Hardening (per round) | Skill file at `<skill-path>` | Yes (per round) | Per adversarial TDD protocol |
| Post-hardening benchmark | `benchmark-artifacts/<SID>/benchmark.json` | Yes | `benchmark: post-hardening SPRT [session: ...]` |
| Compression | `benchmark-artifacts/<SID>/compressed-<skill>.md` | Yes | `benchmark: compress skill [session: ...]` |
| Post-compression benchmark | `benchmark-artifacts/<SID>/benchmark.json` | Yes | `benchmark: post-compression SPRT [session: ...]` |
| Final acceptance | Skill file at `<skill-path>` (compressed version) | Yes | `benchmark: accept final [session: ...]` |
| Rebase | All commits replayed onto target branch | Yes | N/A (rebase, not new commit) |

### Effort Gate

The effort gate is checked once, before the hardening + benchmarking + compression phase begins:

- `effort = low` → Skip entire phase. Output is the design draft with a single-run sanity check (existing behavior).
- `effort = medium` or `high` → Run full phase: benchmark → harden → re-benchmark → compress → re-benchmark.

No distinction between `medium` and `high` for this phase (both run the full loop).

### User Interaction Points

| Point | When | What |
|-------|------|------|
| Test case approval | After auto-generation, before first SPRT | User reviews, adds, removes, or modifies test cases |
| Post-hardening review | After hardening converges and SPRT accepts | User reviews hardening changes before compression |
| Compression result | After compression + re-benchmark | User sees final size reduction and compliance metrics |
| Compression failure | If all 3 compression retries fail | User informed; uncompressed version accepted as final |

### Commit Strategy

- **Ephemeral (no commit):** Individual eval-run outputs, grading intermediaries
- **Committed per occurrence:** Test case file, benchmark.json (overwritten each SPRT pass), hardening rounds,
  compressed skill, protected-sections.txt
- **Final commit:** Accepted skill file overwrites the original at `<skill-path>`
