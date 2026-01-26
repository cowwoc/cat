"""
Handler for /cat:research precomputation.

Provides utility functions for rendering stakeholder research display elements:
- Rating circles conversion
- Scorecard box building
- Comparison table building
- Total calculations

Since research content is reasoning-based, this handler provides templates and
calculation functions rather than output template output.
"""

from . import register_handler


# Rating circle constants
FILLED_CIRCLE = '●'
EMPTY_CIRCLE = '○'

# Box width for displays
BOX_WIDTH = 74


def rating_to_circles(rating: int) -> str:
    """Convert a 1-5 rating to filled/empty circle display.

    Args:
        rating: Integer from 1 to 5

    Returns:
        String like "●●●●○" for rating 4
    """
    if rating < 1:
        rating = 1
    if rating > 5:
        rating = 5
    return FILLED_CIRCLE * rating + EMPTY_CIRCLE * (5 - rating)


def sum_ratings(ratings: dict) -> tuple[int, int]:
    """Calculate total score from a ratings dict.

    Args:
        ratings: Dict like {"Speed": 4, "Cost": 3, "Architect": 5, ...}

    Returns:
        Tuple of (total_score, max_possible)
    """
    total = sum(ratings.values())
    max_possible = len(ratings) * 5
    return total, max_possible


def build_scorecard_row_pair(label1: str, rating1: int, label2: str, rating2: int) -> str:
    """Build a pair of ratings on one line.

    Args:
        label1: First dimension name (e.g., "Speed")
        rating1: First rating (1-5)
        label2: Second dimension name (e.g., "Cost")
        rating2: Second rating (1-5)

    Returns:
        Formatted string like "│ Speed        ●●●●○  Cost         ●●●○○                              │"
    """
    circles1 = rating_to_circles(rating1)
    circles2 = rating_to_circles(rating2)
    content = f"{label1:<12} {circles1}  {label2:<12} {circles2}"
    # Inner width is 69 (BOX_WIDTH - 5). Row format is "│ {content}{padding} │"
    # So padding = inner_width - 2 - len(content) to account for "│ " prefix and " │" suffix
    inner_width = 69
    padding = inner_width - 2 - len(content)
    return f"│ {content}{' ' * padding} │"


def build_scorecard_row_triple(
    label1: str, rating1: int,
    label2: str, rating2: int,
    label3: str, rating3: int
) -> str:
    """Build a triple of ratings on one line.

    Args:
        label1-3: Dimension names
        rating1-3: Ratings (1-5)

    Returns:
        Formatted string with three ratings
    """
    circles1 = rating_to_circles(rating1)
    circles2 = rating_to_circles(rating2)
    circles3 = rating_to_circles(rating3)
    content = f"{label1:<12} {circles1}  {label2:<12} {circles2}  {label3:<11} {circles3}"
    # Inner width is 69 (BOX_WIDTH - 5). Row format is "│ {content}{padding} │"
    # So padding = inner_width - 2 - len(content) to account for "│ " prefix and " │" suffix
    inner_width = 69
    padding = inner_width - 2 - len(content)
    return f"│ {content}{' ' * padding} │"


def build_scorecard(ratings: dict) -> list[str]:
    """Build a complete rating scorecard box.

    Args:
        ratings: Dict with keys: Speed, Cost, Quality (top-level),
                 Architect, Security, Tester, Performance, UX, Sales, Marketing, Legal

    Returns:
        List of strings forming the scorecard box
    """
    inner_width = 69
    lines = [
        "┌" + "─" * inner_width + "┐",
        "│ RATING SCORECARD" + " " * (inner_width - 17) + "│",
        "├" + "─" * inner_width + "┤",
        build_scorecard_row_triple("Speed", ratings.get("Speed", 3),
                                    "Cost", ratings.get("Cost", 3),
                                    "Quality", ratings.get("Quality", 3)),
        "├" + "─" * inner_width + "┤",
        build_scorecard_row_triple("Architect", ratings.get("Architect", 3),
                                    "Security", ratings.get("Security", 3),
                                    "Tester", ratings.get("Tester", 3)),
        build_scorecard_row_triple("Performance", ratings.get("Performance", 3),
                                    "UX", ratings.get("UX", 3),
                                    "Sales", ratings.get("Sales", 3)),
        build_scorecard_row_pair("Marketing", ratings.get("Marketing", 3),
                                  "Legal", ratings.get("Legal", 3)),
        "└" + "─" * inner_width + "┘",
    ]
    return lines


