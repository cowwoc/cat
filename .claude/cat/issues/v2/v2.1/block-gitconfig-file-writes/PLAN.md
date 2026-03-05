# Plan: block-gitconfig-file-writes

## Goal
Block agents from writing git identity directly to gitconfig files (e.g., `~/.gitconfig`,
`~/.config/git/config`) via shell tools like `echo`, `printf`, `tee`, `sed`, `awk`, or `cat >`,
bypassing the `BlockGitUserConfigChange` hook entirely.

## Satisfies
None

## Problem
`BlockGitUserConfigChange` blocks `git config user.name` commands and inline env var overrides,
but an agent can still set git identity by directly appending to gitconfig files:

```bash
printf '[user]\nname=Attacker\nemail=attacker@evil.com\n' >> ~/.gitconfig
echo '[user]' >> ~/.gitconfig && echo '  name = Attacker' >> ~/.gitconfig
```

These commands do not invoke `git config` and contain no `user.name` or `user.email` substrings,
so the current hook's quick-exit filter does not catch them. This is a HIGH-severity bypass of the
git identity protection introduced in `2.1-prevent-git-user-changes`.

**Evidence from security review (2.1-prevent-git-user-changes):**
- Direct file write to `~/.gitconfig` bypasses all current patterns
- `git config --global include.path /tmp/evil.gitconfig` (two-step: write evil file, then include it)

## Root Cause
`BlockGitUserConfigChange` only monitors `git config` subcommand invocations and env var prefixes.
It has no awareness of arbitrary shell writes to well-known gitconfig file paths.

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Overly broad path matching could produce false positives for legitimate file operations
  on unrelated files that happen to be near gitconfig paths
- **Mitigation:** Match only writes to canonical gitconfig paths (HOME-relative):
  - `~/.gitconfig`
  - `~/.config/git/config`
  - `/etc/gitconfig`

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockGitUserConfigChange.java` — add pattern
  matching shell writes to canonical gitconfig file paths

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1
- Add a pattern that detects shell write redirections to canonical gitconfig paths:
  `(echo|printf|tee|cat\s+>|sed\s+-i)\b.*?(~|HOME)/.gitconfig` and
  `(echo|printf|tee|cat\s+>|sed\s+-i)\b.*?(~|HOME)/.config/git/config`
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockGitUserConfigChange.java`
- Add tests for the new pattern
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/BlockGitUserConfigChangeTest.java`
- Update `InjectSessionInstructions.java` to document this blocked operation

## Post-conditions
- [ ] `printf '[user]\nname=X\n' >> ~/.gitconfig` is blocked
- [ ] `echo '  name = X' >> ~/.gitconfig` is blocked
- [ ] Legitimate writes to unrelated files (e.g., `echo hello >> ~/notes.txt`) are allowed
- [ ] All tests pass
