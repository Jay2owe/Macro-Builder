# Add Channel Controls To The Visual Builder

## Why this stage exists

Users need to choose the primary channel and decide which channel each parallel branch starts from. The model and executor can support this after Stages 01 and 02, but without UI controls the feature remains hidden. Later count and batch work depends on the builder producing correct DAG channel metadata.

## Prerequisites

- `01_dag-channel-metadata_COMPLETED.md`
- `02_channel-aware-execution_COMPLETED.md`

## Read first

- `docs/multichannel-hyperstack-pipeline/00_overview.md`
- `AGENTS.md`
- `docs/DEVELOPER.md`
- `src/main/java/macro/builder/ui/sandbox/SandboxDialog.java:38-88`
- `src/main/java/macro/builder/ui/sandbox/SandboxDialog.java:140-205`
- `src/main/java/macro/builder/ui/sandbox/SandboxDialog.java:353-420`
- `src/main/java/macro/builder/ui/sandbox/SandboxModel.java:16-210`
- `src/main/java/macro/builder/ui/sandbox/DagCanvasPanel.java:46-130`
- `src/main/java/macro/builder/ui/sandbox/DagCanvasPanel.java:281-330`
- `src/main/java/macro/builder/ui/sandbox/CombinerEditorPanel.java:20-85`
- `src/main/java/macro/builder/ui/ImagePreviewPanel.java:75-153`

## Scope

- Add primary-channel state to `SandboxModel`, sourced from `DagIR.primaryChannel`.
- Add per-line source-channel state to `SandboxModel.Line`, sourced from `DagLine.sourceChannel`.
- Add a primary-channel selector to the visual builder when the source image has more than one channel.
- Make the first branch default to the selected primary channel.
- Let auxiliary branches choose a source channel from `C1..Cn`.
- Update branch cards to show source labels such as `Branch 1 - Primary C1` and `Branch 2 - C2`.
- Make subtraction order clear in combiner cards and editor text.
- Save the chosen channel metadata through `model.toDag()`.
- Add model-level tests for primary and branch source channel persistence.

## Out of scope

- Do not change low-level execution. Stage 02 owns branch channel extraction.
- Do not change count shootout or batch settings. Stage 04 owns workflow plumbing.
- Do not add channel names from microscope metadata. Use numeric labels only.
- Do not redesign the whole visual builder layout beyond the channel controls needed here.
- Do not add branch multi-selection behavior unless it is already present by the time this stage runs.

## Files touched

| path | action | reason |
| --- | --- | --- |
| `src/main/java/macro/builder/ui/sandbox/SandboxModel.java` | MODIFY | Hold primary channel and branch source channels in editable UI state. |
| `src/main/java/macro/builder/ui/sandbox/SandboxDialog.java` | MODIFY | Add primary-channel control and pass channel count to the canvas/model. |
| `src/main/java/macro/builder/ui/sandbox/DagCanvasPanel.java` | MODIFY | Render branch source labels and branch source selectors. |
| `src/main/java/macro/builder/ui/sandbox/CombinerEditorPanel.java` | MODIFY | Make operation ordering, especially subtraction, clear. |
| `src/main/java/macro/builder/ui/ImagePreviewPanel.java` | MODIFY | Update source preview display only if needed to avoid channel ambiguity. |
| `src/test/java/macro/builder/ui/sandbox/SandboxModelTest.java` | NEW | Test model channel persistence and default branch source behavior. |

## Implementation sketch

Model additions:

```java
final class SandboxModel {
    int primaryChannel = 1;
    int channelCount = 1;

    static SandboxModel fromDag(DagIR dag) {
        ...
        model.primaryChannel = dag == null ? 1 : dag.primaryChannel;
        ...
        Line line = new Line(dagLine.id, dagLine.sourceChannel);
    }

    DagIR toDag() {
        ...
        dagLines.add(new DagLine(line.id, nodes, line.sourceChannel));
        ...
        return new DagIR(1, primaryChannel, dagLines, dagCombiners, output, executionTier(dagLines));
    }

    void setPrimaryChannel(int channel) {
        primaryChannel = clampChannel(channel);
        if (!lines.isEmpty()) {
            lines.get(0).sourceChannel = primaryChannel;
        }
    }

    void setChannelCount(int count) {
        channelCount = Math.max(1, count);
        primaryChannel = clampChannel(primaryChannel);
        for (Line line : lines) line.sourceChannel = clampChannel(line.sourceChannel);
    }

    static final class Line {
        final String id;
        int sourceChannel;
        final List<Node> nodes = new ArrayList<Node>();
    }
}
```