def build_comparison_row(dimension: str, ratings: list[int]) -> str:
    """Build a single row of the comparison table.

    Args:
        dimension: Row label (e.g., "Speed")
        ratings: List of ratings for each option [opt1_rating, opt2_rating, ...]

    Returns:
        Formatted table row string
    """
    cols = [rating_to_circles(r) if r > 0 else "     " for r in ratings]
    # Pad to 3 columns
    while len(cols) < 3:
        cols.append("     ")

    return f"│ {dimension:<14}│ {cols[0]:<14}│ {cols[1]:<14}│ {cols[2]:<14}│"


def build_comparison_table(options: list[dict]) -> list[str]:
    """Build a side-by-side comparison table for all options.

    Args:
        options: List of option dicts, each containing:
                 - name: Option name
                 - ratings: Dict of dimension -> rating

    Returns:
        List of strings forming the comparison table
    """
    # Get option names (truncate if needed)
    names = [opt.get("name", f"Option {i+1}")[:14] for i, opt in enumerate(options)]
    while len(names) < 3:
        names.append("")

    # Header
    lines = [
        "╭" + "─" * BOX_WIDTH + "╮",
        "│ ⚖️  Side-by-Side Comparison" + " " * (BOX_WIDTH - 28) + "│",
        "├" + "─" * BOX_WIDTH + "┤",
        "│" + " " * BOX_WIDTH + "│",
        f"│ {'Dimension':<14}│ {names[0]:<14}│ {names[1]:<14}│ {names[2]:<14}│" + " " * 9 + "│",
        "│ " + "─" * 15 + "┼" + "─" * 15 + "┼" + "─" * 15 + "┼" + "─" * 15 + "│" + " " * 8 + "│",
    ]

    # Speed, Cost, Quality rows (top-level metrics)
    dimensions_core = ["Speed", "Cost", "Quality"]
    for dim in dimensions_core:
        ratings = [opt.get("ratings", {}).get(dim, 3) for opt in options]
        while len(ratings) < 3:
            ratings.append(0)
        lines.append(build_comparison_row(dim, ratings) + " " * 8 + "│")

    # Divider
    lines.append("│ " + "─" * 15 + "┼" + "─" * 15 + "┼" + "─" * 15 + "┼" + "─" * 15 + "│" + " " * 8 + "│")

    # Stakeholder dimensions
    dimensions_stakeholder = ["Architect", "Security", "Tester",
                              "Performance", "UX", "Sales", "Marketing", "Legal"]
    for dim in dimensions_stakeholder:
        ratings = [opt.get("ratings", {}).get(dim, 3) for opt in options]
        while len(ratings) < 3:
            ratings.append(0)
        lines.append(build_comparison_row(dim, ratings) + " " * 8 + "│")

    # Divider and totals
    lines.append("│ " + "─" * 15 + "┼" + "─" * 15 + "┼" + "─" * 15 + "┼" + "─" * 15 + "│" + " " * 8 + "│")

    totals = []
    for opt in options:
        ratings = opt.get("ratings", {})
        total, max_possible = sum_ratings(ratings)
        totals.append(f"{total}/{max_possible}")
    while len(totals) < 3:
        totals.append("")

    lines.append(f"│ {'TOTAL':<14}│ {totals[0]:<14}│ {totals[1]:<14}│ {totals[2]:<14}│" + " " * 9 + "│")
    lines.append("│" + " " * BOX_WIDTH + "│")
    lines.append("╰" + "─" * BOX_WIDTH + "╯")

    return lines


def build_concerns_box(concerns: dict) -> list[str]:
    """Build the stakeholder concerns display box.

    Args:
        concerns: Dict mapping stakeholder name to list of concern strings
                  e.g., {"ARCHITECT": ["Concern 1", "Concern 2"], ...}

    Returns:
        List of strings forming the concerns box
    """
    lines = [
        "╭" + "─" * BOX_WIDTH + "╮",
        "│ 🔍 Stakeholder Concerns" + " " * (BOX_WIDTH - 25) + "│",
        "├" + "─" * BOX_WIDTH + "┤",
        "│" + " " * BOX_WIDTH + "│",
    ]

    stakeholder_order = ["ARCHITECT", "SECURITY", "QUALITY", "TESTER", "PERFORMANCE",
                         "UX", "SALES", "MARKETING", "LEGAL"]

    for stakeholder in stakeholder_order:
        concern_list = concerns.get(stakeholder, [])
        header_text = f"{stakeholder} concerns:"
        lines.append(f"│ {header_text}" + " " * (BOX_WIDTH - len(header_text) - 1) + "│")
        for concern in concern_list[:3]:  # Max 3 concerns per stakeholder
            # Truncate long concerns
            if len(concern) > BOX_WIDTH - 7:
                concern = concern[:BOX_WIDTH - 10] + "..."
            bullet_text = f"  • {concern}"
            lines.append(f"│ {bullet_text}" + " " * (BOX_WIDTH - len(bullet_text) - 1) + "│")
        lines.append("│" + " " * BOX_WIDTH + "│")

    lines.append("╰" + "─" * BOX_WIDTH + "╯")
    return lines


