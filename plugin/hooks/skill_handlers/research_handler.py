"""
Handler for /cat:research precomputation.

Pre-computes box templates for the executive summary output.
The skill fills in reasoned content using these templates.
"""

from pathlib import Path

from . import register_handler

# Box width for executive summary (inner content width)
BOX_WIDTH = 74


def build_border(is_top: bool) -> str:
    """Build top or bottom border."""
    dashes = '─' * (BOX_WIDTH + 2)
    if is_top:
        return '╭' + dashes + '╮'
    else:
        return '╰' + dashes + '╯'


def build_divider() -> str:
    """Build section divider."""
    return '├' + '─' * (BOX_WIDTH + 2) + '┤'


def build_line(content: str) -> str:
    """Build a content line with padding."""
    # Truncate if too long, pad if too short
    if len(content) > BOX_WIDTH:
        content = content[:BOX_WIDTH - 3] + '...'
    return '│ ' + content.ljust(BOX_WIDTH) + ' │'


def build_empty_line() -> str:
    """Build an empty line."""
    return '│' + ' ' * (BOX_WIDTH + 2) + '│'


class ResearchHandler:
    """Handler for /cat:research skill."""

    def handle(self, context: dict) -> str | None:
        """Pre-compute executive summary templates."""
        user_prompt = context.get("user_prompt", "")

        # Only activate for /cat:research
        if "/cat:research" not in user_prompt:
            return None

        # Pre-compute all structural templates
        templates = self._build_templates()

        return f"""PRE-COMPUTED RESEARCH TEMPLATES:

The following templates are pre-computed for executive summary rendering.
Use these templates to build the executive summary output after synthesizing
stakeholder findings.

## Box Structure Templates

```
TOP_BORDER:
{templates['top_border']}

BOTTOM_BORDER:
{templates['bottom_border']}

DIVIDER:
{templates['divider']}

EMPTY_LINE:
{templates['empty_line']}
```

## Line Templates (width={BOX_WIDTH})

To build a content line, use this format:
- Start with "│ "
- Add content left-justified to {BOX_WIDTH} characters
- End with " │"

Example lines:
{templates['example_header']}
{templates['example_content']}
{templates['example_rating']}

## Executive Summary Structure

Use this structure for the executive summary:

```
{{TOP_BORDER}}
{{header: "📋 Executive Summary"}}
{{DIVIDER}}
{{EMPTY_LINE}}
{{line: "Approaches Identified: N"}}
{{EMPTY_LINE}}
{{DIVIDER}}
{{header: "Option 1: [Name]"}}
{{EMPTY_LINE}}
{{line: "Description: [1-2 sentences]"}}
{{EMPTY_LINE}}
{{line: "Advocates: [stakeholders who favor this]"}}
{{EMPTY_LINE}}
{{line: "Tradeoffs:"}}
{{line: "  • [Stakeholder]: [concern]"}}
{{line: "  • [Stakeholder]: [concern]"}}
{{EMPTY_LINE}}
{{line: "Best when: [preference fit]"}}
{{EMPTY_LINE}}
{{DIVIDER}}
... repeat for each option ...
{{DIVIDER}}
{{header: "⚡ Quick Decision Guide"}}
{{EMPTY_LINE}}
{{line: "If you prioritize...        Consider..."}}
{{line: "─────────────────────────────────────────"}}
{{line: "Speed to market             Option X"}}
{{line: "Long-term maintainability   Option Y"}}
{{line: "Minimal cost                Option Z"}}
{{line: "Security compliance         Option W"}}
{{EMPTY_LINE}}
{{BOTTOM_BORDER}}
```

## Rating Display

Use star ratings for quick visual comparison:
- ★★★★★ = Excellent fit
- ★★★★☆ = Good fit
- ★★★☆☆ = Moderate fit
- ★★☆☆☆ = Poor fit
- ★☆☆☆☆ = Not recommended

INSTRUCTION: After synthesizing stakeholder findings into options, build the
executive summary using these templates. Fill in the content and extend to
as many lines as needed using the line format."""

    def _build_templates(self) -> dict:
        """Build all template strings."""
        return {
            'top_border': build_border(is_top=True),
            'bottom_border': build_border(is_top=False),
            'divider': build_divider(),
            'empty_line': build_empty_line(),
            'example_header': build_line('📋 Executive Summary'),
            'example_content': build_line('Description: A cloud-native approach using managed services'),
            'example_rating': build_line('Cost: ★★☆☆☆  Speed: ★★★★★  Simplicity: ★★★☆☆'),
        }


# Register handler
_handler = ResearchHandler()
register_handler("research", _handler)
