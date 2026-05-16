/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.agent;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

/**
 * Utilities for executable file permissions.
 */
public final class ExecutableFiles
{
  private ExecutableFiles()
  {
  }

  /**
   * Makes a file executable by all users.
   *
   * @param path the file path
   * @throws IOException if the operation fails
   */
  public static void makeExecutable(Path path) throws IOException
  {
    requireThat(path, "path").isNotNull();
    try
    {
      Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
      permissions = EnumSet.copyOf(permissions);
      permissions.add(PosixFilePermission.OWNER_EXECUTE);
      permissions.add(PosixFilePermission.GROUP_EXECUTE);
      permissions.add(PosixFilePermission.OTHERS_EXECUTE);
      Files.setPosixFilePermissions(path, permissions);
    }
    catch (UnsupportedOperationException _)
    {
      if (!path.toFile().setExecutable(true, false))
        throw new IOException("Failed to make executable: " + path);
    }
  }
}
