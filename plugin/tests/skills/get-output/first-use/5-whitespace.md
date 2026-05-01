---
category: requirement
---
## Turn 1

The diff tool generated this whitespace-sensitive result:

<output type="test">
   leading
trailing
multiple    spaces
</output>

I need the exact content with all spacing preserved.

## Assertions

1. All whitespace is preserved exactly as in the source
2. Output must contain leading spaces, trailing spaces, and multiple internal spaces verbatim
