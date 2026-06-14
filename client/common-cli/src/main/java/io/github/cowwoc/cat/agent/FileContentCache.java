/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.agent;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded shared cache for UTF-8 file content.
 * <p>
 * Entries are keyed by normalized absolute path and invalidated by file size plus last-modified
 * time. This keeps repeated source/rule/skill reads cheap while still picking up content changes
 * without requiring process restart.
 */
public final class FileContentCache
{
  private static final int MAX_ENTRIES = 512;
  private static final Map<Path, CacheEntry> CACHE = new LinkedHashMap<>(32, 0.75f, true)
  {
    @Override
    protected boolean removeEldestEntry(Map.Entry<Path, CacheEntry> eldest)
    {
      return size() > MAX_ENTRIES;
    }
  };

  private FileContentCache()
  {
  }

  /**
   * Reads UTF-8 content, reusing a cached copy when file metadata is unchanged.
   *
   * @param path the file path
   * @return file content
   * @throws IOException if the file cannot be read
   */
  public static String readString(Path path) throws IOException
  {
    return readString(path, UTF_8);
  }

  /**
   * Reads file content, reusing a cached copy when file metadata is unchanged.
   *
   * @param path the file path
   * @param charset the character set to use
   * @return file content
   * @throws IOException if the file cannot be read
   */
  public static String readString(Path path, Charset charset) throws IOException
  {
    requireThat(path, "path").isNotNull();
    requireThat(charset, "charset").isNotNull();

    Path normalized = path.toAbsolutePath().normalize();
    FileStamp stamp = stamp(normalized);
    synchronized (CACHE)
    {
      CacheEntry cached = CACHE.get(normalized);
      if (cached != null && cached.charset().equals(charset) && cached.stamp().equals(stamp))
        return cached.content();
    }

    String content = Files.readString(normalized, charset);
    synchronized (CACHE)
    {
      CACHE.put(normalized, new CacheEntry(stamp, charset, content));
    }
    return content;
  }

  /**
   * Clears all cached entries.
   * <p>
   * Intended for tests.
   */
  public static void clear()
  {
    synchronized (CACHE)
    {
      CACHE.clear();
    }
  }

  /**
   * Captures cache invalidation metadata for a file.
   *
   * @param path the file path
   * @return the current file stamp
   * @throws IOException if metadata lookup fails
   */
  private static FileStamp stamp(Path path) throws IOException
  {
    return new FileStamp(Files.size(path), Files.getLastModifiedTime(path).toMillis());
  }

  private record FileStamp(long size, long modifiedMillis)
  {
  }

  private record CacheEntry(FileStamp stamp, Charset charset, String content)
  {
  }
}
