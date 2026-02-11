package io.github.cowwoc.cat.hooks.licensing;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import java.util.Locale;

/**
 * License tiers that determine feature entitlements.
 * <p>
 * Tiers are ordered from least to most capable: INDIE, TEAM, ENTERPRISE.
 * Each tier inherits features from all lower tiers.
 */
public enum Tier
{
  /**
   * Free tier with basic features.
   */
  INDIE("indie"),

  /**
   * Team tier with collaboration features.
   */
  TEAM("team"),

  /**
   * Enterprise tier with all features.
   */
  ENTERPRISE("enterprise");

  /**
   * The lowercase string name used in JSON and configuration files.
   */
  public final String name;

  /**
   * Creates a new tier.
   *
   * @param name the lowercase string name for JSON/config matching
   */
  Tier(String name)
  {
    this.name = name;
  }

  /**
   * Looks up a tier by its string name, case-insensitively.
   *
   * @param name the tier name (e.g., "indie", "TEAM", "Enterprise")
   * @return the matching tier
   * @throws NullPointerException if name is null
   * @throws IllegalArgumentException if name is blank or does not match any tier
   */
  public static Tier fromString(String name)
  {
    requireThat(name, "name").isNotBlank();
    String normalized = name.toLowerCase(Locale.ROOT);
    for (Tier tier : values())
    {
      if (tier.name.equals(normalized))
        return tier;
    }
    throw new IllegalArgumentException("Unknown tier: " + name);
  }
}
