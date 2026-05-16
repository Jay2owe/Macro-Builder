# Stage 04 — design validations

## Goal

Resolve the four "default to X, confirm on one real dataset" items the test-counts plan deferred in `docs/test-counts-improvements_COMPLETED/00_overview.md:82-87`. Each item gets a one-paragraph decision recorded back into that overview file (or into a short `DECISIONS.md` if the overview is treated as frozen-on-archive), plus any code change the decision implies.

The current defaults are shipping. This stage's success may be "keep all four defaults, here's the dataset evidence" — that is a complete outcome.

## Why

The four questions exist because the test-counts plan needed to ship without one-real-dataset confirmation. They are now real risks: if a default is wrong, every sidecar written so far records that wrong default, and changing it later means a schema bump or a compatibility shim.

## The four questions

### 4a. Ground-truth matching rule

> For ground-truth scoring (stage 05), default matching rule should be centroid-in-mask for point ROIs and IoU ≥ 0.5 for area ROIs. Confirm with one real dataset before locking in.

- Files: `src/main/java/macro/builder/analysis/GroundTruthScorer.java` (the matching logic), `src/test/java/macro/builder/analysis/GroundTruthScorerTest.java`.
- Validation: pick one real microscope image + RoiSet.zip from the lab dataset. Score with the current default and with the two plausible alternatives (centroid-only-for-all, IoU-only-for-all). Report precision/recall/F1 deltas. Pick whichever produces the most defensible match-by-eye.
- Action if default holds: record a one-line decision note in the overview ("Confirmed on dataset X; centroid-for-points + IoU≥0.5-for-areas matches manual eyeballing within 2% F1") and close the question.
- Action if default changes: change the constant or the dispatch in `GroundTruthScorer`, add a regression test pinned to the new behaviour, bump the sidecar's recorded `matchRule` value (which means coordinating with 4b).

### 4b. Sidecar schema versioning

> For the JSON sidecar (stage 09), settle on `.testcounts.json` extension and one schema version field. Bump the schema version on every additive change.

- Files: `src/main/java/macro/builder/analysis/TestCountsManifest.java:19` (`SCHEMA_VERSION`).
- Validation: confirm the policy is what the team wants by stating it explicitly: "Schema 1 is the shipped schema. Any additive field bumps to 2. Removed/renamed fields bump to 3+. Stage 01's `read(...)` accepts version ≤ current." This stage's deliverable is the written policy in `00_overview.md`; the code already declares the constant.
- Action: add a `## Sidecar schema policy` section to the overview spelling out the bump rules and the deprecation horizon. No code change unless stage 01 has already landed and `read(...)` doesn't match the policy.

### 4c. 3D live-slider slice policy

> For the live slider (stage 10), 3D stacks need a "which slice does the preview show" decision; default to the active slice and offer a small slice scrubber next to the threshold slider.

- Files: `src/main/java/macro/builder/ui/ScrubPane.java`, `src/main/java/macro/builder/analysis/LiveMaskBuilder.java`.
- Validation: open Test Counts → Live slider on a 3D stack. Confirm:
  1. Initial preview shows the active slice (verified by reading `imp.getSlice()`).
  2. A slice scrubber is present and movable next to the threshold slider.
  3. Pin captures the full-stack count, not the single-slice count.
- Action if all three hold: record "confirmed on 3D dataset Y" and close.
- Action if any fail: file the specific gap as a follow-up stage in this folder; do not lump it into this validation pass.

### 4d. Back-solver spread check

> For the back-solver (stage 12), the spread check (so the system can't over-fit to a handful of clicks) needs a concrete rule. Default proposal: variant must catch ≥ 90% of clicks and have a count within ±25% of the median variant count.

- Files: `src/main/java/macro/builder/analysis/BackSolver.java`.
- Validation: run the click-to-mark flow on one real microscope image with 5, 20, and 50 marked points. For each:
  1. Confirm the winning variant catches ≥ 90% of clicks (or, if not, that the dialog says so plainly rather than reporting a winner that misses many).
  2. Compare the winning variant's count to the median variant count. If outside ±25%, confirm the dialog flags it as a possible over-fit.
- Action if both rules hold: record the dataset evidence.
- Action if either is wrong: tune the constants in `BackSolver`, add a regression test pinned to the chosen values, document the new rule in the overview.

## Tests

The validation step is empirical (one real dataset per question). Code-side, each rule change that does land must add a regression test pinned to the new constant or the new dispatch, named e.g. `groundTruthScorerUsesIouForAreaRois()`, `backSolverFlagsOverfitWhenCountOutsidePlusMinus25Percent()`. Tests that already exist for the current defaults stay green if the defaults hold.

## Exit gate

- A `## Design decisions (post-MVP)` section appended to `docs/test-counts-improvements_COMPLETED/00_overview.md` (or a sibling `DECISIONS.md` in that folder) with four entries, one per question, each carrying:
  - The question (one line).
  - The dataset used (image path or description, anonymised if it leaves the lab).
  - The decision ("default holds" or "changed to X").
  - The commit or PR that captured any code change.
- All four questions removed from the overview's "Known open questions" list (or marked `RESOLVED:` inline).
- `.\mvnw.cmd test "-Denforcer.skip=true"` is green.

## Risks and mitigations

- **Single-dataset evidence is thin.** If the chosen dataset is unusually easy or hard, the decision may not generalise. Mitigate by picking a dataset that's representative of typical user input, and by writing the decision note with "confirmed on this dataset; revisit if user feedback contradicts" rather than as a permanent ruling.
- **Schema bump (4b) interacts with stage 01.** If 01 lands first with `>` version refusal, that's already correct. If 01 hasn't landed, this stage's policy decision constrains 01's `read(...)`.
- **Rule changes invalidate prior sidecars.** If 4a or 4d change a constant, sidecars written before the change still parse but their recorded rule is silently different from the new code's rule. Either:
  - Record the rule explicitly in the sidecar JSON (preferred — additive field, bumps schema to 2), or
  - Document in the overview that sidecars from before <commit hash> use the old rule and replays should be interpreted accordingly.
