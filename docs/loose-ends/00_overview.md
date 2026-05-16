# Loose ends

## End goal

Close the post-MVP gaps that surfaced after the test-counts and macro-variations programmes shipped: load back the JSON sidecar that Test Counts writes, give long variant runs a real cancel path, replace the macro-string fallback in Diffuse Object Filter, validate the four open defaults that the test-counts plan deferred to "confirm on one real dataset", and clean up the now-stale "future stage" comments that point at work already delivered.

Each stage stands alone. The plan is a pick list, not a sequence — stages can land in any order. The defaults the test-counts plan picked are already working, so the validation stage is the only one that may produce a "no change required" outcome.

## Why we're doing this

- The `.testcounts.json` sidecar is the reproducibility artefact for Test Counts. Today it can only be written, never re-loaded — so the reviewer-handoff loop the test-counts plan promised is one-way.
- `ProgressDialog` documents itself as "cancellation may arrive in stage 07/08" but stages 07/08 of `create-macro-variations` shipped without it. A long variant run on a big stack currently traps the user.
- `DiffuseObjectFilter.apply(...)` still runs the bundled DoG macro through `IJ.runMacro(...)` plus a `WindowManager` lock instead of executing the DAG directly. The compound handler was meant as a stopgap.
- The test-counts overview lists four "default to X, confirm on a real dataset before locking in" items (ground-truth matching rule, sidecar schema versioning, 3D slice policy, back-solver spread check). None have been revisited.
- Three javadoc blocks point at stages that have since shipped, which is misleading when reading the code.

## What it will feel like when done

- Test Counts has a `Load sidecar...` action that re-populates the dialog from a previously written `.testcounts.json` so the chosen variant, threshold, and settings come back as they were.
- The "Generating variants" dialog has a working Cancel button; clicking it stops the worker and disposes the dialog without partial state being committed.
- Diffuse Object Filter executes through the same DAG path every other filter uses, with no `WindowManager.getCurrentImage()` adoption and no `imp.show()` side-effect.
- The four deferred design questions have a recorded answer (either "default holds" with one-dataset evidence, or a tightened rule plus the diff that changed it).
- The three stale comments either describe current code or point at this plan.

## Stage map

| NN | name | one-line goal | rough size | depends on |
|---|---|---|---|---|
| 01 | sidecar-replay | `Load sidecar...` re-hydrates `ThresholdShootoutDialog` from a written `.testcounts.json`. | medium | none |
| 02 | variant-cancel-button | Working Cancel on `ProgressDialog`, wired to the `SwingWorker` running variants. | small-medium | none |
| 03 | diffuse-native-dag | Replace `IJ.runMacro` + `WindowManager` adoption with the same DAG path other filters use. | medium-large | none |
| 04 | design-validations | Resolve the four open questions from `test-counts-improvements_COMPLETED/00_overview.md:82-87`. | small (per question) | none |
| 05 | stale-comment-cleanup | Update `TilePanel`, `CaptionBaker`, `DiffuseObjectFilter` javadocs to describe current code. | tiny | 03 (touches `DiffuseObjectFilter`) |

Stage 03 supersedes the "Native DAG execution... arrives in stage 03" promise in `DiffuseObjectFilter.java:22`. Once 03 lands, the matching comment cleanup belongs in 05.

## House rules

Carried from `test-counts-improvements_COMPLETED/00_overview.md` because they apply unchanged:

- Bundled Fiji only. No new update-site dependencies.
- Source image is never mutated. Every variant runs on a duplicate.
- Plain-language UI labels everywhere.
- No analysis on the Swing thread. Use `SwingWorker` or `FilterExecutor.runThreadSafe`.
- Headless-safe paths do not create a `RoiManager` when `GraphicsEnvironment.isHeadless()` is true.
- Tests live under `src/test/java/macro/builder/...`. Each stage adds at least one unit test.
- `.\mvnw.cmd test "-Denforcer.skip=true"` must pass before each stage is marked complete.
- Deploy is local-jar only. Do not push to public main or update site unless asked.
- Planning docs (this folder) do not get committed without explicit consent. The user has consented to this folder being committed; future edits to it do not require re-consent.

## Known risks across stages

- **Sidecar replay (01)**: schema-version mismatch. The manifest declares `SCHEMA_VERSION = 1` (`TestCountsManifest.java:19`); a load path must refuse newer versions with a clear message and refuse-with-warning on older versions until 04 closes the schema-versioning question.
- **Variant cancel (02)**: `VariantExecutor.runAll` may not interrupt cleanly mid-variant. Cancellation must be checked between variants at minimum; mid-variant cancellation may need a thread-interrupt-aware path inside the executor.
- **Native DAG (03)**: the bundled DoG preset's macro text is the regression contract. Replacing it with a DAG path must produce pixel-identical (or byte-identical-after-cast) output on the bundled fixtures; otherwise the change is a behaviour break, not a refactor.
- **Design validations (04)**: any tightened rule must keep the existing default reachable through settings, or be gated behind a deliberate version bump on `SCHEMA_VERSION`.
- **Comment cleanup (05)**: cheap but easy to get wrong if 03's outcome reshapes the Diffuse path. Sequence 05 after 03 lands.

## How to run a stage

Pick the stage by number and execute it under the project's normal "/do-step" flow if used, or as a standalone branch. Each stage file has its own goal, file references, exit gate, and tests list.
