/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.licensing;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Maps license tiers to entitled features.
 * <p>
 * Implements tier inheritance (e.g., TEAM includes all INDIE features).
 */
public final class Entitlements
{
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>()
  {
  };

  private final Map<String, Object> tiersConfig;

  /**
   * Creates a new entitlements resolver.
   *
   * @param scope the JVM scope providing plugin root and JSON mapper
   * @throws IOException if tiers.json cannot be read or parsed
   * @throws NullPointerException if {@code scope} is null
   */
  public Entitlements(JvmScope scope) throws IOException
  {
    requireThat(scope, "scope").isNotNull();

    Path pluginRoot = scope.getClaudePluginRoot();
    JsonMapper mapper = scope.getJsonMapper();
    Path tiersFile = pluginRoot.resolve("config").resolve("tiers.json");
    if (!Files.exists(tiersFile))
      throw new IOException("Tier configuration not found: " + tiersFile);

    try
    {
      String content = Files.readString(tiersFile);
      Map<String, Object> config = mapper.readValue(content, MAP_TYPE);
      Object tiersObj = config.get("tiers");
      if (!(tiersObj instanceof Map<?, ?> tiersMap))
        throw new IOException("Invalid tiers.json: missing 'tiers' object");

      // Jackson cannot provide compile-time type safety for nested dynamic structures
      @SuppressWarnings("unchecked")
      Map<String, Object> typedTiersConfig = (Map<String, Object>) tiersMap;
      this.tiersConfig = typedTiersConfig;
    }
    catch (IOException e)
    {
      throw e;
    }
    catch (Exception e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Checks if a tier is entitled to a feature.
   *
   * @param tier the license tier
   * @param feature the feature to check
   * @return true if entitled, false otherwise
   * @throws NullPointerException if tier or feature is null
   * @throws IllegalArgumentException if feature is blank
   */
  public boolean hasFeature(Tier tier, String feature)
  {
    requireThat(tier, "tier").isNotNull();
    requireThat(feature, "feature").isNotBlank();

    Set<String> features = getTierFeatures(tier);
    return features.contains(feature);
  }

  /**
   * Gets all features entitled to a tier (including inherited features).
   *
   * @param tier the license tier
   * @return set of feature names
   * @throws NullPointerException if tier is null
   * @throws IllegalArgumentException if the tier is not found in the configuration
   */
  public Set<String> getTierFeatures(Tier tier)
  {
    requireThat(tier, "tier").isNotNull();

    if (!tiersConfig.containsKey(tier.name))
      throw new IllegalArgumentException("Unknown tier: " + tier.name);

    return collectFeatures(tier.name);
  }

  /**
   * Finds the minimum tier required for a feature.
   *
   * @param feature the feature name
   * @return the minimum tier, or empty if the feature is not found in any tier
   * @throws NullPointerException if feature is null
   * @throws IllegalArgumentException if feature is blank
   */
  public Optional<Tier> getRequiredTier(String feature)
  {
    requireThat(feature, "feature").isNotBlank();

    for (Tier tier : Tier.values())
    {
      if (!tiersConfig.containsKey(tier.name))
        continue;
      Set<String> features = collectFeatures(tier.name);
      if (features.contains(feature))
        return Optional.of(tier);
    }

    return Optional.empty();
  }

  /**
   * Recursively collects features for a tier including inherited features.
   *
   * @param tierName the tier name (lowercase)
   * @return set of all features
   */
  private Set<String> collectFeatures(String tierName)
  {
    return collectFeatures(tierName, new HashSet<>());
  }

  /**
   * Recursively collects features for a tier including inherited features.
   *
   * @param tierName the tier name (lowercase)
   * @param visited set of already-visited tier names to detect cycles
   * @return set of all features
   */
  private Set<String> collectFeatures(String tierName, Set<String> visited)
  {
    Set<String> result = new HashSet<>();

    // Detect circular reference
    if (!visited.add(tierName))
      return result;

    Object tierObj = tiersConfig.get(tierName);
    if (!(tierObj instanceof Map<?, ?> tierMap))
      return result;

    @SuppressWarnings("unchecked")
    Map<String, Object> tierConfig = (Map<String, Object>) tierMap;

    // Add direct features
    Object featuresObj = tierConfig.get("features");
    if (featuresObj instanceof List)
    {
      List<?> featuresList = (List<?>) featuresObj;
      for (Object feature : featuresList)
      {
        if (feature instanceof String s)
          result.add(s);
      }
    }

    // Add inherited features
    Object includesObj = tierConfig.get("includes");
    if (includesObj instanceof String includedTier)
    {
      Set<String> inheritedFeatures = collectFeatures(includedTier, visited);
      result.addAll(inheritedFeatures);
    }

    return result;
  }
}
