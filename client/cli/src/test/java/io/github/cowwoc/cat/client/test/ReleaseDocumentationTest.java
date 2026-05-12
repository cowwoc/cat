/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests release and installation documentation contracts.
 */
public final class ReleaseDocumentationTest
{
  /**
   * Verifies that release installation docs describe the release-asset install path.
   *
   * @throws IOException if reading documentation fails
   */
  @Test
  public void releaseInstallDocsUseCatReleaseAssets() throws IOException
  {
    Path sourceRoot = findSourceRoot();
    Path clientRoot = sourceRoot.resolve("client");

    String readme = Files.readString(sourceRoot.resolve("README.md"), StandardCharsets.UTF_8);
    String claudeMarketplace = Files.readString(sourceRoot.resolve(".claude-plugin/marketplace.json"),
      StandardCharsets.UTF_8);
    requireThat(claudeMarketplace, "claudeMarketplace").contains("\"source\": \"./client/plugin\"");

    requireThat(readme, "readme").contains(
      "https://raw.githubusercontent.com/cowwoc/cat/main/docs/prompts/codex-install.md");
    requireThat(readme, "readme").contains(
      "Run the prompt at https://raw.githubusercontent.com/cowwoc/cat/main/docs/prompts/codex-install.md to " +
        "install or update the CAT plugin to the latest version.");
    requireThat(readme, "readme").contains(
      "Run the prompt at https://raw.githubusercontent.com/cowwoc/cat/main/docs/prompts/codex-install.md to " +
        "install or update the CAT plugin to version 1.2.0.");
    requireThat(readme, "readme").contains("[Codex parity notes](docs/development/codex-parity.md)");
    requireThat(readme, "readme").contains("run `/cat:uninstall` before uninstalling CAT from Codex");
    requireThat(readme, "readme").doesNotContain("The prompt resolves to this bootstrap path");
    requireThat(readme, "readme").doesNotContain("cowwoc/cat-artifacts");
    requireThat(readme, "readme").doesNotContain("Determine the latest release tag");
    requireThat(readme, "readme").doesNotContain("codex plugin marketplace add cowwoc/cat\n");

    String codexInstallPrompt = Files.readString(sourceRoot.resolve("docs/prompts/codex-install.md"),
      StandardCharsets.UTF_8);
    requireThat(codexInstallPrompt, "codexInstallPrompt").contains("Determine the requested CAT version");
    requireThat(codexInstallPrompt, "codexInstallPrompt").contains("https://github.com/cowwoc/cat/releases");
    requireThat(codexInstallPrompt, "codexInstallPrompt").contains("codex plugin marketplace add cowwoc/cat");
    requireThat(codexInstallPrompt, "codexInstallPrompt").contains("Run `/cat:install <release-tag>`");
    requireThat(codexInstallPrompt, "codexInstallPrompt").doesNotContain("cowwoc/cat-artifacts");
    requireThat(codexInstallPrompt, "codexInstallPrompt").contains(
      "If the project root already contains `.cat/`, do not run `/cat:init`");
    requireThat(codexInstallPrompt, "codexInstallPrompt").contains(
      "Run `/cat:init` only when the user wants to create a new CAT project or wrap an existing project");

    String distribution = Files.readString(sourceRoot.resolve("docs/development/plugin-distribution.md"),
      StandardCharsets.UTF_8);
    requireThat(distribution, "distribution").contains("The main `cowwoc/cat` repository is the release catalog");
    requireThat(distribution, "distribution").contains("GitHub Release assets on `cowwoc/cat`");
    requireThat(distribution, "distribution").doesNotContain("cowwoc/cat-artifacts");
    requireThat(distribution, "distribution").contains("git-filter-repo");

    String installSkill = Files.readString(clientRoot.resolve("plugin/skills/common/install/SKILL.md"),
      StandardCharsets.UTF_8);
    requireThat(installSkill, "installSkill").contains("name: install");
    requireThat(installSkill, "installSkill").contains("cat-claude-<release-tag>.tar.gz");
    requireThat(installSkill, "installSkill").contains("cat-codex-<release-tag>.tar.gz");
    requireThat(installSkill, "installSkill").contains("https://github.com/cowwoc/cat/releases/download");
    requireThat(installSkill, "installSkill").doesNotContain("python");
    requireThat(Files.exists(clientRoot.resolve("plugin/skills/codex/install/SKILL.md")),
      "codexInstallSkill").isFalse();
    requireThat(Files.exists(clientRoot.resolve("plugin/skills/claude/install/SKILL.md")),
      "claudeInstallSkill").isFalse();
    requireThat(Files.exists(clientRoot.resolve("plugin/skills/claude/uninstall/SKILL.md")),
      "claudeUninstallSkill").isFalse();

    String codexUninstallSkill = Files.readString(clientRoot.resolve("plugin/skills/codex/uninstall/first-use.md"),
      StandardCharsets.UTF_8);
    requireThat(codexUninstallSkill, "codexUninstallSkill").contains(
      "Run this before uninstalling CAT from Codex");

    String gitFilterRepoDownloader = Files.readString(clientRoot.resolve("plugin/scripts/download-git-filter-repo.sh"),
      StandardCharsets.UTF_8);
    requireThat(gitFilterRepoDownloader, "gitFilterRepoDownloader").
      contains("REPO_NAME=\"cat\"");
    requireThat(gitFilterRepoDownloader, "gitFilterRepoDownloader").doesNotContain("python");
  }

  /**
   * Verifies that approval gate documentation accepts exact option labels without requiring matching case.
   *
   * @throws IOException if reading documentation fails
   */
  @Test
  public void approvalGateDocsUseCaseInsensitiveExactOptionMatching() throws IOException
  {
    Path sourceRoot = findSourceRoot();

    String projectHooks = Files.readString(sourceRoot.resolve(".cat/rules/common/hooks.md"), StandardCharsets.UTF_8);
    requireThat(projectHooks, "projectHooks").contains("case-insensitive exact match");

    String approvalGateProtocol = Files.readString(
      sourceRoot.resolve("client/plugin/rules/common/approval-gate-protocol.md"), StandardCharsets.UTF_8);
    requireThat(approvalGateProtocol, "approvalGateProtocol").contains("case-insensitive exact match");
    requireThat(approvalGateProtocol, "approvalGateProtocol").contains(
      "does not match any presented option after case-insensitive comparison");

    String codexParity = Files.readString(sourceRoot.resolve("docs/development/codex-parity.md"),
      StandardCharsets.UTF_8);
    requireThat(codexParity, "codexParity").contains("case-insensitive exact response");
  }

  private static Path findSourceRoot()
  {
    Path current = Path.of("").toAbsolutePath().normalize();
    while (current != null)
    {
      if (Files.isRegularFile(current.resolve("README.md")) &&
        Files.isDirectory(current.resolve("client/plugin")))
      {
        return current;
      }
      current = current.getParent();
    }
    throw new AssertionError("Unable to find CAT source root");
  }
}