Primary selector in `SandboxDialog.buildMain()` or a small header panel above the canvas:

```java
JComboBox<String> primaryChannel = new JComboBox<String>(channelLabels(model.channelCount));
primaryChannel.setSelectedIndex(model.primaryChannel - 1);
primaryChannel.addActionListener(e -> {
    model.setPrimaryChannel(primaryChannel.getSelectedIndex() + 1);
    canvas.rebuild();
    refreshEditors();
});
```

Get channel count from the preview source display:

```java
ImagePlus display = previewHandler == null ? null : previewHandler.getSourceForDisplay();
model.setChannelCount(display == null ? 1 : Math.max(1, display.getNChannels()));
```

Branch header rendering in `DagCanvasPanel.buildLinePanel`:

```java
String sourceLabel = model.lines.indexOf(line) == 0 && line.sourceChannel == model.primaryChannel
        ? "Primary C" + model.primaryChannel
        : "C" + line.sourceChannel;
JLabel title = new JLabel("Branch " + branchIndex + " - " + sourceLabel);
```

For auxiliary branches, add a compact selector in the branch header. The first branch can either be locked to the primary channel or update when the primary selector changes. Keep that behavior obvious in the label.

```java
final JComboBox<String> source = new JComboBox<String>(channelLabels(model.channelCount));
source.setSelectedIndex(line.sourceChannel - 1);
source.setEnabled(model.lines.indexOf(line) != 0);
source.addActionListener(e -> {
    line.sourceChannel = source.getSelectedIndex() + 1;
    changedAndSelected();
});
```

Combiner wording:

- Change card text from `combiner: [line_A, line_B] SUBTRACT` to a clearer label like `Subtract: Branch 1 minus Branch 2`.
- In `CombinerEditorPanel`, add a short label when `op == SUBTRACT`: `Subtract uses input order: first input minus later inputs.`
- Do not add visible instructional text across the whole app; keep this attached to the relevant combiner editor/card.

Model tests:

```java
@Test
public void toDagPreservesPrimaryAndBranchChannels() {
    SandboxModel model = SandboxModel.fromDag(new DagIR(1, 2,
            Arrays.asList(
                    new DagLine("line_A", Collections.<DagNode>emptyList(), 2),
                    new DagLine("line_B", Collections.<DagNode>emptyList(), 3)),
            Collections.<Combiner>emptyList(),
            "line_A",
            "native"));
    model.setChannelCount(3);

    DagIR dag = model.toDag();

    assertEquals(2, dag.primaryChannel);
    assertEquals(2, dag.lines.get(0).sourceChannel);
    assertEquals(3, dag.lines.get(1).sourceChannel);
}
```

## Exit gate

1. `.\mvnw.cmd -Dtest=SandboxModelTest,DagIRRoundTripTest test "-Denforcer.skip=true"` passes.
2. Manual Fiji check: opening the builder on a two-channel hyperstack shows a primary-channel selector.
3. Manual Fiji check: adding a parallel branch lets the auxiliary branch choose `C1` or `C2`.
4. Manual Fiji check: saving the builder emits a DAG JSON comment containing `primaryChannel` and per-line `sourceChannel`.
5. Manual Fiji check: a `SUBTRACT` combiner makes input order visually clear.

## Known risks

- The builder currently has limited horizontal space. Keep channel controls compact and attached to branch headers.
- If source image channel count changes while the dialog is open, clamp selected channels on preview/run instead of allowing invalid metadata.
- Too much explanatory text in the UI will make the builder noisy. Prefer labels and operation names over long help text.
