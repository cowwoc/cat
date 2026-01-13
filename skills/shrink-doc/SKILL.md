---
name: shrink-doc
description: Reduce document size while preserving essential information. Use when documents exceed context limits or need condensing.
---

# Shrink Document Skill

**Purpose**: Reduce document size while preserving essential information for efficient context usage.

## Execution Model

**MANDATORY: Always spawn subagent for bulk operations.**

| Scope | Execution |
|-------|-----------|
| Single file | Main agent OK |
| 2-5 files | Subagent recommended |
| 6+ files | **Subagent required** |

For bulk operations:
```
/cat:spawn-subagent with prompt:
"Shrink all MD files in {directory} using shrink-doc strategies.
Target: 40-60% reduction. Preserve all essential information."
```

## When to Use

- Document exceeds context window limits
- Need to fit more content in limited space
- Creating summaries for handoffs
- Optimizing documentation for AI consumption

## Shrinking Strategies

### Strategy 1: Remove Redundancy

**Before:**
```markdown
## Installation

To install the package, you need to run the installation command.
The installation command is `npm install`. Running this command
will install the package and all its dependencies.
```

**After:**
```markdown
## Installation

```bash
npm install
```
```

### Strategy 2: Condense Examples

**Before:**
```markdown
## Examples

### Example 1: Basic Usage
Here's a basic example showing how to use the function:
```javascript
const result = myFunction("hello");
console.log(result);
```

### Example 2: Advanced Usage
Here's an advanced example with more options:
```javascript
const result = myFunction("hello", { option: true });
console.log(result);
```
```

**After:**
```markdown
## Examples

```javascript
// Basic
myFunction("hello");

// With options
myFunction("hello", { option: true });
```
```

### Strategy 3: Use Tables Instead of Lists

**Before:**
```markdown
### Options

- `name`: The name of the item. Required.
- `type`: The type of item. Can be "a", "b", or "c". Defaults to "a".
- `count`: Number of items. Must be positive. Optional.
```

**After:**
```markdown
| Option | Description | Default |
|--------|-------------|---------|
| name | Item name (required) | - |
| type | "a"\|"b"\|"c" | "a" |
| count | Positive number | optional |
```

### Strategy 4: Remove Obvious Comments

**Before:**
```javascript
// Import the library
import { lib } from 'library';

// Create a new instance
const instance = new lib();

// Call the method
instance.method();
```

**After:**
```javascript
import { lib } from 'library';
const instance = new lib();
instance.method();
```

### Strategy 5: Inline Short Explanations

**Before:**
```markdown
## Configuration

The configuration file is located at `.config/settings.json`.

You need to set the following values:
- API key
- Base URL
```

**After:**
```markdown
## Configuration

Set in `.config/settings.json`: `apiKey`, `baseUrl`
```

## Shrinking Process

1. **Measure original size**: `wc -l document.md`

2. **Identify removable content**:
   - Verbose explanations of obvious things
   - Duplicate examples showing same concept
   - Excessive whitespace/blank lines
   - Comments that restate the code

3. **Apply strategies** (in order of impact):
   - Remove redundancy (highest impact)
   - Condense examples
   - Convert to tables
   - Remove obvious comments
   - Inline short explanations

4. **Verify essentials preserved**:
   - All key concepts present
   - Critical examples remain
   - Required information intact

5. **Measure final size**: `wc -l document-shrunk.md`

## Size Targets

| Document Type | Target Reduction |
|---------------|------------------|
| Verbose docs | 50-70% |
| Reference docs | 30-50% |
| Code examples | 40-60% |
| API docs | 30-40% |

## Quick Wins

- Remove "This section describes..." openings
- Remove "In this example, we will..." preambles
- Remove "As you can see..." explanations
- Combine related short sections
- Use abbreviations where clear (e.g., "config" for "configuration")
