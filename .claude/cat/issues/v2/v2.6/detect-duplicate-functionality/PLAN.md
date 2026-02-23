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

**Core principle:** Extract WHAT the function does, not HOW it works.
- "iterates through collection and sums elements" → BAD (implementation detail)
- "computes sum of numeric collection" → GOOD (semantic meaning)

Use LLM with temperature=0 for deterministic extraction.

**Claim categories (code-specific):**

| Category | Description | Example |
|----------|-------------|---------|
| `input` | What the function accepts | "accepts nullable string" |
| `output` | What the function returns | "returns boolean" |
| `behavior` | Core semantic action | "compares two values for equality" |
| `side_effect` | External state changes | "writes to file system" |
| `edge_case` | Special handling | "returns false for null input" |

**Claim types (adapted from compare-docs):**

| Type | Pattern | Example |
|------|---------|---------|
| `simple` | Single atomic statement | "returns string length" |
| `conjunction` | Multiple behaviors (AND) | "trims whitespace and converts to lowercase" |
| `conditional` | If-then behavior | "if input is null, returns empty string" |
| `negation` | What it does NOT do | "does_not throw exception on null" |

**Extraction schema:**

```json
{
  "method": "ProcessRunner.readAllLines",
  "claims": [
    {"category": "input", "type": "simple", "text": "accepts reader and output buffer"},
    {"category": "behavior", "type": "simple", "text": "reads all lines from stream"},
    {"category": "behavior", "type": "simple", "text": "appends content to buffer"},
    {"category": "behavior", "type": "simple", "text": "separates lines with newline"},
    {"category": "edge_case", "type": "conditional", "text": "if stream empty, buffer unchanged"}
  ],
  "summary": "reads all lines from reader into buffer with newline separators",
  "confidence": "high"
}
```

**Confidence levels:**
- `high`: Clear, unambiguous function behavior
- `medium`: Some inference required (e.g., implicit nullability)
- `low`: Complex logic with multiple code paths

**Extraction prompt (LLM):**

```
Extract semantic claims from this function. Focus on WHAT it does, not HOW.

Rules:
1. Use abstract terms: "collection" not "ArrayList", "reader" not "BufferedReader"
2. Normalize verbs to present tense: "reads", "returns", "computes"
3. One claim per distinct behavior
4. Include edge cases only if explicitly handled
5. Mark confidence based on code clarity

Categories: input, output, behavior, side_effect, edge_case
Types: simple, conjunction, conditional, negation

Function:
{code}
```

### Claim Normalization (Reused from compare-docs)

**Normalization pipeline:**
1. Lowercase all text
2. Tokenize on whitespace and punctuation
3. Apply tense normalization (verbs → present tense)
4. Apply synonym mapping
5. Apply negation standardization
6. Remove stopwords
7. Apply stemming
8. Generate bigrams for order sensitivity

