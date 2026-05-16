# Apply chosen variant to macro, JSON sidecar, methods paragraph

## Why this stage exists

Once the user picks a winning variant, the decision today lives only in their head. They have to switch to the macro editor and type the threshold line by hand. Six months later, they cannot remember which trial produced the choice. This stage closes the loop: one button writes the choice into the loaded macro and saves a companion file recording exactly what happened, plus a copy-paste methods paragraph for the inevitable revision.

## Prerequisites

- `05_ground-truth-scoring`, `06_quality-score-columns`, `07_fragility-bar`, `08_method-agreement` complete (the sidecar should capture all those scores when they exist).
- Depends on stage 01 for `Macro_Builder.getPluginVersion()` and the plugin version manifest entry.

## Read first

- `src/main/java/macro/builder/image/dag/IjmToDagLoader.java`
- `src/main/java/macro/builder/image/dag/DagToIjmEmitter.java`
- `src/main/java/macro/builder/ui/sandbox/DagUndoHistory.java` (new in current branch)
- `src/main/java/macro/builder/Macro_Builder.java:85-113` for session fields (`lastMacro`, `lastDag`, `macroArea`)
- `src/main/java/macro/builder/Macro_Builder.java:472-509` for current macro updates from the visual builder and recorder
- `src/main/java/macro/builder/Macro_Builder.java:588-596` for handing the loaded macro to Test Counts
- `src/main/java/macro/builder/Macro_Builder.java:1090-1104` for persisting macro/DAG state

## Scope

- Add an "Apply to macro" button next to "Open mask preview". Enabled when exactly one row is selected and the row is successful.
- For DAG-backed macros, first add an explicit threshold representation because the current DAG has no `THRESHOLD` node type. Either add a NEW global-threshold op to `FilterMacroParser.OpType` and teach `DagToIjmEmitter`/`FilterExecutor`, or fall back to applying the threshold to the IJM text and clearing `lastDag`.
- `DagUndoHistory` exists but is package-private in `macro.builder.ui.sandbox` and its API is `record(DagIR)`, not `push()`. Widen it deliberately before reusing it outside the sandbox.
- For recorded `.ijm` macros, append the appropriate line to the bottom of the script:
  - Auto methods: `setAutoThreshold("Otsu dark");` (or whatever method, preserving dark/bright background).
  - Fixed thresholds: `setThreshold(<lower>, <upper>);`.
- Write a sidecar `.testcounts.json` next to whatever the user is exporting (CSV file, or alongside the macro). Schema version `1`. Fields:
  - `schemaVersion`, `pluginVersion`, `fijiVersion`, `timestamp`,
  - `imageSource` (file path + SHA-256, or `"in-memory:<title>"`),
  - `macroSource` (short SHA-256 of the `.ijm` text),
  - `settings` (all of `ShootoutSettings` serialised),
  - `results` (one entry per variant with every score that exists: count, mean size, coverage, separation, distinctness, fragility, agreement, precision, recall, F1),
  - `chosenVariant` (the row the user accepted, if any),
  - `groundTruth` (path + SHA-256 if used).
- Add a "Copy methods paragraph" button. Generates a paragraph from the sidecar data and copies it to the system clipboard. Example output:

  > "Images were thresholded with the Triangle method using Macro-Builder 1.4.2 (Fiji 2.16.0). For n = 12 validation images, the macro-output range was 38–52. Counts were validated against 47 manually-annotated objects (precision 0.93, recall 0.89, F1 0.91). Threshold sensitivity: count changed by 4% under a ±10% threshold wiggle."

- Sidecar auto-writes on Export CSV and on Apply to macro (whichever happens first); subsequent actions update it in place.

## Out of scope

- Loading a sidecar to reproduce a run (worth doing later — leave a `Replay from .testcounts.json` TODO comment in the dialog).
- Per-animal aggregation in the methods paragraph (the sidecar carries enough data; aggregation can be a downstream script for now).

## Files touched

| path | action | reason |
|---|---|---|
| `src/main/java/macro/builder/analysis/TestCountsManifest.java` | NEW | Immutable record of a run; `toJson()` writer using hand-rolled JSON (matches existing codebase pattern). |
| `src/main/java/macro/builder/analysis/MethodsParagraphWriter.java` | NEW | Pure: `TestCountsManifest -> String`. |
| `src/main/java/macro/builder/macro/MacroApplier.java` | NEW | Strategy: `applyToDag(...)` and `applyToIjm(...)`. |
| `src/main/java/macro/builder/ui/ThresholdShootoutDialog.java` | MODIFY | Buttons, sidecar wiring on Export and Apply. |
| `src/main/java/macro/builder/Macro_Builder.java` | MODIFY | Add NEW private `SessionDialog.applyMacroEdit(...)` and pass a callback into `ThresholdShootoutDialog`; no `Macro_Builder.applyMacroEdit(...)` exists today. |
| `src/main/java/macro/builder/analysis/ShootoutResult.java` | MODIFY | Add NEW `Source` enum field (`AUTO`, `FIXED`, `GRID`) so macro application does not parse `variant` text. |
| `src/main/java/macro/builder/image/FilterMacroParser.java` | MODIFY | Only if preserving DAG-backed editing: add a threshold op type; no `THRESHOLD` op exists today. |
| `src/main/java/macro/builder/ui/sandbox/DagUndoHistory.java` | MODIFY | Only if reused outside sandbox: widen visibility and use the existing `record(DagIR)` method. |
| `src/test/java/macro/builder/analysis/TestCountsManifestTest.java` | NEW | Round-trip: write JSON, parse with a minimal reader, assert fields preserved. |
| `src/test/java/macro/builder/analysis/MethodsParagraphWriterTest.java` | NEW | Asserts paragraph mentions every score that exists in the manifest; omits scores that are NaN. |
| `src/test/java/macro/builder/macro/MacroApplierTest.java` | NEW | DAG: a synthetic DAG with no threshold node gets one inserted at the right slot. IJM: bare macro grows a `setAutoThreshold` line. |

