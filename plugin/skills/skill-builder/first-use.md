<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Skill Builder

## Purpose

Design or update skills and commands by reasoning backward from the goal to required preconditions,
then converting to forward-execution steps.

---

## When to Use

- Creating a new skill or command
- Updating an existing skill or command that has unclear or failing steps
- Any procedure where the goal is clear but the path is not

**Note:** Both `skills/` and `commands/` are agent-facing prompt files that define behavior.
Use skill-builder for BOTH types.

---

## Document Structure: XML vs Markdown

Skills and commands can use either XML-based structure or pure markdown sections.
Choose based on the features needed.

### Use XML Structure When

XML tags (`<objective>`, `<process>`, `<step>`, `<execution_context>`) are required when:

| Feature | XML Syntax | Purpose |
|---------|------------|---------|
| **File references** | `@${CLAUDE_PLUGIN_ROOT}/path/file.md` inside `<execution_context>` | Load external files into context |
| **Named step routing** | `<step name="validate">` with "Continue to step: create" | Branch between steps based on conditions |
| **Conditional loading** | `<conditional_context>` | Load files only when specific scenarios occur |
| **Complex workflows** | Multiple `<step>` blocks with routing | Multi-phase processes with 10+ steps |

**Example** (command with file references and routing):
```xml
<execution_context>
@${CLAUDE_PLUGIN_ROOT}/concepts/work.md
@${CLAUDE_PLUGIN_ROOT}/skills/merge-subagent/SKILL.md
</execution_context>

<process>
<step name="validate">
If validation fails, continue to step: error_handler
Otherwise, continue to step: execute
</step>

<step name="execute">
...
</step>
</process>
```

### Use Pure Markdown When

Standard markdown sections (`## Purpose`, `## Procedure`, `## Verification`) are preferred when:

- No file reference expansion needed
- Linear workflow (steps execute in order)
- Simple single-purpose command or skill
- No conditional branching between steps

**Example** (simple skill):
```markdown
## Purpose

Display skill output help content.

---

## Procedure

Output the template content exactly as provided in context.

---

## Verification

- [ ] Content output verbatim
- [ ] No modifications made
```

### Decision Checklist

Before creating a new skill/command, answer:

1. Does it need to load external files? → **XML** (use `<execution_context>`)
2. Does it have conditional step routing? → **XML** (use `<step name="...">`)
3. Does it need conditional file loading? → **XML** (use `<conditional_context>`)
4. Is it a simple linear procedure? → **Markdown** (use `## Purpose/Procedure/Verification`)

**Default**: Use pure markdown unless you need XML-specific features.

---

## Core Principle

**Backward chaining**: Start with what you want to be true, repeatedly ask "what must be
true for this?", until you reach conditions you can directly achieve. Then reverse to
get executable steps.

```
GOAL ← requires ← requires ← ... ← ATOMIC_ACTION
                                         ↓
                                    (reverse)
                                         ↓
ATOMIC_ACTION → produces → produces → ... → GOAL
```

---

## Procedure

### Step 1: State the Goal

Write a single, verifiable statement of the desired end state.

**Format**:
```
GOAL: [Observable condition that indicates success]
```

**Criteria for good goal statements**:
- Observable: Can be verified by inspection or test
- Specific: No ambiguity about what "done" means
- Singular: One condition (decompose compound goals first)

**Examples**:
```
GOAL: All right-side │ characters in the box align vertically
GOAL: The function returns the correct sum for all test cases
GOAL: The user sees a confirmation message after submission
```

### Step 2: Backward Decomposition

For each condition (starting with the goal), ask: **"What must be true for this?"**

**Format**:
```
CONDITION: [what we want]
  REQUIRES: [what must be true for the condition]
  REQUIRES: [another thing that must be true]
```

**Rules**:
- Each REQUIRES is a necessary precondition
- Multiple REQUIRES under one CONDITION means ALL must be true (AND)
- Continue decomposing until you reach atomic conditions

**Atomic condition**: A condition that can be directly achieved by a single action or
is a given input/fact.

