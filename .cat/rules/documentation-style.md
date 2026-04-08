---
paths: ["*.md"]
---
## Documentation Style

**Line wrapping:** Markdown files should wrap at 120 characters.

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
