# Stage 06 — Variant grid window

## Why this stage exists

This is the visible payoff of the whole feature. Once `VariantExecutor` returns N result `ImagePlus` objects, the user sees them as a grid of tiles in a single Swing window: raw image as tile [0], then N variants. A single shared scrollbar drives the Z slice on every tile in lockstep. Every tile inherits the source's LUT, min/max, calibration, and composite mode so JND-level differences aren't drowned by display drift. Every tile has a baked-in caption with its parameter delta so the user can identify what changed without tooltips. This is the "Auto Threshold > Try all" experience adapted to arbitrary DAG variants.

## Prerequisites

- Stage 04 (`VariantResult`).

## Read first

- `docs/create-macro-variations/00_overview.md`
- `docs/create-macro-variations/04_variant-executor.md`
- `ij.gui.ImageCanvas` (read the public API; AWT `Canvas`-based, embeds in `JPanel` with no special handling)
- `ij.ImagePlus` API: `setSlice(int)`, `setSliceWithoutUpdate`, `updateAndDraw`, `getNSlices`, `getCalibration`, `getLuts`, `getDisplayRangeMin/Max`, `getCompositeMode`
- `src/main/java/macro/builder/ui/sandbox/DagCanvasPanel.java` (existing custom Swing canvas in this codebase — copy its conventions for layout, painting, mouse handling)
- For caption baking: `ij.process.ImageProcessor.drawString(text, x, y)`, `setColor`, `setFont`

## Scope

- New `VariantGridFrame extends JFrame`.
- Constructor takes: `String title`, `ImagePlus rawSource`, `List<VariantResult> results`.
- Layout:
  - **Centre**: a `JPanel` with `GridLayout(rows, cols, 4, 4)`. Compute layout from result count: 1 raw + N results. Default fits to nearest 3×3, 3×4, 4×4, etc. (so e.g. 1+5=6 results uses 2×3, 1+8=9 results uses 3×3, 1+15=16 uses 4×4). Cap at 4×4 (16 tiles total).
  - Each cell holds a `TilePanel` (new inner class) with:
    - Top: caption strip — a `JLabel` showing "RAW" for tile [0] or `result.plan.label` for variants. Bold, dark grey, anti-aliased.
    - Centre: `ImageCanvas` wrapping the tile's `ImagePlus`.
    - The caption is *also* baked into the image bottom-left via `drawString` so PNG export carries it.
  - **East**: a vertical `JScrollBar` running 1..nSlices of the source. Setting its value calls `setSlice(value)` on every tile's `ImagePlus` and `repaint` on every `ImageCanvas`. Mouse wheel anywhere on the grid drives the scrollbar.
  - **South**: a thin status strip showing "Slice K of N · Z=K · Variants: 9".
  - **North**: an empty placeholder `JToolBar` (stage 07 fills it; stage 08 fills with export buttons).
- Display-settings inheritance — for each variant tile's `ImagePlus`:
  - `tile.setLut(rawSource.getLuts())` per channel.
  - `tile.setDisplayRange(rawSource.getDisplayRangeMin(), getDisplayRangeMax())` per channel via `setPosition(c, ...)` then `setDisplayRange`.
  - `tile.setCalibration(rawSource.getCalibration().copy())`.
  - If raw is composite, set tile to composite mode via `CompositeImage` wrapping if not already.
