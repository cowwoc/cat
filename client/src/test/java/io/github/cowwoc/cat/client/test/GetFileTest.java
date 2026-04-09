/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;


import io.github.cowwoc.cat.claude.hook.util.GetFile;
import io.github.cowwoc.cat.claude.hook.util.GetSkill;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GetFile functionality.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained with no shared state.
 */
public class GetFileTest
{
  /**
   * Verifies that constructor rejects null scope.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*scope.*")
  public void constructorRejectsNullScope()
  {
    new GetFile(null);
  }

  /**
   * Verifies that getOutput returns the full file content on the first load.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputReturnsFullContentOnFirstLoad() throws IOException
  {
    Path tempDir = Files.createTempDirectory("get-file-test");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path targetFile = tempDir.resolve("test-file.md");
      Files.writeString(targetFile, "Full file content here\n");

      String agentId = UUID.randomUUID().toString();
      GetFile getFile = new GetFile(scope);
      String result = getFile.getOutput(new String[]{agentId, targetFile.toString()});

      requireThat(result, "result").isEqualTo("Full file content here\n");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getOutput returns a short reference on subsequent loads within the same agent session.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputReturnsReferenceOnSubsequentLoad() throws IOException
  {
    Path tempDir = Files.createTempDirectory("get-file-test");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path targetFile = tempDir.resolve("test-file.md");
      Files.writeString(targetFile, "Full file content here\n");

      String agentId = UUID.randomUUID().toString();
      GetFile getFile = new GetFile(scope);
      String firstResult = getFile.getOutput(new String[]{agentId, targetFile.toString()});
      requireThat(firstResult, "firstResult").contains("Full file content here");

      String secondResult = getFile.getOutput(new String[]{agentId, targetFile.toString()});
      requireThat(secondResult, "secondResult").
        contains("see your earlier Read result for test-file.md").
        doesNotContain("Full file content here");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that different file paths produce independent markers — loading one file does not
   * affect the first-load behavior of a different file.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputTracksFilesIndependently() throws IOException
  {
    Path tempDir = Files.createTempDirectory("get-file-test");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path fileA = tempDir.resolve("file-a.md");
      Path fileB = tempDir.resolve("file-b.md");
      Files.writeString(fileA, "Content of file A\n");
      Files.writeString(fileB, "Content of file B\n");

      String agentId = UUID.randomUUID().toString();
      GetFile getFile = new GetFile(scope);

      // Load file A first
      String resultA1 = getFile.getOutput(new String[]{agentId, fileA.toString()});
      requireThat(resultA1, "resultA1").contains("Content of file A");

      // File B should still return full content (independent marker)
      String resultB1 = getFile.getOutput(new String[]{agentId, fileB.toString()});
      requireThat(resultB1, "resultB1").contains("Content of file B");

      // Now both files should return references
      String resultA2 = getFile.getOutput(new String[]{agentId, fileA.toString()});
      requireThat(resultA2, "resultA2").contains("see your earlier Read result for file-a.md");

      String resultB2 = getFile.getOutput(new String[]{agentId, fileB.toString()});
      requireThat(resultB2, "resultB2").contains("see your earlier Read result for file-b.md");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getOutput throws IOException when the file does not exist on first load.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = ".*File not found.*")
  public void getOutputThrowsForNonExistentFile() throws IOException
  {
    Path tempDir = Files.createTempDirectory("get-file-test");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      String agentId = UUID.randomUUID().toString();
      GetFile getFile = new GetFile(scope);
      getFile.getOutput(new String[]{agentId, tempDir.resolve("does-not-exist.md").toString()});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getOutput throws IllegalArgumentException when no arguments are provided.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*catAgentId is required.*")
  public void getOutputRejectsEmptyArgs() throws IOException
  {
    Path tempDir = Files.createTempDirectory("get-file-test");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      GetFile getFile = new GetFile(scope);
      getFile.getOutput(new String[0]);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getOutput throws IllegalArgumentException when only one argument is provided.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*catAgentId is required.*")
  public void getOutputRejectsOnlyOneArg() throws IOException
  {
    Path tempDir = Files.createTempDirectory("get-file-test");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      GetFile getFile = new GetFile(scope);
      getFile.getOutput(new String[]{"only-one-arg"});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getOutput throws IllegalArgumentException when the catAgentId argument is blank.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*catAgentId is required.*")
  public void getOutputRejectsBlankCatAgentId() throws IOException
  {
    Path tempDir = Files.createTempDirectory("get-file-test");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      GetFile getFile = new GetFile(scope);
      getFile.getOutput(new String[]{"   ", "/some/file.md"});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getOutput throws IllegalArgumentException when the file path argument is blank.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*File path is required.*")
  public void getOutputRejectsBlankPath() throws IOException
  {
    Path tempDir = Files.createTempDirectory("get-file-test");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      String agentId = UUID.randomUUID().toString();
      GetFile getFile = new GetFile(scope);
      getFile.getOutput(new String[]{agentId, "   "});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getOutput does not process preprocessor directives in the returned content.
   * <p>
   * GetFile returns raw file content — {@code !} directives are returned verbatim, not expanded.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputReturnsRawContentWithoutPreprocessing() throws IOException
  {
    Path tempDir = Files.createTempDirectory("get-file-test");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path targetFile = tempDir.resolve("skill-with-directive.md");
      Files.writeString(targetFile, """
Some content.
!`"${CLAUDE_PLUGIN_ROOT}/client/bin/some-tool"`
More content.
""");

      String agentId = UUID.randomUUID().toString();
      GetFile getFile = new GetFile(scope);
      String result = getFile.getOutput(new String[]{agentId, targetFile.toString()});

      requireThat(result, "result").
        contains("!`\"${CLAUDE_PLUGIN_ROOT}/client/bin/some-tool\"`").
        contains("Some content.").
        contains("More content.");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getOutput places the marker file in the main agent directory.
   * <p>
   * Main agents use the session ID as catAgentId, so the marker should appear at
   * {@code {catWorkPath}/sessions/{sessionId}/loaded/}.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputPlacesMainAgentMarkerInCorrectDirectory() throws IOException
  {
    Path tempDir = Files.createTempDirectory("get-file-test");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path targetFile = tempDir.resolve("target.md");
      Files.writeString(targetFile, "content");

      // Main agent uses session ID (UUID format)
      String sessionId = UUID.randomUUID().toString();
      GetFile getFile = new GetFile(scope);
      getFile.getOutput(new String[]{sessionId, targetFile.toString()});

      // Marker should be in {catWorkPath}/sessions/{sessionId}/loaded/
      Path sessionBasePath = scope.getCatWorkPath().resolve("sessions").toAbsolutePath().normalize();
      Path expectedDir = sessionBasePath.resolve(sessionId).resolve(GetSkill.LOADED_DIR);
      String encodedPath = URLEncoder.encode(targetFile.toString(), StandardCharsets.UTF_8);
      Path expectedMarker = expectedDir.resolve(encodedPath);

      requireThat(Files.exists(expectedMarker), "markerExists").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getOutput places the marker file in the correct per-subagent directory.
   * <p>
   * Subagents use a composite ID {@code {sessionId}/subagents/{agentId}}, so the marker should
   * appear at {@code {catWorkPath}/sessions/{sessionId}/subagents/{agentId}/loaded/}.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputPlacesSubagentMarkerInCorrectDirectory() throws IOException
  {
    Path tempDir = Files.createTempDirectory("get-file-test");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path targetFile = tempDir.resolve("target.md");
      Files.writeString(targetFile, "content");

      String sessionId = UUID.randomUUID().toString();
      String agentId = "agent-abc123";
      String catAgentId = sessionId + "/" + GetSkill.SUBAGENTS_DIR + "/" + agentId;

      GetFile getFile = new GetFile(scope);
      getFile.getOutput(new String[]{catAgentId, targetFile.toString()});

      // Marker should be in {catWorkPath}/sessions/{sessionId}/subagents/{agentId}/loaded/
      Path sessionBasePath = scope.getCatWorkPath().resolve("sessions").toAbsolutePath().normalize();
      Path agentPath = sessionBasePath.resolve(sessionId).resolve(GetSkill.SUBAGENTS_DIR).resolve(agentId);
      Path expectedDir = agentPath.resolve(GetSkill.LOADED_DIR);
      String encodedPath = URLEncoder.encode(targetFile.toString(), StandardCharsets.UTF_8);
      Path expectedMarker = expectedDir.resolve(encodedPath);

      requireThat(Files.exists(expectedMarker), "markerExists").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getOutput places the main agent marker under {@code {catWorkPath}/sessions/{sessionId}/loaded/},
   * not under {@code {sessionBasePath}/{sessionId}/loaded/}.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputPlacesMainAgentMarkerUnderCatWorkPath() throws IOException
  {
    Path tempDir = Files.createTempDirectory("get-file-test");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path targetFile = tempDir.resolve("target.md");
      Files.writeString(targetFile, "content");

      String sessionId = UUID.randomUUID().toString();
      GetFile getFile = new GetFile(scope);
      getFile.getOutput(new String[]{sessionId, targetFile.toString()});

      // Marker must be in {catWorkPath}/sessions/{sessionId}/loaded/
      Path expectedDir = scope.getCatWorkPath().resolve("sessions").resolve(sessionId).
        resolve(GetSkill.LOADED_DIR);
      String encodedPath = URLEncoder.encode(targetFile.toString(), StandardCharsets.UTF_8);
      Path expectedMarker = expectedDir.resolve(encodedPath);

      requireThat(Files.exists(expectedMarker), "markerExistsUnderCatWorkPath").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getOutput places the subagent marker under
   * {@code {catWorkPath}/sessions/{sessionId}/subagents/{agentId}/loaded/},
   * not under {@code {sessionBasePath}/{sessionId}/subagents/{agentId}/loaded/}.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputPlacesSubagentMarkerUnderCatWorkPath() throws IOException
  {
    Path tempDir = Files.createTempDirectory("get-file-test");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path targetFile = tempDir.resolve("target.md");
      Files.writeString(targetFile, "content");

      String sessionId = UUID.randomUUID().toString();
      String agentId = "agent-xyz789";
      String catAgentId = sessionId + "/" + GetSkill.SUBAGENTS_DIR + "/" + agentId;

      GetFile getFile = new GetFile(scope);
      getFile.getOutput(new String[]{catAgentId, targetFile.toString()});

      // Marker must be in {catWorkPath}/sessions/{sessionId}/subagents/{agentId}/loaded/
      Path catSessions = scope.getCatWorkPath().resolve("sessions");
      Path expectedDir = catSessions.resolve(sessionId).resolve(GetSkill.SUBAGENTS_DIR).
        resolve(agentId).resolve(GetSkill.LOADED_DIR);
      String encodedPath = URLEncoder.encode(targetFile.toString(), StandardCharsets.UTF_8);
      Path expectedMarker = expectedDir.resolve(encodedPath);

      requireThat(Files.exists(expectedMarker), "markerExistsUnderCatWorkPath").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getOutput rejects a catAgentId containing path traversal sequences.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*path traversal.*")
  public void getOutputRejectsInvalidCatAgentIdWithPathTraversal() throws IOException
  {
    Path tempDir = Files.createTempDirectory("get-file-test");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path targetFile = tempDir.resolve("target.md");
      Files.writeString(targetFile, "content");

      GetFile getFile = new GetFile(scope);
      // "../evil" would escape the session base path
      getFile.getOutput(new String[]{"../evil", targetFile.toString()});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getOutput handles file paths containing special characters that trigger URL encoding.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputHandlesSpecialCharactersInFilePath() throws IOException
  {
    Path tempDir = Files.createTempDirectory("get-file-test");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Create a file with a name that requires URL encoding
      Path targetFile = tempDir.resolve("file with spaces & special=chars.md");
      Files.writeString(targetFile, "special content");

      String agentId = UUID.randomUUID().toString();
      GetFile getFile = new GetFile(scope);

      // First load should return content
      String firstResult = getFile.getOutput(new String[]{agentId, targetFile.toString()});
      requireThat(firstResult, "firstResult").isEqualTo("special content");

      // Second load should return reference
      String secondResult = getFile.getOutput(new String[]{agentId, targetFile.toString()});
      requireThat(secondResult, "secondResult").
        contains("see your earlier Read result for file with spaces & special=chars.md");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that two different agents get independent markers for the same file.
   * <p>
   * Loading a file as one agent should not affect first-load behavior for a different agent.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputTracksMarkersPerAgent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("get-file-test");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path targetFile = tempDir.resolve("shared-file.md");
      Files.writeString(targetFile, "Shared file content\n");

      String agentId1 = UUID.randomUUID().toString();
      String agentId2 = UUID.randomUUID().toString();
      GetFile getFile = new GetFile(scope);

      // Agent 1 loads the file
      String result1 = getFile.getOutput(new String[]{agentId1, targetFile.toString()});
      requireThat(result1, "result1").contains("Shared file content");

      // Agent 2 should still get the full content (independent per-agent marker)
      String result2 = getFile.getOutput(new String[]{agentId2, targetFile.toString()});
      requireThat(result2, "result2").contains("Shared file content");

      // Agent 1 second load returns reference
      String result1b = getFile.getOutput(new String[]{agentId1, targetFile.toString()});
      requireThat(result1b, "result1b").contains("see your earlier Read result for shared-file.md");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
