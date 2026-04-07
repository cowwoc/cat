---
category: REQUIREMENT
---
## Turn 1
We are releasing version 2.1.0. Please update the version string from '2.0.9' to '2.1.0' in
these 5 independent configuration files:

1. client/pom.xml — update the <version>2.0.9</version> element
2. plugin/package.json — update the "version": "2.0.9" field
3. docs/conf.py — update release = '2.0.9'
4. .github/workflows/release.yml — update DEFAULT_VERSION: '2.0.9'
5. README.md — update the badge that shows version 2.0.9

Each edit targets a different file and does not depend on the result of any other edit.

## Assertions
1. The Skill tool was invoked
2. Five Write or Edit tool calls were issued in a single LLM response
