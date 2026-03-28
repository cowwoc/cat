/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.session;

import io.github.cowwoc.cat.hooks.ClaudeHook;
import io.github.cowwoc.cat.hooks.util.VersionUtils;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/**
 * Checks GitHub for a newer CAT version and displays an update notice.
 * <p>
 * Caches the result for 24 hours to avoid repeated network requests. Gracefully handles
 * network failures by returning an empty result (never crashes the session).
 * <p>
 * Communication with the version endpoint uses HTTPS/TLS for transport security. The JVM's default
 * TrustManager is used for certificate validation.
 */
public final class CheckUpdateAvailable implements SessionStartHandler
{
  private static final long CACHE_MAX_AGE_SECONDS = 24 * 60 * 60;
  private static final String GITHUB_PLUGIN_JSON_URL =
    "https://raw.githubusercontent.com/cowwoc/cat/main/plugin/.claude-plugin/plugin.json";
  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  /**
   * Creates a new CheckUpdateAvailable handler.
   */
  public CheckUpdateAvailable()
  {
  }

  /**
   * Checks for available updates and returns a notice if a newer version exists.
   *
   * @param scope the Claude hook context
   * @return a result with update notice if newer version available, empty otherwise
   * @throws WrappedCheckedException if an I/O error occurs reading version or cache files
   */
  @Override
  public Result handle(ClaudeHook scope)
  {
    try
    {
      String currentVersion = VersionUtils.getPluginVersion(scope);

      String latestVersion = getLatestVersion(scope);
      if (latestVersion.isEmpty())
        return Result.empty();

      int cmp = VersionUtils.compareVersions(currentVersion, latestVersion);
      if (cmp >= 0)
        return Result.empty();

      String stderrNotice = "\n" +
        "================================================================================\n" +
        "CAT UPDATE AVAILABLE\n" +
        "================================================================================\n" +
        "\n" +
        "Current version: " + currentVersion + "\n" +
        "Latest version:  " + latestVersion + "\n" +
        "\n" +
        "Run: /plugin update cat\n" +
        "\n" +
        "================================================================================\n";

      String contextMessage = "CAT update available: " + currentVersion + " -> " + latestVersion +
        ". User has been notified.";

      return Result.both(contextMessage, stderrNotice);
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Gets the latest version from cache or network.
   *
   * @param scope the Claude hook context
   * @return the latest version string, or empty if unavailable
   * @throws IOException if reading the cache file fails
   */
  private String getLatestVersion(ClaudeHook scope) throws IOException
  {
    Path cacheFile = scope.getCatWorkPath().resolve("cache/update-check/latest_version.json");
    // Try cache first
    if (isCacheFresh(cacheFile))
    {
      String cached = readCachedVersion(scope, cacheFile);
      if (!cached.isEmpty())
        return cached;
    }

    // Fetch from network
    String latest = fetchLatestVersion(scope);
    if (latest.isEmpty())
      return "";

    // Update cache
    Path cacheDir = scope.getCatWorkPath().resolve("cache/update-check");
    updateCache(scope, cacheFile, cacheDir, latest);
    return latest;
  }

  /**
   * Checks if the cache file exists and is less than 24 hours old.
   *
   * @param cacheFile the cache file path
   * @return true if cache is fresh
   * @throws IOException if reading the cache file metadata fails
   */
  private boolean isCacheFresh(Path cacheFile) throws IOException
  {
    if (!Files.isRegularFile(cacheFile))
      return false;
    Instant lastModified = Files.getLastModifiedTime(cacheFile).toInstant();
    long ageSeconds = Duration.between(lastModified, Instant.now()).getSeconds();
    return ageSeconds < CACHE_MAX_AGE_SECONDS;
  }

  /**
   * Reads the cached version from the cache file.
   *
   * @param scope the Claude hook context
   * @param cacheFile the cache file path
   * @return the cached version, or empty string if the version field is absent
   * @throws IOException if reading the cache file fails
   */
  private String readCachedVersion(ClaudeHook scope, Path cacheFile) throws IOException
  {
    JsonNode root = scope.getJsonMapper().readTree(Files.readString(cacheFile));
    JsonNode versionNode = root.get("version");
    if (versionNode != null && versionNode.isString())
      return versionNode.asString();
    return "";
  }

  /**
   * Fetches the latest version from the plugin.json file on the main branch of the CAT repository.
   *
   * @param scope the Claude hook context
   * @return the latest version, or empty string on failure
   */
  private String fetchLatestVersion(ClaudeHook scope)
  {
    try (HttpClient client = HttpClient.newBuilder().
      connectTimeout(TIMEOUT).
      build())
    {
      HttpRequest request = HttpRequest.newBuilder().
        uri(URI.create(GITHUB_PLUGIN_JSON_URL)).
        timeout(TIMEOUT).
        GET().
        build();

      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200)
        return "";

      JsonNode root = scope.getJsonMapper().readTree(response.body());
      JsonNode versionNode = root.get("version");
      if (versionNode == null || !versionNode.isString())
        return "";

      String version = versionNode.asString();
      if (!VersionUtils.isValidVersion(version))
        return "";

      return version;
    }
    catch (IOException | InterruptedException e)
    {
      Logger log = LoggerFactory.getLogger(CheckUpdateAvailable.class);
      log.debug("Failed to fetch latest version", e);
      return "";
    }
  }

  /**
   * Updates the cache file with the latest version.
   *
   * @param scope the Claude hook context
   * @param cacheFile the cache file path
   * @param cacheDir the cache directory path
   * @param version the version to cache
   */
  private void updateCache(ClaudeHook scope, Path cacheFile, Path cacheDir, String version)
  {
    try
    {
      Files.createDirectories(cacheDir);
      ObjectNode node = scope.getJsonMapper().createObjectNode();
      node.put("version", version);
      node.put("checked", String.valueOf(Instant.now().getEpochSecond()));
      Files.writeString(cacheFile, scope.getJsonMapper().writeValueAsString(node));
    }
    catch (IOException _)
    {
      // Silently ignore cache write failures
    }
  }
}
