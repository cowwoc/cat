/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.util;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Utility methods for working with session files.
 * <p>
 * Session files are stored in JSONL format at {claudeConfigPath}/projects/{encodedProjectDir}/[session_id].jsonl
 */
public final class SessionFileUtils
{
  private SessionFileUtils()
  {
  }

  /**
   * Get the last N lines from a file.
   *
   * @param file the file to read
   * @param lineCount the number of lines to read from the end
   * @return the last N lines
   * @throws NullPointerException if file is null
   * @throws IllegalArgumentException if lineCount is not positive
   * @throws IOException if reading fails
   */
  public static List<String> getRecentLines(Path file, int lineCount) throws IOException
  {
    requireThat(file, "file").isNotNull();
    requireThat(lineCount, "lineCount").isPositive();

    // Use a bounded deque to keep only the last lineCount lines,
    // avoiding loading the entire (potentially multi-MB) session file into memory.
    Deque<String> buffer = new ArrayDeque<>(lineCount + 1);
    try (BufferedReader reader = Files.newBufferedReader(file))
    {
      String line = reader.readLine();
      while (line != null)
      {
        buffer.addLast(line);
        if (buffer.size() > lineCount)
          buffer.removeFirst();
        line = reader.readLine();
      }
    }
    return new ArrayList<>(buffer);
  }
}
