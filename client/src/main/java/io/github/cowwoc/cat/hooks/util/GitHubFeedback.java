/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * Performs GitHub operations for filing bug reports.
 * <p>
 * Supports searching for existing issues via the unauthenticated GitHub Search API and opening
 * a pre-filled issue creation page in the user's browser. No authentication token is required.
 * <p>
 * CLI usage:
 * <pre>
 *   GitHubFeedback search "query string"
 *   GitHubFeedback open "title" "body" [label1,label2,...]
 * </pre>
 * <p>
 * Returns JSON output suitable for agent parsing.
 */
public final class GitHubFeedback
{
  private static final String GITHUB_API_BASE = "https://api.github.com";
  private static final String GITHUB_BASE = "https://github.com";
  private static final String REPOSITORY = "cowwoc/cat";
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

  private final JvmScope scope;
  private final HttpClient httpClient;

  /**
   * Creates a new GitHubFeedback instance.
   *
   * @param scope the JVM scope providing the JSON mapper
   * @throws NullPointerException if {@code scope} is null
   */
  public GitHubFeedback(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
    this.httpClient = HttpClient.newBuilder().
      connectTimeout(REQUEST_TIMEOUT).
      build();
  }

  /**
   * Searches for GitHub issues matching the given query in the cowwoc/cat repository.
   * <p>
   * Uses the unauthenticated GitHub Search API (10 requests/minute rate limit).
   * Returns a JSON object with an {@code issues} array. Each element contains {@code number},
   * {@code title}, {@code url}, and {@code state}.
   *
   * @param query the search query string
   * @return JSON string with search results
   * @throws NullPointerException if {@code query} is null
   * @throws IllegalArgumentException if {@code query} is blank
   * @throws IOException if the HTTP request fails or returns an unexpected response
   */
  public String searchIssues(String query) throws IOException
  {
    requireThat(query, "query").isNotBlank();

    JsonMapper mapper = scope.getJsonMapper();
    String encodedQuery = URLEncoder.encode(
      "repo:" + REPOSITORY + " is:issue " + query, StandardCharsets.UTF_8);
    URI uri = URI.create(GITHUB_API_BASE + "/search/issues?q=" + encodedQuery + "&per_page=10");

    HttpRequest request = HttpRequest.newBuilder().
      uri(uri).
      timeout(REQUEST_TIMEOUT).
      header("Accept", "application/vnd.github+json").
      header("User-Agent", "cowwoc-cat-feedback/1.0").
      header("X-GitHub-Api-Version", "2022-11-28").
      GET().
      build();

    HttpResponse<String> response;
    try
    {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
      throw new IOException("GitHub search request was interrupted", e);
    }

    if (response.statusCode() != 200)
    {
      throw new IOException("GitHub search API returned status " + response.statusCode() +
        " for query: " + query + ". Response: " + response.body());
    }

    JsonNode responseJson = mapper.readTree(response.body());
    ArrayNode resultArray = mapper.createArrayNode();

    JsonNode items = responseJson.get("items");
    if (items != null && items.isArray())
    {
      for (JsonNode item : items)
      {
        ObjectNode issueNode = mapper.createObjectNode();
        issueNode.put("number", item.path("number").asInt());
        issueNode.put("title", item.path("title").asString());
        issueNode.put("url", item.path("html_url").asString());
        issueNode.put("state", item.path("state").asString());
        resultArray.add(issueNode);
      }
    }

    ObjectNode result = mapper.createObjectNode();
    result.set("issues", resultArray);
    return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
  }

  /**
   * Opens a pre-filled GitHub new issue page in the user's default browser.
   * <p>
   * Constructs a {@code https://github.com/{owner}/{repo}/issues/new?title=...&body=...&labels=...}
   * URL and opens it using {@code xdg-open} (Linux) or {@code open} (macOS). The user's browser
   * session handles GitHub authentication — no token is required.
   * <p>
   * Returns a JSON object with the {@code url} that was opened.
   *
   * @param title the issue title
   * @param body the issue body (markdown supported)
   * @param labels comma-separated list of labels to apply (may be empty)
   * @return JSON string with the opened URL
   * @throws NullPointerException if {@code title}, {@code body}, or {@code labels} are null
   * @throws IllegalArgumentException if {@code title} or {@code body} are blank
   * @throws IOException if the browser cannot be opened
   */
  public String openIssue(String title, String body, String labels) throws IOException
  {
    requireThat(title, "title").isNotBlank();
    requireThat(body, "body").isNotBlank();
    requireThat(labels, "labels").isNotNull();

    String encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8);
    String encodedBody = URLEncoder.encode(body, StandardCharsets.UTF_8);