**Example decomposition**:
```
GOAL: Right borders align
  REQUIRES: All lines have identical display width
    REQUIRES: Each line follows the formula: width = content + padding + borders
      REQUIRES: padding = max_content - this_content
        REQUIRES: max_content is known
          REQUIRES: display_width calculated for all content items
            REQUIRES: emoji widths handled correctly
              ATOMIC: Use width table (emoji → width mapping)
            REQUIRES: all content items identified
              ATOMIC: List all content strings
        REQUIRES: this_content display_width is known
          (same as above - shared requirement)
      REQUIRES: borders add fixed width (4)
        ATOMIC: Use "│ " prefix (2) and " │" suffix (2)
```

### Step 3: Identify Leaf Nodes

Extract all ATOMIC conditions from the decomposition tree. These are your starting points.

**Format**:
```
LEAF NODES (atomic conditions):
1. [First atomic condition]
2. [Second atomic condition]
...
```

### Step 4: Build Dependency Graph

Determine the order in which conditions can be satisfied based on their dependencies.

**Rules**:
- A condition can only be satisfied after ALL its REQUIRES are satisfied
- Conditions with no REQUIRES (leaf nodes) can be done first
- Multiple conditions at the same level can be done in parallel (or any order)

**Format**:
```
DEPENDENCY ORDER:
Level 0 (no dependencies): [atomic conditions]
Level 1 (depends on L0): [conditions requiring only L0]
Level 2 (depends on L1): [conditions requiring L0 and/or L1]
...
Level N: GOAL
```

### Step 5: Extract Reusable Functions

Scan the decomposition tree for patterns that should become functions.

**Extract a function when**:
1. **Same logic appears multiple times** in the tree (even with different inputs)
2. **Recursive structure**: The same pattern applies at multiple nesting levels
3. **Reusable calculation**: A computation that transforms input → output cleanly

**Function identification signals**:
```
Pattern A: Repeated subtree
  REQUIRES: X for item A
    REQUIRES: Y for A
      ATOMIC: Z
  REQUIRES: X for item B       ← Same structure, different input
    REQUIRES: Y for B
      ATOMIC: Z
  → Extract: function X(item) that does Y and Z

Pattern B: Recursive structure
  REQUIRES: process outer container
    REQUIRES: process inner container    ← Same pattern, nested
      REQUIRES: process innermost        ← Same pattern again
  → Extract: function process(container) that calls itself for nested containers

Pattern C: Transform chain
  REQUIRES: result C
    REQUIRES: intermediate B from A
      REQUIRES: input A
  → Extract: function transform(A) → C
```

**Function definition format**:
```
FUNCTIONS:
  function_name(inputs) → output
    Purpose: [what it computes]
    Logic: [derived from the decomposition subtree]
    Used by: [which steps will call this]
```

**Composition rules**:
- Functions can call other functions
- Order function definitions so dependencies come first
- For recursive functions, define the base case and recursive case

**Deriving logic for variable-length inputs**:

When a function operates on a collection of arbitrary length, derive the algorithm by:

1. **Minimum case**: Solve for the smallest valid input (often length 1)
2. **Next increment**: Solve for length 2 (or next meaningful size)
3. **Generalize**: Identify the pattern that extends to length N

```
Example: max_content_width(contents[])

Length 1: contents = ["Hello"]
  max = display_width("Hello") = 5
  → For single item, max is just that item's width

Length 2: contents = ["Hello", "World!"]
  w1 = display_width("Hello") = 5
  w2 = display_width("World!") = 6
  max = larger of w1, w2 = 6
  → For two items, compare and take larger

Length N: contents = [c1, c2, ..., cN]
  → Pattern: compare each item's width, keep the largest
  → General: max(display_width(c) for c in contents)
```

```
Example: build_box(contents[])

Length 1: contents = ["Hi"]
  Lines needed: top border, one content line, bottom border
  Width: display_width("Hi") + 4 = 6
  → Single item: frame around one line

Length 2: contents = ["Hi", "Bye"]
  Lines needed: top, content1, content2, bottom
  Width: max(display_width("Hi"), display_width("Bye")) + 4
  → Two items: both must fit in same width frame

Length N:
  → Pattern: all content lines share same width (the maximum)
  → General: find max width, pad each line to that width, add frame
```

This technique prevents over-generalization and ensures the algorithm handles edge cases.

