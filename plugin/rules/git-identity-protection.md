---
mainAgent: true
---
## Git Identity Protection
**CRITICAL**: NEVER modify `git config user.name` or `git config user.email` without explicit
user instruction.

Silently overwriting git commit identity changes authorship on every future commit, which is a
permanent and highly visible side effect that users do not expect.

**Allowed** (read-only):
```bash
git config user.name       # read current name
git config user.email      # read current email
git config --get user.name
```

**BLOCKED** (requires explicit user instruction):
```bash
git config user.name "Alice"          # set name
git config user.email "alice@example.com"  # set email
git config --global user.name "Alice"  # global scope
git config --unset user.name           # unset
git config --unset-all user.email      # unset all
git config --remove-section user       # remove section
git -c user.name=Alice commit ...      # inline override
GIT_AUTHOR_NAME=Alice git commit ...   # env var override
git commit --author='Alice <a@b.com>'  # commit --author flag
echo '[user]' >> ~/.gitconfig          # direct file write
printf '[user]\nname=X\n' >> ~/.gitconfig  # direct file write
tee ~/.gitconfig                       # direct file write
sed -i 's/name/X/' ~/.gitconfig        # in-place file edit
awk '{...}' > ~/.gitconfig             # direct file write
cat > ~/.gitconfig                     # direct file write
```

**When to write**: Only when the user explicitly says something like "set my git user name to X"
or "update my git email".
