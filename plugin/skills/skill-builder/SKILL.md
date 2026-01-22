---
name: skill-builder
description: "Use BEFORE creating or updating any skill - decomposes goal into forward steps via backward reasoning"
---

# Skill Builder

## Purpose

Design or update skills by reasoning backward from the goal to required preconditions,
then converting to forward-execution steps.

---

## When to Use

- Creating a new skill
- Updating an existing skill that has unclear or failing steps
- Any procedure where the goal is clear but the path is not

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
    Logic: Sum character widths (emoji=2, others=1)
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

The description tells WHEN to invoke the skill, not just what it does.

```
Format: "[WHEN to use] - [what it does]"

Examples:
  "MANDATORY: Load BEFORE rendering any box output"
  "Use BEFORE creating or updating any skill - decomposes goal into forward steps"
  "MANDATORY: Use for approval gate reviews - transforms git diff into table"
  "Use when user requests git rebase - provides automatic backup and recovery"
```

**Trigger patterns**:
- `MANDATORY: Load BEFORE [action]` - Must be loaded before doing something
- `MANDATORY: Use for [situation]` - Required for specific scenarios
- `Use BEFORE [action]` - Should be used before doing something
- `Use when [condition]` - Triggered by a specific situation
- `Use instead of [alternative]` - Replaces a dangerous or complex operation

---

## Template Output

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

## Functions

List functions in dependency order (functions with no dependencies first).

### function_name(inputs) → output

**Purpose**: [What it computes]

**Definition**:
```
[Algorithm or formula derived from decomposition]
```

**Example**:
```
function_name(example_input) = expected_output
```

### composed_function(inputs) → output

**Purpose**: [Higher-level operation that uses other functions]

**Definition**:
```
1. intermediate = other_function(inputs)
2. result = transform(intermediate)
3. return result
```

### recursive_function(structure) → output

**Purpose**: [Operation that applies to nested structures]

**Definition**:
```
Base case: if structure is atomic, return direct_result
Recursive case:
  1. Process current level
  2. For each nested element: recursive_function(element)
  3. Combine results
```

---

## Procedure

Steps call functions rather than duplicating logic.

### Step 1: Require pre-computed results (if computation was extracted)

**MANDATORY:** Check context for "PRE-COMPUTED [SKILL-NAME]".

If found:
1. Output the pre-computed content **directly without preamble or commentary**
2. Skip to Step N (output/verification)

**Silent output rule (M194):** When outputting pre-computed content, do NOT:
- Announce "I can see the pre-computed display..."
- Explain "Let me output this exactly as provided..."
- Add any text before the pre-computed content

Simply output the content. The user doesn't need to know about internal computation mechanisms.

If NOT found: **FAIL** with:
```
ERROR: Pre-computed results not found.
Hook precompute-{skill-name}.sh should have provided these.
Do NOT attempt manual computation.
```

### Step 2: [Gather inputs] (only if no extraction)

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

**Task**: Create a skill for calculating the area of a rectangle from user input.

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

**Task**: Create a skill for rendering aligned boxes with emoji support.

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
              ATOMIC: Use width lookup (emoji → 2, other → 1)
        REQUIRES: content_width is known for THIS item
          REQUIRES: display_width calculated for this item          ← Same as above!
      REQUIRES: borders are fixed width (4)
        ATOMIC: Use "│ " prefix + " │" suffix
```

### Step 3: Leaf Nodes

```
1. Width lookup table (emoji → 2, other → 1)
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
width = 0
for each char in text:
  if char in [☑️, 🔄, 🔳, 📊, ...]: width += 2
  else: width += 1
return width
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
# BAD - Fallback hides hook failure
If pre-computed exists: use it
Else: compute manually (error-prone!)

