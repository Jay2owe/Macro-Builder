package macro.builder.ui.sandbox.variation;

import ij.ImagePlus;
import macro.builder.image.variation.VariantResult;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.event.ChangeListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.ArrayList;
import java.util.List;

/**
 * Swing window showing the raw source image plus N variant outputs as a grid
 * of {@link TilePanel}s, all driven from a single shared Z scrollbar.
 *
 * <p>Layout (BorderLayout):
 * <ul>
 *   <li><b>NORTH</b> — toolbar with the MIP toggle. Stages 07 and 08 add more
 *       buttons to this strip.</li>
 *   <li><b>CENTER</b> — {@link GridLayout} of tiles. Tile [0] is RAW; tiles
 *       [1..N] are variants. Layout fits the smallest near-square grid that
 *       holds {@code 1+N} tiles, capped at 4×4.</li>
 *   <li><b>EAST</b> — vertical {@link JScrollBar} bound to a
 *       {@link SharedSliceDriver}. Mouse-wheel anywhere in the grid scrolls
 *       it. Hidden in MIP mode.</li>
 *   <li><b>SOUTH</b> — status strip ("Slice K of N · Variants: M").</li>
 * </ul>
 *
 * <p>Display-settings inheritance is delegated to {@link DisplaySettingsCloner};
 * caption baking to {@link CaptionBaker}. Both run once at frame construction —
 * the captioned clone is what the driver registers, and the original
 * {@link VariantResult#output} is left untouched for downstream consumers.
 *
 * <p>Per stage 06's known risks: error variants ({@code result.error != null})
 * render an error tile via {@link TilePanel#forError} and are <em>not</em>
 * registered with the driver, so a single bad variant cannot collapse the Z
 * range to 1.
 */
public final class VariantGridFrame extends JFrame {

    private static final int MAX_TILES = 16;

    private final SharedSliceDriver driver = new SharedSliceDriver();
    private final List<TilePanel> tiles = new ArrayList<TilePanel>();
    private final JScrollBar zBar;
    private final JLabel statusLabel;
    private final JToggleButton mipToggle;
    private final int variantTileCount;
    private final int errorTileCount;

    public VariantGridFrame(String title, ImagePlus rawSource, List<VariantResult> results) {
        super(title == null ? "Variant Grid" : title);
        if (rawSource == null) throw new IllegalArgumentException("rawSource must not be null");
        if (results == null) throw new IllegalArgumentException("results must not be null");

        int requested = 1 + results.size();
        int totalTiles = Math.min(requested, MAX_TILES);
        int variantCap = Math.max(0, totalTiles - 1);
        int[] dims = computeGridDims(totalTiles);
        int rows = dims[0];
        int cols = dims[1];

        JPanel grid = new JPanel(new GridLayout(rows, cols, 4, 4));
        grid.setBackground(new Color(0x1a, 0x1a, 0x1a));
        grid.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        addImageTile(grid, rawSource, "RAW", true);

        int errors = 0;
        int kept = 0;
        for (int i = 0; i < results.size() && kept < variantCap; i++) {
            VariantResult r = results.get(i);
            if (r == null) continue;
            String caption = (r.plan == null || r.plan.label == null) ? "(unlabelled)" : r.plan.label;
            if (r.error != null || r.output == null) {
                TilePanel tile = TilePanel.forError(caption, r.error);
                tiles.add(tile);
                grid.add(tile);
                errors++;
            } else {
                ImagePlus styled = DisplaySettingsCloner.cloneFrom(rawSource, r.output);
                CaptionBaker.bakeAll(styled, caption);
                addImageTile(grid, styled, caption, false);
            }
            kept++;
        }
        this.variantTileCount = kept;
        this.errorTileCount = errors;

        // Pad empty grid cells so GridLayout doesn't stretch the last row's tiles.
        int filled = 1 + kept;
        int totalCells = rows * cols;
        for (int i = filled; i < totalCells; i++) {
            JPanel empty = new JPanel();
            empty.setBackground(new Color(0x1a, 0x1a, 0x1a));
            grid.add(empty);
        }

        int maxSlice = Math.max(1, driver.maxSlice());
        zBar = new JScrollBar(JScrollBar.VERTICAL, 1, 1, 1, maxSlice + 1);
        zBar.setEnabled(maxSlice > 1);
        zBar.addAdjustmentListener(new java.awt.event.AdjustmentListener() {
            @Override public void adjustmentValueChanged(java.awt.event.AdjustmentEvent e) {
                driver.setSlice(e.getValue());
                refreshStatus();
            }
        });

        MouseWheelListener wheel = new MouseWheelListener() {
            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                if (!zBar.isVisible() || !zBar.isEnabled()) return;
                int next = zBar.getValue() + e.getWheelRotation();
                next = Math.max(zBar.getMinimum(), Math.min(zBar.getMaximum() - 1, next));
                zBar.setValue(next);
            }
        };
        grid.addMouseWheelListener(wheel);

        mipToggle = new JToggleButton("MIP");
        mipToggle.setToolTipText("Show maximum-intensity Z projection on every tile");
        mipToggle.addChangeListener(new ChangeListener() {
            @Override public void stateChanged(javax.swing.event.ChangeEvent e) {
                boolean on = mipToggle.isSelected();
                for (TilePanel t : tiles) t.setMipMode(on);
                zBar.setVisible(!on);
                revalidate();
                repaint();
            }
        });

        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(mipToggle);
        toolbar.addSeparator();   // space reserved for stages 07 and 08

        statusLabel = new JLabel(" ");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        JPanel south = new JPanel(new BorderLayout());
        south.add(statusLabel, BorderLayout.WEST);
        south.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xc0, 0xc0, 0xc0)));

        setLayout(new BorderLayout());
        add(toolbar, BorderLayout.NORTH);
        add(grid, BorderLayout.CENTER);
        add(zBar, BorderLayout.EAST);
        add(south, BorderLayout.SOUTH);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        refreshStatus();
        pack();
        Dimension pref = getPreferredSize();
        setSize(Math.min(pref.width, 1400), Math.min(pref.height, 1000));
    }

    private void addImageTile(JPanel grid, ImagePlus imp, String caption, boolean isRaw) {
        final TilePanel tile = new TilePanel(imp, caption, isRaw);
        tiles.add(tile);
        grid.add(tile);
        driver.register(imp, new Runnable() {
            @Override public void run() {
                if (tile.getActiveCanvas() != null) tile.getActiveCanvas().repaint();
            }
        });
    }

    private void refreshStatus() {
        int slice = driver.currentSlice();
        int max = driver.maxSlice();
        StringBuilder sb = new StringBuilder();
        sb.append("Slice ").append(slice).append(" of ").append(max);
        sb.append("  ·  Variants: ").append(variantTileCount);
        if (errorTileCount > 0) sb.append(" (").append(errorTileCount).append(" failed)");
        statusLabel.setText(sb.toString());
    }

    /** Compute the grid (rows, cols) for {@code n} tiles, capped to a 4×4 layout. */
    static int[] computeGridDims(int n) {
        int capped = Math.max(1, Math.min(n, MAX_TILES));
        int cols = (int) Math.ceil(Math.sqrt(capped));
        int rows = (int) Math.ceil(capped / (double) cols);
        return new int[] { rows, cols };
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension base = super.getPreferredSize();
        return new Dimension(Math.max(640, base.width), Math.max(480, base.height));
    }
}
