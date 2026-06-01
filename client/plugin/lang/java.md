<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Java Red Flags

## Performance
| Pattern | Issue | Fix |
|---------|-------|-----|
| String `+` in loops | Creates objects each iteration | `StringBuilder` |
| `new` in tight loops | Unnecessary object creation | Cache or primitives |
| Missing `final` on cacheable | Recomputed each access | Cache in `final` field |
| `synchronized` in hot paths | Contention bottleneck | Lock-free alternatives |

## Security
| Pattern | Issue | Fix |
|---------|-------|-----|
| SQL string concat | Injection vulnerability | `PreparedStatement` |
| `ObjectInputStream.readObject()` on untrusted | Deserialization attack | Validate/whitelist |
| Hardcoded "password"/"secret"/"apikey" | Credential exposure | External config |

## Quality
| Pattern | Issue |
|---------|-------|
| Empty `catch` blocks | Silent failures |
| Raw types (`List` not `List<String>`) | Type safety loss |
| Mutable `static` fields | Thread safety risk |

## Testing
| Pattern | Issue |
|---------|-------|
| `Thread.sleep()` in tests | Flaky timing dependency |
| Missing `@Test` | Test won't execute |
| `assertEquals` with floats | Needs delta parameter |
| `try { call(); fail(); } catch (ExpectedException e) { ... }` in TestNG tests | Prefer `@Test(expectedExceptions = ExpectedException.class, expectedExceptionsMessageRegExp = "...")` |
| Common/engine-neutral tests reference engine-specific identifiers (for example `claude-runner`, `codex-runner`, engine class names) | Breaks neutrality boundary; move these assertions to engine-specific test modules |

## Architecture
| Pattern | Issue |
|---------|-------|
| Circular package deps | A imports B, B imports A |
| God classes | 20+ methods or 500+ lines |
| Static-only utility classes | Should be instance methods |
| Common module/package/class references engine-specific names or types (including via reflection or `ServiceLoader`) | Violates layer boundaries; creates hidden coupling |
| Common module contains `main()` for a class that has engine-specific equivalents | Entry-point ownership belongs to engine layer |

## Correctness
| Pattern | Issue | Fix |
|---------|-------|-----|
| `!Files.exists(path)` | True for both "doesn't exist" and "can't determine" (permissions) | `Files.notExists(path)` |
| `String.trim()` | Only handles ASCII whitespace (≤ U+0020), misses Unicode whitespace | `String.strip()` |
| `"<literal>".equals(value)` | Reverses natural operand order for null safety | `Objects.equals(value, "<literal>")` |

## Style
| Pattern | Issue | Fix |
|---------|-------|-----|
| `i += 1` | Hides intent for simple increment/decrement | `++i` (or `i++` when expression semantics require postfix) |
| `i -= 1` | Hides intent for simple increment/decrement | `--i` (or `i--` when expression semantics require postfix) |
| Test package names with internal `.test.` component (for example `a.b.test.c`) | Inconsistent package topology | End test package names with `.test` (for example `a.b.c.test`) |

## Javadoc Terminology
Project rule (required): Javadoc must be understandable to a reader who is new to this codebase. Never assume familiarity with internal/project-specific terms.
Assume users/readers are unfamiliar with project jargon unless that term is defined in the same Javadoc block or linked to its canonical definition.

Terminology clarity requirement: for every project-specific term, Javadoc must either define it in place or link to the canonical definition section in another file. Do not leave jargon unexplained.
This explicitly includes terms like `setup-input`, `topic`, `topic files`, `carry-forward`, and other workflow-specific labels.

Required behavior: every project-specific term in Javadoc (for example workflow labels, abbreviations, and acronyms such as `setup-input`, `SPRT`, `topic files`, `carry-forward`) must do one of the following at first use in that Javadoc block:
1. Define the term inline in that Javadoc block.
2. Link to the exact canonical file/section that defines the term.

If no canonical definition exists yet, create one in project docs first, then link to that new canonical section.
This applies to class docs, method docs, field docs, and all tags (`@param`, `@return`, `@throws`, `@see`, etc.).
This is mandatory for all project-specific terminology.
Review enforcement: any Javadoc that uses project-specific terminology without an inline definition or canonical link is non-compliant and must be fixed before merge.

| Pattern | Issue | Fix |
|---------|-------|-----|
| Javadoc uses any project-specific term (for example `setup-input`, `topic files`, `carry-forward`) without definition | Readers cannot infer exact meaning, causing misuse and brittle maintenance | Javadoc **must** define every project-specific term at first use, or link to the canonical file/section that defines it |
| Javadoc assumes reader familiarity with internal workflow jargon | New contributors and external reviewers misinterpret behavior and constraints | Do **not** assume familiarity: include a short in-place definition or a direct link to the canonical terminology file/section |
| Javadoc links to a vague location (for example just a directory or repo root) instead of a canonical definition | Readers still cannot resolve terminology precisely | Link to the exact defining document/section (for example a specific skill/rule section or glossary heading), not a broad parent location |
