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

    String claudeMarketplace = Files.readString(sourceRoot.resolve(".claude-plugin/marketplace.json"),
      StandardCharsets.UTF_8);
    requireThat(claudeMarketplace, "claudeMarketplace").contains("\"source\": \"./client/plugin\"");

    String codexInstallPrompt = Files.readString(sourceRoot.resolve("docs/prompts/codex-install.md"),
      StandardCharsets.UTF_8);
    requireThat(codexInstallPrompt, "codexInstallPrompt").contains("This copy of the prompt installs CAT `v2.1`");
    requireThat(codexInstallPrompt, "codexInstallPrompt").contains("REQUESTED_VERSION=\"v2.1\"");
    requireThat(codexInstallPrompt, "codexInstallPrompt").doesNotContain("use `main` in the prompt URL");
    requireThat(codexInstallPrompt, "codexInstallPrompt").contains("https://github.com/cowwoc/cat/releases");
    requireThat(codexInstallPrompt, "codexInstallPrompt").contains("do not rely on any `/cat:*` command");
    requireThat(codexInstallPrompt, "codexInstallPrompt").contains("\"name\": \"cat\"");
    requireThat(codexInstallPrompt, "codexInstallPrompt").contains("[plugins.\"cat@cat\"]");
    requireThat(codexInstallPrompt, "codexInstallPrompt").contains("enabled = true");
    requireThat(codexInstallPrompt, "codexInstallPrompt").contains("plugins/data/cat-cat");
    requireThat(codexInstallPrompt, "codexInstallPrompt").contains(
      "codex plugin marketplace add \"${LOCAL_MARKETPLACE_ROOT}\"");
    requireThat(codexInstallPrompt, "codexInstallPrompt").contains(
      "try_codex_plugin_browser_install()");
    requireThat(codexInstallPrompt, "codexInstallPrompt").contains(
      "\"plugin/install\"");
    requireThat(codexInstallPrompt, "codexInstallPrompt").contains(
      "codex app-server proxy");
    requireThat(codexInstallPrompt, "codexInstallPrompt").doesNotContain(
      "codex app-server generate-json-schema");
    requireThat(codexInstallPrompt, "codexInstallPrompt").contains(
      "CODEX_PLUGIN_CACHE=\"${CODEX_PLUGIN_CACHE_ROOT}/${PLUGIN_VERSION}\"");
    requireThat(codexInstallPrompt, "codexInstallPrompt").contains(
      "rm -rf \"${CODEX_PLUGIN_CACHE_ROOT}\"");
    requireThat(codexInstallPrompt, "codexInstallPrompt").contains(
      "cp -R \"${RELEASE_ARTIFACT}\" \"${CODEX_PLUGIN_CACHE}\"");
    requireThat(codexInstallPrompt, "codexInstallPrompt").contains(
      "test -f \"${CODEX_PLUGIN_CACHE}/skills/add/SKILL.md\"");
    requireThat(codexInstallPrompt, "codexInstallPrompt").contains(
      "test -f \"${CODEX_PLUGIN_CACHE}/commands/init.md\"");
    requireThat(codexInstallPrompt, "codexInstallPrompt").contains("CAT_RUNTIME=\"codex\"");
    requireThat(codexInstallPrompt, "codexInstallPrompt").contains(
      "ASSET_NAME=\"cat-${CAT_RUNTIME}-${RELEASE_TAG}.tar.gz\"");
    requireThat(codexInstallPrompt, "codexInstallPrompt").doesNotContain("codex plugin marketplace add cowwoc/cat");
    requireThat(codexInstallPrompt, "codexInstallPrompt").doesNotContain("cowwoc/cat-artifacts");
    requireThat(codexInstallPrompt, "codexInstallPrompt").contains(
      "Run `/cat:init` only when the");
    requireThat(codexInstallPrompt, "codexInstallPrompt").contains(
      "user wants to create a new CAT project or wrap an existing project");
    requireThat(codexInstallPrompt, "codexInstallPrompt").contains(
      "Restart Codex to complete the installation.");

    String distribution = Files.readString(sourceRoot.resolve("docs/development/plugin-distribution.md"),
      StandardCharsets.UTF_8);
    requireThat(distribution, "distribution").contains("The main `cowwoc/cat` repository is the release catalog");
    requireThat(distribution, "distribution").contains("GitHub Release assets on `cowwoc/cat`");
    requireThat(distribution, "distribution").doesNotContain("cowwoc/cat-artifacts");
    requireThat(distribution, "distribution").contains("git-filter-repo");

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
      if (Files.isDirectory(current.resolve("client/plugin")) &&
        Files.isRegularFile(current.resolve("client/pom.xml")))
      {
        return current;
      }
      current = current.getParent();
    }
    throw new AssertionError("Unable to find CAT source root");
  }
}
