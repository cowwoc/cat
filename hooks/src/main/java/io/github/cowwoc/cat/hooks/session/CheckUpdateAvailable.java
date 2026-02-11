package io.github.cowwoc.cat.hooks.session;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.HookInput;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.util.VersionUtils;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
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
 */
public final class CheckUpdateAvailable implements SessionStartHandler
{
  private static final long CACHE_MAX_AGE_SECONDS = 24 * 60 * 60;
  private static final String GITHUB_API_URL =
    "https://api.github.com/repos/cowwoc/cat/releases/latest";
  private static final Duration TIMEOUT = Duration.ofSeconds(5);
  private final JsonMapper mapper = JsonMapper.builder().build();
  private final JvmScope scope;

  /**
   * Creates a new CheckUpdateAvailable handler.
   *
   * @param scope the JVM scope providing environment configuration
   * @throws NullPointerException if scope is null
   */
  public CheckUpdateAvailable(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Checks for available updates and returns a notice if a newer version exists.
   *
   * @param input the hook input
   * @return a result with update notice if newer version available, empty otherwise
   * @throws NullPointerException if input is null
   * @throws WrappedCheckedException if an I/O error occurs reading version or cache files
   */
  @Override
  public Result handle(HookInput input)
  {
    requireThat(input, "input").isNotNull();
    Path projectDir = scope.getClaudeProjectDir();
    Path pluginRoot = scope.getClaudePluginRoot();

    try
    {
      String currentVersion = VersionUtils.getPluginVersion(pluginRoot);

      Path cacheDir = projectDir.resolve(".claude/cat/backups/update-check");
      Path cacheFile = cacheDir.resolve("latest_version.json");

      String latestVersion = getLatestVersion(cacheFile, cacheDir);
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
   * @param cacheFile the cache file path
   * @param cacheDir the cache directory path
   * @return the latest version string, or empty if unavailable
   * @throws IOException if reading the cache file fails
   */
  private String getLatestVersion(Path cacheFile, Path cacheDir) throws IOException
  {
    // Try cache first
    if (isCacheFresh(cacheFile))
    {
      String cached = readCachedVersion(cacheFile);
      if (!cached.isEmpty())
        return cached;
    }

    // Fetch from network
    String latest = fetchLatestVersion();
    if (latest.isEmpty())
      return "";

    // Update cache
    updateCache(cacheFile, cacheDir, latest);
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
   * @param cacheFile the cache file path
   * @return the cached version, or empty string if the version field is absent
   * @throws IOException if reading the cache file fails
   */
  private String readCachedVersion(Path cacheFile) throws IOException
  {
    JsonNode root = mapper.readTree(Files.readString(cacheFile));
    JsonNode versionNode = root.get("version");
    if (versionNode != null && versionNode.isString())
      return versionNode.asString();
    return "";
  }

  /**
   * Fetches the latest version from the GitHub releases API.
   *
   * @return the latest version (without leading 'v'), or empty string on failure
   */
  private String fetchLatestVersion()
  {
    try (HttpClient client = HttpClient.newBuilder().
      connectTimeout(TIMEOUT).
      build())
    {
      HttpRequest request = HttpRequest.newBuilder().
        uri(URI.create(GITHUB_API_URL)).
        timeout(TIMEOUT).
        GET().
        build();

      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200)
        return "";

      JsonNode root = mapper.readTree(response.body());
      JsonNode tagNode = root.get("tag_name");
      if (tagNode == null || !tagNode.isString())
        return "";

      String version = tagNode.asString();
      // Strip leading 'v' if present
      if (version.startsWith("v"))
        version = version.substring(1);
      return version;
    }
    catch (IOException | InterruptedException _)
    {
      return "";
    }
  }

  /**
   * Updates the cache file with the latest version.
   *
   * @param cacheFile the cache file path
   * @param cacheDir the cache directory path
   * @param version the version to cache
   */
  private void updateCache(Path cacheFile, Path cacheDir, String version)
  {
    try
    {
      Files.createDirectories(cacheDir);
      ObjectNode node = mapper.createObjectNode();
      node.put("version", version);
      node.put("checked", String.valueOf(Instant.now().getEpochSecond()));
      Files.writeString(cacheFile, mapper.writeValueAsString(node));
    }
    catch (IOException _)
    {
      // Silently ignore cache write failures
    }
  }
}
