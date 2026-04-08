# Terminology Consistency Convention

## Rule

When the same concept is named, valued, or described in multiple places in the plugin, all occurrences
must use identical terminology. No paraphrasing, synonyms, or partial overlap.

## Scope

This applies to **configuration options** — the most common source of inconsistency — but the rule is
general: any named concept that appears in both code and user-facing text must be expressed identically.

### Configuration Options

Config option names, enum value names, and their descriptions must be **identical** across:

| Location | Example |
|----------|---------|
| Java enum Javadoc | `/** Approve at merge gate only. */` on `TrustLevel.MEDIUM` |
| Config wizard option label | `Medium` |
| Config wizard option description | `Approve at merge gate only.` |
| `worktree-isolation.md` effective config reference | `"trust": "medium"` |
| `init/first-use.md` default config JSON | `"trust": "medium"` |
| Any skill or agent documentation that describes the option | same phrasing |

**Correct:**

```java
/**
 * Approve at merge gate only.
 */
MEDIUM,
```

Wizard text: `Medium — Approve at merge gate only.`

**Incorrect (paraphrase):**

```java
/**
 * One approval gate before merging.
 */
MEDIUM,
```

Wizard text: `Medium — Approve once, before merge.`

## When to Apply

- Adding a new config option or enum value: write the description once, copy it everywhere
- Modifying an existing description: update ALL occurrences in the same commit
- Reviewing a config-related change: verify that Javadoc and wizard text match exactly before approving

## Enforcement

When implementing or reviewing config option changes, check that:
1. The Java enum Javadoc description matches the config wizard description word-for-word
2. The config key name in `config.json` matches the field name used in skills and documentation
3. Enum value names (e.g., `LOW`, `MEDIUM`, `HIGH`) match the lowercase values in JSON (`"low"`, `"medium"`, `"high"`)