**Example - Box alignment functions**:
```
FUNCTIONS:
  display_width(text) → integer
    Purpose: Calculate terminal display width of text
    Logic: Use lib/emoji_widths.py lookup for terminal-specific widths
    Used by: max_content_width, padding calculation

  max_content_width(contents[]) → integer
    Purpose: Find maximum display width among content items
    Logic: max(display_width(c) for c in contents)
    Used by: box_width, padding calculation

  box_width(contents[]) → integer
    Purpose: Calculate total box width including borders
    Logic: max_content_width(contents) + 4
    Used by: border construction, nested box embedding

  build_box(contents[]) → string[]
    Purpose: Construct complete box with aligned borders
    Logic:
      1. mw = max_content_width(contents)
      2. for each content: line = "│ " + content + padding(mw - display_width(content)) + " │"
      3. top = "╭" + "─"×(mw+2) + "╮"
      4. bottom = "╰" + "─"×(mw+2) + "╯"
      5. return [top] + lines + [bottom]
    Used by: outer box construction, nested box construction (recursive)
```

### Step 6: Convert to Forward Steps

Transform the dependency order into executable procedure steps, using extracted functions.

**Rules**:
- Start with Level 0 (leaf nodes)
- Progress through levels toward the goal
- Each step should be a concrete action, not a state description
- **Call functions** instead of repeating logic
- For recursive operations, the step invokes the function which handles recursion internally
- Include verification where applicable

**Format**:
```
PROCEDURE:
1. [Action to achieve leaf condition 1]
2. [Action to achieve leaf condition 2]
3. Call function_a(inputs) to achieve Level 1 condition
4. For each item: call function_b(item)    ← Function handles repeated application
5. Call function_c(nested_structure)        ← Function handles recursion internally
...
N. [Final action that achieves the GOAL]

VERIFICATION:
- [How to confirm the goal is met]
```

**Composing functions in steps**:
```
# Instead of inline logic:
BAD:  "Calculate width by counting characters and adding 1 for each emoji"
GOOD: "Call display_width(text) to get the terminal width"

# Instead of repeated steps:
BAD:  "Calculate width for item 1, then for item 2, then for item 3..."
GOOD: "For each content item, call display_width(item)"

# Instead of manual recursion:
BAD:  "Build inner box, then embed in outer box, checking alignment..."
GOOD: "Call build_box(contents) - function handles nesting internally"
```

### Step 7: Write the Skill

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

```
Format: "[WHEN to use] - [what it does briefly]"

Good examples:
  "Work on or resume issues - use when user says work, resume, continue, or pick up"
  "Use BEFORE creating or updating any skill - decomposes goal into forward steps"
  "Use when session crashed or locks blocking - cleans abandoned worktrees and locks"

Bad examples (implementation details leak):
  "Work on issues (approval required unless trust=high; auto-continues when trust >= medium)"
  "Run stakeholder review (spawns 6 parallel subagents, costs ~30K tokens)"
  "Merge branch (uses --ff-only, requires linear history)"
```

**Trigger patterns**:
- `MANDATORY: Load BEFORE [action]` - Must be loaded before doing something
- `MANDATORY: Use for [situation]` - Required for specific scenarios
- `Use BEFORE [action]` - Should be used before doing something
- `Use when [condition]` - Triggered by a specific situation
- `Use instead of [alternative]` - Replaces a dangerous or complex operation

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

([BANG] = the exclamation mark, written as placeholder to avoid preprocessor expansion. See M440 caution below.)

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

**MANDATORY: Skills must not contain inline bash for deterministic operations.**

All deterministic bash belongs in external script files (plugin/scripts/). Skills contain only:
- **When to use**: Conditions and prerequisites for the operation
- **Script invocation**: Single bash command to invoke the script
- **Result handling table**: JSON status codes → meaning → agent recovery action
- **Judgment-dependent guidance**: Conflict resolution strategy, commit message construction, error recovery decisions

Scripts (bash or python in plugin/scripts/) handle:
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
| DIVERGED | Base branch advanced | Rebase onto base, retry |
| ERROR | Unexpected failure | Read message, report to user |
```

#### Example: Before and After

**Before** (348 lines of inline bash in skill):
```markdown
### Step 2: Check for Divergence
\```bash
BASE=$(git rev-parse "$BASE_BRANCH")
DIVERGED=$(git rev-list --count "HEAD..$BASE")
if [[ "$DIVERGED" -gt 0 ]]; then
  echo "ERROR: ..."
  exit 1
fi
\```
### Step 3: Check File Deletions
\```bash
DELETED=$(git diff --name-status "$BASE..HEAD" | grep "^D")
...
\```
[... 10 more steps with inline bash ...]
```

