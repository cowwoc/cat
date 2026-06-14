### Test Access Seams

Shared-secret or other test-only access seams must be registered by the class that owns the behavior under test.
Do not route tests for engine-specific helper methods through an unrelated common orchestrator.

Keep shared test access methods named after the behavior they expose, not after the incidental caller that currently
reaches it.

### Do Not Test Non-Code File Contents

Do not add tests that verify the literal contents of non-code files such as Markdown instructions, documentation,
plans, rules, skills, or concepts. This includes tests that scan `.md` files for frontmatter keys, phrases, include
targets, section names, or source-layout conventions.

If a non-code file affects engine behavior, test the executable behavior that consumes it instead. For example, test
that an artifact builder produces the expected engine artifact from synthetic inputs, or that a parser rejects invalid
synthetic content. Do not test that repository Markdown files contain or omit specific text.

### Do Not Add Retrospective Documentation Tests

Do not add tests that merely document a change after the fact. A test belongs in the suite only when it would catch a
real behavioral regression that matters to users or callers. Avoid tests whose only value is proving that a recently
removed implementation detail, retired artifact layout, or historical packaging choice remains absent when no engine
behavior is exercised.
