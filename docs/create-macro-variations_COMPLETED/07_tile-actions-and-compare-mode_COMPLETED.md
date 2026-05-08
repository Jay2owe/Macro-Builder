# Stage 07 — Tile actions and Compare mode

## Why this stage exists

A grid of tiles you can only look at is half a feature — the user has to be able to *act* on it. This stage adds the per-tile controls (Promote, Save preset, Eliminate-from-view) and the two-tier narrowing flow that mirrors Lightroom's Survey → Compare. When the user has eliminated their way down to two tiles (raw + one finalist, or two variants), the grid auto-collapses into a 2-up Compare view with spacebar flicker between them. Subtle pixel-level differences below the JND threshold pop visually under flicker — that's how pro retouchers make sub-1% calls, and that's how a microscopist will pick between σ=2.0 and σ=2.5.

## Prerequisites

- Stage 06 (`VariantGridFrame`, `TilePanel`, `SharedSliceDriver`).

## Read first

- `docs/create-macro-variations/00_overview.md`
- `docs/create-macro-variations/06_variant-grid-window.md`
- `src/main/java/macro/builder/ui/sandbox/variation/VariantGridFrame.java` (just created in stage 06)
- `src/main/java/macro/builder/ui/sandbox/variation/TilePanel.java` (stage 06)
- Lightroom Survey/Compare reference — https://helpx.adobe.com/lightroom-classic/help/browse-compare-photos.html (UX precedent)
- `javax.swing.Timer` for the flicker timer

## Scope

- Extend `TilePanel` with three buttons in the caption strip (left-aligned, small icons + text):
  - **Promote** (green checkmark) — fires `TileActionListener.onPromote(VariantPlan)`. The actual DAG mutation is wired in stage 08; this stage just exposes the listener API and makes the button a stub callback.
  - **Save preset** (floppy disk) — fires `TileActionListener.onSavePreset(VariantPlan)`. Stub callback wired in stage 08.
  - **Eliminate** (X) — local to this stage. Calls `VariantGridFrame.eliminateTile(this)`.
