# Comprehensive Reference Checking on Renames

When renaming a concept, section header, file, or any named identifier across the plugin, you MUST
grep for all occurrences before considering the rename complete.

## Required Grep Step

After making the rename in target files, run with **case-insensitive** flag to catch all forms:

```bash
grep -rni "<old-term>" plugin/ --include="*.md"
```

Review every hit. For each occurrence:
- If it refers to the renamed concept: update it
- If it is a different concept that happens to use the same word: leave it unchanged

## What to Search For

Search for the root word AND common compound phrases:

| Old term | Also search for |
|----------|-----------------|
| `wave` | `waves`, `wave split`, `wave structure`, `execution waves` |
| `agent` | `agent splitting`, `agent sizing`, `agent batch`, `parallel agent` |

**Do NOT** rely on matching only structural markers like `### Wave N` or `## Sub-Agents`. Prose
references — parenthetical labels, verb phrases, inline descriptions — will be missed:

- ❌ Searches only: `grep "### Wave"`  — misses "Proactive Wave Split", "wave structure"
- ✅ Searches broadly: `grep -i "wave"` — catches all capitalizations and compound phrases

## Scope

- **Changed files**: Always grep changed files for leftover old-term references
- **All plugin files**: Also grep the full `plugin/` tree — cross-references to renamed concepts
  appear in files you did not edit (concept docs, skill instructions, agent rules)

## When to Run

Run the grep **immediately after** the rename subagent completes — before squashing or presenting
an approval gate. Do not defer until the user reports missed instances.

## Why

Renames that only update structural markers leave stale prose references in:
- Parenthetical section labels ("Proactive Wave Split")
- Verb phrases ("use agent splitting to manage context")
- Inline descriptions ("parallel agent execution model")
- Cross-file references in concept docs and skill instructions

Stale references cause confusion when the old term no longer matches the concept it describes.