**After** (97 lines — invocation + result handling):
```markdown
## Execute
\```bash
"$(git rev-parse --show-toplevel)/plugin/scripts/git-merge-linear.sh" "$WORKTREE_PATH"
\```

## Handle Results
| Status | Meaning | Recovery |
|--------|---------|----------|
| OK | Merged | Done |
| FF_FAILED | Base advanced | Rebase, retry |
| DIVERGED | New base commits | Rebase, retry |
| DELETIONS | Suspicious removals | Investigate resolution |
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

**Critical rule**: Skills MUST NOT contain embedded box-drawing examples in their instructions.
Embedded boxes cause agents to manually render similar output instead of using preprocessing.

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

**Critical rule**: Formatting details (emoji meanings, box characters, column widths) belong
in preprocessing scripts, not in skill documentation.

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

> **See also:** [workflow-output.md](workflow-output.md) for clean output standards
> including pre-computation patterns and subagent batching strategies.

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

**The [BANG]`command` syntax:**

```markdown
## Example Skill

The current status:
[BANG]`cat-status --format=box`

Continue with your analysis...
```

**How it works:**
1. When Claude Code loads the skill, it scans for [BANG]`command` patterns
2. Each command executes **immediately** during skill expansion
3. The command output **replaces the placeholder** in the skill content
4. Claude receives the fully-rendered prompt with actual data

**Claude never sees the command** - only the output. This is preprocessing, not something Claude executes.

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
[BANG]`get-status-display.py`
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

**Pattern 3: Handler Preprocessing** (Python handler generates output before skill loads)

Use when the skill's output is entirely script-generated and the first-use.md is just a thin wrapper
that tells the agent to output the handler's result verbatim. The handler runs as a hook
(UserPromptSubmit or PostToolUse:Skill) and its output appears as `additionalContext` above the
skill content.

```
┌──────────────────────┐     ┌──────────────────────────┐
│   Handler            │     │   first-use.md (thin)   │
│   (Python)           │     │                          │
│                      │     │ "Output the content from │
│ Runs script/logic    │──→  │  SKILL OUTPUT X above,  │
│ Returns SKILL OUTPUT│     │  verbatim."              │
│ as additionalContext │     │                          │
└──────────────────────┘     └──────────────────────────┘
```

**Example**: Help display - handler generates full help output, first-use.md outputs it.

Handler (`skill_handlers/help_handler.py`):
```python
def handle(self, context):
    return "SKILL OUTPUT HELP DISPLAY:\n\n" + rendered_help
```

first-use.md:
```markdown
The user wants you to respond with the content from "SKILL OUTPUT HELP DISPLAY" above, verbatim.
Do NOT add any other text before or after it.

