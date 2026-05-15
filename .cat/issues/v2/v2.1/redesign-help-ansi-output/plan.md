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

- [ ] `$cat:help` returns the exact approved content below as its final assistant response.
- [ ] Claude's help output returns equivalent content with command examples using slash prefixes instead of dollar
  prefixes.
- [ ] Claude's help output does not include `/cat:uninstall`.
- [ ] The output uses raw SGR ANSI truecolor codes outside code fences so the Codex terminal can render colors.
- [ ] Section headers use bold white.
- [ ] The horizontal divider uses the amber-to-rose gradient.
- [ ] Commands and example branch names use the rose color from the approved palette, not green.
- [ ] The `Personality traits` section does not include a separate `Values: low, medium, high` line.
- [ ] The `Branch names` section describes support for `MAJOR`, `MAJOR.MINOR`, and `MAJOR.MINOR.PATCH` versioning
  schemes and says issue and branch names map to `<version>-title`.
- [ ] The help output remains readable if ANSI color is stripped.

## Approved Output

The literal output should be:

```text
[1;37mCAT help[0m    [2missue workflow for Codex[0m

[38;2;255;214;102m━━━━━━━━[38;2;255;180;118m━━━━━━━━[38;2;255;142;146m━━━━━━━━[38;2;235;116;164m━━━━━━━━━━━━━━━━━━━━[0m

[1;37mStart here[0m
  [38;2;235;116;164m$cat:status[0m     See active issues, locks, and next steps
  [38;2;235;116;164m$cat:init[0m       Set up CAT in this project
  [38;2;235;116;164m$cat:config[0m     Tune autonomy, validation, review depth, cleanup, and detail
  [38;2;235;116;164m$cat:cleanup[0m    Remove stale locks and abandoned worktrees

[1;37mWhat CAT does[0m
  CAT organizes your work, augments code quality, and applies your personal style
  so you can walk away with a sense of ownership and pride in the resulting work.

[1;37mPersonality traits[0m
  [38;2;255;180;118mtrust[0m       how much CAT acts independently before asking you
  [38;2;255;180;118mcaution[0m     how conservative CAT is about validation and risk
  [38;2;255;180;118mcuriosity[0m   how much surrounding context CAT explores
  [38;2;255;180;118mperfection[0m  how willing CAT is to improve adjacent problems
  [38;2;255;180;118mverbosity[0m   how much CAT explains while it works

[1;37mCommon work requests[0m
  [38;2;235;116;164mNext issue[0m              Work through incomplete issues
  [38;2;235;116;164mWork on v1 issues[0m       Work all v1.x.x issues
  [38;2;235;116;164mWork on v1.0 issues[0m     Work all v1.0.x issues
  [38;2;235;116;164mWork on 1.0-parse[0m       Work one specific issue

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

The Claude slash-command equivalent should match the approved output above, except commands use slash prefixes:

```text
[1;37mCAT help[0m    [2missue workflow for Codex[0m

[38;2;255;214;102m━━━━━━━━[38;2;255;180;118m━━━━━━━━[38;2;255;142;146m━━━━━━━━[38;2;235;116;164m━━━━━━━━━━━━━━━━━━━━[0m

[1;37mStart here[0m
  [38;2;235;116;164m/cat:status[0m     See active issues, locks, and next steps
  [38;2;235;116;164m/cat:init[0m       Set up CAT in this project
  [38;2;235;116;164m/cat:config[0m     Tune autonomy, validation, review depth, cleanup, and detail
  [38;2;235;116;164m/cat:cleanup[0m    Remove stale locks and abandoned worktrees

[1;37mWhat CAT does[0m
  CAT organizes your work, augments code quality, and applies your personal style
  so you can walk away with a sense of ownership and pride in the resulting work.

[1;37mPersonality traits[0m
  [38;2;255;180;118mtrust[0m       how much CAT acts independently before asking you
  [38;2;255;180;118mcaution[0m     how conservative CAT is about validation and risk
  [38;2;255;180;118mcuriosity[0m   how much surrounding context CAT explores
  [38;2;255;180;118mperfection[0m  how willing CAT is to improve adjacent problems
  [38;2;255;180;118mverbosity[0m   how much CAT explains while it works

[1;37mCommon work requests[0m
  [38;2;235;116;164mNext issue[0m              Work through incomplete issues
  [38;2;235;116;164mWork on v1 issues[0m       Work all v1.x.x issues
  [38;2;235;116;164mWork on v1.0 issues[0m     Work all v1.0.x issues
  [38;2;235;116;164mWork on 1.0-parse[0m       Work one specific issue

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
