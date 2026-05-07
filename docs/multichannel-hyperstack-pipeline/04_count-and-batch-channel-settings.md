# Carry Channels Through Preview, Count, And Batch

## Why this stage exists

The visual builder can save multichannel DAGs after Stage 03, but the rest of Macro Builder still has workflow entry points that duplicate whole images or save count settings without channel context. This stage makes preview, run-on-duplicate, count shootout, saved macros, and exported batch wrappers respect the selected primary channel. That keeps visual DAG macros and ordinary imported or recorded macros predictable on hyperstacks.

## Prerequisites

- `01_dag-channel-metadata_COMPLETED.md`
- `02_channel-aware-execution_COMPLETED.md`
- `03_sandbox-channel-ui_COMPLETED.md`

## Read first

- `docs/multichannel-hyperstack-pipeline/00_overview.md`
- `AGENTS.md`
- `docs/DEVELOPER.md`
- `src/main/java/macro/builder/Macro_Builder.java:517-650`
- `src/main/java/macro/builder/Macro_Builder.java:673-735`
- `src/main/java/macro/builder/Macro_Builder.java:851-873`
- `src/main/java/macro/builder/Macro_Builder.java:1092-1128`
- `src/main/java/macro/builder/Macro_Builder.java:1231-1240`
- `src/main/java/macro/builder/analysis/ThresholdShootoutRunner.java:20-42`
- `src/main/java/macro/builder/analysis/ThresholdShootoutRunner.java:256-280`
- `src/main/java/macro/builder/analysis/BatchShootoutRunner.java:28-58`
- `src/main/java/macro/builder/analysis/BatchShootoutRunner.java:153-188`
- `src/main/java/macro/builder/analysis/BatchMacroExporter.java:25-96`
- `src/main/java/macro/builder/Macro_Builder_Batch_Count.java:20-45`
- `src/main/java/macro/builder/ui/ThresholdShootoutDialog.java:320-360`
- `src/main/java/macro/builder/ui/ThresholdShootoutDialog.java:533-585`
- `src/test/java/macro/builder/analysis/ThresholdShootoutRunnerTest.java:20-105`
- `src/test/java/macro/builder/analysis/BatchMacroExporterTest.java:23-60`

## Scope

- Add a reusable helper for duplicating a single source channel as a one-channel stack.
- For embedded DAG macros, keep passing the full source duplicate to `FilterExecutor` so branch source channels remain available.
- For non-DAG macros, run preview, run-on-duplicate, count shootout, and batch count on the selected primary channel only.
- Persist primary-channel state in Macro Builder local state.
- Include `primaryChannel` in exported batch `.settings.json`.
- Read old batch settings without `primaryChannel` as channel 1.
- Pass primary channel from `Macro_Builder_Batch_Count` into `BatchShootoutRunner`.
- Add tests for primary-channel-only count behavior and batch settings round trip.

## Out of scope

- Do not change visual builder controls. Stage 03 owns channel UI.
- Do not change DAG JSON channel metadata. Stage 01 owns DAG serialization.
- Do not change branch execution. Stage 02 owns DAG execution.
- Do not add Bio-Formats batch container support.
- Do not add project-specific channel naming setup.

## Files touched

| path | action | reason |
| --- | --- | --- |
| `src/main/java/macro/builder/Macro_Builder.java` | MODIFY | Store selected primary channel and use channel-aware duplicates in launcher actions. |
| `src/main/java/macro/builder/image/FilterExecutor.java` | MODIFY | Expose or reuse a safe channel-duplication helper if appropriate. |
| `src/main/java/macro/builder/analysis/ThresholdShootoutRunner.java` | MODIFY | Run non-DAG macros/counts on the selected primary channel. |
| `src/main/java/macro/builder/analysis/BatchShootoutRunner.java` | MODIFY | Pass primary channel through batch count runs. |
| `src/main/java/macro/builder/analysis/BatchMacroExporter.java` | MODIFY | Save and load primary channel in exported settings. |
| `src/main/java/macro/builder/Macro_Builder_Batch_Count.java` | MODIFY | Use exported primary channel during batch count execution. |
| `src/main/java/macro/builder/ui/ThresholdShootoutDialog.java` | MODIFY | Pass primary channel to single-image and batch shootout runners. |
| `src/test/java/macro/builder/analysis/ThresholdShootoutRunnerTest.java` | MODIFY | Cover primary-channel-only counting on multichannel input. |
| `src/test/java/macro/builder/analysis/BatchMacroExporterTest.java` | MODIFY | Cover settings JSON primary-channel round trip and old settings default. |

## Implementation sketch

First decide where the channel duplicate helper should live. Prefer a small shared utility if both `FilterExecutor` and analysis code need it. If keeping it inside `FilterExecutor`, expose a narrowly named public static method.