```python
# Tense normalization (verbs → present tense base form)
TENSE_MAP = {
    "reads": "read", "reading": "read",
    "writes": "write", "writing": "write", "written": "write",
    "returns": "return", "returning": "return", "returned": "return",
    "throws": "throw", "throwing": "throw", "thrown": "throw",
    "computes": "compute", "computing": "compute", "computed": "compute",
    "creates": "create", "creating": "create", "created": "create",
    "converts": "convert", "converting": "convert", "converted": "convert",
    "validates": "validate", "validating": "validate", "validated": "validate",
    "checks": "check", "checking": "check", "checked": "check",
    "iterates": "iterate", "iterating": "iterate", "iterated": "iterate",
    "appends": "append", "appending": "append", "appended": "append",
}

# Expanded synonym mapping
SYNONYMS = {
    # Null-related
    "none": "null", "nil": "null", "nothing": "null", "undefined": "null",
    # Parameters
    "input": "parameter", "argument": "parameter", "arg": "parameter", "param": "parameter",
    # Collections
    "list": "collection", "array": "collection", "slice": "collection",
    "set": "collection", "sequence": "collection", "iterable": "collection",
    # Iteration
    "iterates": "iterate", "loops": "iterate", "traverses": "iterate", "walks": "iterate",
    # Adding
    "appends": "append", "adds": "append", "pushes": "append", "inserts": "append",
    # Comparison
    "equals": "equal", "matches": "equal", "same": "equal", "identical": "equal",
    # Boolean
    "true": "true", "yes": "true", "truthy": "true",
    "false": "false", "no": "false", "falsy": "false",
    # String operations
    "concatenates": "concat", "joins": "concat", "combines": "concat",
    "trims": "trim", "strips": "trim",
    # Errors
    "throws": "throw", "raises": "throw", "signals": "throw",
    "exception": "error", "fault": "error", "failure": "error",
}

# Negation standardization (multi-word → single token)
NEGATION_PATTERNS = [
    ("does not", "does_not"),
    ("do not", "does_not"),
    ("doesn't", "does_not"),
    ("don't", "does_not"),
    ("is not", "is_not"),
    ("isn't", "is_not"),
    ("cannot", "can_not"),
    ("can't", "can_not"),
    ("never", "does_not"),
    ("no ", "does_not "),  # "no exception" → "does_not exception"
]

STOPWORDS = {'a', 'an', 'the', 'is', 'are', 'was', 'were', 'be', 'been',
             'being', 'have', 'has', 'had', 'do', 'does', 'did', 'will',
             'would', 'could', 'should', 'may', 'might', 'must', 'shall',
             'to', 'of', 'in', 'for', 'on', 'with', 'at', 'by', 'from',
             'as', 'into', 'through', 'during', 'before', 'after', 'above',
             'below', 'between', 'under', 'again', 'further', 'then', 'once',
             'and', 'but', 'or', 'nor', 'so', 'yet', 'both', 'each', 'few',
             'more', 'most', 'other', 'some', 'such', 'only', 'own', 'same',
             'than', 'too', 'very', 'just', 'also', 'now', 'here', 'there',
             'when', 'where', 'why', 'how', 'all', 'any', 'both', 'each',
             'it', 'its', 'this', 'that', 'these', 'those', 'what', 'which',
             'who', 'whom', 'whose'}

def normalize_claim(claim: str) -> tuple[set[str], set[str]]:
    """Returns (tokens, bigrams) for order-sensitive comparison."""
    text = claim.lower()

    # Apply negation standardization first
    for pattern, replacement in NEGATION_PATTERNS:
        text = text.replace(pattern, replacement)

    tokens = tokenize(text)
    tokens = [TENSE_MAP.get(t, t) for t in tokens]  # Tense normalization
    tokens = [SYNONYMS.get(t, t) for t in tokens]   # Synonym mapping
    tokens = [t for t in tokens if t not in STOPWORDS]  # Remove stopwords
    tokens = [stem(t) for t in tokens]              # Stemming

    bigrams = {f"{tokens[i]}_{tokens[i+1]}" for i in range(len(tokens)-1)}
    return set(tokens), bigrams
```

### Claim Relationships (Future Enhancement)

Relationships between claims can improve matching precision by capturing semantic structure.
Not implemented in v1, but the extraction schema supports future extension.

| Relationship | Description | Example |
|--------------|-------------|---------|
| `precondition→behavior` | Input constraint enables behavior | "accepts non-null string" → "computes hash" |
| `condition→effect` | Conditional triggers outcome | "if input empty" → "returns zero" |
| `input→output` | Input type determines output | "accepts collection" → "returns count" |
| `behavior→side_effect` | Action causes external change | "writes data" → "modifies file" |

**Extraction format (optional, for future use):**

