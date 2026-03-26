# LLM-to-Java Extraction Policy

**MANDATORY goal:** When adding new features to the plugin or updating existing features, extract as much
logic as possible from LLM skills (Markdown instructions, referenced files) into Java code.

The aim is to make processes deterministic wherever possible. Only leave in the LLM layer:
- Contextual decision-making that genuinely requires reasoning
- Text output to the terminal

## What belongs in Java

| Logic type | Examples |
|------------|---------|
| File reads and writes | Reading/writing `config.json` |
| Data derivation | Mapping questionnaire answers to config values |
| Validation | Checking that a value is within an allowed set |
| Display formatting | Rendering settings boxes, progress banners |
| JSON manipulation | Merging a new value into an existing JSON object |

## What stays in the LLM

| Logic type | Examples |
|------------|---------|
| Presenting menus | `AskUserQuestion` dialogs |
| Navigation between steps | Routing based on user selection |
| Generating novel text | Summaries, explanations, commit messages |
| Contextual reasoning | Choosing an approach based on issue context |

## Pattern

When designing a skill step that reads a file, transforms data, and writes a result:

1. Implement the read-transform-write logic as a Java CLI tool
2. The skill invokes the tool via Bash and passes user input as arguments
3. The tool returns structured output (JSON or plain text) that the skill uses for next-step routing

**Example:** Instead of the LLM reading `config.json`, mentally merging a new value, and writing it back,
a Java CLI tool handles the merge atomically:

```bash
update-config trust=medium caution=high
```

**Why:** LLM-driven file manipulation is error-prone (JSON formatting, dropped keys, hallucinated values)
and wastes tokens on deterministic work. Java handles it correctly every time and is faster.
