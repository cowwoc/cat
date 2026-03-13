<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Skill Writing Conventions

This document describes Step 7 (writing the skill), working examples, complex cases, preprocessing directives, and
priming prevention checklists.

---

## Step 7: Write the Skill

Structure the skill document with:

1. **Frontmatter**: YAML with name and trigger-oriented description
2. **Purpose**: The goal statement
3. **Functions**: Reusable calculations extracted in Step 5
4. **Procedure**: The forward steps from Step 6, calling functions as needed
5. **Verification**: How to confirm success

**Frontmatter description must be trigger-oriented**:

The description is used for **intent routing** — it tells Claude WHEN to invoke this skill based
on user input. Include ONLY trigger conditions and synonyms. Exclude implementation details
(trust levels, auto-continue behavior, internal architecture, etc.).

**Descriptions must start with an imperative keyword** from the trigger patterns below. Do NOT start
with bare "When..." — it's passive and doesn't instruct the agent to act. Always use "Use when..."
instead.

**Trigger patterns** (start descriptions with one of these):
- `MANDATORY:` - Required for specific scenarios (e.g., "MANDATORY: Use instead of `git rebase`")
- `PREFER` - Recommended over alternatives (e.g., "PREFER when reading 3+ related files")
- `Use when` - Triggered by a condition (e.g., "Use when session crashed or locks blocking")
- `Use BEFORE` - Should be loaded before an action (e.g., "Use BEFORE creating or updating any skill")
- `Use after` - Post-action skill (e.g., "Use after complex issues to analyze session efficiency")
- `Use instead of` - Replaces a dangerous operation (e.g., "Use instead of `git commit --amend`")
- `Internal` - For non-user-facing skills (e.g., "Internal (invoked by /cat:work) - merge phase")

```
Format: "<trigger-pattern> [condition] - [what it does briefly]"

Good examples:
  "Use when user says work, resume, continue, or pick up - start or resume issue work"
  "Use BEFORE creating or updating any skill - decomposes goal into forward steps"
  "Use when session crashed or locks blocking - cleans abandoned worktrees and locks"
  "PREFER when reading 3+ related files - batch operation eliminates round-trips"
  "MANDATORY: Use instead of `git rebase` - provides automatic backup and conflict recovery"

Bad examples:
  "When an issue is too large..."        ← passive, use "Use when" instead
  "Test-Driven Development workflow..."  ← describes what, not when to use
  "Merge branch (uses --ff-only)"        ← leaks implementation details
```

**Include user synonyms**: If users might say "resume" instead of "work on", include both
in the description so intent routing matches correctly.

**Frontmatter defaults — only set non-default values**:

| Field | Default | Only set when |
|-------|---------|---------------|
| `user-invocable` | `true` | Set to `false` for internal-only skills |
| `allowed-tools` | all tools | Set to restrict available tools |
| `model` | inherited | Set to override (e.g., `haiku` for simple skills) |
| `context` | main agent | Set to `fork` to run in isolated sub-agent |

Do NOT add fields set to their default value — it adds noise and obscures intentional overrides.

**Positional arguments (MANDATORY)**: Skills that reference `$0`...`$N` or `$ARGUMENTS` in their
content MUST specify `argument-hint` in frontmatter. See `concepts/skill-loading.md`
§ Skill Arguments for full argument-hint syntax, quoting rules, and variable substitution.

**catAgentId requirement**: If `user-invocable: false` AND the preprocessor directive uses
`get-skill` (with `$ARGUMENTS` or fixed `$N` positional refs), then `argument-hint` MUST
start with `<catAgentId>`. Omitting it causes runtime failures:
`catAgentId '<first-arg>' does not match a valid format`.

- [ ] If `user-invocable: false` and skill uses `get-skill "$ARGUMENTS"`: argument-hint
      starts with `<catAgentId>`
- [ ] If `user-invocable: false` and skill uses `get-skill` with fixed `$N` refs (e.g.,
      `!get-skill <name> "$0" "$1"`): argument-hint starts with `<catAgentId>`

**Positional argument completeness**: If the preprocessor directive references `$0`...`$N`,
`argument-hint` MUST document ALL positional args (including `$0`). Every `$N` reference
must have a corresponding token in `argument-hint`.

- [ ] Count of tokens in argument-hint matches the highest `$N` reference + 1
- [ ] Each positional arg has a descriptive name (e.g., `<catAgentId>`, `<issue-path>`)

---

## Skill Structure Template

```markdown
---
name: [skill-name]
description: "[WHEN to use] - [what it does]"
---

# [Skill Name]

## Purpose

[Goal statement from Step 1]

---

## Prerequisites

[Any atomic conditions that are external inputs or assumptions]

---

## Procedure

### Step 1: [For skills with preprocessed output]

([BANG] = the exclamation mark, written as placeholder to avoid preprocessor expansion.)

# Direct preprocessing pattern:
[BANG]`render-output.sh`

# Or delegated preprocessing pattern (if LLM determines data):
Analyze context and invoke renderer skill with args.

### Step 2: [Gather inputs] (only if no preprocessing)

[Collect data needed by functions]

### Step 3: [Apply function]

Call `function_name(inputs)` to compute [result].

**MANDATORY CALCULATION GATE:**

Before proceeding, you MUST show:

1. **List each item with its computed value:**
   ```
   [item]: [computation] = [result]
   ```

2. **State aggregate if applicable:**
   ```
   [aggregate] = [value]
   ```

**BLOCKING:** Do NOT proceed until calculations are written out.

### Step N: [Final assembly / output]

[Combine results to achieve goal]

---

## Verification

- [ ] [Checkable condition that confirms goal is met]
```

---

