---
category: negative
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

I need two things done in order. First, create a new file src/util/StringUtils.java that defines a public class
StringUtils with a static method truncate(String s, int max). Then, edit src/service/UserService.java to add
'import com.example.util.StringUtils;' at the top and call 'StringUtils.truncate(name, 50)' inside the existing
saveUser() method.

## Assertions

1. Agent does not propose batching these two operations together
2. Agent does not suggest executing both in a single response
3. Agent sequences the operations: create StringUtils.java first, then edit UserService.java
4. Agent demonstrates understanding that the second operation depends on the first
