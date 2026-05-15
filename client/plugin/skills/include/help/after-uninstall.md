<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->

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
  [38;2;235;116;164m${CAT_COMMAND_PREFIX}cat:learn[0m               Record mistakes and prevent recurrence
  [38;2;235;116;164m${CAT_COMMAND_PREFIX}cat:optimize-execution[0m  Analyze session efficiency

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
