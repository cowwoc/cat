package io.github.cowwoc.cat.hooks.licensing;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Feature gate that orchestrates license validation and entitlement checking.
 * <p>
 * Checks if the user's license tier allows access to a specific feature.
 */
public final class FeatureGate
{
  private final LicenseValidator validator;
  private final Entitlements entitlements;

  /**
   * Creates a new feature gate with dependency injection.
   *
   * @param validator the license validator
   * @param entitlements the entitlements resolver
   * @throws NullPointerException if validator or entitlements is null
   */
  public FeatureGate(LicenseValidator validator, Entitlements entitlements)
  {
    requireThat(validator, "validator").isNotNull();
    requireThat(entitlements, "entitlements").isNotNull();
    this.validator = validator;
    this.entitlements = entitlements;
  }

  /**
   * Creates a new feature gate from plugin root.
   *
   * @param pluginRoot the plugin root directory
   * @param mapper the JSON mapper
   * @return a new feature gate
   * @throws IOException if tiers.json cannot be loaded
   * @throws NullPointerException if {@code pluginRoot} or {@code mapper} are null
   */
  public static FeatureGate create(Path pluginRoot, JsonMapper mapper) throws IOException
  {
    requireThat(pluginRoot, "pluginRoot").isNotNull();
    requireThat(mapper, "mapper").isNotNull();
    return new FeatureGate(new LicenseValidator(pluginRoot, mapper), new Entitlements(pluginRoot, mapper));
  }

  /**
   * Checks if a feature is allowed for the project's license.
   *
   * @param projectDir the project root directory
   * @param feature the feature to check
   * @return the gate result
   * @throws NullPointerException if projectDir or feature is null
   * @throws IllegalArgumentException if feature is blank
   */
  public GateResult check(Path projectDir, String feature)
  {
    requireThat(projectDir, "projectDir").isNotNull();
    requireThat(feature, "feature").isNotBlank();

    // Validate license
    LicenseResult licenseResult = validator.validate(projectDir);
    Tier tier = licenseResult.tier();
    String warning = licenseResult.warning();

    // Check entitlement
    boolean allowed = entitlements.hasFeature(tier, feature);

    if (allowed)
      return new GateResult(true, tier, feature, "", warning);

    // Find required tier
    Optional<Tier> requiredTier = entitlements.getRequiredTier(feature);
    String message;
    if (requiredTier.isEmpty())
    {
      message = "Feature '" + feature + "' is not available in any tier.";
    }
    else
    {
      message = "Feature '" + feature + "' requires " + requiredTier.get().name +
        " tier. Current: " + tier.name + ". Upgrade at https://catsforbots.com/pricing/";
    }

    return new GateResult(false, tier, feature, message, warning);
  }

  /**
   * Result of a feature gate check.
   *
   * @param allowed whether the feature is allowed
   * @param tier the current license tier
   * @param feature the checked feature
   * @param message error message if blocked, empty string if allowed
   * @param warning warning message (e.g., license expiring), empty string if none
   */
  public record GateResult(
    boolean allowed,
    Tier tier,
    String feature,
    String message,
    String warning)
  {
    /**
     * Creates a new gate result with validation.
     *
     * @param allowed whether the feature is allowed
     * @param tier the current license tier
     * @param feature the checked feature
     * @param message error message if blocked, empty string if allowed
     * @param warning warning message (e.g., license expiring), empty string if none
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if feature is blank
     */
    public GateResult
    {
      requireThat(tier, "tier").isNotNull();
      requireThat(feature, "feature").isNotBlank();
      requireThat(message, "message").isNotNull();
      requireThat(warning, "warning").isNotNull();
    }
  }
}
