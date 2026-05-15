# Plan

## Goal

Update `$cat:help` to return the finalized terminal-first ANSI styled help text exactly as approved in the
conversation. Update Claude's slash-command help output to return equivalent content, replacing dollar-prefixed
commands with slash-prefixed commands.

## Type

feature

## Force Stakeholders

- ux
- business

## Pre-conditions

(none)

## Post-conditions

- [x] `$cat:help` returns the exact approved content below as its final assistant response.
- [x] Claude's help output returns equivalent content with command examples using slash prefixes instead of dollar
  prefixes.
- [x] Claude's help output does not include `/cat:uninstall`.
- [x] The output uses raw SGR ANSI truecolor codes outside code fences so the Codex terminal can render colors.
- [x] Section headers use bold white.
- [x] The horizontal divider uses the amber-to-rose gradient.
- [x] Commands and example branch names use the rose color from the approved palette, not green.
- [x] The help output includes prompt examples that trigger adding/removing issues or versions instead of listing
  `add`, `remove`, or `work` as top-level commands.
- [x] The help output includes `uninstall` in the Codex `$cat:*` output.
- [x] The help output includes `learn` and `optimize-execution` under `Advanced`.
- [x] The `Personality traits` section does not include a separate `Values: low, medium, high` line.
- [x] The `Branch names` section describes support for `MAJOR`, `MAJOR.MINOR`, and `MAJOR.MINOR.PATCH` versioning
  schemes and says issue and branch names map to `<version>-title`.
- [x] The help output remains readable if ANSI color is stripped.
- [x] Project conventions forbid Java/TestNG tests from validating skill-file contents.
- [x] This implementation does not add Java/TestNG assertions over `client/plugin/skills/**` prose, examples, command
  lists, ANSI text, or other skill-file content.
- [x] Codex and Claude help share the common help body through `cat:include` fragments.
- [x] Runtime help output uses `${CAT_COMMAND_PREFIX}` in shared fragments and resolves it at build time.
- [x] The Codex-only uninstall row remains in the Codex-specific help file rather than a shared placeholder row.
- [x] Artifact-builder tests validate runtime placeholder replacement behavior without asserting real skill-file
  contents.

## Approved Output

The literal output should be:

```text
[1;37mCAT help[0m

[38;2;255;214;102m━━━━━━━━[38;2;255;180;118m━━━━━━━━[38;2;255;142;146m━━━━━━━━[38;2;235;116;164m━━━━━━━━━━━━━━━━━━━━[0m

[1;37mWhat CAT does[0m
  CAT organizes your work, augments code quality, and applies your personal style
  so you can walk away with a sense of ownership and pride in the resulting work.

[1;37mStart here[0m
  [38;2;235;116;164m$cat:init[0m       Set up CAT in this project
  [38;2;235;116;164m$cat:config[0m     View or update configuration
  [38;2;235;116;164m$cat:status[0m     See active issues, locks, and next steps
  [38;2;235;116;164m$cat:cleanup[0m    Remove stale locks and abandoned worktrees
  [38;2;235;116;164m$cat:uninstall[0m  Uninstall CAT from Codex

[1;37mPersonality traits[0m
  [38;2;255;180;118mtrust[0m       how much CAT acts independently before asking you
  [38;2;255;180;118mcaution[0m     how conservative CAT is about validation and risk
  [38;2;255;180;118mcuriosity[0m   how much surrounding context CAT explores
  [38;2;255;180;118mperfection[0m  how willing CAT is to improve adjacent problems
  [38;2;255;180;118mverbosity[0m   how much CAT explains while it works

[1;37mWork prompts[0m
  [38;2;235;116;164mNext issue[0m              Work on the next available issue
  [38;2;235;116;164mResume 1.0-parse[0m       Resume a specific issue
  [38;2;235;116;164mWork on v1 issues[0m       Work all v1.x.x issues
  [38;2;235;116;164mWork on v1.0 issues[0m     Work all v1.0.x issues

[1;37mPlanning prompts[0m
  [38;2;255;180;118mversions[0m  [38;2;235;116;164mAdd version 2.8[0m
  [38;2;255;180;118mversions[0m  [38;2;235;116;164mRemove version 1.2[0m
  [38;2;255;180;118missues[0m    [38;2;235;116;164mAdd a screenshot of the X feature to README.md[0m
  [38;2;255;180;118missues[0m    [38;2;235;116;164mRemove 1.2-add-webscraper[0m

[1;37mAdvanced[0m
  [38;2;235;116;164m$cat:learn[0m               Record mistakes and prevent recurrence
  [38;2;235;116;164m$cat:optimize-execution[0m  Analyze session efficiency

[1;37mBranch names[0m
  Supports [38;2;255;214;102mMAJOR[0m, [38;2;255;214;102mMAJOR.MINOR[0m, and [38;2;255;214;102mMAJOR.MINOR.PATCH[0m versioning.
  Issue and branch names map to [38;2;235;116;164m<version>-title[0m so related work is easy to find.

  [2mexample[0m  [38;2;235;116;164m1.0-parse-tokens[0m
  [2mexample[0m  [38;2;235;116;164m1.0.1-fix-edge-case[0m

[1;37mProject layout[0m
  [2m.cat/project.md[0m      project overview
  [2m.cat/roadmap.md[0m      version summaries
  [2m.cat/config.json[0m     workflow settings
  [2m.cat/v1/v1.0/...[0m     issue folders
```

