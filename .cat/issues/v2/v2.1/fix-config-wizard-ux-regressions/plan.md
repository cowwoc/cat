# Plan

## Goal

Correct cat:config UX and output-rendering regressions:

- Ask the UX stakeholder to evaluate restrained ANSI gradient usage like cat:help uses, enough to improve scanability
  without making the display noisy.
- Replace unlabeled personality summaries such as "currently: medium · high · high · high · medium" with summaries
  that identify which setting maps to each value.
- Ensure all personality questionnaire answer choices end with periods.
- Change the final questionnaire prompt from "You're reviewing a PR with a tricky bug. You'd prefer CAT to:" to
  "When explaining why a change was made, you'd prefer CAT to:".
- Investigate why the get-output CLI result was not rendered verbatim in the config session screenshot, including
  the visible misaligned/interleaved lines, and fix or document the rendering path so cat:config output is copied
  cleanly.

## Pre-conditions

(none)

## Post-conditions

- [ ] cat:config UX stakeholder review explicitly evaluates restrained ANSI gradient usage aligned with cat:help.
- [ ] Personality current-value summaries label each mapped setting and value instead of showing unlabeled value
  sequences.
- [ ] Personality questionnaire answer choices consistently end with periods.
- [ ] The final questionnaire prompt uses: "When explaining why a change was made, you'd prefer CAT to:".
- [ ] The get-output CLI rendering path is investigated and fixed or documented so cat:config output is rendered
  verbatim without misaligned or interleaved lines.
- [ ] Regression test added for the corrected config wizard copy and rendering behavior.
- [ ] E2E verification covers the config wizard flow shown in the screenshot.
- [ ] No new issues.