# GOOD - Fail-fast exposes problems
If pre-computed exists: use it
Else: FAIL with "Hook failed - check precompute-status-display.sh"
```

**Why fail-fast is better:**
- Fallback to manual computation defeats the purpose of extraction
- Silent degradation makes debugging harder
- Errors in fallback path are often worse than no output
- Forces fixing the root cause (broken hook) rather than masking it

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

**MANDATORY CALCULATION GATE (M191):**

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

### Output Artifact Gates (M192 Prevention)

**Critical insight**: Calculation gates alone are insufficient. When a skill produces structured
output (boxes, tables, formatted text), the gate must require showing the **exact artifact strings**
that will appear in the output, not just the numeric calculations.

**The failure pattern (M192)**:
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

**MANDATORY BUILD RESULTS GATE (M192):**

Before writing final output, verify:
- [ ] Each artifact (line, cell, row) has an explicit string recorded above
- [ ] Padding/spacing counts are noted in parentheses
- [ ] Final output will COPY these exact strings (no re-typing)

**BLOCKING:** If Step 4 does not contain explicit artifact strings, STOP and complete
Step 4 before proceeding. Re-typing output causes errors even when calculations are correct.
```

**Key distinctions**:
| Calculation Gate (M191) | Artifact Gate (M192) |
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

### Extracting Computation to External Scripts (M192 Prevention)

**Critical insight**: When a skill contains functions that perform deterministic computation
(algorithms, formulas, calculations), these are candidates for extraction to external scripts
that run via hooks. This prevents the agent from "computing in context" and re-typing results
from memory.

**Identify computation candidates during function extraction (Step 5):**

```
For each function identified, ask:
1. Is the output deterministic given the inputs?
2. Could the agent get the wrong result by "thinking" instead of computing?
3. Does the function involve precise formatting, counting, or arithmetic?

If YES to all three → Extract to external script
```

**Computation candidate signals**:
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

### MANDATORY: Planning Verification Checklist (M198)

**BLOCKING:** Before recommending ANY approach for a skill-builder rewrite, complete this checklist:

```yaml
extraction_verification:
  # Check each computation candidate signal against the skill
  signals_present:
    counting_chars_widths: true|false  # display_width, strlen, emoji counting
    arithmetic_with_vars: true|false   # padding = max - width, calculations
    building_formatted_strings: true|false  # "│ " + content + " │", table rows
    aggregating_collections: true|false     # max(widths), sum, counts

  # If ANY signal is true, extraction is REQUIRED
  extraction_required: true|false

  # Verify recommendation matches requirement
  recommendation_valid:
    if_extraction_required_and_recommending_simplified: INVALID
    if_extraction_required_and_recommending_hooks: VALID
    if_no_extraction_and_recommending_simplified: VALID
```

**Anti-pattern (M198):** Recommending "simplified rewrite without hooks" based on content type
(ASCII vs emoji) rather than computation type. The extraction criteria are about WHAT OPERATIONS
the skill performs (counting, arithmetic, formatting), NOT what characters appear in the output.

**Anti-pattern (M203):** Acknowledging extraction is required, then overriding with case-specific
reasoning. Common override patterns to REJECT:
- "HOWEVER, consider that this only runs once..."
- "BUT for this specific case, hooks are overkill..."
- "Given the static content, we can simplify..."
- "The complexity cost outweighs the benefit..."

**If signals show extraction_required: true, the determination is FINAL.** Do not add "HOWEVER"
exceptions. The methodology exists precisely because case-specific reasoning leads to errors.
Frequency of execution, content type, and perceived complexity are NOT valid override reasons.

**Example - Token-report skill:**
```yaml
# WRONG analysis (led to M198):
"Table contents are ASCII-only, so hooks are overkill"

# CORRECT analysis:
signals_present:
  counting_chars_widths: true      # Column widths must be calculated
  arithmetic_with_vars: true       # padding = column_width - content_width
  building_formatted_strings: true # "│ Type            │ Description..."
  aggregating_collections: true    # Total tokens = sum of subagent tokens

extraction_required: true  # 4/4 signals present
recommendation: "Full hook-based pre-computation (Approach A)"
```

**When computation candidates exist, generate three artifacts:**

**1. External Script** (Python or Bash):
```python
# scripts/{skill-name}-compute.py
# Implements the deterministic functions identified in Step 5

def function_name(inputs):
    # Exact algorithm from decomposition
    return computed_result

if __name__ == "__main__":
    # Accept inputs, output JSON results
    result = function_name(parse_inputs())
    print(json.dumps(result))
```

**2. UserPromptSubmit Hook**:
```bash
# hooks/precompute-{skill-name}.sh
# Runs when user invokes the skill, provides pre-computed results

# Detect skill invocation
if [[ ! "$USER_PROMPT" =~ /skill-name ]]; then
    echo '{}'; exit 0
fi

# Run computation script
RESULT=$(python3 "$PLUGIN_ROOT/scripts/{skill-name}-compute.py" "$INPUTS")

# Return via additionalContext
output_hook_message "UserPromptSubmit" "PRE-COMPUTED RESULT:\n$RESULT\n\nUse exactly as shown."
```

