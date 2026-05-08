package macro.builder.ui.sandbox.variation;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.frame.Recorder;
import macro.builder.image.variation.VariantPlan;
import macro.builder.image.variation.VariantResult;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.KeyboardFocusManager;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class VariantGridFrame extends JFrame {

    private static final int MAX_TILES = 16;

    private final SharedSliceDriver driver = new SharedSliceDriver();
    private final List<TilePanel> tiles = new ArrayList<TilePanel>();
    private final JPanel grid;
    private final JScrollBar zBar;
    private final JLabel statusLabel;
    private final JToggleButton mipToggle;
    private final int initialVariantTileCount;
    private final int initialErrorTileCount;
    private TilePanel rawTile;
    private TileActionListener actionListener;
    private VariationSessionLog sessionLog;
    private MontageExporter montageExporter;
    private IjmClipboardExporter ijmClipboardExporter;

    public VariantGridFrame(String title, ImagePlus rawSource, List<VariantResult> results) {
        super(title == null ? "Variant Grid" : title);
        if (rawSource == null) throw new IllegalArgumentException("rawSource must not be null");
        if (results == null) throw new IllegalArgumentException("results must not be null");

        int requested = 1 + results.size();
        int totalTiles = Math.min(requested, MAX_TILES);
        int variantCap = Math.max(0, totalTiles - 1);
        int[] dims = computeGridDims(totalTiles);

        grid = new JPanel(new GridLayout(dims[0], dims[1], 4, 4));
        grid.setBackground(new Color(0x1a, 0x1a, 0x1a));
        grid.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        rawTile = addImageTile(rawSource, "RAW", true, null);

        int errors = 0;
        int kept = 0;
        for (int i = 0; i < results.size() && kept < variantCap; i++) {
            VariantResult r = results.get(i);
            if (r == null) continue;
            VariantPlan plan = r.plan;
            String caption = (plan == null || plan.label == null || plan.label.length() == 0)
                    ? "(unlabelled)"
                    : plan.label;
            if (r.error != null || r.output == null) {
                TilePanel tile = TilePanel.forError(caption, r.error, plan);
                tiles.add(tile);
                grid.add(tile);
                errors++;
            } else {
                ImagePlus styled = DisplaySettingsCloner.cloneFrom(rawSource, r.output);
                CaptionBaker.bakeAll(styled, caption);
                addImageTile(styled, caption, false, plan);
            }
            kept++;
        }
        this.initialVariantTileCount = kept;
        this.initialErrorTileCount = errors;
        padGrid();

        int maxSlice = Math.max(1, driver.maxSlice());
        zBar = new JScrollBar(JScrollBar.VERTICAL, 1, 1, 1, maxSlice + 1);
        zBar.setEnabled(maxSlice > 1);
        zBar.addAdjustmentListener(e -> {
            driver.setSlice(e.getValue());
            refreshStatus();
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
                for (TilePanel t : tiles) {
                    if (t.isVisible()) t.setMipMode(on);
                }
                zBar.setVisible(!on);
                revalidate();
                repaint();
            }
        });

        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(mipToggle);
        toolbar.addSeparator();
        JButton saveMontage = new JButton("Save montage");
        saveMontage.setToolTipText("Save a labelled PNG montage of the visible tiles");
        saveMontage.addActionListener(e -> saveMontage());
        JButton copyIjm = new JButton("Copy .ijm");
        copyIjm.setToolTipText("Copy ImageJ macro code for visible variants");
        copyIjm.addActionListener(e -> copyIjm());
        JButton saveFocusedPreset = new JButton("Save preset");
        saveFocusedPreset.setToolTipText("Save the focused visible variant as a preset");
        saveFocusedPreset.addActionListener(e -> saveFocusedPreset());
        toolbar.add(saveMontage);
        toolbar.add(copyIjm);
        toolbar.add(saveFocusedPreset);

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

        refreshAllTileActions();
        refreshStatus();
        pack();
        Dimension pref = getPreferredSize();
        setSize(Math.min(pref.width, 1400), Math.min(pref.height, 1000));
    }

    public void setActionListener(TileActionListener actionListener) {
        this.actionListener = actionListener;
        refreshAllTileActions();
    }

    public void setSessionLog(VariationSessionLog sessionLog) {
        this.sessionLog = sessionLog;
    }

    public void attachExporters(MontageExporter montageExporter, IjmClipboardExporter ijmClipboardExporter) {
        this.montageExporter = montageExporter;
        this.ijmClipboardExporter = ijmClipboardExporter;
    }

    public List<TilePanel> visibleTilesInDisplayOrder() {
        List<TilePanel> out = new ArrayList<TilePanel>();
        for (TilePanel tile : tiles) {
            if (tile.isVisible()) out.add(tile);
        }
        return out;
    }

    public List<VariantPlan> visibleVariantPlansInDisplayOrder() {
        List<VariantPlan> out = new ArrayList<VariantPlan>();
        for (TilePanel tile : visibleVariantTilesInDisplayOrder()) {
            if (tile.plan() != null) out.add(tile.plan());
        }
        return out;
    }

    public void eliminateTile(TilePanel tile) {
        if (tile == null || tile.isRawTile() || !tile.isVisible()) return;
        tile.setVisible(false);
        driver.unregister(tile.getScrubImp());
        if (sessionLog != null) sessionLog.recordEliminate(tile.plan(), tile.label());
        if (Recorder.record) {
            Recorder.recordString("// macro-builder variation: eliminated " + tile.label() + "\n");
        }

        List<TilePanel> remainingVariants = visibleVariantTilesInDisplayOrder();
        if (remainingVariants.size() == 1) {
            TilePanel survivor = remainingVariants.get(0);
            if (survivor.hasImage()) {
                openCompare(rawTile, survivor);
                dispose();
            } else {
                relayoutGrid();
                refreshStatus();
            }
        } else if (remainingVariants.isEmpty()) {
            relayoutGrid();
            statusLabel.setText("Only raw remains. Re-open Variations to try again.");
        } else {
            relayoutGrid();
            refreshStatus();
        }
    }

    /** Compute the grid (rows, cols) for {@code n} tiles, capped to a 4x4 layout. */
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

    private TilePanel addImageTile(ImagePlus imp, String caption, boolean isRaw, VariantPlan plan) {
        final TilePanel tile = new TilePanel(imp, caption, isRaw, plan);
        tiles.add(tile);
        grid.add(tile);
        driver.register(imp, new Runnable() {
            @Override public void run() {
                if (tile.getActiveCanvas() != null) tile.getActiveCanvas().repaint();
            }
        });
        return tile;
    }

    private void refreshAllTileActions() {
        for (final TilePanel tile : tiles) {
            tile.setActions(actionListener, new Runnable() {
                @Override public void run() {
                    eliminateTile(tile);
                }
            });
        }
    }

    private List<TilePanel> visibleVariantTilesInDisplayOrder() {
        List<TilePanel> out = new ArrayList<TilePanel>();
        for (TilePanel tile : tiles) {
            if (tile.isVisible() && !tile.isRawTile()) out.add(tile);
        }
        return out;
    }

    private void relayoutGrid() {
        List<TilePanel> visible = visibleTilesInDisplayOrder();
        int[] dims = computeGridDims(visible.size());
        grid.removeAll();
        grid.setLayout(new GridLayout(dims[0], dims[1], 4, 4));
        for (TilePanel tile : visible) grid.add(tile);
        padGrid();
        refreshZBar();
        grid.revalidate();
        grid.repaint();
    }

    private void padGrid() {
        GridLayout layout = (GridLayout) grid.getLayout();
        int totalCells = layout.getRows() * layout.getColumns();
        int filled = grid.getComponentCount();
        for (int i = filled; i < totalCells; i++) {
            JPanel empty = new JPanel();
            empty.setBackground(new Color(0x1a, 0x1a, 0x1a));
            grid.add(empty);
        }
    }

    private void refreshZBar() {
        int maxSlice = Math.max(1, driver.maxSlice());
        int value = Math.max(1, Math.min(zBar.getValue(), maxSlice));
        zBar.setMaximum(maxSlice + 1);
        zBar.setValue(value);
        zBar.setEnabled(maxSlice > 1);
    }

    private void refreshStatus() {
        int slice = driver.currentSlice();
        int max = driver.maxSlice();
        int visibleVariants = visibleVariantTilesInDisplayOrder().size();
        StringBuilder sb = new StringBuilder();
        sb.append("Slice ").append(slice).append(" of ").append(max);
        sb.append("  |  Variants: ").append(visibleVariants).append(" visible");
        if (initialVariantTileCount != visibleVariants) {
            sb.append(" of ").append(initialVariantTileCount);
        }
        if (initialErrorTileCount > 0) sb.append(" (").append(initialErrorTileCount).append(" failed)");
        statusLabel.setText(sb.toString());
    }

    private void openCompare(TilePanel left, TilePanel right) {
        CompareFrame compare = new CompareFrame(
                "Compare: " + left.label() + " vs " + right.label(),
                left.getScrubImp(),
                right.getScrubImp(),
                left.label(),
                right.label(),
                left.plan(),
                right.plan(),
                actionListener);
        compare.setLocationRelativeTo(this);
        compare.setVisible(true);
    }

    private void saveMontage() {
        if (montageExporter == null) montageExporter = new MontageExporter(this);
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save labelled montage");
        chooser.setSelectedFile(new File(defaultExportBaseName() + "_variations.png"));
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("PNG image (*.png)", "png"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = ensureExtension(chooser.getSelectedFile(), ".png");
        montageExporter.exportTo(file);
        if (Recorder.record) {
            Recorder.recordString("// macro-builder variation: saved montage " + file.getName() + "\n");
        }
    }

    private void copyIjm() {
        if (ijmClipboardExporter == null) ijmClipboardExporter = new IjmClipboardExporter(this);
        try {
            ijmClipboardExporter.copyVisibleVariantsToClipboard();
            if (Recorder.record) Recorder.recordString("// macro-builder variation: copied visible .ijm\n");
        } catch (RuntimeException ex) {
            IJ.showMessage("Variations", "Could not copy .ijm to clipboard:\n" + ex.getMessage());
        }
    }

    private void saveFocusedPreset() {
        TilePanel tile = focusedVariantTile();
        if (tile == null || tile.plan() == null) {
            IJ.showMessage("Variations", "Click a visible variant tile first.");
            return;
        }
        if (actionListener != null) actionListener.onSavePreset(tile.plan());
    }

    private TilePanel focusedVariantTile() {
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        for (TilePanel tile : visibleVariantTilesInDisplayOrder()) {
            if (focusOwner != null && (focusOwner == tile || javax.swing.SwingUtilities.isDescendingFrom(focusOwner, tile))) {
                return tile;
            }
        }
        List<TilePanel> variants = visibleVariantTilesInDisplayOrder();
        return variants.isEmpty() ? null : variants.get(0);
    }

    private String defaultExportBaseName() {
        if (rawTile == null || rawTile.getScrubImp() == null) return "variations";
        String title = rawTile.getScrubImp().getTitle();
        if (title == null || title.trim().isEmpty()) return "variations";
        return title.replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    private static File ensureExtension(File file, String extension) {
        if (file == null) return null;
        String name = file.getName().toLowerCase();
        if (name.endsWith(extension)) return file;
        return new File(file.getParentFile(), file.getName() + extension);
    }
}