```json
{
  "relationships": [
    {"from": 0, "to": 1, "type": "precondition→behavior"},
    {"from": 2, "to": 3, "type": "condition→effect"}
  ]
}
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

### Index Storage (Git-Tracked SQLite)

**Why SQLite:**
- O(log n) indexed queries vs O(n) linear scan
- Only loads pages needed, not entire file
- Native FTS5 for text search
- Scales to 50K+ methods

**Why git-track the DB (not ignore):**
- Each commit has its matching index state
- Checkout old commit → get that commit's index
- No separate manifest needed
- file_hash column provides built-in staleness detection
- Merge conflicts: pick either side, staleness detection handles rest

**Storage layout:**

```
.claude/cat/
└── claim-index.db    ← SQLite database (git-TRACKED)
```

**SQLite schema:**

```sql
-- Metadata table
CREATE TABLE metadata (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
-- Store: version, extracted_with model name

-- Methods table (file_hash enables staleness detection)
CREATE TABLE methods (
    id INTEGER PRIMARY KEY,
    file_path TEXT NOT NULL,
    method_name TEXT NOT NULL,
    signature TEXT,
    language TEXT,
    claims_json TEXT NOT NULL,
    summary TEXT,
    file_hash TEXT NOT NULL  -- SHA256 of source file at indexing time
);
CREATE INDEX idx_file_path ON methods(file_path);

-- Token inverted index for fast candidate retrieval
CREATE TABLE tokens (
    token TEXT NOT NULL,
    method_id INTEGER NOT NULL,
    FOREIGN KEY (method_id) REFERENCES methods(id) ON DELETE CASCADE
);
CREATE INDEX idx_token ON tokens(token);

-- Bigram index for order-sensitive matching
CREATE TABLE bigrams (
    bigram TEXT NOT NULL,
    method_id INTEGER NOT NULL,
    FOREIGN KEY (method_id) REFERENCES methods(id) ON DELETE CASCADE
);
CREATE INDEX idx_bigram ON bigrams(bigram);

-- JDK/stdlib patterns (pre-populated)
CREATE TABLE jdk_patterns (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    tokens_json TEXT NOT NULL,
    recommendation TEXT NOT NULL
);
```

**Staleness detection (no manifest needed):**

The file_hash stored with each method entry detects when re-indexing is needed:

```python
def ensure_index_current(db: sqlite3.Connection):
    """Detect and re-index stale entries using file_hash comparison."""
    # Get all indexed files and their stored hashes
    indexed_files = db.execute("""
        SELECT DISTINCT file_path, file_hash FROM methods
    """).fetchall()

    stale_files = []
    deleted_files = []

    for file_path, stored_hash in indexed_files:
        if not os.path.exists(file_path):
            deleted_files.append(file_path)
        elif hash_file(file_path) != stored_hash:
            stale_files.append(file_path)

    # Remove deleted files from index
    for f in deleted_files:
        db.execute("DELETE FROM methods WHERE file_path = ?", (f,))

    # Re-index stale files (LLM calls for claim extraction)
    for f in stale_files:
        reindex_file(db, f)  # Extracts claims, normalizes, stores

    # Check for new files not yet indexed
    for file_path in find_source_files():
        exists = db.execute(
            "SELECT 1 FROM methods WHERE file_path = ? LIMIT 1", (file_path,)
        ).fetchone()
        if not exists:
            reindex_file(db, file_path)

    db.commit()
```

**Merge conflict resolution:**

```bash
# On merge conflict (SQLite is binary):
git checkout --ours .claude/cat/claim-index.db    # Pick one side
# OR
git checkout --theirs .claude/cat/claim-index.db

# Next query automatically detects stale entries via file_hash
# and re-indexes only what's needed
```

### Pre-Commit Hook (Index Validation)

**Problem:** If developer modifies code but forgets to re-index, they commit stale DB.

**Solution:** Delta validation - only check files changed by this commit (or merge).

**Key insight:** If previous commit's index was valid, we only need to validate the delta.
By induction, all commits remain valid.

**Validation requirements for each file in delta:**

| File State | In Index? | Result |
|------------|-----------|--------|
| Exists (new/modified) | Yes, hash matches | ✓ Valid |
| Exists (new/modified) | Yes, hash differs | ✗ Stale |
| Exists (new) | No | ✗ Missing |
| Deleted | No | ✓ Valid |
| Deleted | Yes | ✗ Phantom |

**Performance (delta validation):**

| Scenario | Files to Check | Time |
|----------|----------------|------|
| Normal commit (5 files) | 5 | ~20ms |
| Merge (50 files each side) | ~100 | ~50ms |
| Large merge (500 each side) | ~1000 | ~200ms |

```python
# plugin/hooks/bash_handlers/validate_claim_index.py
class ValidateClaimIndexHandler(PreToolUseHandler):
    """Block commits if claim index doesn't match delta."""

    def check(self, command: str, tool_input: dict) -> dict | None:
        if 'git commit' not in command:
            return None

        db_path = '.claude/cat/claim-index.db'
        if not os.path.exists(db_path):
            return None  # No index yet, skip validation

        # Determine which files to validate
        files_to_check = self._get_files_to_validate()
        if not files_to_check:
            return None

        # Validate delta
        valid, error_msg = self._validate_delta(db_path, files_to_check)
        if not valid:
            return {
                'decision': 'block',
                'message': f"""Claim index validation failed:

{error_msg}

Run `/cat:index-claims` to update the index before committing."""
            }

        return None

    def _get_files_to_validate(self) -> set[str]:
        """Get files that need validation based on commit type."""

        # Check if this is a merge (MERGE_HEAD exists)
        is_merge = subprocess.run(
            ['git', 'rev-parse', 'MERGE_HEAD'],
            capture_output=True
        ).returncode == 0

        if is_merge:
            # Merge: validate union of both sides' changes
            merge_base = subprocess.run(
                ['git', 'merge-base', 'HEAD', 'MERGE_HEAD'],
                capture_output=True, text=True
            ).stdout.strip()

            # Files changed on our side (HEAD)
            ours = subprocess.run(
                ['git', 'diff', '--name-only', merge_base, 'HEAD'],
                capture_output=True, text=True
            ).stdout.strip().split('\n')

            # Files changed on their side (MERGE_HEAD)
            theirs = subprocess.run(
                ['git', 'diff', '--name-only', merge_base, 'MERGE_HEAD'],
                capture_output=True, text=True
            ).stdout.strip().split('\n')

            return {f for f in ours + theirs if f and is_source_file(f)}
        else:
            # Normal commit: validate staged files only
            result = subprocess.run(
                ['git', 'diff', '--cached', '--name-only'],
                capture_output=True, text=True
            )
            return {f for f in result.stdout.strip().split('\n')
                    if f and is_source_file(f)}

    def _validate_delta(self, db_path: str, files: set[str]) -> tuple[bool, str]:
        """Validate specific files against index."""
        db = sqlite3.connect(db_path)

        # Get stored hashes for files we're checking
        placeholders = ','.join('?' * len(files))
        results = db.execute(f"""
            SELECT file_path, file_hash FROM methods
            WHERE file_path IN ({placeholders})
        """, tuple(files)).fetchall()
        stored = {r[0]: r[1] for r in results}
        db.close()

        errors = []

        for file_path in files:
            file_exists = os.path.exists(file_path)
            in_index = file_path in stored

            if file_exists and not in_index:
                # NEW FILE: Added but not indexed
                errors.append(f"New file not in index: {file_path}")

            elif file_exists and in_index:
                # EXISTING FILE: Check hash matches
                if hash_file(file_path) != stored[file_path]:
                    errors.append(f"Stale hash: {file_path}")

            elif not file_exists and in_index:
                # DELETED FILE: Should be removed from index
                errors.append(f"Deleted file still in index: {file_path}")

            # not file_exists and not in_index: OK (deleted and not indexed)

        if errors:
            return False, "\n".join(errors[:10]) + \
                   (f"\n... and {len(errors) - 10} more" if len(errors) > 10 else "")
        return True, ""


def is_source_file(path: str) -> bool:
    """Check if file is a source file that should be indexed."""
    extensions = {'.java', '.py', '.ts', '.js', '.go', '.rs', '.kt', '.scala'}
    return any(path.endswith(ext) for ext in extensions)
```

**Why delta validation is sufficient:**
- Base case: Initial index is empty or fully validated
- Inductive step: If commit N is valid, validating N+1's delta ensures N+1 is valid
- Merge case: Union of both sides' changes covers all possible inconsistencies
- Performance: O(changed files) instead of O(all files)

**Workflow:**
```
Developer modifies code
    ↓
Attempts git commit
    ↓
Pre-commit hook checks staged files vs index
    ↓
├── Index current → Commit proceeds
└── Index stale → Commit blocked with message
        ↓
    Developer runs /cat:index-claims
        ↓
    Re-attempts commit → Success
```

### Fast Lookup Algorithm

```python
def find_duplicates(db: sqlite3.Connection, new_claims: list, threshold: float = 0.9):
    """Find potential duplicates using indexed lookup. No LLM calls."""
    # Normalize new method's claims
    new_tokens, new_bigrams = set(), set()
    for c in new_claims:
        t, b = normalize_claim(c['text'])
        new_tokens.update(t)
        new_bigrams.update(b)

    candidates = []

    # Stage 1: Fast candidate retrieval via token index
    # Find methods sharing at least one token (indexed lookup)
    placeholders = ','.join('?' * len(new_tokens))
    candidate_ids = db.execute(f"""
        SELECT method_id, COUNT(*) as shared_count
        FROM tokens
        WHERE token IN ({placeholders})
        GROUP BY method_id
        HAVING shared_count >= ?
    """, (*new_tokens, len(new_tokens) * 0.3)).fetchall()  # 30% overlap minimum

    # Stage 2: Full Jaccard comparison only for candidates
    for method_id, _ in candidate_ids:
        method_tokens = {r[0] for r in db.execute(
            "SELECT token FROM tokens WHERE method_id = ?", (method_id,)
        )}
        method_bigrams = {r[0] for r in db.execute(
            "SELECT bigram FROM bigrams WHERE method_id = ?", (method_id,)
        )}

        score = 0.7 * jaccard(new_tokens, method_tokens) + \
                0.3 * jaccard(new_bigrams, method_bigrams)

        if score >= threshold:
            method_info = db.execute(
                "SELECT file_path, method_name FROM methods WHERE id = ?",
                (method_id,)
            ).fetchone()
            candidates.append({
                'type': 'codebase',
                'file': method_info[0],
                'method': method_info[1],
                'score': score
            })

    # Also check JDK patterns (small table, full scan is fine)
    for row in db.execute("SELECT name, tokens_json, recommendation FROM jdk_patterns"):
        pattern_tokens = set(json.loads(row[1]))
        score = jaccard(new_tokens, pattern_tokens)
        if score >= threshold:
            candidates.append({
                'type': 'jdk',
                'name': row[0],
                'score': score,
                'recommendation': row[2]
            })

    return sorted(candidates, key=lambda x: -x['score'])
```

**Performance characteristics:**

| Operation | Complexity | 10K methods |
|-----------|------------|-------------|
| Token lookup | O(log n) | <1ms |
| Candidate filtering | O(k) where k = candidates | ~5ms |
| Full comparison | O(k) | ~10ms |
| **Total query time** | | **<20ms** |

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
| `plugin/lib/claim_index.py` | Create | SQLite index management, staleness detection |
| `plugin/lib/claim_extraction.py` | Create | LLM-based claim extraction (temp=0) |
| `plugin/lib/claim_similarity.py` | Create | Normalization, Jaccard + bigrams |
| `plugin/data/jdk_patterns.sql` | Create | Pre-built JDK/stdlib patterns (SQL inserts) |
| `plugin/hooks/bash_handlers/validate_claim_index.py` | Create | Pre-commit hook to block stale index |
| `plugin/skills/index-claims/SKILL.md` | Create | Manual/incremental indexing command |
| `.claude/cat/claim-index.db` | Create | SQLite database (git-tracked) |

## Acceptance Criteria

- [ ] Claim extraction extracts WHAT (semantic meaning), not HOW (implementation)
- [ ] Claim extraction uses categories: input, output, behavior, side_effect, edge_case
- [ ] Claim extraction uses types: simple, conjunction, conditional, negation
- [ ] Claim extraction works with temperature=0 for determinism
- [ ] Normalization includes tense normalization, synonym mapping, negation standardization
- [ ] Jaccard + bigram similarity correctly identifies duplicates (≥0.9) and non-duplicates (<0.9)
- [ ] SQLite index with token/bigram tables for O(log n) lookups
- [ ] SQLite DB is git-tracked (each commit has matching index state)
- [ ] file_hash column detects stale entries, triggers incremental re-indexing
- [ ] Pre-commit hook blocks commits when staged files have stale index entries
- [ ] Merge conflicts resolve via pick-one + automatic staleness detection
- [ ] JDK patterns (Objects.equals, etc.) are pre-built and checked first
- [ ] Design stakeholder integrates duplicate check in review workflow
- [ ] LLM verification only triggered for scores ≥0.9 (rare)
- [ ] Cross-language duplicates detected (Java/Python/TypeScript/Go)
- [ ] Query time <20ms for 10K method codebase
- [ ] Tests written and passing
- [ ] No regressions

## Execution Steps

1. **Create claim extraction module**
   - Files: `plugin/lib/claim_extraction.py`
   - Implement LLM-based extraction with temp=0
   - Extract WHAT (semantic meaning), not HOW (implementation)
   - Claim categories: input, output, behavior, side_effect, edge_case
   - Claim types: simple, conjunction, conditional, negation
   - Include confidence scoring (high/medium/low)
   - Verify: Unit tests for extraction

2. **Create similarity module**
   - Files: `plugin/lib/claim_similarity.py`
   - Implement normalization pipeline (from compare-docs):
     - Tense normalization (verbs → present tense)
     - Expanded synonym mapping
     - Negation standardization ("does not" → "does_not")
     - Stopword removal, stemming
   - Implement Jaccard with bigrams for order sensitivity
   - Verify: Test with 9 validated pairs

3. **Create SQLite index management**
   - Files: `plugin/lib/claim_index.py`
   - Implement schema creation (methods, tokens, bigrams, jdk_patterns, metadata)
   - Implement file_hash-based stale detection (no manifest needed)
   - Implement incremental re-indexing for changed files only
   - Implement fast candidate lookup via token index
   - Verify: Query time <20ms on 1K method test set

4. **Create JDK patterns database**
   - Files: `plugin/data/jdk_patterns.sql`
   - Add common patterns: Objects.equals, Objects.requireNonNull, Optional methods
   - Add Collections utilities, String utilities
   - Verify: Patterns inserted on DB initialization

5. **Integrate into design stakeholder**
   - Files: `plugin/stakeholders/design.md`
   - Add duplicate detection to review checklist
   - Define violation format with location and recommendation
   - Verify: Stakeholder correctly flags test duplicates

6. **Add pre-commit hook for index validation**
   - Files: `plugin/hooks/bash_handlers/validate_claim_index.py`
   - Check that staged source files have matching file_hash in DB
   - Block commit if index is stale (prevents invalid DB in commits)
   - Auto-prompt: "Index out of date. Run /cat:index-claims to update."
   - Verify: Commit blocked when index stale, allowed when current

7. **Add indexing command**
   - Files: `plugin/skills/index-claims/SKILL.md`
   - Full re-index: scan all source files, extract claims, update DB
   - Incremental: only re-index files where hash differs
   - Verify: Index populated correctly, file_hash matches
   - Default: Auto-index on first review, incremental thereafter
   - Verify: Index populated correctly

8. **Run full test suite**
   - Verify: `python3 /workspace/run_tests.py`
   - Verify: No regressions in existing functionality
