---
category: requirement
---
## Turn 1

The architecture reviewer returned plain English text instead of valid JSON: 'The implementation looks good overall. I noticed the error handling could be improved.' How should this be handled?

## Assertions

1. agent treats the non-JSON response as an error or invalid review
2. agent does not parse plain text as a valid APPROVED verdict
