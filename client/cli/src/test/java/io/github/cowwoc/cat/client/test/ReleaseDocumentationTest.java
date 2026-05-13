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
 * Tests release source contracts.
 */
public final class ReleaseDocumentationTest
{
  /**
   * Verifies release source contracts that are independent of documentation wording.
   *
   * @throws IOException if reading source files fails
   */
  @Test
  public void releaseSourcesUseCatReleaseAssets() throws IOException
  {
    Path sourceRoot = findSourceRoot();
    Path clientRoot = sourceRoot.resolve("client");

    String claudeMarketplace = Files.readString(sourceRoot.resolve(".claude-plugin/marketplace.json"),
      StandardCharsets.UTF_8);
    requireThat(claudeMarketplace, "claudeMarketplace").contains("\"source\": \"./client/plugin\"");

    requireThat(Files.exists(clientRoot.resolve("plugin/skills/claude/uninstall/SKILL.md")),
      "claudeUninstallSkill").isFalse();

    requireThat(Files.exists(clientRoot.resolve("plugin/skills/common/help/SKILL.md")),
      "commonHelpSkill").isFalse();
    requireThat(clientRoot.resolve("plugin/skills/claude/help/SKILL.md"), "claudeHelpSkill").
      isRegularFile();
    requireThat(clientRoot.resolve("plugin/skills/codex/help/SKILL.md"), "codexHelpSkill").
      isRegularFile();

    requireThat(clientRoot.resolve("plugin/skills/codex/uninstall/first-use.md"), "codexUninstallSkill").
      isRegularFile();

    String gitFilterRepoDownloader = Files.readString(clientRoot.resolve("plugin/scripts/download-git-filter-repo.sh"),
      StandardCharsets.UTF_8);
    requireThat(gitFilterRepoDownloader, "gitFilterRepoDownloader").
      contains("REPO_NAME=\"cat\"");
    requireThat(gitFilterRepoDownloader, "gitFilterRepoDownloader").doesNotContain("python");
  }

  /**
   * Verifies the runtime help skills emit Markdown directly and omit the redundant introductory copy.
   *
   * @throws IOException if reading source files fails
   */
  @Test
  public void helpSkillsEmitMarkdownDirectlyWithoutIntroCopy() throws IOException
  {
    Path sourceRoot = findSourceRoot();
    Path clientRoot = sourceRoot.resolve("client");

    assertHelpSkillContract(clientRoot.resolve("plugin/skills/claude/help/first-use.md"), "claudeHelp");
    assertHelpSkillContract(clientRoot.resolve("plugin/skills/codex/help/first-use.md"), "codexHelp");
  }

  private static void assertHelpSkillContract(Path firstUse, String name) throws IOException
  {
    String content = Files.readString(firstUse, StandardCharsets.UTF_8);
    requireThat(content, name).contains("Return the Markdown below as your final assistant response.");
    requireThat(content, name).contains("Do not wrap the response in a code block.");
    requireThat(content, name).contains("# CAT Command Reference");
    requireThat(content, name).doesNotContain("hierarchical project planning with multi-agent issue execution");
    requireThat(content, name).doesNotContain("add an issue to fix login");
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
