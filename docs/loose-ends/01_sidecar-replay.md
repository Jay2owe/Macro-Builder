# Stage 01 — sidecar replay

## Goal

Add a `Load sidecar...` action to `ThresholdShootoutDialog` that reads a previously written `.testcounts.json`, validates its schema version, and re-hydrates the dialog state so the recorded run is reproduced: same counting mode, same threshold mode + value, same auto-method list, same fixed thresholds, same size bounds, same ground-truth reference path, same recommended row.

## Why

The sidecar is the reproducibility artefact the test-counts programme promised (`docs/test-counts-improvements_COMPLETED/09_macro-roundtrip-and-sidecar_COMPLETED.md`). Write is done; read is not. Without read, a reviewer who receives the CSV + JSON pair cannot replay it without re-typing every setting. `ThresholdShootoutDialog.java:177` marks this with `// TODO: Replay from .testcounts.json in a later stage.` next to the `groundTruthFile` field.

## Files touched

- `src/main/java/macro/builder/ui/ThresholdShootoutDialog.java` — add Load action, plumb the manifest into existing `applyShootoutSettings`/`setActiveSettings` paths.
- `src/main/java/macro/builder/analysis/TestCountsManifest.java` — add a `read(File)` (or `parse(String)`) static returning `TestCountsManifest`, paired with the existing builder. Use `JsonReader`/`JsonParser` if present, else a small hand-rolled parser (no new dependency).
- `src/test/java/macro/builder/analysis/TestCountsManifestTest.java` — new or extended; round-trip a built manifest through write → read → assert equal field-by-field.
- `src/test/java/macro/builder/ui/ThresholdShootoutDialogTest.java` (if extant) or a new dialog-state test using a synthetic manifest fixture.

## Approach

1. Extend `TestCountsManifest` with a `read(File)` static. Required validation:
   - Refuse if `schemaVersion > SCHEMA_VERSION` with the message `"Sidecar schema version N is newer than this plugin's version M. Update the plugin and try again."`
   - Accept lower versions as long as the required fields are present; missing optional fields stay null/empty.
   - JSON malformed → `IOException` with file path in the message.
2. Add a `Load sidecar...` button alongside the existing "Apply to macro" surface. Plain-language label; no jargon.
3. On click: open a `JFileChooser` filtered to `*.testcounts.json` and the user's current macro folder. Default to the directory of the last-loaded image.
4. On selection: call `TestCountsManifest.read(...)` off the EDT (use the existing `SwingWorker` pattern that other loads in this dialog already use), then on `done()` apply settings via the existing `applyShootoutSettings(ShootoutSettings)` path and select the row matching `chosenVariant.variant` if present.
5. If `imageSource` does not match the currently loaded image, surface a yellow status banner: `"Sidecar was recorded against <imageSource.title>; current image is <current.title>. Settings applied; results need a re-run."` — do not auto-run.
6. If `groundTruth` is set, attempt to re-resolve the file path. If missing, status banner: `"Ground-truth file not found at <path>. Load it manually before scoring."`

## Tests

- `TestCountsManifestTest#readRejectsNewerSchema()` — schemaVersion=2 file → exception with version in message.
- `TestCountsManifestTest#readRoundTrip()` — build a manifest with every field populated, write to temp file, read, assert all fields equal.
- `TestCountsManifestTest#readMissingOptionalFields()` — file with only required fields parses; optional fields are null/empty.
- `TestCountsManifestTest#readMalformedJsonIncludesPath()` — bad JSON → `IOException`, message contains the file path.
- Dialog test (UI surface): given a synthetic `TestCountsManifest`, calling the dialog's load handler reaches `applyShootoutSettings` with matching field values. (Run with `assumeFalse(GraphicsEnvironment.isHeadless())` if construction needs a real Swing context.)

## Exit gate

- `.\mvnw.cmd test "-Denforcer.skip=true"` is green.
- Manual smoke check on the dev box:
  1. Run Test Counts on any image, apply, confirm `.testcounts.json` is written next to the CSV.
  2. Close the dialog, reopen on the same image, click `Load sidecar...`, pick the JSON, confirm the threshold mode, fixed list, size bounds, and the previously starred row all come back.
  3. Repeat with a different image loaded — confirm the yellow image-mismatch banner appears and no auto-run fires.
  4. Hand-edit the JSON to set `schemaVersion: 2` — confirm refuse message appears and dialog state is unchanged.
- Comment at `ThresholdShootoutDialog.java:177` is removed (it points to this stage's work).

## Risks and mitigations

- **Schema not versioned defensively today.** If stage 04 lands first and bumps the version, this stage's load path must already tolerate the previous version. Build the version check as `>` not `!=` so older sidecars keep loading.
- **`applyShootoutSettings` may not exist by that exact name.** Verify the current method that swaps settings in the dialog (likely tied to the "Apply" / settings-change flow). If absent, factor one out as a private helper — do not duplicate the settings-write logic.
- **`groundTruth` paths from another machine.** Absolute paths from a colleague's drive will not resolve. The fallback banner is the contract; do not silently substitute a relative path.
- **EDT discipline.** File read is small but `JFileChooser` and any parse-error dialog must run on the EDT; the `read(...)` call must not.
