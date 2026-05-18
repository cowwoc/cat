/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.agent;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Shared file-system helpers for agent engine setup.
 */
public final class FileSystemUtils
{
  /**
   * Prevents construction.
   */
  private FileSystemUtils()
  {
  }

  /**
   * Writes a UTF-8 string only when the file content changed.
   *
   * @param path the output path
   * @param content the desired file content
   * @throws IOException if file operations fail
   */
  public static void writeStringIfChanged(Path path, String content) throws IOException
  {
    requireThat(path, "path").isNotNull();
    requireThat(content, "content").isNotNull();
    if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) &&
      readStringNoFollow(path).equals(content))
    {
      return;
    }
    Files.writeString(path, content, StandardCharsets.UTF_8,
      StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE,
      LinkOption.NOFOLLOW_LINKS);
  }

  /**
   * Reads a UTF-8 file without following a symbolic link at the final path component.
   *
   * @param path the file to read
   * @return the file content
   * @throws IOException if the file cannot be read
   */
  private static String readStringNoFollow(Path path) throws IOException
  {
    try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS))
    {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