## Example 1: Simple Skill (Rectangle Calculator)

**Issue**: Create a skill for calculating the area of a rectangle from user input.

### Step 1: Goal

```
GOAL: Output displays the correct area of the rectangle
```

### Step 2: Decomposition

```
GOAL: Output displays correct area
  REQUIRES: area value is correct
    REQUIRES: area = width × height
      REQUIRES: width is a valid number
        REQUIRES: width input is parsed
          ATOMIC: Read width from user
        REQUIRES: parsing succeeds
          ATOMIC: Validate width is numeric
      REQUIRES: height is a valid number
        REQUIRES: height input is parsed
          ATOMIC: Read height from user
        REQUIRES: parsing succeeds
          ATOMIC: Validate height is numeric
  REQUIRES: output is displayed
    ATOMIC: Print result to screen
```

### Step 3: Leaf Nodes

```
1. Read width from user
2. Validate width is numeric
3. Read height from user
4. Validate height is numeric
5. Print result to screen
```

### Step 4: Dependency Order

```
Level 0: Read width, Read height
Level 1: Validate width (needs width), Validate height (needs height)
Level 2: Calculate area = width × height (needs both validated)
Level 3: Print result (needs area)
```

### Step 5: Extract Functions

```
FUNCTIONS:
  validate_number(input) → number or error
    Purpose: Parse and validate numeric input
    Logic: Parse input; if not positive number, return error
    Used by: width validation, height validation

Note: This function is identified because validation logic appears twice
(for width and height) with identical structure.
```

### Step 6: Forward Steps

```
1. Read width from user
2. Call validate_number(width); abort if error
3. Read height from user
4. Call validate_number(height); abort if error
5. Calculate area = width × height
6. Print "Area: {area}"
```

### Step 7: Resulting Skill

```markdown
# Rectangle Area Calculator

## Purpose

Output displays the correct area of the rectangle.

## Functions

### validate_number(input) → number or error

Parse input and validate it is a positive number.

```
parsed = parse_as_number(input)
if parsed is NaN or parsed <= 0:
  return error("Must be a positive number")
return parsed
```

## Procedure

### Step 1: Get width
Read width value from user input.

### Step 2: Validate width
Call `validate_number(width)`. If error, display message and stop.

### Step 3: Get height
Read height value from user input.

### Step 4: Validate height
Call `validate_number(height)`. If error, display message and stop.

### Step 5: Calculate
Compute area = width × height.

### Step 6: Display
Output "Area: {area}".

## Verification

- [ ] Output matches expected area for test inputs
```

---

## Example 2: Function Extraction (Box Alignment)

**Issue**: Create a skill for rendering aligned boxes with emoji support.

### Step 1: Goal

```
GOAL: All right-side │ characters align vertically
```

### Step 2: Decomposition

```
GOAL: Right borders align
  REQUIRES: All lines have identical display width
    REQUIRES: line_width = content_width + padding + 4 (borders)
      REQUIRES: padding = max_content_width - content_width
        REQUIRES: max_content_width is known
          REQUIRES: display_width calculated for ALL content items  ← Repeated operation
            REQUIRES: emoji widths handled correctly
              ATOMIC: Use lib/emoji_widths.py lookup
        REQUIRES: content_width is known for THIS item
          REQUIRES: display_width calculated for this item          ← Same as above!
      REQUIRES: borders are fixed width (4)
        ATOMIC: Use "│ " prefix + " │" suffix
```

### Step 3: Leaf Nodes

```
1. Width lookup via lib/emoji_widths.py
2. Border constants ("│ " = 2, " │" = 2)
```

### Step 4: Dependency Order

```
Level 0: Width lookup table, border constants
Level 1: display_width for each content item (uses lookup)
Level 2: max_content_width (uses all display_widths)
Level 3: padding for each item (uses max and item width)
Level 4: construct each line (uses padding)
Level 5: assemble box (uses all lines)
```

### Step 5: Extract Functions

```
FUNCTIONS:
  display_width(text) → integer
    Purpose: Calculate terminal display width
    Logic: sum(2 if char is emoji else 1 for char in text)
    Used by: max_content_width, padding calculation

  max_content_width(contents[]) → integer
    Purpose: Find widest content item
    Logic: max(display_width(c) for c in contents)
    Used by: padding calculation, border construction

  box_width(contents[]) → integer
    Purpose: Total box width including borders
    Logic: max_content_width(contents) + 4
    Used by: border construction
```

### Step 6: Forward Steps

```
1. List all content items
2. For each item: call display_width(item)
3. Call max_content_width(contents) to get max
4. For each item: padding = max - display_width(item)
5. Construct each line: "│ " + content + " "×padding + " │"
6. Construct top: "╭" + "─"×(max+2) + "╮"
7. Construct bottom: "╰" + "─"×(max+2) + "╯"
8. Assemble: [top] + lines + [bottom]
```

### Step 7: Resulting Skill

```markdown
# Box Alignment

## Purpose

All right-side │ characters align vertically.

## Functions

### display_width(text) → integer

Calculate terminal display width of a string.

```
Use lib/emoji_widths.py lookup for terminal-specific emoji widths.
The library handles variation selectors and terminal detection automatically.
```

### max_content_width(contents[]) → integer

Find maximum display width among all content items.

```
return max(display_width(c) for c in contents)
```

### box_width(contents[]) → integer

Calculate total box width including borders.

```
return max_content_width(contents) + 4
```

## Procedure

### Step 1: List content

Identify all strings that will appear in the box.

### Step 2: Calculate widths

For each content item, call `display_width(item)`.

### Step 3: Find maximum

Call `max_content_width(contents)`.

### Step 4: Construct lines

For each content:
  padding = max - display_width(content)
  line = "│ " + content + " "×padding + " │"

### Step 5: Construct borders

top = "╭" + "─"×(max+2) + "╮"
bottom = "╰" + "─"×(max+2) + "╯"

### Step 6: Assemble

Output: [top] + [all lines] + [bottom]

## Verification

- [ ] All right │ characters are in the same column
```

