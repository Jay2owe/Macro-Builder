# Stage 05 — stale-comment cleanup

## Goal

Remove or rewrite three javadoc blocks that point at "future stage" work which has either already shipped or been replaced by this plan's stages. The goal is that a reader looking at the file gets an accurate picture of current behaviour, not the historical state when the file was written.

## Why

Stale "future stage" comments mislead readers about what's done, what's pending, and where to look for follow-up. The original test-counts plan's house rules require plain-language UI text; the same plain-language rule applies to source comments.

## Files and edits

### 5a. `src/main/java/macro/builder/ui/sandbox/variation/TilePanel.java:32-33`

Current text (paraphrased): "Stage 06 keeps the tile read-only — per-tile action buttons (Promote, Save preset, X) land in stage 07 and must live in the caption strip above the canvas..."

Stage 07 of `create-macro-variations` shipped. Promote, Save, and X buttons are present and wired (see `TilePanel.java:267-282`).

**Edit**: rewrite the paragraph to describe the contract that's still load-bearing — i.e., that action buttons must live in the caption strip above the canvas, not floating over it, because `ImageCanvas` extends AWT `Canvas` and Swing's lightweight clipping doesn't apply. Drop the "Stage 06"/"stage 07" framing. One short paragraph.

### 5b. `src/main/java/macro/builder/ui/sandbox/variation/CaptionBaker.java:16-18`

Current text: "Callers MUST clone the variant's ImagePlus before baking — see stage 06's known risks: the uncaptioned VariantResult.output must remain available for downstream consumers (Promote, montage export, future analysis)."

Promote and montage export shipped. "Future analysis" is aspirational and not tied to any planned stage.

**Edit**: replace "see stage 06's known risks" with the actual constraint — "the uncaptioned `VariantResult.output` is reused by Promote and montage export, so it must not be mutated in place." Drop "future analysis." One short paragraph.

### 5c. `src/main/java/macro/builder/image/DiffuseObjectFilter.java:22`

Current text: "Native DAG execution (without `WindowManager`) arrives in stage 03; until then this compound handler keeps batch runs safe by routing through a known macro path. Caller MUST hold `WindowManagerLock.LOCK`."

After stage 03 of this plan lands, native DAG execution is the path. The comment is wrong.

**Edit (conditional on 03)**: rewrite the javadoc to describe the native algorithm: two Gaussian blurs at σ_small/σ_big, slice-wise subtract via `Blitter.SUBTRACT`, then 3D median. Drop the `WindowManagerLock` requirement. If stage 03 has not landed yet, leave this edit out — describing native execution that doesn't exist is worse than the current stale comment.

### 5d. `src/main/java/macro/builder/ui/sandbox/variation/ProgressDialog.java:18-20`

Current text: "Closing the window from the title bar is disabled because the SwingWorker backing the run cannot be cleanly cancelled at this stage — stage 07 / 08 may add a cancel button."

After stage 02 of this plan lands, cancellation works and the title-bar close is wired to it (or removed entirely).

**Edit (conditional on 02)**: rewrite to describe the actual cancel path. If stage 02 has not landed, leave the comment alone — it accurately describes current behaviour.

### 5e. `src/main/java/macro/builder/ui/ThresholdShootoutDialog.java:177`

Current text: `// TODO: Replay from .testcounts.json in a later stage.`

After stage 01 lands, replay exists.

**Edit (conditional on 01)**: remove the TODO entirely. Do not replace it with anything; the surrounding code is self-explanatory.

## Approach

1. Sequence after the stages that supersede each comment. Comments 5a and 5b can land at any time; 5c depends on 03, 5d on 02, 5e on 01.
2. Each edit is a one-line or one-paragraph rewrite. No behavioural change.
3. Commit as a single "stage 05: refresh stale future-stage comments" commit per dependency batch. Multiple commits are fine if the stages land at different times.

## Tests

No new tests. `.\mvnw.cmd test "-Denforcer.skip=true"` must still pass — verifies no accidental code change snuck in alongside a comment edit.

## Exit gate

- All five comment sites describe current code or carry a forward-looking note that points at a real, named planned stage (not a vague "future").
- `.\mvnw.cmd test "-Denforcer.skip=true"` is green.
- Manual spot-check: open each of the five files in an editor, confirm the comment reads cleanly without prior-context knowledge.

## Risks and mitigations

- **Editing a stale comment can drift into editing nearby code.** Diff-review each commit to confirm only comments changed.
- **A comment that documents an invariant might lose the invariant if reworded carelessly.** For 5a specifically, the load-bearing claim ("buttons must live above the canvas, not over it, because `ImageCanvas` is heavyweight AWT") must survive the edit. Read each comment to identify the invariant before rewriting.
