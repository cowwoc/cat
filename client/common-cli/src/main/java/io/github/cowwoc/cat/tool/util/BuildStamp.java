/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.tool.util;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * Computes deterministic build stamps for file and directory inputs.
 * <p>
 * Usage:
 * <pre>
 *   BuildStamp compute &lt;path&gt;...
 *   BuildStamp matches &lt;stamp-file&gt; &lt;path&gt;...
 *   BuildStamp write &lt;stamp-file&gt; &lt;path&gt;...
 * </pre>
 */
public final class BuildStamp
{
  private BuildStamp()
  {
  }

  /**
   * CLI entry point.
   *
   * @param args CLI arguments
   * @throws IOException if file I/O fails
   */
  public static void main(String[] args) throws IOException
  {
    if (args.length < 2)
      throw new IllegalArgumentException("""
        Usage:
          BuildStamp compute <path>...
          BuildStamp matches <stamp-file> <path>...
          BuildStamp write <stamp-file> <path>...""");
    switch (args[0])
    {
      case "compute" -> System.out.println(computeDigest(paths(args, 1)));
      case "matches" ->
      {
        if (matches(Path.of(args[1]), paths(args, 2)))
          return;
        System.exit(1);
      }
      case "write" -> write(Path.of(args[1]), paths(args, 2));
      default -> throw new IllegalArgumentException("Unknown BuildStamp command: " + args[0]);
    }
  }

  /**
   * Computes the deterministic digest for one or more input paths.
   *
   * @param inputs input files or directories
   * @return SHA-256 digest hex string
   * @throws IOException if file reading fails
   */
  public static String computeDigest(List<Path> inputs) throws IOException
  {
    MessageDigest digest = sha256();
    for (Path input : inputs)
      updateDigest(digest, input.toAbsolutePath().normalize());
    return HexFormat.of().formatHex(digest.digest());
  }

  /**
   * Returns whether a stamp file matches the current digest of the supplied inputs.
   *
   * @param stampFile the stamp file path
   * @param inputs input files or directories
   * @return {@code true} if the stamp exists and matches the current digest
   * @throws IOException if file I/O fails
   */
  public static boolean matches(Path stampFile, List<Path> inputs) throws IOException
  {
    if (!Files.isRegularFile(stampFile))
      return false;
    String expected = Files.readString(stampFile, UTF_8).trim();
    return expected.equals(computeDigest(inputs));
  }

  /**
   * Writes a stamp file atomically.
   *
   * @param stampFile the stamp file path
   * @param inputs input files or directories
   * @throws IOException if file I/O fails
   */
  public static void write(Path stampFile, List<Path> inputs) throws IOException
  {
    Files.createDirectories(stampFile.toAbsolutePath().normalize().getParent());
    Path tmp = stampFile.resolveSibling(stampFile.getFileName() + ".tmp");
    Files.writeString(tmp, computeDigest(inputs) + "\n", UTF_8);
    Files.move(tmp, stampFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
  }

  /**
   * Converts a CLI argument tail into a path list.
   *
   * @param args CLI arguments
   * @param startIndex first path argument
   * @return immutable list of paths
   */
  private static List<Path> paths(String[] args, int startIndex)
  {
    List<Path> result = new ArrayList<>(args.length - startIndex);
    for (int i = startIndex; i < args.length; ++i)
      result.add(Path.of(args[i]));
    return List.copyOf(result);
  }

  /**
   * Adds one input path to the running digest.
   *
   * @param digest running SHA-256 digest
   * @param input normalized input path
   * @throws IOException if file reading fails
   */
  private static void updateDigest(MessageDigest digest, Path input) throws IOException
  {
    if (Files.isDirectory(input))
    {
      digest.update(("dir\t" + input + "\n").getBytes(UTF_8));
      try
      {
        List<Path> files;
        try (Stream<Path> stream = Files.walk(input))
        {
          files = stream.filter(Files::isRegularFile).sorted(Comparator.naturalOrder()).toList();
        }
        for (Path file : files)
        {
          String rel = input.relativize(file).toString().replace('\\', '/');
          digest.update(("file\t" + input + "\t" + rel + "\t").getBytes(UTF_8));
          digest.update(Files.readAllBytes(file));
          digest.update((byte) '\n');
        }
      }
      catch (UncheckedIOException e)
      {
        throw e.getCause();
      }
      return;
    }
    if (Files.isRegularFile(input))
    {
      digest.update(("file\t" + input + "\t").getBytes(UTF_8));
      digest.update(Files.readAllBytes(input));
      digest.update((byte) '\n');
      return;
    }
    digest.update(("missing\t" + input + "\n").getBytes(UTF_8));
  }

  /**
   * Creates a SHA-256 digest instance.
   *
   * @return a SHA-256 digest
   */
  private static MessageDigest sha256()
  {
    try
    {
      return MessageDigest.getInstance("SHA-256");
    }
    catch (NoSuchAlgorithmException e)
    {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }
}
