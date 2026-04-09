/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.util.ReadSessionMarker;
import io.github.cowwoc.cat.claude.tool.ClaudeTool;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import static java.nio.charset.StandardCharsets.UTF_8;


/**
 * Tests for {@link ReadSessionMarker#run(ClaudeTool, String[], PrintStream)}.
 */
public class ReadSessionMarkerMainTest
{
  /**
   * Verifies that run() writes the marker file content to the provided PrintStream when the file exists.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void writesOutputWhenMarkerFileExists() throws IOException
  {
    Path tempDir = Files.createTempDirectory("read-session-marker-main-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      String sessionId = "session-abc123";
      String markerName = "squash-complete-2.1-fix-foo";
      String expectedContent = "squashed:abc123def";
      Path markerFile = tempDir.resolve(".cat/work/sessions").resolve(sessionId).resolve(markerName);
      Files.createDirectories(markerFile.getParent());
      Files.writeString(markerFile, expectedContent, UTF_8);

      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, UTF_8);
      ReadSessionMarker.run(scope, new String[]{sessionId, markerName}, out);

      String actualOutput = buffer.toString(UTF_8);
      requireThat(actualOutput, "actualOutput").isEqualTo(expectedContent);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that invoking run() with no arguments throws IllegalArgumentException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = "(?s).*Expected exactly 2 arguments.*")
  public void noArgsThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("read-session-marker-main-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, UTF_8);
      ReadSessionMarker.run(scope, new String[]{}, out);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() throws NullPointerException for null args.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*args.*")
  public void nullArgsThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("read-session-marker-main-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      ReadSessionMarker.run(scope, null,
        new PrintStream(new ByteArrayOutputStream(), true, UTF_8));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() throws NullPointerException for null output stream.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*out.*")
  public void nullOutThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("read-session-marker-main-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      ReadSessionMarker.run(scope, new String[]{}, null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