    StringBuilder urlBuilder = new StringBuilder(128);
    urlBuilder.append(GITHUB_BASE).append('/').append(REPOSITORY).append("/issues/new").
      append("?title=").append(encodedTitle).
      append("&body=").append(encodedBody);

    if (!labels.isEmpty())
    {
      StringJoiner labelJoiner = new StringJoiner(",");
      for (String label : labels.split(","))
      {
        String trimmed = label.strip();
        if (!trimmed.isEmpty())
          labelJoiner.add(trimmed);
      }
      String joinedLabels = labelJoiner.toString();
      if (!joinedLabels.isEmpty())
        urlBuilder.append("&labels=").append(URLEncoder.encode(joinedLabels, StandardCharsets.UTF_8));
    }

    String url = urlBuilder.toString();
    openInBrowser(url);

    JsonMapper mapper = scope.getJsonMapper();
    ObjectNode result = mapper.createObjectNode();
    result.put("url", url);
    return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
  }

  /**
   * Opens the given URL in the user's default browser.
   *
   * @param url the URL to open
   * @throws IOException if the browser command fails to start or the OS is not supported
   */
  private static void openInBrowser(String url) throws IOException
  {
    String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    ProcessBuilder processBuilder;
    if (osName.contains("win"))
      processBuilder = new ProcessBuilder("cmd", "/c", "start", "", url);
    else if (osName.contains("mac"))
      processBuilder = new ProcessBuilder("open", url);
    else
      processBuilder = new ProcessBuilder("xdg-open", url);
    processBuilder.inheritIO();
    Process process = processBuilder.start();
    try
    {
      int exitCode = process.waitFor();
      if (exitCode != 0)
      {
        throw new IOException("Browser command exited with code " + exitCode +
          " for URL: " + url);
      }
    }
    catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
      throw new IOException("Browser open request was interrupted for URL: " + url, e);
    }
  }

  /**
   * Prints an error JSON message to stderr and exits with code 1.
   *
   * @param message the error message
   */
  @SuppressWarnings("PMD.DoNotTerminateVM")
  private static void exitWithError(String message)
  {
    System.err.println("""
      {
        "status": "error",
        "message": "%s"
      }""".formatted(message.replace("\"", "\\\"")));
    System.exit(1);
  }

  /**
   * Runs the "search" subcommand.
   *
   * @param feedback the GitHub feedback instance
   * @param args the command-line arguments
   */
  private static void runSearch(GitHubFeedback feedback, String[] args)
  {
    String query = args[1];
    try
    {
      String result = feedback.searchIssues(query);
      System.out.println(result);
    }
    catch (IOException e)
    {
      exitWithError(e.getMessage());
    }
  }

  /**
   * Runs the "open" subcommand.
   *
   * @param feedback the GitHub feedback instance
   * @param args the command-line arguments
   */
  private static void runOpen(GitHubFeedback feedback, String[] args)
  {
    if (args.length < 3)
    {
      exitWithError("Usage: GitHubFeedback open <title> <body> [labels]");
      return;
    }
    String title = args[1];
    String body = args[2];
    String labels;
    if (args.length >= 4)
    {
      StringJoiner joiner = new StringJoiner(",");
      for (int i = 3; i < args.length; ++i)
        joiner.add(args[i]);
      labels = joiner.toString();
    }
    else
      labels = "";

    try
    {
      String result = feedback.openIssue(title, body, labels);
      System.out.println(result);
    }
    catch (IOException e)
    {
      exitWithError(e.getMessage());
    }
  }

  /**
   * Main method for CLI invocation.
   * <p>
   * Accepts two subcommands:
   * <ul>
   *   <li>{@code search <query>} — search issues and print JSON results</li>
   *   <li>{@code open <title> <body> [labels]} — open browser with pre-filled issue URL and print JSON</li>
   * </ul>
   *
   * @param args the command-line arguments
   * @throws IOException if an I/O error occurs during initialization
   */
  public static void main(String[] args) throws IOException
  {
    if (args.length < 2)
    {
      System.err.println("""
        {
          "status": "error",
          "message": "Usage: GitHubFeedback search <query> | GitHubFeedback open <title> <body> [labels]"
        }""");
      System.exit(1);
      return;
    }

    String subcommand = args[0];

    try (JvmScope scope = new MainJvmScope())
    {
      GitHubFeedback feedback = new GitHubFeedback(scope);

      switch (subcommand)
      {
        case "search" -> runSearch(feedback, args);
        case "open" -> runOpen(feedback, args);
        default -> exitWithError(
          "Unknown subcommand: %s. Use 'search' or 'open'.".formatted(subcommand));
      }
    }
  }
}