**FAIL-FAST:** If you do NOT see "SKILL OUTPUT HELP DISPLAY" above, preprocessing FAILED. STOP.
```

**first-use.md pattern for handler-preprocessed skills:**

The thin wrapper first-use.md MUST follow this exact pattern:
1. Line 1: `The user wants you to respond with the content from "SKILL OUTPUT X" above, verbatim.`
2. Line 2: `Do NOT add any other text before or after it.`
3. FAIL-FAST block if marker is missing

**Anti-pattern - meta-description that agents echo literally:**
```markdown
The handler has injected the help reference as additional context
(look for "SKILL OUTPUT HELP DISPLAY" marker above).
```
This text gets echoed to the user verbatim because the agent treats it as output content.

**Handler vs first-use.md responsibilities:**

| Aspect | Handler (runs every invocation) | first-use.md (loaded once per session) |
|--------|--------------------------------|--------------------------------------|
| Output | Fresh SKILL OUTPUT based on current args | Static behavioral instructions |
| Args | Parses args from `context["user_prompt"]` | References `$ARGUMENTS` for conditional logic |
| Changes | Different output each call | Same instructions, reused via reference.md |

For skills with argument-dependent behavior (e.g., `/cat:work` vs `/cat:work 2.1-task`), the handler
generates argument-specific SKILL OUTPUT while first-use.md contains the static conditional logic
("if ARGUMENTS contains a filter, do X"). Both work correctly on subsequent invocations: the handler
re-runs with new args, and the agent follows the already-loaded first-use.md instructions.

**When to use handler preprocessing instead of direct preprocessing:**
- Skill output requires Python logic (complex rendering, config reading, multi-script orchestration)
- Handler needs to compose output from multiple sources
- Output depends on context not available to shell scripts (e.g., parsed skill arguments)

**Decision checklist**:

| Question | If YES | Pattern |
|----------|--------|---------|
| Can script read all needed data from files/environment? | Script is self-sufficient | Direct |
| Does output depend on LLM analysis or judgment? | LLM must decide first | Delegated |
| Is the same rendering used with different data sources? | Reusable renderer | Delegated |
| Is there only one way to determine what goes in output? | No LLM needed | Direct |
| Does output need Python logic or multi-source composition? | Handler computes it | Handler |
| Is the entire skill output script-generated (thin wrapper)? | Handler + thin first-use.md | Handler |

**Benefits of handler pattern**:
- **Session-efficient**: The full skill content loads only once per session (via `load-skill.sh`).
  Subsequent invocations load a tiny reference (~2 lines) instead. The handler output (SKILL OUTPUT)
  is fresh every time, but the skill instructions are not re-loaded, saving significant context.
  After context compaction, `clear_skill_markers.py` (SessionStart hook) resets the markers so skills
  re-load in full automatically.
- Python handlers can compose output from multiple scripts, config files, and state
- Handler runs before the agent sees the skill, so output is guaranteed correct
- Agent cannot attempt manual construction - it only sees the final rendered output
- Testable: handlers are Python modules with unit tests (see `tests/` directory)

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
| `$ARGUMENTS` in [BANG] shell command | Shell interprets | **Dangerous** |

### Shell Safety with `$ARGUMENTS`

**Critical**: When `$ARGUMENTS` appears inside [BANG]`command` preprocessing, the shell
interprets special characters:

| Character | In Markdown | In Shell [BANG] |
|-----------|-------------|--------------|
| `"` | Preserved | Consumed as quote |
| `$VAR` | Literal | Expanded (empty if unset) |
| `` `cmd` `` | Literal | Executed as command |
| `'` | Preserved | Quote delimiter |

**Test results**:
```
Input: it"s complex with $VAR and `backticks`

$ARGUMENTS in markdown: it"s complex with $VAR and `backticks`  ✓ preserved
$ARGUMENTS in [BANG]`echo`: its complex with andbackticks`"`     ✗ mangled
```

### Safe Patterns

**Use `$ARGUMENTS` in markdown only** (no shell):
```markdown
## User Request

The user asked: $ARGUMENTS

Now analyze this request...
```

**For shell processing, use controlled inputs**:
```markdown
# Instead of passing user text to shell:
[BANG]`process-input.sh "$ARGUMENTS"`     # ❌ Dangerous