```java
public static ImagePlus duplicateChannel(ImagePlus source, int sourceChannel, String title) {
    // Same stack-index approach as Stage 02 cloneChannelStack.
    // Throw IllegalArgumentException for invalid source/channel inputs.
}
```

Detect embedded DAG macros before deciding whether to duplicate the whole source or one primary channel.

```java
private static boolean hasEmbeddedDag(String macroContent) {
    return IjmToDagLoader.loadEmbeddedDag(macroContent) != null;
}
```

In `Macro_Builder`, keep a primary channel field. Default to channel 1 and update it from the DAG returned by the visual builder.

```java
private int selectedPrimaryChannel = 1;

private int currentPrimaryChannel() {
    if (lastDag != null) return Math.max(1, lastDag.primaryChannel);
    return Math.max(1, selectedPrimaryChannel);
}
```

Preview/run duplicate behavior:

```java
private ImagePlus duplicateForMacro(ImagePlus source, String title, String macroContent) {
    if (IjmToDagLoader.loadEmbeddedDag(macroContent) != null) {
        return duplicateImage(source, title);
    }
    return FilterExecutor.duplicateChannel(source, currentPrimaryChannel(), title);
}
```

Use this helper in:

- `previewMacroAsync`
- `runMacroOnDuplicateAsync`
- count shootout source preparation if the dialog does not own it

Threshold runner overloads:

```java
public List<ShootoutResult> run(ImagePlus source, String macro, ShootoutSettings settings,
                                int primaryChannel, FilterExecutor.Progress progress) {
    ImagePlus processed = duplicateForMacro(source, macro, primaryChannel);
    ...
}
```

Keep the old signature and delegate to channel 1 to avoid breaking call sites:

```java
public List<ShootoutResult> run(ImagePlus source, String macro, ShootoutSettings settings,
                                FilterExecutor.Progress progress) {
    return run(source, macro, settings, 1, progress);
}
```

Batch runner overload:

```java
public List<BatchShootoutResult> run(List<File> files, String macro, ShootoutSettings settings,
                                     int primaryChannel, Progress progress) {
    ...
    List<BatchShootoutResult> fileRows = runOneFile(file, macro, settings, primaryChannel);
}
```

Batch settings JSON:

```json
{
  "schemaVersion": 1,
  "macroPath": "Macro_Builder_Batch_Count_Filter.ijm",
  "resultsFile": "Macro_Builder_Batch_Count.csv",
  "primaryChannel": 2,
  "countingMode": "PARTICLES_2D"
}
```

`BatchMacroExporter.readSettings` should use optional default 1:

```java
int primaryChannel = intField(json, "primaryChannel", 1);
```

Extend `ExportedSettings`:

```java
public final int primaryChannel;

public ExportedSettings(File settingsFile, String macroPath, String resultsFile,
                        ShootoutSettings settings, int primaryChannel) {
    ...
    this.primaryChannel = primaryChannel < 1 ? 1 : primaryChannel;
}
```

If changing the existing constructor, keep an overload for old tests and call sites.

Test outline for `ThresholdShootoutRunnerTest`:

```java
@Test
public void nonDagMacroRunsOnSelectedPrimaryChannelOnly() {
    ImagePlus source = twoChannelImage(5, 200);
    ShootoutSettings settings = fixedSettings(100.0);

    List<ShootoutResult> rows = new ThresholdShootoutRunner().run(
            source, "", settings, 2, null);

    assertTrue(rows.get(0).isSuccess());
    assertEquals(1, rows.get(0).countSummary.count);
}
```

For embedded DAG macros, add a test proving both channels remain available. A DAG macro with `C1 SUBTRACT C2` should still work even when primary channel is 1 because the full source reaches the DAG executor.

## Exit gate

1. `.\mvnw.cmd -Dtest=ThresholdShootoutRunnerTest,BatchMacroExporterTest test "-Denforcer.skip=true"` passes.
2. `.\mvnw.cmd test "-Denforcer.skip=true"` passes.
3. Old batch settings JSON without `primaryChannel` reads as channel 1.
4. Saved batch settings JSON includes `primaryChannel`.
5. Manual Fiji check: a non-DAG macro on a two-channel hyperstack counts only the selected primary channel.
6. Manual Fiji check: an embedded DAG macro using an auxiliary channel still sees that auxiliary channel during preview and count testing.

## Known risks

- Duplicating only the primary channel for embedded DAG macros would break auxiliary branches. Always detect embedded DAG macros before narrowing the source.
- Extending `ShootoutSettings` itself may create unnecessary churn because it stores count settings, not macro source settings. Prefer `ExportedSettings` or runner parameters unless a broader settings object already exists by this stage.
- Batch CSV currently reports source image channel count. Do not change CSV columns unless the user explicitly asks for output-source-channel metadata.

