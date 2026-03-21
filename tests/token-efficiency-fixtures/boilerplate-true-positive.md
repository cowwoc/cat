<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Skill With Repeated Boilerplate

## Purpose

Demonstrate a true positive for boilerplate repetition detection.

## Procedure

### Step 1: Create the Skill File

Before writing, ensure the skill file has the correct frontmatter structure.
YAML frontmatter must include `user-invocable: true` for user-facing skills.

Create the SKILL.md file in the target directory.

### Step 2: Validate the Skill Content

Verify the skill follows all conventions.

### Step 3: Register the Skill

After writing, register the skill in the index.
YAML frontmatter must include `user-invocable: true` for user-facing skills.

The above line is a verbatim repeat of the same constraint from Step 1. Both occurrences
are 50+ characters long and appear 2+ times — this is a true positive for boilerplate
repetition. One occurrence should be removed and replaced with a reference like
"(see Step 1 frontmatter requirement)".