- Caption baking — apply once at frame construction, not per scroll. Use a 14pt sans-serif font, white text with black 1px stroke for contrast against any image. Position at bottom-left, 6px margin. For multichannel tiles, draw on the displayed projection only (a separate "labelled view" `ImagePlus` distinct from the raw computational result — needed so export shows captions but the underlying pixel values for downstream analysis aren't poisoned).
- MIP toggle (north toolbar): a single `JToggleButton` "MIP" that, when on, replaces each tile's display with `ZProjector.run(imp, "max")`. Z scrollbar is hidden in MIP mode. Default off (scrub mode).

## Out of scope

- Per-tile action buttons (Promote, Save preset, X) — stage 07.
- Compare mode / flicker — stage 07.
- Export / save buttons — stage 08.
- Cross-window sync (other Fiji windows) — out of scope for v1.
- Hover tooltips with full parameter detail — captions are baked in; tooltips are nice-to-have, defer.

## Files touched

| Path | NEW / MODIFY | Reason |
|------|--------------|--------|
| `src/main/java/macro/builder/ui/sandbox/variation/VariantGridFrame.java` | NEW | The main JFrame container |
| `src/main/java/macro/builder/ui/sandbox/variation/TilePanel.java` | NEW | One tile (caption + ImageCanvas) |
| `src/main/java/macro/builder/ui/sandbox/variation/SharedSliceDriver.java` | NEW | Pure logic — owns the current Z, fans out setSlice on listeners |
| `src/main/java/macro/builder/ui/sandbox/variation/CaptionBaker.java` | NEW | Draws caption pixels into an ImagePlus's slices |
| `src/main/java/macro/builder/ui/sandbox/variation/DisplaySettingsCloner.java` | NEW | Copies LUT/range/calibration/composite mode from source to variant |
| `src/test/java/macro/builder/ui/sandbox/variation/SharedSliceDriverTest.java` | NEW | Listener fan-out logic |
| `src/test/java/macro/builder/ui/sandbox/variation/CaptionBakerTest.java` | NEW | Pixels at expected positions are darker after caption baked |

## Implementation sketch

```java
// SharedSliceDriver.java — pure logic, no Swing
public final class SharedSliceDriver {
    private final List<ImagePlus> slaves = new ArrayList<>();
    private final List<ImageCanvas> canvases = new ArrayList<>();
    private int currentSlice = 1;

    public void register(ImagePlus imp, ImageCanvas canvas) { slaves.add(imp); canvases.add(canvas); }

    public void setSlice(int slice) {
        currentSlice = Math.max(1, Math.min(slice, maxSlice()));
        for (int i = 0; i < slaves.size(); i++) {
            slaves.get(i).setSliceWithoutUpdate(currentSlice);
            canvases.get(i).repaint();
        }
    }

    public int maxSlice() { return slaves.isEmpty() ? 1 : slaves.get(0).getNSlices(); }
    public int currentSlice() { return currentSlice; }
}
```

```java
// CaptionBaker.java
public final class CaptionBaker {
    public static void bakeAll(ImagePlus imp, String caption) {
        ImageStack stack = imp.getStack();
        for (int i = 1; i <= stack.size(); i++) {
            ImageProcessor ip = stack.getProcessor(i);
            bakeOne(ip, caption);
        }
        imp.updateAndDraw();
    }

    private static void bakeOne(ImageProcessor ip, String caption) {
        ip.setFont(new Font("SansSerif", Font.BOLD, 14));
        ip.setAntialiasedText(true);
        // 1px black stroke
        ip.setColor(Color.BLACK);
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++)
            ip.drawString(caption, 6 + dx, ip.getHeight() - 6 + dy);
        // white fill
        ip.setColor(Color.WHITE);
        ip.drawString(caption, 6, ip.getHeight() - 6);
    }
}
```

```java
// VariantGridFrame.java skeleton
public final class VariantGridFrame extends JFrame {
    private final SharedSliceDriver driver = new SharedSliceDriver();
    private final List<TilePanel> tiles = new ArrayList<>();
    private final JScrollBar zBar;

    public VariantGridFrame(String title, ImagePlus raw, List<VariantResult> results) {
        super(title);
        int total = 1 + results.size();
        int cols = (int) Math.ceil(Math.sqrt(total));
        int rows = (int) Math.ceil(total / (double) cols);
        JPanel grid = new JPanel(new GridLayout(rows, cols, 4, 4));

        // tile 0 = raw
        addTile(grid, raw, "RAW");
        for (VariantResult r : results) {
            if (r.output == null) addErrorTile(grid, r);
            else {
                ImagePlus styled = DisplaySettingsCloner.cloneFrom(raw, r.output);
                CaptionBaker.bakeAll(styled, r.plan.label);
                addTile(grid, styled, r.plan.label);
            }
        }

        zBar = new JScrollBar(JScrollBar.VERTICAL, 1, 1, 1, driver.maxSlice() + 1);
        zBar.addAdjustmentListener(e -> driver.setSlice(e.getValue()));
        grid.addMouseWheelListener(e -> zBar.setValue(zBar.getValue() + e.getWheelRotation()));

        setLayout(new BorderLayout());
        add(grid, BorderLayout.CENTER);
        add(zBar, BorderLayout.EAST);
        add(new JToolBar(), BorderLayout.NORTH);   // populated in stages 07, 08
        add(buildStatusStrip(), BorderLayout.SOUTH);

        pack();
    }
}
```

## Exit gate

1. `mvn test -Dtest=SharedSliceDriverTest,CaptionBakerTest` passes.
2. Test coverage:
   - `SharedSliceDriverTest`: register 3 mock `ImagePlus`-equivalents, call `setSlice(5)`, assert all 3 advance to slice 5; `setSlice(1000)` clamps to `maxSlice()`.
   - `CaptionBakerTest`: bake "TEST" caption on a 64×64 white image, assert pixels in expected caption region are non-white (i.e. some pixels were modified). Don't assert specific glyph shapes — that's brittle.
3. Manual smoke (Fiji running):
   - Build a `VariantGridFrame` with raw + 5 synthetic variant `ImagePlus` (just duplicates of raw with different sigmas applied).
   - 6 tiles visible in 2×3 layout. RAW tile in [0,0]. Captions readable bottom-left of each tile.
   - Scroll the right scrollbar — every tile's slice advances together. No tearing, no lag (test on a 100-slice stack).
   - Mouse wheel on any tile drives the scrollbar.
   - Toggle MIP — all tiles switch to max projection, scrollbar disabled.
   - Resize the window — grid reflows; tiles scale.
4. Visual: variant tiles use the *same* LUT as raw; opening the Channels Tool on a variant tile shows the same min/max as raw. Calibration shown in tile header (e.g. "1.024 µm/px") matches raw.
5. `mvn compile` produces no new warnings.

## Known risks

- `ImageCanvas` is AWT (`Canvas`), not Swing. Embedding inside a `JPanel` works but Swing's lightweight clipping doesn't apply — the canvas always paints in front of anything else in its area. For v1 this is fine (no overlapping widgets). Stage 07's per-tile action buttons must be in the caption strip *above* the canvas, not floating over it.
- Caption baking modifies the variant `ImagePlus`. If a downstream caller (e.g. Promote → re-run on full image, stage 08) relies on the variant being clean, that path must use the *original* uncaptioned `ImagePlus` from `VariantResult.output`, not the captioned copy. Keep `result.output` untouched and clone before baking.
- `DisplaySettingsCloner` for multichannel composite images is finicky. Test with a 3-channel Cy3/DAPI/GFP image before declaring success. Each channel's LUT and range must be copied; channel activation state (which channels are visible in composite) should also be inherited.
- Variant outputs may have *different* slice counts from the raw if a node consumes Z (e.g. Z-projection node). The driver's `maxSlice()` should be the minimum across all tiles. Surface this in the status strip ("Variant 3 has 1 slice; Z scroll limited to 1"). For v1, just clamp to the smallest variant's slice count.
- A `VariantResult` with `error != null` becomes an "error tile" — render a red-bordered panel with the exception message. Don't crash the whole grid.
- `JFrame` is non-modal. The user can interact with the rest of Fiji while it's open. That's intentional and matches Fiji conventions.
