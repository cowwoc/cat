## Documentation Style

Apply these terminology and explanation rules to agent responses, not just generated files. When answering in chat or
writing technical documentation, define uncommon terms before relying on them, or link clearly to the file, class, or
other local source that defines the term.

**Line wrapping:** Markdown files should wrap at 120 characters.

**Define uncommon terms.** When writing technical documentation, including Markdown and Javadoc, define uncommon
technical terms the first time they are used unless a nearby authoritative definition already exists.

**Prefer links over repetition when appropriate.** It is acceptable to link to the file, class, or other local source
that defines a term instead of repeating the full definition inline every time the term is used. When relying on a
link instead of an inline definition, make sure the reference is specific enough that the reader can resolve the term
without guesswork.

**Explain concepts before relying on them.** Do not assume readers already understand a concept that is central to the
documentation. Give a short definition, context, or concrete example before building on it, unless you provide a
clear pointer to the canonical definition elsewhere in the codebase or docs.

**Automated tests:** Do not assert documentation or skill prose content. README files, documentation files, rules,
skills, and prompt text are user-facing or agent-facing instructions whose wording should be reviewed directly, not
locked by brittle content assertions. Automated tests should target underlying source files, generated artifact
structure, schemas, metadata, and observable behavior instead.

**No retrospective commentary.** Do not add documentation or comments that discuss:
- What was changed or implemented
- What was removed or refactored
- Historical context of modifications

This applies to all file types, including Java Javadoc, inline comments, and Markdown documentation.

**Example:**
```java
// Bad - describes what was done historically
* <li>{@code {sessionDir}/skills-loaded-*} — legacy flat-file markers (cleaned up for migration)</li>

// Good - describes current behavior
* <li>{@code {sessionDir}/skills-loaded-*} — legacy flat-file markers (deleted when found)</li>
```

**Exception:** Files specifically designed for history tracking (e.g., `changelog.md`).

**Rationale:** Code and documentation should describe current state and intent, not narrate their own evolution. Git
history provides the authoritative record of changes.