def build_options_box_header() -> list[str]:
    """Build the header for the recommended approaches box."""
    return [
        "╭" + "─" * BOX_WIDTH + "╮",
        "│ 📋 Recommended Approaches" + " " * (BOX_WIDTH - 27) + "│",
        "├" + "─" * BOX_WIDTH + "┤",
        "│" + " " * BOX_WIDTH + "│",
    ]


def build_options_box_footer() -> str:
    """Build the footer for the recommended approaches box."""
    return "╰" + "─" * BOX_WIDTH + "╯"


def build_option_section_divider() -> str:
    """Build a divider between options."""
    return "├" + "─" * BOX_WIDTH + "┤"


class ResearchHandler:
    """Handler for /cat:research skill.

    Provides utility functions for the skill rather than output template output,
    since research content requires AI reasoning.
    """

    def handle(self, context: dict) -> str | None:
        """Return template instructions for research display.

        Since research involves AI reasoning to generate content, this handler
        provides the template structure and utility function references.
        """
        # Example ratings for template demonstration
        example_ratings = {
            "Speed": 4, "Cost": 3, "Architect": 4, "Security": 5, "Quality": 3,
            "Tester": 3, "Performance": 3, "UX": 4, "Sales": 4, "Marketing": 3, "Legal": 5
        }
        example_scorecard = build_scorecard(example_ratings)
        example_total, example_max = sum_ratings(example_ratings)

        return f"""RESEARCH DISPLAY TEMPLATES LOADED

## Rating System

Use these output template circle patterns for ratings 1-5:
- 5 → ●●●●●  (Excellent)
- 4 → ●●●●○  (Good)
- 3 → ●●●○○  (Moderate)
- 2 → ●●○○○  (Poor)
- 1 → ●○○○○  (Very Poor)

## 11 Rating Dimensions

**Top-level metrics (1-5):**
1. Speed - Time to implement and deploy
2. Cost - Total cost of ownership
3. Quality - Code quality and maintainability

**Stakeholder dimensions (1-5):**
4. Architect - Addresses architectural concerns
5. Security - Addresses security concerns
6. Tester - Addresses testing concerns
7. Performance - Addresses performance concerns
8. UX - Addresses user experience concerns
9. Sales - Addresses sales/value concerns
10. Marketing - Addresses marketing concerns
11. Legal - Addresses legal/compliance concerns

## Scorecard Template

Example scorecard (total: {example_total}/{example_max}):
```
{chr(10).join(example_scorecard)}
```

## Calculation Gates (VERIFY BEFORE OUTPUT)

- [ ] All ratings are integers 1-5
- [ ] Each option has all 11 dimensions rated
- [ ] Total = sum of all 11 ratings
- [ ] Max possible = 55 (11 × 5)
- [ ] Circle pattern matches rating number

## Display Sequence

1. CONCERNS BOX - Present stakeholder concerns FIRST
2. OPTIONS WITH SCORECARDS - Each option with its rating scorecard
3. COMPARISON TABLE - Side-by-side with all options
4. WIZARD - Use AskUserQuestion for selection

## Provider Research

When an option refers to a category (e.g., "Payment Orchestration Platform"):
- Research top 3 specific providers
- List with brief rationale for each

INSTRUCTION: Use circle patterns exactly as shown. Calculate totals correctly.
Do not hand-draw boxes. Fill in the template structures with reasoned content."""


# Expose utility functions at module level for imports
__all__ = [
    'rating_to_circles',
    'sum_ratings',
    'build_scorecard_row_pair',
    'build_scorecard_row_triple',
    'build_scorecard',
    'build_comparison_row',
    'build_comparison_table',
    'build_concerns_box',
    'build_options_box_header',
    'build_options_box_footer',
    'build_option_section_divider',
]

# Register handler
_handler = ResearchHandler()
register_handler("research", _handler)