---

## Handling Complex Cases

### Multiple Paths (OR conditions)

When a condition can be satisfied by alternative approaches:

```
CONDITION: User is authenticated
  OPTION A:
    REQUIRES: Valid session token exists
  OPTION B:
    REQUIRES: Valid API key provided
```

**IMPORTANT - Fail-Fast, Not Fallback:**

When designing skills with multiple paths, distinguish between:

1. **Legitimate alternatives** (user choice): Present options for user to select
2. **Fallback patterns** (degraded operation): **AVOID** - these hide failures

```
# BAD - Fallback hides preprocessing failure
If preprocessing ran: output result
Else: compute manually (error-prone!)

# GOOD - Fail-fast exposes problems
Preprocessing via [BANG]`script.sh` runs automatically.
If script fails, skill expansion fails visibly.
```

**Why preprocessing is better than manual fallback:**
- Script failures are visible (no silent degradation)
- No fallback path means no error-prone manual computation
- Forces fixing the root cause (broken script) rather than masking it

### Script Extraction: Deterministic Bash Must Be External

**Skills must not contain inline bash for deterministic operations** — because inline bash in skill
markdown gets read by Claude, who then generates nearly identical bash via tool calls rather than
invoking the script directly. This doubles the token cost and introduces transcription errors. External
scripts are executed once, produce verified output, and keep the skill focused on intent rather than mechanics.

All deterministic bash belongs in external script files (plugin/scripts/). Skills contain only:
- **When to use**: Conditions and prerequisites for the operation
- **Script invocation**: Single bash command to invoke the script
- **Result handling table**: JSON status codes → meaning → agent recovery action
- **Judgment-dependent guidance**: Conflict resolution strategy, commit message construction, error recovery decisions

Scripts (bash in plugin/scripts/) handle:
- All deterministic git/file/system operations
- Input validation and fail-fast error reporting
- Structured JSON output for both success and error cases
- Backup creation and verification

#### Architecture: Hybrid Workflow

```
┌─────────────────────┐     ┌──────────────────────┐
│   Skill (markdown)  │     │   Script (bash/py)   │
│                     │     │                      │
│ • When to use       │────>│ • Deterministic ops  │
│ • Invoke script     │     │ • JSON output        │
│ • Handle results    │<────│ • Fail-fast errors   │
│ • Recovery guidance │     │ • Backup/verify      │
└─────────────────────┘     └──────────────────────┘
```

#### When to Extract

