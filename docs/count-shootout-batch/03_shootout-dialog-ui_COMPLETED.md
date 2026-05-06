# Test counts dialog UI

## Why this stage exists

The runner is only useful if users can configure it without writing code. This stage adds the `Test counts...` workflow so users can choose 2D vs 3D counting, compare auto methods, enter fixed numeric thresholds, preview masks, and export results.

## Prerequisites

- `01_count-model-native-counter.md` completed and renamed with `_COMPLETED`.
- `02_threshold-shootout-runner.md` completed and renamed with `_COMPLETED`.

## Read first

- `docs/count-shootout-batch/00_overview.md`
- `AGENTS.md`
- `src/main/java/macro/builder/Macro_Builder.java:87-150` for main dialog button layout.
- `src/main/java/macro/builder/Macro_Builder.java:443-470` for preview/save flow.
- `src/main/java/macro/builder/ui/PipelineDialog.java:82-95` for dialog construction.
- `src/main/java/macro/builder/ui/PipelineDialog.java:343-390` for choice/message/numeric controls.
- `src/main/java/macro/builder/ui/PipelineDialog.java:602-642` for footer buttons and transient status.

## Scope

- Add a `Test counts...` button to the main Macro Builder dialog.
- Add `ThresholdShootoutDialog`.
- Expose `Counting mode`: `2D particles` or `3D stack objects`.
- Expose `Threshold mode`: `Auto threshold shootout`, `Fixed numeric threshold`, or `Auto methods + fixed thresholds`.
- Add fixed threshold entry supporting one value or comma-separated values.
- Show macro output range before or after the first processing pass.
- Display a results table and allow CSV export.
- Allow users to open mask previews for selected result rows.

## Out of scope

- Batch folder processing; stage 04 owns that.
- Saved batch macro export; stage 05 owns that.
- Adding new image processing filters to the visual builder.

## Files touched

| path | action | reason |
|---|---|---|
| `src/main/java/macro/builder/Macro_Builder.java` | MODIFY | Add `Test counts...` button and launch the dialog with current image/macro. |
| `src/main/java/macro/builder/ui/ThresholdShootoutDialog.java` | NEW | User-facing count testing dialog. |
| `src/main/java/macro/builder/analysis/ShootoutSettings.java` | MODIFY | Add parsing helpers/defaults if needed by the UI. |

## Implementation sketch

Main dialog button placement should follow the existing left footer group:

```java
JButton testCounts = new JButton("Test counts...");
testCounts.addActionListener(e -> openCountTester());
left.add(testCounts);
```

Dialog settings:

```text
Counting mode: [2D particles | 3D stack objects]
Threshold mode: [Auto threshold shootout | Fixed numeric threshold | Auto methods + fixed thresholds]
Auto methods: Default,Otsu,Li,Triangle,Huang,Moments,Yen,MaxEntropy,IsoData,Minimum
Fixed thresholds: 2000, 5000
Size filter min/max: numeric fields
```

The fixed threshold field help text must say:

```text
Fixed thresholds use the macro output's native intensity values. A value of 2000 on a 16-bit image means intensity 2000, not a 0-255 value.
```

Results table columns:

```text
Variant | Count mode | Threshold value | Count | Mean size | Coverage % | Range | Status
```

## Exit gate

1. `.\mvnw.cmd test "-Denforcer.skip=true"` passes.
2. Manual: launching Macro Builder still shows existing build/record/preview/run/save controls plus `Test counts...`.
3. Manual: with no macro, `Test counts...` shows the same kind of clear message as preview/run.
4. Manual: fixed threshold entry `2000,5000` produces two fixed result rows.
5. Manual: source image remains unchanged after running the dialog.

## Known risks

- Swing table previews can leak images if masks are not closed. Track preview images like `macroPreview` and close them on dialog disposal.
- The output range is only known after the macro runs. If the dialog shows it before running, it must be labelled as source range, not macro output range.
