# Make Execution Channel-Aware

## Why this stage exists

After Stage 01, the DAG can remember branch source channels, but execution still clones the whole source image for every branch. This stage makes the saved metadata real: each branch must begin from its selected channel and produce a one-channel stack. The UI and count stages depend on this behavior being correct before they expose it to users.

## Prerequisites

- `01_dag-channel-metadata_COMPLETED.md`

## Read first

- `docs/multichannel-hyperstack-pipeline/00_overview.md`
- `AGENTS.md`
- `docs/DEVELOPER.md`
- `src/main/java/macro/builder/image/FilterExecutor.java:333-380`
- `src/main/java/macro/builder/image/FilterExecutor.java:451-650`
- `src/main/java/macro/builder/image/dag/DagToIjmEmitter.java:13-130`
- `src/main/java/macro/builder/image/dag/DagIR.java:7-54`
- `src/main/java/macro/builder/image/dag/DagLine.java:7-31`
- `src/test/java/macro/builder/image/dag/DagIRRoundTripTest.java:15-53`
- TODO: locate or create the nearest `FilterExecutor` test file under `src/test/java/macro/builder/image/`.

## Scope

- Replace whole-source branch cloning in native DAG execution with channel extraction.
- Preserve Z slices, T frames, calibration, and open-as-hyperstack state where applicable.
- Keep output branch images one-channel even when the source has multiple channels.
- Validate that branch source channels are available on the input image.
- Update combiner dimension checks so they compare one-channel branch outputs by width, height, stack size, and logical Z/T where useful.
- Update IJM fallback emission so each branch duplicates only its source channel before running branch steps.
- Add tests showing `C1 SUBTRACT C2` works on a synthetic hyperstack.

## Out of scope

- Do not add primary-channel or branch-source UI controls. Stage 03 owns the visual builder UI.
- Do not alter count shootout or batch setting signatures. Stage 04 owns public workflow plumbing.
- Do not add named channel support. Use numeric 1-based channels.
- Do not change non-DAG legacy macro behavior except where embedded DAG fallback requires channel duplication.

## Files touched

| path | action | reason |
| --- | --- | --- |
| `src/main/java/macro/builder/image/FilterExecutor.java` | MODIFY | Extract branch source channels and combine one-channel branch outputs. |
| `src/main/java/macro/builder/image/dag/DagToIjmEmitter.java` | MODIFY | Emit IJM fallback that duplicates the selected branch channel. |
| `src/test/java/macro/builder/image/FilterExecutorChannelDagTest.java` | NEW | Cover native multichannel DAG execution. |
| `src/test/java/macro/builder/image/dag/DagIRRoundTripTest.java` | MODIFY | Cover emitted IJM channel duplication if no better emitter test exists. |

## Implementation sketch

In `runDagThreadSafe`, change the branch setup from whole-stack cloning to channel cloning.

```java
for (DagLine line : dag.lines) {
    ImagePlus work = cloneChannelStack(source, line.sourceChannel, line.id);
    for (DagNode node : line.ops) {
        executeOpOnStack(work, new FilterMacroParser.Op(node.type, node.args));
    }
    bus.put(line.id, work);
    releaseIfUnused(bus, remainingUses, line.id);
}
```

Add a helper next to `cloneStackPerSlice`.