**Hook output formatting for multiline results:**

When building multiline output strings in shell scripts, use backslash (`\`) line continuations
to make the script more readable and debuggable:

```bash
# BAD - Hard to read, hard to debug
OUTPUT=$(cat << EOF
PRE-COMPUTED STATUS:

${BOX_OUTPUT}

Next steps: ${NEXT_STEPS}
EOF
)

# GOOD - Readable with clear structure
OUTPUT="PRE-COMPUTED STATUS:\n\n" \
OUTPUT+="${BOX_OUTPUT}\n\n" \
OUTPUT+="Next steps:\n" \
OUTPUT+="${NEXT_STEPS}"

# ALSO GOOD - Explicit line building
OUTPUT=""
OUTPUT+="PRE-COMPUTED STATUS:\n"
OUTPUT+="\n"
OUTPUT+="${BOX_OUTPUT}\n"
OUTPUT+="\n"
OUTPUT+="Next steps:\n"
OUTPUT+="${NEXT_STEPS}"
```

**Benefits of explicit line building:**
- Easier to see structure at a glance
- Simpler to add/remove/reorder sections
- Debugging shows which line caused issues
- Git diffs are more meaningful

**3. Skill Preamble** (add to generated skill - FAIL-FAST, not fallback):
```markdown
### Step 1: Require pre-computed results

**MANDATORY:** Check context for "PRE-COMPUTED RESULT".

If found: Use those values exactly as provided, skip to output step.

If NOT found: **FAIL immediately** with message:
```
ERROR: Pre-computed results not found in context.

The hook (precompute-{skill-name}.sh) should have provided these.
Check:
1. Hook is registered in hooks.json
2. Hook script exists and is executable
3. Hook ran without errors (check hook output above)

Do NOT attempt manual computation - it will produce incorrect results.
```

**Why fail-fast?** Manual computation was extracted precisely because agents
cannot do it reliably. Falling back to manual defeats the purpose and hides
hook failures.
```

**Example - Box alignment extraction**:

```
Functions identified in Step 5:
  display_width(text) → integer     ← COMPUTATION CANDIDATE
  max_content_width(items) → int    ← COMPUTATION CANDIDATE
  build_line(content, max) → string ← COMPUTATION CANDIDATE

Generate:
  1. scripts/build-box-lines.py     - implements all three functions
  2. hooks/precompute-box-lines.sh  - runs script, returns via additionalContext
  3. Skill preamble                 - REQUIRES pre-computed lines (fail-fast)

Result: Agent receives exact computed strings, cannot corrupt them by re-typing.
If hook fails, skill fails immediately - no silent degradation.
```

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
  Extract to external script + hook
         │
  Skill REQUIRES pre-computed result (fail-fast if missing)
```

---

## Checklist Before Finalizing Skill

- [ ] Frontmatter description is trigger-oriented (WHEN to use, not just what it does)
- [ ] Goal is observable and verifiable
- [ ] All REQUIRES chains end in ATOMIC conditions
- [ ] Dependency order has no cycles
- [ ] Repeated patterns extracted as functions
- [ ] Recursive structures have function with base case + recursive case
- [ ] Variable-length functions derived via min-case → increment → generalize
- [ ] Functions listed in dependency order (no forward references)
- [ ] Forward steps call functions (no duplicated logic)
- [ ] **Computation candidates identified and extracted** (M192)
- [ ] External script created for deterministic functions (if any)
- [ ] UserPromptSubmit hook created to pre-compute (if any)
- [ ] **Skill REQUIRES pre-computed results - FAIL-FAST if missing** (no fallback)
- [ ] **No "if not found, continue to manual..." patterns** (fail-fast principle)
- [ ] **Calculation gates added for transformation steps** (M191)
- [ ] **Artifact gates added when output has precise formatting** (M192)
- [ ] Gates use MANDATORY and BLOCKING keywords
- [ ] Calculation gates require explicit numeric results before construction
- [ ] Artifact gates require explicit output strings before final assembly
- [ ] Verification criteria exist for the goal
