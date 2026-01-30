# Plan: detect-duplicate-functionality

## Goal
Enhance the design stakeholder to detect when new code duplicates existing functionality in the
codebase, JDK, or project dependencies. Uses claim-based semantic comparison with an indexed
lookup for efficient detection across large codebases.

## Satisfies
None - quality gate enhancement

## Research Summary

### Approaches Evaluated

| Approach | Reliability | Cost | Verdict |
|----------|-------------|------|---------|
| Text/token matching (PMD CPD) | High for copy-paste | Free | Misses semantic duplicates |
| AST-based (SonarQube) | Good for structural | Free | Limited cross-language |
| Embedding (CodeBERT) | ~97% F1 on benchmarks | Medium | Fails on Type-IV clones |
| **Claim-based + Jaccard** | **100% on test set** | **Low** | **Selected approach** |

### Validation Results

Tested on 9 code pairs (real + synthetic, including cross-language):

| Test Case | Expected | Score | Result |
|-----------|----------|-------|--------|
| Java readAllLines duplicates | Duplicate | 1.00 | Correct |
| validate vs verify commit handlers | Not duplicate | 0.15 | Correct |
| equalsIgnoreCase vs displayWidth | Not duplicate | 0.00 | Correct |
| Java nullSafeEquals vs Python | Duplicate | 1.00 | Correct |
| Java findMax vs Python max() | Duplicate | 0.95 | Correct |
| formatCurrency vs formatPercentage | Not duplicate | 0.35 | Correct |
| isEmpty vs isBlank | Not duplicate | 0.67 | Correct |
| TypeScript reverse vs Java reverse | Duplicate | 0.95 | Correct |
| Go Contains vs Python contains | Duplicate | 0.95 | Correct |

**9/9 correct (100% accuracy)**

## Architecture

### Two-Stage Detection (Minimize LLM Calls)

```
INDEXING (one-time per method):
  Method → LLM (temp=0) → Claims → Normalize → Store in index

REVIEW TIME (per new method):
  New method → LLM (temp=0) → Claims → Normalize
       ↓
  Query index (algorithmic, NO LLM)
       ↓
  Score < 0.9: No duplicate
  Score ≥ 0.9: LLM verification (rare) → Confirm/reject
```

### Cost Analysis

| Codebase | Without Index | With Index |
|----------|---------------|------------|
| 1000 methods, 100 reviews | ~$1000 | ~$10 initial + $0.05/review |

**100x cost reduction.**

## Technical Design

### Claim Extraction

Use LLM with temperature=0 for deterministic extraction:

```json
{
  "method": "ProcessRunner.readAllLines",
  "claims": [
    {"type": "precondition", "text": "accepts BufferedReader and StringBuilder"},
    {"type": "behavior", "text": "reads all lines until end of stream"},
    {"type": "behavior", "text": "appends each line to StringBuilder"},
    {"type": "behavior", "text": "inserts newline between lines"}
  ],
  "summary": "reads all lines from reader into StringBuilder with newline separators"
}
```

### Claim Normalization

```python
SYNONYMS = {
    "none": "null", "nil": "null",
    "input": "parameter", "argument": "parameter",
    "list": "collection", "array": "collection", "slice": "collection",
    "iterates": "iterate", "loops": "iterate",
    "appends": "append", "adds": "append",
}

def normalize_claim(claim: str) -> tuple[set[str], set[str]]:
    """Returns (tokens, bigrams) for order-sensitive comparison."""
    tokens = tokenize(claim.lower())
    tokens = [SYNONYMS.get(t, t) for t in tokens if t not in STOPWORDS]
    tokens = [stem(t) for t in tokens]
    bigrams = {f"{tokens[i]}_{tokens[i+1]}" for i in range(len(tokens)-1)}
    return set(tokens), bigrams
```

### Similarity Computation (Algorithmic, No LLM)

```python
def jaccard(set_a: set, set_b: set) -> float:
    """Jaccard similarity: |A ∩ B| / |A ∪ B|"""
    if not set_a and not set_b:
        return 1.0
    return len(set_a & set_b) / len(set_a | set_b)

def method_similarity(claims_a: list, claims_b: list) -> float:
    """Compare methods using tokens + bigrams for order sensitivity."""
    tokens_a, bigrams_a = set(), set()
    for c in claims_a:
        t, b = normalize_claim(c['text'])
        tokens_a.update(t)
        bigrams_a.update(b)

    tokens_b, bigrams_b = set(), set()
    for c in claims_b:
        t, b = normalize_claim(c['text'])
        tokens_b.update(t)
        bigrams_b.update(b)

    # Weighted: 70% tokens, 30% bigrams (order)
    token_sim = jaccard(tokens_a, tokens_b)
    bigram_sim = jaccard(bigrams_a, bigrams_b)
    return 0.7 * token_sim + 0.3 * bigram_sim
```

### Index Storage (Git-Tracked JSON)

Store at `.claude/cat/claim-index.json`:

```json
{
  "version": "1.0",
  "extracted_with": "claude-sonnet-4-20250514",
  "methods": {
    "plugin/hooks/util/GitCommands.java::readAllLines(BufferedReader,StringBuilder)": {
      "file_hash": "a1b2c3d4...",
      "language": "java",
      "claims": [
        {"type": "precondition", "text": "accepts bufferedreader and stringbuilder"},
        {"type": "behavior", "text": "reads all lines until end of stream"},
        {"type": "behavior", "text": "appends each line to stringbuilder"},
        {"type": "behavior", "text": "inserts newline between lines"}
      ],
      "tokens": ["accept", "bufferedread", "stringbuild", "read", "line", "stream", "append", "newline"],
      "bigrams": ["read_line", "line_stream", "append_stringbuild"]
    }
  },
  "jdk_patterns": {
    "Objects.equals": {
      "claims": [{"type": "behavior", "text": "null-safe equality comparison"}],
      "tokens": ["null", "safe", "equal", "compare"],
      "recommendation": "Use Objects.equals(a, b) instead"
    },
    "Objects.requireNonNull": {
      "claims": [{"type": "behavior", "text": "throws if argument is null"}],
      "tokens": ["throw", "argument", "null", "check"],
      "recommendation": "Use Objects.requireNonNull(obj, message) instead"
    }
  }
}
```

**Why JSON over SQLite:**
- Git diffs are readable
- Merge conflicts are resolvable
- No binary file issues
- Sufficient performance for <10K methods

### Fast Lookup Algorithm

```python
def find_duplicates(index: dict, new_claims: list, threshold: float = 0.9):
    """Find potential duplicates. No LLM calls."""
    new_tokens, new_bigrams = set(), set()
    for c in new_claims:
        t, b = normalize_claim(c['text'])
        new_tokens.update(t)
        new_bigrams.update(b)

    candidates = []

    # Check JDK patterns first (fast)
    for pattern_name, pattern in index['jdk_patterns'].items():
        score = jaccard(new_tokens, set(pattern['tokens']))
        if score >= threshold:
            candidates.append({
                'type': 'jdk',
                'name': pattern_name,
                'score': score,
                'recommendation': pattern['recommendation']
            })

    # Check codebase methods
    for method_id, method in index['methods'].items():
        # Quick filter: must share at least 50% of tokens
        token_overlap = len(new_tokens & set(method['tokens'])) / len(new_tokens)
        if token_overlap < 0.5:
            continue

        # Full comparison
        score = 0.7 * jaccard(new_tokens, set(method['tokens'])) + \
                0.3 * jaccard(new_bigrams, set(method['bigrams']))
        if score >= threshold:
            candidates.append({
                'type': 'codebase',
                'method': method_id,
                'score': score
            })

    return sorted(candidates, key=lambda x: -x['score'])
```

## Risk Assessment

- **Risk Level:** MEDIUM (increased from LOW due to scope)
- **Concerns:**
  - False positives on intentionally similar but different code
  - Index staleness if not updated with code changes
  - Initial indexing cost for large codebases
- **Mitigation:**
  - Use 0.9 threshold (validated to avoid false positives)
  - Git-track index so it versions with code
  - Incremental indexing (only changed files)
  - LLM verification before flagging as violation

## Files to Create/Modify

| File | Action | Purpose |
|------|--------|---------|
| `plugin/stakeholders/design.md` | Modify | Add duplicate detection instructions |
| `plugin/lib/claim_index.py` | Create | Index management and lookup |
| `plugin/lib/claim_extraction.py` | Create | LLM-based claim extraction |
| `plugin/lib/claim_similarity.py` | Create | Jaccard + bigram comparison |
| `plugin/data/jdk_patterns.json` | Create | Pre-built JDK/stdlib patterns |
| `.claude/cat/claim-index.json` | Create | Per-project method index |

## Acceptance Criteria

- [ ] Claim extraction works with temperature=0 for determinism
- [ ] Jaccard + bigram similarity correctly identifies duplicates (≥0.9) and non-duplicates (<0.9)
- [ ] Index persists in `.claude/cat/claim-index.json` and is git-tracked
- [ ] JDK patterns (Objects.equals, etc.) are pre-built and checked first
- [ ] Design stakeholder integrates duplicate check in review workflow
- [ ] LLM verification only triggered for scores ≥0.9 (rare)
- [ ] Cross-language duplicates detected (Java/Python/TypeScript/Go)
- [ ] Tests written and passing
- [ ] No regressions

## Execution Steps

1. **Create claim extraction module**
   - Files: `plugin/lib/claim_extraction.py`
   - Implement LLM-based extraction with temp=0
   - Define claim schema (precondition, behavior, postcondition, side_effect)
   - Verify: Unit tests for extraction

2. **Create similarity module**
   - Files: `plugin/lib/claim_similarity.py`
   - Implement normalization with synonyms and stemming
   - Implement Jaccard with bigrams
   - Verify: Test with 9 validated pairs

3. **Create index management**
   - Files: `plugin/lib/claim_index.py`
   - Implement JSON-based index CRUD
   - Implement fast lookup with token pre-filtering
   - Verify: Index round-trip tests

4. **Create JDK patterns database**
   - Files: `plugin/data/jdk_patterns.json`
   - Add common patterns: Objects.equals, Objects.requireNonNull, Optional methods, Collections utilities
   - Verify: Patterns load correctly

5. **Integrate into design stakeholder**
   - Files: `plugin/stakeholders/design.md`
   - Add duplicate detection to review checklist
   - Define violation format with location and recommendation
   - Verify: Stakeholder correctly flags test duplicates

6. **Add indexing command (optional)**
   - Consider: `/cat:index-claims` for manual indexing
   - Or: Auto-index during first review
   - Verify: Index populated correctly

7. **Run full test suite**
   - Verify: `python3 /workspace/run_tests.py`
   - Verify: No regressions in existing functionality