- Raw tile (tile [0]) gets only Eliminate (the user *can* eliminate raw if they want; doesn't make sense for them to Promote raw — it's already the baseline). Actually: raw tile gets *no* buttons. The user can't promote raw and shouldn't be able to eliminate it (it's the reference). Lock it.
- Add `TileActionListener` interface on `VariantGridFrame`. Stage 08's wiring code attaches the listener.
- `VariantGridFrame.eliminateTile(TilePanel)`:
  - Marks the tile hidden (set `JPanel.setVisible(false)` and remove from `SharedSliceDriver`'s slave list so it doesn't re-render on scroll).
  - Re-runs grid layout with remaining tiles. Use a flow-style relayout — count visible tiles N, choose `cols = ceil(sqrt(N))`, `rows = ceil(N/cols)`, recreate `GridLayout`.
  - Animation: simple — just relayout. Fancy fade animation deferred to Tier 2.
  - When count of visible variant tiles drops to 1 (raw + 1 variant) → trigger Compare mode.
  - When count drops to 0 (only raw left) → show a banner: "Only raw remains. Re-open Variations to try again." Do not collapse; user can close window.
- New `CompareFrame extends JFrame` for 2-up flicker mode. Constructor takes `ImagePlus left`, `ImagePlus right`, `String leftLabel`, `String rightLabel`, the same `TileActionListener`.
- `CompareFrame` layout:
  - Two large tiles side-by-side (or stacked if window narrower than tall — just use a `GridLayout(1,2)` for v1).
  - Both tiles share the same `SharedSliceDriver`.
  - Both tiles inherit display settings (already done at the variant-output level by stage 06's cloner).
  - Bottom toolbar with `Flicker` toggle button and a flicker-rate spinner (default 2 Hz, range 0.5–5 Hz).
  - Spacebar binding (`KeyStroke.getKeyStroke("SPACE")` on root pane) toggles flicker on/off.
  - When flicker is on: a `javax.swing.Timer` at the chosen rate alternates which tile is "displayed" (the other tile's `JPanel` set invisible, so the visible one occupies the full window via a `CardLayout`).
  - Both tiles still have Promote and Save preset buttons. Eliminate is hidden (only 2 left; eliminating one would defeat the purpose).
- Transition policy: when grid has 2 visible variant tiles + raw, eliminating one more → drop into Compare with raw and the surviving variant. When grid has 3 visible (2 variants + raw), eliminating raw is locked, so user eliminates a variant → 2 remain (raw + 1) → Compare. Edge case: user eliminates everything except 2 variants without raw — also drops into Compare with those two.
- Macro-recordability: every action emits a recordable line via `Recorder.record(...)` if recording is on:
  - Eliminate: `// macro-builder variation: eliminated <variantLabel>`
  - Promote: `// macro-builder variation: promoted <variantLabel>` (the actual `.ijm` for the promoted DAG is emitted by stage 08).
  - Save preset: `// macro-builder variation: saved preset <name>` (stage 08 owns the actual save).

## Out of scope

- DAG mutation on Promote — stage 08 wires the listener.
- Preset file format / location — stage 08.
- The `Recorder` integration plumbing (just call `Recorder.recordString` from this stage; if recording is off, the call is a no-op).
- Animated tile transitions (fade-out, slide) — Tier 2.
- Three-up Compare or arbitrary-N Survey — only 2-up in v1.
- Brightness/contrast adjustment in Compare — inherits source settings, no per-side overrides.

## Files touched

| Path | NEW / MODIFY | Reason |
|------|--------------|--------|
| `src/main/java/macro/builder/ui/sandbox/variation/TileActionListener.java` | NEW | Listener interface (Promote, Save preset) |
| `src/main/java/macro/builder/ui/sandbox/variation/TilePanel.java` | MODIFY | Add the three buttons, fire listener |
| `src/main/java/macro/builder/ui/sandbox/variation/VariantGridFrame.java` | MODIFY | Eliminate logic, relayout, transition to CompareFrame |
| `src/main/java/macro/builder/ui/sandbox/variation/CompareFrame.java` | NEW | 2-up window with flicker toggle |
| `src/main/java/macro/builder/ui/sandbox/variation/FlickerController.java` | NEW | javax.swing.Timer-based alternation logic |
| `src/test/java/macro/builder/ui/sandbox/variation/VariantGridFrameTest.java` | NEW | Eliminate count-down → Compare transition |
| `src/test/java/macro/builder/ui/sandbox/variation/FlickerControllerTest.java` | NEW | Timer fires at expected rate, alternates state |

## Implementation sketch

```java
// TileActionListener.java
public interface TileActionListener {
    void onPromote(VariantPlan plan);
    void onSavePreset(VariantPlan plan);
    // Eliminate is purely internal to the grid — no listener method.
}
```

```java
// TilePanel — add to caption strip
JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
if (!isRawTile) {
    JButton promote = new JButton("✓ Promote");
    promote.addActionListener(e -> listener.onPromote(plan));
    JButton save = new JButton("💾");
    save.setToolTipText("Save as preset");
    save.addActionListener(e -> listener.onSavePreset(plan));
    JButton eliminate = new JButton("✕");
    eliminate.setToolTipText("Eliminate from view");
    eliminate.addActionListener(e -> gridFrame.eliminateTile(this));
    buttons.add(promote); buttons.add(save); buttons.add(eliminate);
}
captionStrip.add(buttons, BorderLayout.EAST);
```

```java
// VariantGridFrame.eliminateTile
public void eliminateTile(TilePanel tile) {
    if (tile.isRawTile()) return;  // locked
    tile.setVisible(false);
    driver.unregister(tile.getImagePlus(), tile.getCanvas());
    eliminated.add(tile);
    Recorder.recordString("// macro-builder variation: eliminated " + tile.label() + "\n");

    int remainingVariants = visibleVariantTiles().size();
    if (remainingVariants == 1) {
        TilePanel survivor = visibleVariantTiles().get(0);
        ImagePlus rawImp = rawTile.getImagePlus();
        ImagePlus winnerImp = survivor.getImagePlus();
        CompareFrame compare = new CompareFrame("Compare: RAW vs " + survivor.label(),
            rawImp, winnerImp, "RAW", survivor.label(), tileActionListener);
        compare.setVisible(true);
        this.dispose();
    } else if (remainingVariants == 0) {
        showOnlyRawBanner();
    } else {
        relayoutGrid();
    }
}
```

```java
// FlickerController.java
public final class FlickerController {
    private final Timer timer;
    private final Runnable toggle;
    private boolean leftVisible = true;

    public FlickerController(double hz, Runnable toggle) {
        this.toggle = toggle;
        int periodMs = (int) Math.max(50, 1000.0 / (2 * hz));   // half-period (each side)
        this.timer = new Timer(periodMs, e -> { leftVisible = !leftVisible; toggle.run(); });
    }

    public void start() { leftVisible = true; timer.start(); }
    public void stop() { timer.stop(); }
    public boolean leftVisible() { return leftVisible; }
    public void setRate(double hz) { timer.setDelay((int) Math.max(50, 1000.0 / (2 * hz))); }
}
```

```java
// CompareFrame skeleton
public final class CompareFrame extends JFrame {
    private final FlickerController flicker;
    private final CardLayout flickerCards = new CardLayout();
    private final JPanel flickerPanel = new JPanel(flickerCards);
    private boolean flickerOn = false;

    public CompareFrame(String title, ImagePlus left, ImagePlus right,
                        String leftLabel, String rightLabel, TileActionListener listener) {
        // build two TilePanels (reuse from stage 06)
        // sideBySide: GridLayout(1,2), holds left + right
        // flickerPanel: cards "left" and "right" each holding the corresponding TilePanel
        // CardLayout used only when flickerOn==true
        // Spacebar binding on root pane → toggle flickerOn
        flicker = new FlickerController(2.0, () ->
            flickerCards.show(flickerPanel, flicker.leftVisible() ? "left" : "right"));
    }
}
```

## Exit gate

1. `mvn test -Dtest=VariantGridFrameTest,FlickerControllerTest` passes.
2. Test coverage:
   - `VariantGridFrameTest`: build a frame with raw + 5 variants. Eliminate 4 variants → asserts the frame is disposed and a `CompareFrame` is constructed with the surviving variant. Eliminate all 5 → asserts the "only raw" banner is visible (frame still alive).
   - `VariantGridFrameTest`: attempt to eliminate the raw tile via `gridFrame.eliminateTile(rawTile)` → asserts no-op (raw still visible).
   - `FlickerControllerTest`: at 2 Hz, after 600ms, `leftVisible` has alternated at least once. At 0.5 Hz, after 600ms, no alternation (period = 2s). Toggle `setRate` mid-stream, period changes.
3. Manual smoke (Fiji running):
   - Open VariantGridFrame with raw + 4 variants.
   - Click X on 3 variants → grid relayouts to 2 tiles (raw + 1 variant) → window closes, CompareFrame opens with raw and surviving variant.
   - In CompareFrame, press Spacebar → flicker starts; image alternates ~2 Hz. Press Spacebar again → flicker stops, both visible side-by-side.
   - Click Promote → console shows "// macro-builder variation: promoted σ=2.0" via Recorder. (Actual DAG mutation is stage 08; here just verify the listener fires.)
4. Macro recordability: with `Plugins > Macros > Record` open, perform Eliminate → see comment line appear; Promote → see comment line appear.
5. `mvn compile` produces no new warnings.

## Known risks

- `javax.swing.Timer` runs on the EDT — that's correct for repaint-driven flicker. Don't use `java.util.Timer` (different thread, would race with the painter).
- Spacebar binding can collide with the focused button (button activates on Space too). Either set `setFocusable(false)` on all toolbar buttons in CompareFrame, or use `WHEN_IN_FOCUSED_WINDOW` on the input map binding so spacebar always reaches the flicker toggle regardless of focus.
- CardLayout flicker hides one tile at a time. Some users might want both visible side-by-side *while* a separate flicker shows the same image swapping with itself — that's a different interaction (more like Photoshop's onion-skin). v1 is simple swap. Note this in the docstring.
- Eliminating tiles via `JPanel.setVisible(false)` inside a `GridLayout` leaves an empty cell (GridLayout doesn't reflow on visibility). The `relayoutGrid` method has to *reconstruct* the GridLayout with only visible tiles, not just toggle visibility.
- The Recorder calls must be wrapped in `if (Recorder.record) { ... }` to avoid no-op overhead when recording is off — or just call `Recorder.recordString` which already checks internally. Verify which is true in the local IJ version.