# Have the script read from a known source:
[BANG]`get-current-issue.sh`                # ✓ Script controls input
```

**For skill-to-skill calls**: No escaping needed - the Skill tool's `args` parameter
passes through unchanged:
```
Skill(skill="other-skill", args="text with \"quotes\" and $vars")
→ other-skill receives: text with "quotes" and $vars
```

### Checklist

- [ ] `$ARGUMENTS` only appears in markdown context, not inside [BANG] commands
- [ ] Shell scripts use controlled inputs, not raw user text
- [ ] If shell processing needed, script validates/sanitizes input first

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
  validation_score: {actual score from compare-docs}
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
Note: Running /compare-docs spawns 2 subagents for parallel extraction.
For batch operations, this can be costly.

# ❌ WRONG - Suggests overhead
This approach spawns subagents which adds context overhead.

# ✅ CORRECT - No cost language
Note: /compare-docs ensures semantic comparison by running parallel extraction.
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
Even when an internal doc (e.g., COMPRESSION-AGENT.md) contains the full algorithm, verify the
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
Instead: "Run /compare-docs and report the actual score."

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

## Checklist Before Finalizing Skill

- [ ] Frontmatter description is trigger-oriented (WHEN to use, not what it does internally)
- [ ] Description contains NO implementation details (trust levels, token costs, internal architecture)
- [ ] Description includes user synonyms for the action (e.g., "resume", "continue", "pick up")
- [ ] Goal is observable and verifiable
- [ ] All REQUIRES chains end in ATOMIC conditions
- [ ] Dependency order has no cycles
- [ ] Repeated patterns extracted as functions
- [ ] Recursive structures have function with base case + recursive case
- [ ] Variable-length functions derived via min-case → increment → generalize
- [ ] Functions listed in dependency order (no forward references)
- [ ] Forward steps call functions (no duplicated logic)

### Preprocessing Architecture

- [ ] **Architecture decision made**: Direct vs. Delegated vs. Handler preprocessing
- [ ] **Direct**: Script collects all inputs → use [BANG]`script.sh` in skill
- [ ] **Delegated**: LLM determines data → Skill A invokes Skill B with args
- [ ] **Handler**: Python handler generates SKILL OUTPUT → thin first-use.md outputs verbatim
- [ ] **Computation extracted to preprocessing scripts**
- [ ] **No manual formatting in skill** - all rendering via preprocessing
- [ ] **No embedded box drawings in skill instructions or examples**
- [ ] Box-drawing characters only appear in preprocessing scripts
- [ ] Visual patterns handled by scripts, not documented in skill
- [ ] Verification criteria exist for the goal

### Priming Prevention

- [ ] **Information ordering**: "Invoke skill" appears BEFORE any algorithm details
- [ ] **No Functions/Prerequisites sections** teaching manual construction
- [ ] **Output formats specify structure only** - no expected values (1.0, PASS, etc.)
- [ ] **No cost/efficiency language** suggesting proper approach is expensive
- [ ] **Encapsulation verified**: Orchestrator cannot perform issue after reading doc
- [ ] **Delegation-safe**: No expected scores in acceptance criteria
- [ ] **Formatting details in preprocessing scripts**, not skill doc

### Subagent Skill Preloading

When a skill spawns subagents (via Task tool), check whether those subagents would benefit from
having skills preloaded via frontmatter.

**The problem**: Subagents cannot invoke skills (Skill tool unavailable). If a subagent needs
domain knowledge from skills (git operations, validation patterns, etc.), it must receive that
knowledge through its context at startup.

**Claude Code `skills` frontmatter field**: Agents in `plugin/agents/` can specify skills to
preload into their context automatically:

```yaml
---
name: work-merge
description: Merge phase for /cat:work
tools: Read, Bash, Grep, Glob
model: haiku
skills:
  - git-squash
  - git-rebase
  - git-merge-linear
---
```

**Design decision during skill creation**:

If your skill spawns a subagent with `subagent_type: "general-purpose"`, ask:

| Question | If YES |
|----------|--------|
| Does subagent need domain knowledge (git, validation, etc.)? | Skills would benefit execution |
| Would subagent try to invoke skills if it could? | Skills should be preloaded instead |
| Is this a recurring pattern (same domain knowledge needed)? | Dedicated agent type warranted |

**When skills would benefit the subagent, use AskUserQuestion**:

```yaml
question: "The subagent needs [domain] knowledge but cannot invoke skills. How should I proceed?"
header: "Subagent Design"
options:
  - label: "Create dedicated agent type"
    description: "New agent in plugin/agents/ with skills preloaded via frontmatter"
  - label: "Embed guidance in prompt"
    description: "Include relevant skill content directly in the delegation prompt"
```

**Option 1: Create dedicated agent type** (preferred for recurring patterns):

1. Create `plugin/agents/{domain}-agent.md` with `skills` frontmatter
2. Update skill to use `subagent_type: "{domain}-agent"`
3. The agent receives skill knowledge automatically at startup

**Option 2: Embed guidance in prompt** (acceptable for one-off cases):

1. Read the relevant skill content
2. Include key guidance in the delegation prompt
3. Note: This approach doesn't scale if multiple skills invoke similar subagents

**Checklist for subagent-spawning skills**:

- [ ] **Subagent domain identified**: What knowledge does the subagent need?
- [ ] **Skills identified**: Which skills contain that knowledge?
- [ ] **Decision made**: Dedicated agent type OR embedded guidance?
- [ ] **If dedicated agent**: Agent exists in `plugin/agents/` with `skills` frontmatter?
- [ ] **If embedded**: Guidance included in delegation prompt?

---

## Conditional Section Lazy-Loading

**Principle**: Conditional sections of a skill (content only needed in certain execution paths) MUST be stored in
separate files and loaded on-demand, not embedded inline.

**Why lazy-loading matters:**
- Reduces token cost when the conditional path isn't taken
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

Files referenced **only** by a single skill MUST reside within that skill's directory:

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
