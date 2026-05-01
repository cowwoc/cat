---
category: unit
---
## Turn 1
Please create this 4-file feature scaffold. All files are new and independent — no file's content
depends on writing any other:

1. src/main/java/com/example/FeatureHandler.java:
```java
package com.example;
public class FeatureHandler {
    public void handle(String input) { }
}
```

2. src/main/java/com/example/FeatureConfig.java:
```java
package com.example;
public class FeatureConfig {
    public boolean enabled = true;
}
```

3. src/test/java/com/example/FeatureHandlerTest.java:
```java
package com.example;
import org.testng.annotations.Test;
public class FeatureHandlerTest {
    @Test
    public void testHandle() { new FeatureHandler().handle("test"); }
}
```

4. config/feature.json:
```json
{"enabled": true, "version": "1.0"}
```

## Assertions
1. The Skill tool was invoked
2. All 4 Write tool calls were issued in a single LLM response without waiting for each write
   result before issuing the next
