## Goal

Improve the `pre-bash` hook's redirect-path checker so that simple literal shell variable
assignments defined earlier in the same script block are expanded before the worktree-isolation
check runs.

**Current behaviour:** Any redirect (`>`, `>>`, `2>`) whose target contains a `${VAR}` or `$VAR`
reference is unconditionally rejected with:

```
WARNING: Cannot verify Bash redirect to variable-expanded path: "${VAR}"
One or more variables in the path are unset in the hook process environment.
```

This fires even when the variable is clearly defined as a literal path in the same script, e.g.:

```bash
OUT="/workspace/.cat/work/worktrees/my-issue/.cat/work/out.txt"
some-command > "${OUT}"   # ← blocked despite OUT being a known literal
```

**Desired behaviour:** Before rejecting, the hook performs a lightweight static scan of the
script text for assignments of the form `VAR="<literal>"` or `VAR='<literal>'` that precede the
redirect. If the variable resolves to a literal path, the hook substitutes it and continues with
the normal worktree-isolation check on the resolved path.

Paths that contain command substitutions (`$(...)`, `` `...` ``), arithmetic expansions, or other
non-literal forms remain rejected as before — they cannot be safely evaluated statically.

## Scope

- `BlockWorktreeIsolationViolation.java` — the Java class that implements the pre-bash hook logic
- Unit tests in `BlockWorktreeIsolationViolationTest.java`

## Post-conditions

- [ ] A redirect to `> "${VAR}"` where `VAR="/workspace/.cat/work/worktrees/my-issue/file.txt"` is
  defined earlier in the same script is allowed (path is inside the active worktree)
- [ ] A redirect to `> "${VAR}"` where `VAR` is genuinely undefined remains blocked
- [ ] A redirect to `> "${VAR}"` where `VAR` is set via command substitution (`VAR=$(mktemp)`)
  remains blocked (cannot statically resolve)
- [ ] A redirect to `> "${VAR}"` where `VAR` resolves to a path outside the active worktree is
  blocked with the existing worktree-isolation violation message
- [ ] All existing tests pass (`mvn -f client/pom.xml verify -e`)
- [ ] New unit tests cover the three new cases above
