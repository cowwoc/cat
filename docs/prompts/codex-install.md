<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Codex CAT Install Prompt

You are installing CAT for Codex. CAT may not be installed yet.

Determine the requested CAT version from the user's message that pointed to this prompt:

- If the user requested the latest version, determine the latest release tag from
  `https://github.com/cowwoc/cat/releases`.
- If the user requested a specific version, use that version as the release tag. If the user provided a bare version
  like `2.1.0`, normalize it to `v2.1.0`.

Install the CAT marketplace from the source repository at the selected release tag:

```bash
codex plugin marketplace add cowwoc/cat --ref <release-tag> --sparse client/plugin
```

Then guide the user through these steps:

1. Open Codex's plugin browser.
2. Install CAT from the `cowwoc/cat` marketplace entry.
3. Run `/cat:install <release-tag>` to download and install the full CAT Codex release artifact.
4. Restart Codex.
5. If the project root already contains `.cat/`, do not run `/cat:init`.

Run `/cat:init` only when the user wants to create a new CAT project or wrap an existing project.