## Claude Help Output

The Claude slash-command equivalent should match the approved output above, except commands use slash prefixes and
the runtime label says `Claude Code`:

```text
[1;37mCAT help[0m

[38;2;255;214;102m━━━━━━━━[38;2;255;180;118m━━━━━━━━[38;2;255;142;146m━━━━━━━━[38;2;235;116;164m━━━━━━━━━━━━━━━━━━━━[0m

[1;37mWhat CAT does[0m
  CAT organizes your work, augments code quality, and applies your personal style
  so you can walk away with a sense of ownership and pride in the resulting work.

[1;37mStart here[0m
  [38;2;235;116;164m/cat:init[0m       Set up CAT in this project
  [38;2;235;116;164m/cat:config[0m     View or update configuration
  [38;2;235;116;164m/cat:status[0m     See active issues, locks, and next steps
  [38;2;235;116;164m/cat:cleanup[0m    Remove stale locks and abandoned worktrees

[1;37mPersonality traits[0m
  [38;2;255;180;118mtrust[0m       how much CAT acts independently before asking you
  [38;2;255;180;118mcaution[0m     how conservative CAT is about validation and risk
  [38;2;255;180;118mcuriosity[0m   how much surrounding context CAT explores
  [38;2;255;180;118mperfection[0m  how willing CAT is to improve adjacent problems
  [38;2;255;180;118mverbosity[0m   how much CAT explains while it works

[1;37mWork prompts[0m
  [38;2;235;116;164mNext issue[0m              Work on the next available issue
  [38;2;235;116;164mResume 1.0-parse[0m       Resume a specific issue
  [38;2;235;116;164mWork on v1 issues[0m       Work all v1.x.x issues
  [38;2;235;116;164mWork on v1.0 issues[0m     Work all v1.0.x issues

[1;37mPlanning prompts[0m
  [38;2;255;180;118mversions[0m  [38;2;235;116;164mAdd version 2.8[0m
  [38;2;255;180;118mversions[0m  [38;2;235;116;164mRemove version 1.2[0m
  [38;2;255;180;118missues[0m    [38;2;235;116;164mAdd a screenshot of the X feature to README.md[0m
  [38;2;255;180;118missues[0m    [38;2;235;116;164mRemove 1.2-add-webscraper[0m

[1;37mAdvanced[0m
  [38;2;235;116;164m/cat:learn[0m               Record mistakes and prevent recurrence
  [38;2;235;116;164m/cat:optimize-execution[0m  Analyze session efficiency

[1;37mBranch names[0m
  Supports [38;2;255;214;102mMAJOR[0m, [38;2;255;214;102mMAJOR.MINOR[0m, and [38;2;255;214;102mMAJOR.MINOR.PATCH[0m versioning.
  Issue and branch names map to [38;2;235;116;164m<version>-title[0m so related work is easy to find.

  [2mexample[0m  [38;2;235;116;164m1.0-parse-tokens[0m
  [2mexample[0m  [38;2;235;116;164m1.0.1-fix-edge-case[0m

[1;37mProject layout[0m
  [2m.cat/project.md[0m      project overview
  [2m.cat/roadmap.md[0m      version summaries
  [2m.cat/config.json[0m     workflow settings
  [2m.cat/v1/v1.0/...[0m     issue folders
```
