<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Personality Questionnaire Template

Reusable questionnaire content for deriving behavior settings from situational questions.

## Questions and Answer Mappings

### Question 1: Trust

**Header:** "How do you lead?"

**Question:**
```
It's Wednesday evening and you're on vacation. A junior developer messages you:
"I'm close to finishing the new feature — what should I do when I have it?"
You tell them:
```

**Options:**
- "Push it when you're ready" → TRUST=high
- "Send me a quick summary to review before pushing anything out" → TRUST=medium
- "Sit tight until Monday — we'll go through everything together before it ships" → TRUST=low

### Question 2: Caution

**Header:** "Friday deploy"

**Question:**
```
It's 4:55pm on a Friday and production is down. You've found the fix. Before you push and head out,
you run:
```

**Options:**
- "Nothing — you live dangerously" → CAUTION=low
- "The tests for what you changed — close enough" → CAUTION=medium
- "The full test suite — the pub can wait" → CAUTION=high

### Question 3: Curiosity

**Header:** "The old module"

**Question:**
```
You're handed a bug report in a module nobody has touched in two years. Do you:
```

**Options:**
- "Fix the line, close the ticket, move on" → CURIOSITY=low
- "Poke around enough to understand what you're changing" → CURIOSITY=medium
- "Read the whole thing — you don't touch code you don't understand" → CURIOSITY=high

### Question 4: Perfection

**Header:** "Someone else's mess"

**Question:**
```
While fixing a bug you stumble across an obvious hack someone left in the code. Do you:
```

**Options:**
- "Leave it — it's a problem for another day" → PERFECTION=low
- "Clean it up if it'll take less than ten minutes" → PERFECTION=medium
- "Fix it — you're not leaving that in the codebase" → PERFECTION=high

### Question 5: Verbosity

**Header:** "The code review"

**Question:**
```
You're reviewing a PR with a tricky bug. You'd prefer CAT to:
```

**Options:**
- "Give you the short answer" → VERBOSITY=low
- "Walk you through the reasoning" → VERBOSITY=medium
- "Explain everything, including what it ruled out" → VERBOSITY=high

## Explanation Text Lookup

After collecting all 5 answers, select explanation text from the following table:

**trust:**
- `low` → "You prefer to stay closely involved and review each step"
- `medium` → "You like to review before things ship"
- `high` → "You trust your partner to handle it"

**caution:**
- `low` → "You move fast and trust your instincts"
- `medium` → "You validate the changes you made"
- `high` → "You don't push without full validation"

**curiosity:**
- `low` → "You fix what's asked and move on"
- `medium` → "You understand what you're changing"
- `high` → "You don't touch code you don't understand"

**perfection:**
- `low` → "You keep focused and defer improvements"
- `medium` → "You take quick wins when they're easy"
- `high` → "You can't leave known issues alone"

**verbosity:**
- `low` → "You prefer short answers"
- `medium` → "You like to understand the reasoning"
- `high` → "You want the full picture"

## Result Display Format

After collecting all 5 answers, display results as plain text (not as an AskUserQuestion):

```
Your working style:

  trust: {TRUST}         {trust_explanation}
  caution: {CAUTION}     {caution_explanation}
  curiosity: {CURIOSITY} {curiosity_explanation}
  perfection: {PERFECTION} {perfection_explanation}
  verbosity: {VERBOSITY} {verbosity_explanation}

You can update any of these later with /cat:config.
```
