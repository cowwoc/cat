<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Design Methodology: Backward Chaining for Skills

This document describes the backward chaining methodology used to design skills and commands. Steps 1-6 guide you through:
1. State the goal
2. Decompose using backward reasoning
3. Identify leaf nodes (atomic conditions)
4. Build dependency graph
5. Extract reusable functions
6. Convert to forward steps

## Core Principle

**Backward chaining**: Start with what you want to be true, repeatedly ask "what must be true for this?", until you reach conditions you can directly achieve. Then reverse to get executable steps.

```
GOAL ← requires ← requires ← ... ← ATOMIC_ACTION
                                    ↓
                               (reverse)
                                    ↓
ATOMIC_ACTION → produces → produces → ... → GOAL
```

---

## Step 1: State the Goal

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

## Step 2: Backward Decomposition

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

**Atomic condition**: A condition that can be directly achieved by a single action or is a given input/fact.

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

## Step 3: Identify Leaf Nodes

Extract all ATOMIC conditions from the decomposition tree. These are your starting points.

**Format**:
```
LEAF NODES (atomic conditions):
1. [First atomic condition]
2. [Second atomic condition]
...
```

## Step 4: Build Dependency Graph

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

## Step 5: Extract Reusable Functions

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

## Step 6: Convert to Forward Steps

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
