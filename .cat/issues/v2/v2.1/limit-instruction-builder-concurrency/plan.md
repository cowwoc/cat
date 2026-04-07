# Plan

## Goal

Limit the number of parallel subagents spawned by instruction-builder-agent to `nproc` (number of CPU
cores), with a fallback to 8 if `nproc` is unavailable or returns an unexpected value. Prevents resource
exhaustion when many subagents run simultaneously on machines with fewer cores.

## Pre-conditions

(none)

## Post-conditions

- [ ] instruction-builder-agent detects the number of CPU cores at runtime using `nproc`
- [ ] The number of concurrently spawned subagents never exceeds the detected `nproc` value
- [ ] When `nproc` is unavailable or returns ≤ 0, the agent falls back to a default of 8
- [ ] Tests verify the concurrency cap is applied
- [ ] No regressions in existing instruction-builder-agent functionality
- [ ] E2E verification: running instruction-builder-agent on a multi-step issue respects the `nproc` cap

## Research Findings

The concurrency cap lives entirely in `plugin/skills/instruction-builder-agent/first-use.md` in the
"Pipeline Control Flow" section (approx. line 843). The relevant text currently reads:

```
Track `WAVE_SLOTS` (initial value: 2, maximum: 16). After each wave where every run passed, double
`WAVE_SLOTS`: `WAVE_SLOTS = min(WAVE_SLOTS * 2, 16)`.
```

The hardcoded cap of `16` appears three times:
1. `maximum: 16` in the prose description of WAVE_SLOTS
2. `min(WAVE_SLOTS * 2, 16)` in the doubling formula description
3. `WAVE_SLOTS = min(WAVE_SLOTS * 2, 16)` in the wave result handling (line 872)

The fix: detect `nproc` once before the wave loop starts, store as `MAX_WAVE_SLOTS`, then replace all
three `16` occurrences with `MAX_WAVE_SLOTS`.

The `nproc` command is a standard Linux/macOS utility and is available in the plugin runtime environment.
It prints the number of processing units (CPU cores/threads) available.

Fallback logic:
- `nproc 2>/dev/null` returns empty on failure
- A successful result may be non-numeric if something unexpected happens
- Guard: `[[ ! "$MAX_WAVE_SLOTS" =~ ^[0-9]+$ ]] || [[ "$MAX_WAVE_SLOTS" -le 0 ]]` → use 8

## Jobs

### Job 1

- Update `plugin/skills/instruction-builder-agent/first-use.md`:
  1. In the "Pipeline Control Flow" section, immediately before the `Track \`WAVE_SLOTS\`` paragraph,
     add a `MAX_WAVE_SLOTS` detection block:

     ```
     Before starting wave dispatch, detect the concurrency cap from the host CPU count:

     ```bash
     MAX_WAVE_SLOTS=$(nproc 2>/dev/null)
     if [[ ! "$MAX_WAVE_SLOTS" =~ ^[0-9]+$ ]] || [[ "$MAX_WAVE_SLOTS" -le 0 ]]; then
       MAX_WAVE_SLOTS=8
     fi
     ```

     This sets `MAX_WAVE_SLOTS` to the number of CPU cores reported by `nproc`, or 8 if `nproc` is
     unavailable or returns a non-positive value.
     ```

  2. Change `Track \`WAVE_SLOTS\` (initial value: 2, maximum: 16).` →
     `Track \`WAVE_SLOTS\` (initial value: 2, maximum: \`MAX_WAVE_SLOTS\`).`

  3. Change `\`WAVE_SLOTS = min(WAVE_SLOTS * 2, 16)\`` (first occurrence, in the prose description) →
     `\`WAVE_SLOTS = min(WAVE_SLOTS * 2, MAX_WAVE_SLOTS)\``

  4. Change `WAVE_SLOTS = min(WAVE_SLOTS * 2, 16)` (second occurrence, in wave result handling step 3) →
     `WAVE_SLOTS = min(WAVE_SLOTS * 2, MAX_WAVE_SLOTS)`

- Add test case
  `plugin/tests/skills/instruction-builder-agent/first-use/wave-slots-nproc-cap.md`
  with this exact content:

  ```markdown
  ---
  category: REQUIREMENT
  ---
  ## Turn 1

  Please create a new skill called `activity-logger` that logs timestamped user messages.
  The SPRT phase is about to begin. Describe the Pipeline Control Flow for wave dispatch,
  including how you determine the maximum number of concurrent subagents.

  ## Assertions

  1. The Skill tool was invoked
  2. The agent runs `nproc` (or equivalent) to detect the CPU core count before the wave loop
  3. The agent stores the result in `MAX_WAVE_SLOTS` (or equivalent), not a hardcoded number
  4. The doubling formula uses `MAX_WAVE_SLOTS` as the cap (not the literal value `16`)
  ```

- Add test case
  `plugin/tests/skills/instruction-builder-agent/first-use/wave-slots-fallback-to-8.md`
  with this exact content:

  ```markdown
  ---
  category: CONDITIONAL
  ---
  ## Turn 1

  Please create a new skill called `activity-logger` that logs timestamped user messages.
  The SPRT phase is about to begin. `nproc` is not available on this machine (it returns
  empty output). What value does `MAX_WAVE_SLOTS` take and why?

  ## Assertions

  1. The Skill tool was invoked
  2. The agent sets `MAX_WAVE_SLOTS` to `8` because `nproc` returned empty output
  3. The agent does not use the literal value `16` as the concurrency cap
  ```

- Update `index.json` in the same commit: set `"status": "closed"`, `"progress": 100`.

- Commit type: `feature:`
