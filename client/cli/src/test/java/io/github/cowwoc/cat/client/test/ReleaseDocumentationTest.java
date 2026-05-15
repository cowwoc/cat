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
   * Verifies work-execute instructions do not treat a clean pre-implementation branch as already applied.
   *
   * @throws IOException if reading source files fails
   */
  @Test
  public void workExecuteAllowsCleanPreImplementationBranches() throws IOException
  {
    Path sourceRoot = findSourceRoot();
    String workExecute = Files.readString(sourceRoot.resolve("client/plugin/agents/common/work-execute.md"),
      StandardCharsets.UTF_8);

    requireThat(workExecute, "workExecute").
      contains("A clean pre-implementation branch with no implementation diff is normal");
    requireThat(workExecute, "workExecute").
      contains("Do not classify an empty implementation diff as already applied");
    requireThat(workExecute, "workExecute").
      doesNotContain("no diff for the\nimplementation files");
  }

  /**
   * Verifies common work verification instructions stay runtime-neutral.
   *
   * @throws IOException if reading source files fails
   */
  @Test
  public void commonWorkVerificationUsesRuntimeNeutralTerminology() throws IOException
  {
    Path sourceRoot = findSourceRoot();
    String workVerify = Files.readString(sourceRoot.resolve("client/plugin/agents/common/work-verify.md"),
      StandardCharsets.UTF_8);
    assertRuntimeNeutralWorkVerification(workVerify, "workVerify");
  }

  private static void assertRuntimeNeutralWorkVerification(String content, String name)
  {
    requireThat(content, name).contains("E2E tests must use the runtime selected by `CAT_RUNTIME`");
    requireThat(content, name).contains("runtime-native test infrastructure");
    requireThat(content, name).contains("Do not skip E2E because another runtime's infrastructure is unavailable");
    requireThat(content, name).doesNotContain("Claude");
    requireThat(content, name).doesNotContain("Codex");
    requireThat(content, name).doesNotContain("CAT_RUNTIME:-claude");
    requireThat(content, name).doesNotContain("ModelIdResolver.detectClaudeCodeVersion");
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