```java
private static ImagePlus cloneChannelStack(ImagePlus source, int sourceChannel, String label)
        throws DagRejectedException {
    if (source == null || source.getStack() == null) {
        throw new DagRejectedException("Source image is required");
    }
    int channels = Math.max(1, source.getNChannels());
    int slices = Math.max(1, source.getNSlices());
    int frames = Math.max(1, source.getNFrames());
    if (sourceChannel < 1 || sourceChannel > channels) {
        throw new DagRejectedException("Branch source channel C" + sourceChannel
                + " is not available; image has C=" + channels);
    }

    ImageStack src = source.getStack();
    ImageStack copy = new ImageStack(source.getWidth(), source.getHeight());
    for (int t = 1; t <= frames; t++) {
        for (int z = 1; z <= slices; z++) {
            int stackIndex = source.getStackIndex(sourceChannel, z, t);
            ImageProcessor ip = src.getProcessor(stackIndex);
            Rectangle oldRoi = ip.getRoi();
            ip.setRoi(0, 0, source.getWidth(), source.getHeight());
            ImageProcessor cropped = ip.crop();
            if (oldRoi != null) ip.setRoi(oldRoi); else ip.resetRoi();
            copy.addSlice(src.getSliceLabel(stackIndex), cropped);
        }
    }

    ImagePlus out = new ImagePlus(source.getTitle() + "-" + label + "-C" + sourceChannel, copy);
    if (source.getCalibration() != null) out.setCalibration(source.getCalibration().copy());
    if (slices * frames == copy.getSize()) {
        out.setDimensions(1, slices, frames);
        if (source.isHyperStack()) out.setOpenAsHyperStack(true);
    }
    return out;
}
```

Keep `cloneStackPerSlice` for places that still need a whole-image duplicate, but do not use it for DAG line branches.

In `DagToIjmEmitter.emit`, capture source dimensions once and duplicate the selected channel for each line. The exact macro string can be adjusted to ImageJ syntax during implementation, but it should follow this shape:

```java
sb.append("source_id = getImageID();\n");
sb.append("getDimensions(width, height, channels, slices, frames);\n");
...
int channel = Math.max(1, line.sourceChannel);
sb.append("selectImage(source_id);\n");
sb.append("run(\"Duplicate...\", \"title=").append(escapeMacroArg(lineId))
        .append(" duplicate channels=").append(channel).append("-").append(channel)
        .append(" slices=1-\" + slices + \" frames=1-\" + frames);\n");
```

If quoting becomes awkward in Java string building, build the macro command as separate IJM variables:

```ijm
range = "channels=2-2 slices=1-" + slices + " frames=1-" + frames;
run("Duplicate...", "title=line_B duplicate " + range);
```

Native test outline:

```java
@Test
public void subtractsAuxiliaryChannelFromPrimaryChannel() throws Exception {
    ImagePlus source = twoChannelImage(10, 3);
    DagIR dag = new DagIR(1, 1,
            Arrays.asList(
                    new DagLine("line_A", Collections.<DagNode>emptyList(), 1),
                    new DagLine("line_B", Collections.<DagNode>emptyList(), 2)),
            Collections.singletonList(new Combiner("combined", CombinerOp.SUBTRACT,
                    Arrays.asList("line_A", "line_B"))),
            "combined",
            "native");

    ImagePlus result = FilterExecutor.runDagThreadSafe(source, dag);

    assertEquals(1, result.getNChannels());
    assertEquals(7.0f, result.getStack().getProcessor(1).getf(0), 0.0001f);
}
```

Build the synthetic hyperstack in ImageJ stack order: C changes fastest, then Z, then T.

## Exit gate

1. `.\mvnw.cmd -Dtest=FilterExecutorChannelDagTest,DagIRRoundTripTest test "-Denforcer.skip=true"` passes.
2. A DAG with `line_A.sourceChannel=1`, `line_B.sourceChannel=2`, and `SUBTRACT` returns one output channel.
3. A DAG that asks for channel 3 on a two-channel image fails with a clear `DagRejectedException`.
4. `DagToIjmEmitter.emit` output contains branch-specific channel duplication for non-channel-1 branches.
5. Existing single-channel DAG tests still pass without changing expected output.

## Known risks

- ImageJ stack ordering is easy to get wrong. Use `source.getStackIndex(c, z, t)` instead of manual index arithmetic.
- Some ImageJ commands in branch steps may alter dimensions. Keep existing combiner dimension validation strict so bad branch outputs fail clearly.
- IJM fallback quoting can be brittle. Add an emitter assertion before relying on manual Fiji checks.
