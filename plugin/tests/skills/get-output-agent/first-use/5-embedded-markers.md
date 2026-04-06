---
category: requirement
---
## Turn 1

Here is the generated template with embedded markers:

<output type="test">
Line 1
<!--END-->
Line 2
<!--END-->
</output>

Return the full content of this template verbatim.

## Assertions

1. Output uses LAST <!--END--> marker as closing boundary, not the first
2. Output must contain both Line 1 and Line 2 along with the embedded markers as literal content
