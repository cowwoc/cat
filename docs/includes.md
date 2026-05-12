<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Rule Includes

CAT supports a source-only include directive for project rules:

```markdown
<!-- cat:include relative/path.md -->
```

CAT replaces the directive with the target file's contents before injecting rules. Plugin release builds use the same
directive for source-only authoring files, but end-user preprocessing is limited to `.cat/rules`.

Relative paths are resolved from the directory of the file that contains the directive, not from the current working
directory or repository root. For example, `.cat/rules/common/java.md` can include
`.cat/rules/common/fragments/imports.md` with:

```markdown
<!-- cat:include fragments/imports.md -->
```

Use includes for content that is always loaded but split into separate files for maintainability. Do not use includes
for optional references, tests, examples, or large background material that an agent should read only when needed.

Includes are confined to the source tree being processed. A project rule may include files under the same `.cat/rules`
tree. Absolute include paths are rejected.