## Implementation sketch

JSON writer (terse, no library):

```java
StringBuilder sb = new StringBuilder();
sb.append("{");
sb.append("\"schemaVersion\":1,");
sb.append("\"pluginVersion\":").append(quote(pluginVersion)).append(",");
// ...
sb.append("}");
```

Macro applier (IJM path):

```java
String line;
if (variant.thresholdValue != null && variant.source == ShootoutResult.Source.FIXED) {
    line = "setThreshold(" + format(variant.thresholdValue) + ", " + format(rangeMax) + ");";
} else {
    String bg = settings.darkBackground ? " dark" : "";
    line = "setAutoThreshold(\"" + variant.variant + bg + "\");";
}
return originalIjm + (originalIjm.endsWith("\n") ? "" : "\n") + line + "\n";
```

DAG path: there is no `THRESHOLD` node today. If this stage adds one, locate an existing threshold node and mutate it; otherwise append a new threshold node after the last filter node. Record the previous DAG with `DagUndoHistory.record(updatedDag)` if the undo history is made visible outside `macro.builder.ui.sandbox`.
<!-- audit:agent1 corrected Macro_Builder line ranges, applyMacroEdit absence, DagUndoHistory API/visibility, missing THRESHOLD op, and missing variant.isFixed/methodName helpers -->

Threading model:

- Button enablement, confirmation dialogs, macro-area updates, clipboard writes, and visible status messages run on the EDT.
- Macro text/DAG transformation should be pure and fast; if a DAG conversion becomes slow, run it in a `SwingWorker` and apply the final edit on the EDT through `SessionDialog.applyMacroEdit(...)`.
- Sidecar JSON construction runs off the EDT when it needs image hashing or filesystem metadata. Only the final status update returns to the EDT.
- Sidecar-write failure must not break the dialog or prevent Apply to macro; show the error and leave the selected result usable.

## Exit gate

1. All existing and new tests pass with `.\mvnw.cmd test "-Denforcer.skip=true"`.
2. `MacroApplierTest` exercises the new macro-application path for auto, fixed, and grid variants; generated numeric thresholds use `Locale.ROOT` and no trailing zeros.
3. `TestCountsManifestTest` exercises sidecar JSON writing with quotes, backslashes, and newline characters in recorded `.ijm` source and asserts the JSON parses back to the original text.
4. `MethodsParagraphWriterTest` exercises paragraph generation with all scores present and with ground truth, fragility, and agreement absent.
5. Apply to macro on an Auto variant updates the loaded macro and the new line is visible in the main Macro Builder window within one click.
6. Apply to macro on a fixed-grid variant inserts `setThreshold(<value>, <max>);` with `12.5`, not `12,5`, on a comma-decimal machine.
7. A `.testcounts.json` appears next to the exported CSV with all populated fields; absent fields such as no ground truth are omitted, not written as `null`.
8. The methods paragraph generated with no ground truth, fragility, or agreement contains method, range, and plugin version, and contains none of `NaN`, `null`, doubled spaces, or dangling punctuation before the final period.

## Known risks

- DAG insertion point is genuinely ambiguous when the macro has no threshold step. Mitigation: default to `after the last filter node` but show that choice in the confirmation dialog so the user can cancel.
- The Fiji version is read from `IJ.getFullVersion()`, which may fail in unit tests or ImageJ2 hybrid classloaders. Mitigation: catch missing/linkage failures and record `"headless"` or `"unknown"` without blocking sidecar creation.
- Hand-rolled JSON must escape quotes, backslashes, newlines, and other control characters in macro source. Mitigation: use a single `escape(String)` helper for every string field and test `\"`, `\\`, `\n`, `\r`, and `\t` round-trips in `TestCountsManifestTest`.
- Macro language quoting can break `setAutoThreshold(...)` if a user-recorded method/source string contains a quote, backslash, or newline. Mitigation: emit macro string literals through a dedicated IJM string-escape helper and test the same special characters in `MacroApplierTest`.
- Locale-dependent formatting can write comma decimals into `setThreshold(...)`, JSON, or methods text. Mitigation: use `Locale.ROOT` for every numeric string that is parsed later or written to CSV/JSON/macro text.
- Hashing large image files on the EDT can freeze the dialog. Mitigation: hash files asynchronously off the EDT, stream in bounded chunks, and show sidecar status separately from Apply-to-macro success.
- Sidecar writes can fail due to permissions or cloud-sync locks. Mitigation: catch `IOException`, show the target path and error, and keep the selected row usable.
