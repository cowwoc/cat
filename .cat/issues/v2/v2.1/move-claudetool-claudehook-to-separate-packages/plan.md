# Plan

## Goal

Move all classes that use `ClaudeTool` to `io.github.cowwoc.cat.claude.tool` package and all classes that use
`ClaudeHook` to `io.github.cowwoc.cat.claude.hook` package. This separates the two execution contexts (hooks vs tools)
at the package level, making the architecture more explicit.

## Pre-conditions

- [ ] Issue `2.1-extract-plugin-root-from-jvmscope` is closed (scope hierarchy refactored)

## Post-conditions

- [ ] All classes whose scope parameter or constructor takes `ClaudeTool` are in `io.github.cowwoc.cat.claude.tool`
- [ ] All classes whose scope parameter or constructor takes `ClaudeHook` are in `io.github.cowwoc.cat.claude.hook`
- [ ] Shared interfaces and abstract classes (`JvmScope`, `AbstractJvmScope`, `ClaudeStatusline`,
      `AbstractClaudeStatusline`) remain in `io.github.cowwoc.cat.hooks` (or an appropriate shared package)
- [ ] All imports updated across the codebase
- [ ] `module-info.java` updated with new package exports
- [ ] Tests passing (`mvn -f client/pom.xml verify -e` exit code 0)
- [ ] No remaining references to old package paths for moved classes