Extract to a script when:
- Skill section has 3+ sequential bash operations that always execute the same logic
- Operations have no judgment decisions (no branching based on Claude's analysis)
- Claude reads bash from markdown then generates nearly identical bash via tool calls

#### When NOT to Extract

Keep in skill markdown when:
- Operations require Claude's judgment (conflict resolution, commit message construction from topic analysis)
- Interactive workflows need user input mid-stream
- One-off operations that won't be repeated

#### Script Conventions

- Location: `plugin/scripts/<skill-name>.sh` or `plugin/scripts/<operation>.sh`
- Header: `#!/usr/bin/env bash` with `set -euo pipefail`
- Arguments: positional or from environment, with fail-fast validation
- Output: JSON to stdout, errors to stderr
- Exit codes: 0 success, 1 failure
- Invocation from skill: `"$(git rev-parse --show-toplevel)/plugin/scripts/<name>.sh" "$ARG1" "$ARG2"`

#### Result Handling Table Pattern

Skills should include a table mapping script JSON status codes to agent actions:

```markdown
| Status | Meaning | Agent Recovery Action |
|--------|---------|----------------------|
| OK | Operation succeeded | Continue workflow |
| CONFLICT | Merge/rebase conflict | Examine files, decide resolution strategy |
| DIVERGED | Target branch advanced | Rebase onto target branch, retry |
| ERROR | Unexpected failure | Read message, report to user |
```

### Shared Dependencies

When multiple branches require the same condition, note it once and reference it:

```
REQUIRES: display_width calculated for all items
  (see: emoji width calculation above)
```

### Verification Gates (M191 Prevention)

When a skill has steps that produce intermediate calculations or data that the final
output depends on, add **verification gates** that require showing the intermediate
work before proceeding.

**Why gates matter**: Without explicit gates, agents may:
- Mentally acknowledge a step without executing it
- Write approximate output instead of calculated output
- Skip straight to the final result, causing errors

**Identify gate candidates during decomposition**:
```
GOAL: Output is correct
  REQUIRES: Final output uses calculated values    ← Gate candidate
    REQUIRES: Intermediate values are computed
      REQUIRES: Input data is collected
```

When the decomposition shows a REQUIRES that transforms data (calculates, computes,
derives), that transformation should have a gate that makes the result visible.

**Gate format**:
```markdown
**MANDATORY CALCULATION GATE (reference):**

Before proceeding to [next step], you MUST show explicit [calculations/results]:

1. **List each [item] with its [derived value]:**
   ```
   [item1]: [explicit breakdown] = [result]
   [item2]: [explicit breakdown] = [result]
   ```

2. **State the [aggregate value]:**
   ```
   [aggregate_name] = [value] (from [derivation])
   ```

**BLOCKING:** Do NOT [produce output] until these [calculations/results] are written out.
[Explanation of what goes wrong if skipped].
```

**Gate placement in procedure**:
- Place gates AFTER the calculation step, BEFORE the step that uses the results
- Use "MANDATORY" and "BLOCKING" keywords
- Reference a mistake ID if the gate prevents a known issue

**Example - Box alignment gate**:
```markdown
### Step 3: Calculate maximum width

Call `max_content_width(all_content_items)`.

**MANDATORY CALCULATION GATE:**

Before proceeding to Step 4, you MUST show explicit width calculations:

1. **List each content item with its display_width:**
   ```
   "Hello 👋": 7 chars + 1 emoji(2) = 8
   "World":    5 chars = 5
   ```

2. **State max_content_width:**
   ```
   max_content_width = 8
   ```

**BLOCKING:** Do NOT render any box output until calculations are written out.
Hand-writing approximate output without calculation causes alignment errors.

### Step 4: Build output
[Uses the calculated values from Step 3]
```

### No Embedded Box Drawings in Skills

**Skills should not contain embedded box-drawing examples in their instructions** — because when
agents see a rendered box in a skill document, they interpret it as a template and attempt to reproduce
it manually. Manual reproduction produces misaligned boxes. Preprocessing scripts produce correctly
aligned output every time.

**Important distinction**: This rule applies to skills that **output boxes to users**. Documentation
diagrams in skills that **do not produce boxes** (e.g., state machine diagrams in tdd-implementation,
architecture flowcharts) are acceptable because:
- They illustrate concepts for human readers, not templates for agent output
- The agent is not asked to recreate or render them
- They don't trigger the "copy this pattern" failure mode

**The failure pattern:**
1. Skill document shows example box output:
   ```
   ╭──────────────────────╮
   │ Example Header       │
   ├──────────────────────┤
   │ Content here         │
   ╰──────────────────────╯
   ```
2. Agent sees this pattern and attempts to recreate it manually
3. Manual rendering produces misaligned, incorrect boxes
4. Preprocessing scripts (which would produce correct output) go unused

**Correct approach for skills that produce boxes:**

1. **Use preprocessing, not visual examples:**
   ```markdown
   # BAD - Embedded box causes manual rendering
   Display the result in this format:
   ╭──────────────────────╮
   │ {content}            │
   ╰──────────────────────╯

   # GOOD - Preprocessing handles rendering
   [BANG]`render-box.sh "$content"`
   ```

2. **For circle/rating patterns, preprocess them:**
   ```markdown
   # BAD - Embedded pattern causes manual typing
   Display ratings like: ●●●●○ (4/5) or ●●○○○ (2/5)

   # GOOD - Script renders rating
   [BANG]`render-rating.sh "$score"`
   ```

3. **For output format documentation, describe structure not rendering:**
   ```markdown
   # BAD - Shows rendered output
   The status display looks like:
   ╭────────────────────────────────╮
   │ 📊 Progress: [████░░░░] 40%   │
   ╰────────────────────────────────╯

   # GOOD - Preprocessing renders status
   [BANG]`get-status-display.sh`
   ```

**Verification during skill creation:**
- [ ] No box-drawing characters (╭╮╰╯│├┤┬┴┼─) appear in instruction examples
- [ ] No formatted table examples with borders appear in skill text
- [ ] Visual patterns (circles, bars, etc.) handled by preprocessing scripts
- [ ] All display rendering uses exclamation-backtick preprocessing

### Conditional Information Principle

**Formatting details (emoji meanings, box characters, column widths) belong in preprocessing
scripts, not in skill documentation** — because any reference information in a skill document
becomes material the agent tries to apply manually. Even when a section is labeled "for reference
only", the agent sees it and uses it. Move all formatting reference to the script that applies it.

**The failure pattern:**
1. Skill doc contains "reference" information (emoji meanings, circle patterns, formatting rules)
2. Agent sees this reference material before executing
3. Agent attempts to manually construct output instead of using preprocessed output
4. Manual construction produces incorrect results (emoji widths, alignment errors)

**Where information belongs:**

| Information Type | Location | Why |
|------------------|----------|-----|
| What preprocessing command to use | Skill doc | Always needed |
| What args to pass (if delegated) | Skill doc | Always needed |
| Emoji meanings (☑️, 🔄, 🔳) | Preprocessing script | Script handles rendering |
| Rating patterns (●●●●○) | Preprocessing script | Script handles rendering |
| Box character reference | Preprocessing script | Script handles rendering |
| Formatting rules (widths, padding) | Preprocessing script | Script handles rendering |

**Correct pattern:**
```markdown
# GOOD - Skill uses preprocessing, doesn't explain formatting

### Step 1: Display status

[BANG]`get-status-display.sh`

> Script handles all emoji selection, box alignment, and formatting.
```

**Anti-pattern (teaching then forbidding):**
```markdown
# BAD - Provides reference that enables manual construction

## Emoji Reference (for understanding output, NOT for manual use)

| Status | Emoji |
|--------|-------|
| Completed | ☑️ |
| In Progress | 🔄 |
| Pending | 🔳 |

**Do not construct manually!**

# Problem: Agent sees the reference, uses it anyway
```

**Self-check during skill creation:**
- [ ] Does the skill contain formatting reference tables?
- [ ] Is there any "for reference only" or "do not use manually" information?
- [ ] Could the agent construct output manually after reading the skill?

If YES to any: Move the information to the preprocessing script.

### Output Artifact Gates (M192 Prevention)

**Critical insight**: Calculation gates alone are insufficient. When a skill produces structured
output (boxes, tables, formatted text), the gate must require showing the **exact artifact strings**
that will appear in the output, not just the numeric calculations.

**The failure pattern**:
1. Agent correctly calculates widths, counts, positions
2. Agent understands the formula for constructing output
3. Agent **re-types** the output from memory instead of copying calculated artifacts
4. Output has subtle errors despite correct calculations

**Solution**: Add a second gate that requires **explicit artifact construction**:

```markdown
### Step 4: Construct lines

For each item, apply the formula and **record the exact result string**:

```
build_line("📊 Status", 20) = "│ 📊 Status          │"  (padding: 10)
                              ^^^^^^^^^^^^^^^^^^^^^^^^^^^^
                              This exact string goes to output
```

**MANDATORY BUILD RESULTS GATE:**

Before writing final output, verify:
- [ ] Each artifact (line, cell, row) has an explicit string recorded above
- [ ] Padding/spacing counts are noted in parentheses
- [ ] Final output will COPY these exact strings (no re-typing)

**BLOCKING:** If Step 4 does not contain explicit artifact strings, STOP and complete
Step 4 before proceeding. Re-typing output causes errors even when calculations are correct.
```

**Key distinctions**:
| Calculation Gate | Artifact Gate |
|------------------------|----------------------|
| Shows numeric values | Shows exact output strings |
| "max_width = 20" | `"│ content      │"` |
| Prevents wrong math | Prevents wrong assembly |
| Required BEFORE construction | Required AFTER construction, BEFORE output |

**When to add artifact gates**:
- Output has precise formatting (aligned columns, borders, spacing)
- Small errors in spacing/padding break the result
- The construction formula combines multiple values

### Recursive Structures

For problems with recursive structure (e.g., nested boxes), the decomposition will
show the same pattern at multiple levels. Extract this as a function that can be
applied recursively.

**Identifying recursive patterns in decomposition**:
```
GOAL: Render nested structure correctly
  REQUIRES: Outer container rendered correctly
    REQUIRES: Inner container rendered correctly        ← Same pattern!
      REQUIRES: Innermost container rendered correctly  ← Same pattern again!
        ATOMIC: Base case - no more nesting
```

**Converting to recursive function**:
```
FUNCTION: render_container(container) → rendered_output
  Base case: if container has no children
    return render_leaf(container)
  Recursive case:
    1. For each child: rendered_child = render_container(child)  ← Recursive call
    2. Combine rendered children with container frame
    3. Return combined result
```

**Order of operations for nested structures**:
```
1. Process innermost elements first (base cases)
2. Work outward, combining results
3. Final step produces the outermost result

This is "inside-out" construction - the decomposition reveals this naturally
because inner elements are REQUIRES for outer elements.
```

**Example - Nested boxes**:
```
Decomposition shows:
  REQUIRES: Outer box contains inner boxes correctly
    REQUIRES: Each inner box is self-consistent
      REQUIRES: Inner box borders align
        (same requirements as any box - recursive!)

Function:
  build_box(contents[]) → string[]
    For each content item:
      if content is itself a box structure:
        inner_lines = build_box(content.items)  ← Recursive call
        add inner_lines to processed_contents
      else:
        add content string to processed_contents
    return construct_box_frame(processed_contents)

Procedure step:
  "Call build_box(root_contents) to construct the complete nested structure"
```

### Silent Preprocessing with exclamation-backtick syntax (Preferred)

**Critical insight**: When a skill contains functions that perform deterministic computation
(algorithms, formulas, calculations), the output MUST be generated BEFORE Claude sees the content.
Claude Code provides a built-in mechanism for this: **silent preprocessing**.

See `concepts/skill-loading.md` § Output Tags in Preprocessor Directives for preprocessing
syntax and how the two-phase expansion works.

**⚠️ CAUTION: Pattern Collision in Documentation**

When documenting the silent preprocessing syntax within a skill, **never use literal** [BANG]`command`
**patterns as examples**. Claude Code's pattern matcher scans the entire skill file and will attempt
to expand any [BANG]`...` pattern it finds - including those in documentation sections.

```markdown
# ❌ WRONG - Pattern matcher will try to execute "command"
This skill uses silent preprocessing ([BANG]`command`) for output.

# ✅ CORRECT - Use descriptive text instead
This skill uses silent preprocessing (exclamation-backtick syntax) for output.
```

This applies to any invocable skill (listed in plugin.json). Reference documentation that is not
directly invoked (like this skill-builder) can safely contain the patterns for teaching purposes.

**Why this is the preferred approach:**
- **Guaranteed correctness**: Output is computed, not approximated by the LLM
- **No visible tool calls**: Users see clean skill output, not Bash/Read noise
- **Simpler implementation**: No Python handlers needed, just shell scripts
- **No LLM manipulation errors**: Prevents M246, M256, M257, M288, M298

**Example - Progress banner skill:**

```markdown
---
name: cat-banner
description: Display issue progress banner
---

[BANG]`cat-progress-banner.sh --issue-id "${ISSUE_ID}" --phase "${PHASE}"`
```

The script generates the complete banner with correct box alignment, emoji widths, and padding.
Claude receives the rendered banner and outputs it directly.

**When to use silent preprocessing:**
- Status displays with boxes/tables
- Progress indicators
- Any formatted output with precise alignment
- Data that must be computed (counts, sums, percentages)

**Creating preprocessing scripts:**
1. Create script in `plugin/scripts/` (e.g., `cat-progress-banner.sh`)
2. Script accepts arguments via shell variables or command-line args
3. Script outputs the final formatted content to stdout
4. Reference in skill with [BANG]`script.sh args`

**Identify extraction candidates during function extraction (Step 5):**

```
For each function identified, ask:
1. Is the output deterministic given the inputs?
2. Could the agent get the wrong result by "thinking" instead of computing?
3. Does the function involve precise formatting, counting, or arithmetic?

If YES to all three → Extract to silent preprocessing script
```

**Extraction candidate signals**:
| Signal | Example | Why Extract? |
|--------|---------|--------------|
| Counting characters/widths | `display_width(text)` | Agent may miscount emojis |
| Arithmetic with variables | `padding = max - width` | Agent may compute incorrectly |
| Building formatted strings | `"│ " + content + spaces + " │"` | Agent may mis-space |
| Aggregating over collections | `max(widths)` | Agent may miss items |

**Non-candidates** (keep in skill):
| Type | Example | Why Keep? |
|------|---------|-----------|
| Reasoning/judgment | "Identify atomic conditions" | Requires understanding |
| Pattern matching | "Find repeated subtrees" | Requires semantic analysis |
| Decision making | "Choose appropriate level" | Requires context |

**Decision flow during Step 5**:
```
For each function:
  Is it deterministic? ─────No────→ Keep in skill (reasoning required)
         │
        Yes
         │
  Could agent compute wrong? ─No─→ Keep in skill (trivial/reliable)
         │
        Yes
         │
  Extract to preprocessing script (plugin/scripts/)
         │
  Script computes output BEFORE Claude sees skill
         │
  Claude receives rendered output, outputs directly
```

### Architecture Decision: Direct vs. Delegated Preprocessing

Choose the architecture based on **where the data comes from**:

**Pattern 1: Direct Preprocessing** (script collects all inputs)

Use when the script can discover all necessary data from the environment (files, git state,
config, etc.) without LLM judgment.

```
┌─────────────────┐
│   Skill A       │
│                 │
│ [BANG]`script.sh`│──→ Script reads files/state ──→ Rendered output
│                 │
│ [output here]   │
└─────────────────┘
```

**Example**: Status display - script reads STATE.md files, computes progress, renders box.

```markdown
# Status Skill
[BANG]`get-status-display.sh`
```

**Pattern 2: Delegated Preprocessing** (LLM determines data)

Use when the LLM must analyze, decide, or select what data appears in the output.
Split into two skills:

```
┌─────────────────┐     ┌─────────────────────┐
│   Skill A       │     │   Skill B           │
│  (Orchestrator) │     │   (Renderer)        │
│                 │     │                     │
│ Analyze context │     │ [BANG]`render.sh $ARGS`│─→ Rendered output
│ Decide on data  │     │                     │
│ Invoke Skill B  │────→│ [output here]       │
│ with args       │     │                     │
└─────────────────┘     └─────────────────────┘
```

**Example**: Stakeholder concern rendering - LLM selects which concerns apply, then
delegates to a render skill.

```markdown
# Skill A: Stakeholder Review (orchestrator)
Analyze the changes and identify applicable concerns.

For each concern, invoke the `render-concern` skill:
- skill: render-concern
- args: {"stakeholder": "security", "concern": "SQL injection", "severity": "high"}

# Skill B: Render Concern (renderer)
[BANG]`render-concern.sh '$ARGUMENTS'`
```

**Pattern 3: Centralized Output Dispatch** (Java handler generates output via `get-output` preprocessor)

Use when the skill's output is entirely script-generated and the skill content is just a thin wrapper
that invokes the `get-output` binary dispatcher via a ! preprocessor directive and echoes the result verbatim.
The `get-output` dispatcher routes to the appropriate Java `SkillOutput` handler and returns the output
wrapped in `<output type="TYPE">` tags.

```
┌──────────────────────┐     ┌──────────────────────────────┐
│   Java Handler       │     │   skill content (thin)       │
│   (SkillOutput impl) │     │                              │
│                      │     │ !`"${CLAUDE_PLUGIN_ROOT}/    │
│ Returns content via  │──→  │  client/bin/get-output" TYPE`│
│ <output type="TYPE"> │     │  (! preprocessor directive)  │
└──────────────────────┘     └──────────────────────────────┘
```

**Example**: Status display — handler generates full status output, skill content echoes it.

Java handler (`GetStatusOutput.java`):
```java
public String getOutput(String[] args) throws IOException {
    // ... generates status content
    return renderedStatus;
}
```

Registered in `GetOutput.java`:
```java
case "status" -> new GetStatusOutput(scope).getOutput(handlerArgs);
```

Skill content (SKILL.md):
```markdown
---
description: Use when user asks about progress, status, what's done, or what's next
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-output" status`
```

**Skill content pattern for handler-dispatched skills:**

The thin wrapper skill content MUST follow this exact pattern:
1. `!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-output" TYPE`` — calls the centralized dispatcher via ! preprocessor
2. The output is automatically wrapped in `<output type="TYPE">` tags by the dispatcher

**Anti-pattern - meta-description that agents echo literally:**
```markdown
The handler has injected the help reference as additional context
(look for the output tag marker above).
```
This text gets echoed to the user verbatim because the agent treats it as output content.

**Handler vs skill content responsibilities:**

| Aspect | Java Handler (runs every invocation) | Skill content (loaded once per session) |
|--------|--------------------------------------|--------------------------------------|
| Output | Fresh content based on current state | Static behavioral instructions |
| Args | Receives args from `getOutput(String[] args)` | References `$ARGUMENTS` for conditional logic |
| Changes | Different output each call | Same instructions, reused via reference.md |

For skills with argument-dependent behavior (e.g., `/cat:work` vs `/cat:work 2.1-task`), the handler
generates argument-specific output while skill content contains the static conditional logic
("if ARGUMENTS contains a filter, do X"). Both work correctly on subsequent invocations: the handler
re-runs via `get-output` with new args, and the agent follows the already-loaded skill content instructions.

**When to use centralized output dispatch instead of direct preprocessing:**
- Skill output requires complex Java logic (rendering, config reading, multi-source composition)
- Handler needs to compose output from multiple sources
- Output depends on runtime state not available to shell scripts

**Decision checklist**:

| Question | If YES | Pattern |
|----------|--------|---------|
| Can script read all needed data from files/environment? | Script is self-sufficient | Direct |
| Does output depend on LLM analysis or judgment? | LLM must decide first | Delegated |
| Is the same rendering used with different data sources? | Reusable renderer | Delegated |
| Is there only one way to determine what goes in output? | No LLM needed | Direct |
| Does output need complex Java logic or multi-source composition? | Java handler computes it | Centralized |
| Is the entire skill output script-generated (thin wrapper)? | Java handler + thin skill content | Centralized |

**Benefits of centralized output dispatch pattern**:
- **Session-efficient**: The full skill content loads only once per session (via `get-skill`).
  Subsequent invocations load a tiny reference (~2 lines) instead. The handler output (`<output type="...">` tag)
  is fresh every time via `get-output`, but the skill instructions are not re-loaded, saving significant context.
  After context compaction, skill markers reset so skills re-load in full automatically.
- Java handlers compose output from multiple sources (config files, git state, session data)
- Output is type-safe and testable via `TestJvmScope` (see `tests/` directory)
- Agent cannot attempt manual construction — it only sees the final rendered output

**Benefits of delegated pattern**:
- Rendering logic is reusable across multiple orchestrator skills
- LLM handles judgment, script handles formatting
- Arguments pass through unchanged (no escaping needed at Skill tool level)
- Clean separation: orchestrator = brain, renderer = hands

---

## Skill Arguments and `$ARGUMENTS`

Skills can receive arguments via the `$ARGUMENTS` placeholder, which is substituted
with the value passed to the skill.

### How Arguments Flow

| Stage | What Happens | Escaping |
|-------|--------------|----------|
| User types `/skill-name arg text` | `arg text` captured | Raw, unchanged |
| Agent invokes `Skill(skill="name", args="value")` | `value` passed | Raw, unchanged |
| `$ARGUMENTS` in skill markdown | Substituted literally | No transformation |

### Safe Patterns

**Use `$ARGUMENTS` in markdown** (the placeholder is substituted before the agent reads the skill):
```markdown
## User Request

The user asked: $ARGUMENTS

Now analyze this request...
```

**For skill-to-skill calls**: No escaping needed - the Skill tool's `args` parameter
passes through unchanged:
```
Skill(skill="other-skill", args="text with \"quotes\" and $vars")
→ other-skill receives: text with "quotes" and $vars
```

---

## Priming Prevention Checklist

**Critical**: Skills can accidentally TEACH agents to bypass proper workflows. Before finalizing,
verify the skill doesn't prime agents for incorrect behavior.

### Information Ordering Check

| Question | If YES | Fix |
|----------|--------|-----|
| Does skill teach HOW before saying "invoke tool"? | Primes manual approach | Move algorithm to preprocessing script |
| Are there Functions/Prerequisites before Procedure? | Primes manual construction | Remove or move after Procedure |
| Does skill explain what to preserve/remove? | Primes content fabrication | Move to internal agent prompt only |

**Correct ordering**: WHAT to invoke → WHAT postconditions to verify → (internals hidden)

### Output Format Check

Output format specifications must define **structure only**, never **expected content**.

```yaml
# ❌ WRONG - Embeds expected value
Output format:
  validation_score: 1.0 (required)
  status: PASS

# ✅ CORRECT - Structure only
Output format:
  validation_score: {actual score from validation check}
  status: {PASS if score >= threshold, FAIL otherwise}
```

**Never include**:
- Expected numeric values (1.0, 100%, etc.)
- Success indicators in examples (PASS, ✓, etc.)
- "Required" or "must be" next to values

### Cost/Efficiency Language Check

**Remove any language suggesting proper approach is "expensive" or "costly":**

```yaml
# ❌ WRONG - Encourages shortcuts
Note: Running validation spawns 2 subagents for parallel extraction.
For batch operations, this can be costly.

# ❌ WRONG - Suggests overhead
This approach spawns subagents which adds context overhead.

# ✅ CORRECT - No cost language
Note: Validation ensures semantic comparison by running parallel extraction.
```

**Why**: Cost language primes agents to take shortcuts under context pressure.

### Encapsulation Check

Verify orchestrator-facing content is separated from internal agent content:

| Content Type | Orchestrator Doc | Internal Doc |
|--------------|------------------|--------------|
| What skill to invoke | ✅ | |
| Postconditions to verify | ✅ | |
| Fail-fast conditions | ✅ | |
| How to report results | ✅ | |
| Algorithm details | | ✅ |
| What to preserve/remove | | ✅ |
| Compression techniques | | ✅ |
| Detailed output format | | ✅ |

**Principle**: The orchestrator should NOT learn HOW to do the issue - only WHAT to invoke
and WHAT results to expect. If the orchestrator could do the issue after reading the doc,
the doc has exposed too much.

**Critical**: External file existence does NOT automatically mean encapsulation is complete.
Even when an internal doc (e.g., compression-protocol.md) contains the full algorithm, verify the
orchestrator doc contains ZERO actionable guidance. Partial information like "preserve section
headers" or "condense explanatory text" can still prime manual attempts. The orchestrator doc
should contain only: what to invoke, postconditions to verify, and fail-fast conditions.

### Delegation Safety Check

If the skill will be delegated to subagents:

- [ ] Skill does NOT tell subagent what validation score to expect
- [ ] Producer and validator are separate (subagent A produces, subagent B validates)
- [ ] Output format uses placeholders (`{actual score}`), not expected values
- [ ] Acceptance criteria specify WHAT to measure, not WHAT result to report
- [ ] Subagent is required to include raw tool output, not summaries

**Anti-pattern**: Telling a subagent "validation score must be 1.0" primes fabrication.
Instead: "Run validation and report the actual score."

### Reference Information Check

Formatting details belong in preprocessing scripts, not skill documentation:

```yaml
# ❌ WRONG - Skill doc contains reference info
## Emoji Reference (for understanding output, NOT for manual use)
| Status | Emoji |
|--------|-------|
| Completed | ☑️ |

## Procedure
Step 1: Render status display...

# ✅ CORRECT - Preprocessing handles formatting
## Procedure
Step 1: Display status
[BANG]`get-status-display.sh`
```

**Self-check**:
- [ ] No "for reference only" or "do not use manually" information in skill doc
- [ ] All formatting logic in preprocessing scripts
- [ ] Agent cannot construct output manually even if they tried

---

## Conditional Section Lazy-Loading

**Conditional sections of a skill (content only needed in certain execution paths) should be stored in
separate files and loaded on-demand, not embedded inline** — because an agent reads all inline content
regardless of which branch it takes. Content that's only relevant in edge cases still primes the agent,
consumes context, and may cause it to follow an unintended path. Lazy-loading ensures each agent only
receives the content it actually needs for the path it's on.

**Why lazy-loading matters:**
- Reduces token usage when the conditional path isn't taken
- Keeps the main skill focused on the primary workflow
- Prevents priming from information that won't be used
- Allows conditional content to be more detailed without bloating the skill

**Identify conditional sections during design:**

```
If step says "If X, then do Y workflow..." where Y is substantial:
  → Extract Y to a separate file
  → Reference it: "Read {skill-name}/Y-WORKFLOW.md and follow its instructions"
```

**Conditional section signals:**

| Signal | Example | Action |
|--------|---------|--------|
| "If [condition], then [multi-step process]" | "If batch mode, follow batch workflow" | Extract to `BATCH-WORKFLOW.md` |
| "When [scenario] occurs, handle by..." | "When conflicts occur, resolve using..." | Extract to `CONFLICT-RESOLUTION.md` |
| Special handling for edge cases | "For CLAUDE.md files specifically..." | Extract to `CLAUDEMD-HANDLING.md` |
| Alternative execution models | "For parallel execution..." | Extract to `PARALLEL-EXECUTION.md` |

**File structure with lazy-loaded sections:**

```
plugin/skills/my-skill/
├── SKILL.md                    # Main workflow (always loaded)
├── EDGE-CASE-A.md              # Loaded only when edge case A detected
├── EDGE-CASE-B.md              # Loaded only when edge case B detected
└── ALTERNATIVE-MODE.md         # Loaded only when alternative mode selected
```

**Reference pattern in main skill:**

```markdown
### Step N: Handle special case

**If [condition detected]:**

Read `{skill-directory}/SPECIAL-CASE.md` and execute its workflow.

**Otherwise:** Continue to Step N+1.
```

**Anti-pattern (inline conditional content):**

```markdown
# ❌ WRONG - Conditional content embedded inline
### Step N: Handle special case

**If [condition detected]:**

[50+ lines of conditional workflow that's only used 10% of the time]

**Otherwise:** Continue to Step N+1.
```

**Why this anti-pattern fails:**
1. Agent reads all 50 lines even when condition is false
2. Content may prime agent to follow that path unnecessarily
3. Main skill becomes bloated and harder to maintain
4. Token cost paid even when content isn't used

**Threshold for extraction:**
- Content > 20 lines → Extract to separate file
- Content used < 50% of invocations → Extract to separate file
- Content represents alternative execution model → Always extract

---

## File Colocation

Files referenced **only** by a single skill should reside within that skill's directory — because
colocation makes ownership explicit, simplifies deletion (remove the skill directory and you remove all
its dependencies), and prevents concept files from accumulating in shared locations where they no longer
have a clear owner:

```
plugin/skills/
├── my-skill/
│   ├── SKILL.md            # Main skill definition
│   ├── helper-workflow.md  # Only used by my-skill → colocated
│   └── templates/          # Subdirectories allowed
│       └── output.md
```

**Why:** Colocation ensures:
- Clear ownership (skill owns its dependencies)
- Easier maintenance (related files grouped together)
- Simpler lazy-loading (load skill directory, get all needed files)
- Cleaner deletions (remove skill → remove all its files)

**Shared files** (referenced by multiple skills/commands) belong in `plugin/concepts/`:

```
plugin/concepts/
├── work.md                 # Used by work command AND skills
└── merge-and-cleanup.md    # Used by multiple commands
```

**Decision rule:**
| Referenced By | Location |
|---------------|----------|
| Single skill only | `plugin/skills/{skill-name}/` |
| Multiple skills | `plugin/concepts/` |

**After creating a concept file, wire it into agent context.** Files in `plugin/concepts/` are NOT loaded
automatically — they must be explicitly referenced by a skill, hook, or rules file to reach agents. After
creating a concept file:

1. Identify every skill or hook that should apply the convention.
2. Add a `[description](${CLAUDE_PLUGIN_ROOT}/concepts/<filename>.md)` markdown link or
   `` `${CLAUDE_PLUGIN_ROOT}/concepts/<filename>.md` `` inline code reference inside the skill's
   `first-use.md`.
3. Verify the reference appears in at least one skill's content before committing.

**File path resolution:** See `concepts/skill-loading.md` § Referencing Files From Skills.

If no existing skill is the right home, add the concept to a rules file in `.claude/rules/` instead — rules
files ARE injected automatically into the main agent's context.
