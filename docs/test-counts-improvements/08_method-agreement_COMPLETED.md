# Method agreement column and consensus mask

## Why this stage exists

When eight methods produce eight counts within a 20% spread, the user has no way to know whether they are picking the same objects with small disagreements or completely different objects with similar totals. This stage adds an "Agreement" column that scores each variant against the majority of others, and a "Show consensus mask" button that overlays what the majority of methods agree on. It gives a trust signal even when no ground truth is available.

## Prerequisites

- `01_foundation-perf-refactor` complete.
- `07_fragility-bar` complete, so this stage appends `agreement_score` after the fragility CSV columns.
- Depends on stage 01 for typed byte masks and the `ShootoutRun` context.

## Read first

- `src/main/java/macro/builder/analysis/ThresholdShootoutRunner.java` after stage 01
- `src/main/java/macro/builder/analysis/ObjectCounter.java`
- `src/main/java/macro/builder/ui/ThresholdShootoutDialog.java:502-523` for opening/replacing mask preview windows
- `src/main/java/macro/builder/ui/ThresholdShootoutDialog.java:844-854` for closing preview images safely
<!-- audit:agent1 corrected ThresholdShootoutDialog preview line ranges -->

## Scope

- Add `agreementScore` field to `ShootoutResult`. Value is the IoU between this variant's mask and the consensus mask of all *other* successful variants. Range 0..1.
- Build a display consensus mask: for each pixel, count how many variants set it foreground; if >= ceil(N/2) say yes, set it foreground in the consensus. Compute once per shootout.
- For each row's `agreementScore`, compare that row to the majority of the *other* successful variants. Use shared per-slice vote counts and subtract the current row while scoring; do not materialise one consensus stack per row.
- Add an "Agreement" column to the results table. Sortable. Default visible.
- Add a "Show consensus mask" button next to "Open mask preview". Opens the consensus mask as an `ImagePlus` titled "Test Counts Consensus".
- Tooltip on the Agreement header: "How much this method overlaps with the majority of the other methods. Lower means this method picked different objects."
- When fewer than 3 successful variants exist, hide the UI column, leave `agreement_score` blank in CSV, and disable the consensus button.
- If the estimated retained mask memory would exceed the cap in `00_overview.md` "Memory budget", skip agreement before retaining consensus data. For very large stacks, also allow per-row mask previews to degrade to "preview unavailable due to memory" rather than keeping every mask in memory.

## Out of scope

- Pairwise agreement matrix (N×N heatmap is a future stage if requested).
- Consensus-based recommendation (the plateau in stage 02 and the F1 winner in stage 05 keep their roles; consensus is informational only).

## Files touched

| path | action | reason |
|---|---|---|
| `src/main/java/macro/builder/analysis/ConsensusMaskBuilder.java` | NEW | Pure: takes a list of masks, returns the consensus mask plus per-mask agreement scores. |
| `src/main/java/macro/builder/analysis/ShootoutResult.java` | MODIFY | Add `agreementScore`. |
| `src/main/java/macro/builder/analysis/ThresholdShootoutRunner.java` | MODIFY | After all variants run, build consensus, write agreement scores. |
| `src/main/java/macro/builder/ui/ThresholdShootoutDialog.java` | MODIFY | "Agreement" column, "Show consensus mask" button, lifecycle (close consensus on dialog close). |
| `src/test/java/macro/builder/analysis/ConsensusMaskBuilderTest.java` | NEW | Three masks: two identical, one outlier — assert outlier has lowest agreement and consensus matches the majority. |

## Implementation sketch

Consensus build:

```java
public static ConsensusResult build(List<ImagePlus> masks) {
    int w = masks.get(0).getWidth(), h = masks.get(0).getHeight();
    int z = masks.get(0).getStackSize();
    int needYes = (int) Math.ceil(masks.size() / 2.0);
    ImageStack out = new ImageStack(w, h);
    for (int s = 1; s <= z; s++) {
        byte[] consensus = new byte[w * h];
        int[] votes = new int[w * h];
        for (ImagePlus m : masks) {
            byte[] in = (byte[]) m.getStack().getProcessor(s).getPixels();
            for (int i = 0; i < in.length; i++) if (in[i] != 0) votes[i]++;
        }
        for (int i = 0; i < consensus.length; i++) {
            if (votes[i] >= needYes) consensus[i] = (byte) 255;
        }
        out.addSlice(new ByteProcessor(w, h, consensus, null));
    }
    return new ConsensusResult(new ImagePlus("Consensus", out), perMaskAgreements);
}
```

Agreement score per variant:

```java
double iou(byte[] a, byte[] b) {
    long intersect = 0, union = 0;
    for (int i = 0; i < a.length; i++) {
        boolean av = a[i] != 0, bv = b[i] != 0;
        if (av && bv) intersect++;
        if (av || bv) union++;
    }
    return union == 0 ? 0.0 : (double) intersect / union;
}
```

Memory: do not materialise the consensus stack twice, and do not build one consensus stack per row. The consensus `ImagePlus` is owned by the dialog and closed in `windowClosed` alongside `activeMaskPreview`.

Threading model:

- `ConsensusMaskBuilder` runs inside the shootout worker after all successful variant masks are available.
- Vote counting may use a bounded per-slice worker from stage 01, but it must keep only one slice's `int[] votes` in memory at a time.
- The "Show consensus mask" button, table column updates, memory-cap messages, and `ImagePlus.show()` call run on the EDT.
- Headless tests call `ConsensusMaskBuilder` directly and never construct the dialog button/window.

## Exit gate

1. All existing and new tests pass with `.\mvnw.cmd test "-Denforcer.skip=true"`.
2. `ConsensusMaskBuilderTest` exercises the new consensus path with three masks: two identical and one outlier; the outlier has the lowest agreement and the consensus equals the majority mask byte-for-byte.
3. On a sweep with 8 methods where 6 agree closely and 2 are outliers, the 2 outliers have agreement < 0.5 and the 6 have agreement > 0.85.
4. "Show consensus mask" opens one window; clicking again replaces that same window and leaves exactly one "Test Counts Consensus" window open.
5. With only 2 successful variants, the column is hidden, the consensus button is disabled, and CSV cells for `agreement_score` are blank.
6. CSV export includes `agreement_score` in the order locked in `00_overview.md`; values are blank when fewer than 3 variants exist or the memory cap skips consensus, and numeric values use `Locale.ROOT`.

## Known risks

- Two methods agreeing perfectly can both still be wrong. Mitigation: the tooltip says `agreement`, not `correctness`, and consensus never changes the recommendation by itself.
- For 3D stacks, IoU is computed across the whole stack as a single bit-array; memory is O(WxHxZ) bytes per mask. Mitigation: guard with the shared optional-pixel cap in `00_overview.md` "Memory budget" and skip agreement/consensus when retained masks would exceed 256 MiB.
- Very large images such as 4K x 4K x 100 can exceed memory even before consensus rendering. Mitigation: estimate mask retention before storing any masks, allow per-row previews to be unavailable, and leave `agreement_score` blank with a disabled-button reason.
- Virtual stacks can be slow if consensus forces every mask slice back from disk. Mitigation: build and score consensus per slice inside the worker without caching source slices beyond the retained masks already allowed by the cap.
- Variants that failed have no mask. Mitigation: skip them from consensus, set `agreementScore = NaN`, and cover the failed-row case in `ConsensusMaskBuilderTest`.
