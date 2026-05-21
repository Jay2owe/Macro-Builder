package macro.builder.ui.sandbox;

import macro.builder.image.FilterExecutor;
import macro.builder.image.NamedFilterLoader;
import macro.builder.image.dag.DagIR;
import macro.builder.image.dag.DagIRSerializer;
import macro.builder.image.dag.DagToIjmEmitter;
import macro.builder.image.dag.IjmToDagLoader;
import macro.builder.image.variation.VariantResult;
import macro.builder.ui.HistogramPanel;
import macro.builder.ui.ImagePreviewPanel;
import macro.builder.ui.MacroFileSaver;
import macro.builder.ui.PreviewDisplaySettings;
import macro.builder.ui.sandbox.variation.IjmClipboardExporter;
import macro.builder.ui.sandbox.variation.MontageExporter;
import macro.builder.ui.sandbox.variation.VariantGridFrame;
import macro.builder.ui.sandbox.variation.VariationActionsBinder;
import macro.builder.ui.sandbox.variation.VariationChooserDialog;
import macro.builder.ui.sandbox.variation.VariationSessionLog;
import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.plugin.Duplicator;
import ij.plugin.frame.Recorder;
import ij.process.LUT;

import javax.swing.BorderFactory;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.SecondaryLoop;
import java.awt.Window;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.List;

public final class SandboxDialog extends JDialog {

    private static final double PREVIEW_COLUMN_FRACTION = 0.37;
    private static final double AVAILABLE_STEPS_FRACTION = 0.10;
    private static final double INITIAL_DESKTOP_FRACTION = 0.88;
    private static final Color PREVIEW_ACCENT = new Color(36, 104, 170);
    private static final Color PREVIEW_ACCENT_DARK = new Color(25, 76, 130);
    private static final Color PREVIEW_ACCENT_LIGHT = new Color(229, 241, 253);

    public interface PreviewHandler {
        ImagePlus createSource() throws Exception;
        ImagePlus getSourceForDisplay();
        ImagePlus showPreview(ImagePlus result, ImagePlus existingPreview) throws Exception;
        void close(ImagePlus imp);
    }

    public static final class Result {
        public final DagIR dag;
        public final String ijmFallback;
        public final File savedMacroFile;

        private Result(DagIR dag, String ijmFallback, File savedMacroFile) {
            this.dag = dag;
            this.ijmFallback = ijmFallback;
            this.savedMacroFile = savedMacroFile;
        }

        public static Result cancel() {
            return new Result(null, null, null);
        }
    }

    private final SandboxModel model;
    private final PreviewHandler previewHandler;
    private final File stateDir;
    private final VariationSessionLog variationLog = new VariationSessionLog();
    private final CountDownLatch done = new CountDownLatch(1);
    private final DagCanvasPanel canvas;
    private final FilterCatalog catalog;
    private final ImagePreviewPanel sourcePreview = new ImagePreviewPanel("Source image");
    private final ImagePreviewPanel outputPreview = new ImagePreviewPanel("Preview output");
    private final JLabel status = new JLabel(" ");
    private final JLabel legacyBanner = new JLabel("This chain runs through legacy execution (slower, single-threaded per image).");
    private final JButton previewSelected = new JButton("Preview to selected point");
    private final JButton previewFinal = new JButton("Preview full filter");
    private final JButton largePreview = new JButton("Large view");
    private final JButton brightnessContrast = new JButton("Brightness/Contrast");
    private final JButton lutToggle = new JButton("Grey LUT");
    private final JButton createVariations = new JButton("Create Variations");
    private final JButton showVariationLog = new JButton("Variation Log");
    private final JButton startFromPreset = new JButton("Start from a preset...");
    private final JButton help = new JButton("?");
    private final JButton save = new JButton("Save Macro");
    private final JButton cancel = new JButton("Cancel");
    private final JComboBox<String> primaryChannelSelector = new JComboBox<String>();
    private JSplitPane mainSplit;
    private JSplitPane centerRightSplit;
    private JPanel primaryChannelBar;

    private SecondaryLoop loop;
    private Result result = Result.cancel();
    private ImagePlus previewImage;
    private LargePreviewDialog largePreviewDialog;
    private PreviewDisplayDialog displayDialog;
    private PreviewDisplaySettings.LutMode lutMode = PreviewDisplaySettings.LutMode.USER;
    private PreviewDisplaySettings sourceDisplaySettings =
            PreviewDisplaySettings.defaultFor(PreviewDisplaySettings.LutMode.USER);
    private PreviewDisplaySettings outputDisplaySettings =
            PreviewDisplaySettings.defaultFor(PreviewDisplaySettings.LutMode.USER);
    private boolean busy = false;
    private final String initialIjm;
    private final int initialNodeCount;
    private final DagUndoHistory undoHistory;
    private boolean applyingUndo;
    private boolean updatingPrimaryChannelSelector;
    private boolean syncingPreviewSlices;

    private SandboxDialog(String channelLabel, File stateDir, DagIR initialDag,
                          PreviewHandler previewHandler, boolean openVariationsOnStart) {
        super((java.awt.Frame) null, "Filter Builder - " + safe(channelLabel), false);
        this.model = SandboxModel.fromDag(initialDag);
        this.previewHandler = previewHandler;
        this.stateDir = stateDir;
        model.setChannelCount(channelCount(currentSourceDisplay()));
        this.initialIjm = DagToIjmEmitter.emit(model.toDag());
        this.initialNodeCount = countNodes(model);
        this.undoHistory = new DagUndoHistory(model.toDag());
        this.catalog = new FilterCatalog();
        this.canvas = new DagCanvasPanel(model, new DagCanvasPanel.CatalogSupplier() {
            @Override public FilterCatalog.Entry getSelectedCatalogEntry() {
                return catalog.getSelectedEntry();
            }
        }, new DagCanvasPanel.NodeCreator() {
            @Override public boolean addNode(SandboxModel.Line line, FilterCatalog.Entry entry) {
                return addCatalogNode(line, entry);
            }
        }, new DagCanvasPanel.NodeActionHandler() {
            @Override public void editNode(SandboxModel.Line line, SandboxModel.Node node) {
                editNodeInline(node);
            }

            @Override public void previewToNode(SandboxModel.Line line, SandboxModel.Node node) {
                previewToNodeInline(node);
            }
        }, new DagCanvasPanel.CombinerActionHandler() {
            @Override public void editCombiner(SandboxModel.CombinerNode combiner) {
                editCombinerInline(combiner);
            }

            @Override public void previewToCombiner(SandboxModel.CombinerNode combiner) {
                previewToCombinerInline(combiner);
            }
        }, new DagCanvasPanel.UndoActionHandler() {
            @Override public boolean canUndo() {
                return !busy && undoHistory.canUndo();
            }

            @Override public void undoLastChange() {
                SandboxDialog.this.undoLastChange();
            }
        }, new Runnable() {
            @Override public void run() { refreshEditors(); }
        }, new Runnable() {
            @Override public void run() { recordModelChange(); }
        });
        canvas.setUnitContextProvider(new DagCanvasPanel.UnitContextProvider() {
            @Override public ArgsEditorModel.UnitContext getUnitContext() {
                return currentUnitContext();
            }
        });
        catalog.setAddRequestListener(new FilterCatalog.AddRequestListener() {
            @Override public void onAddRequested(FilterCatalog.Entry entry) {
                SandboxModel.Line target = resolveTargetLine();
                if (target == null) return;
                if (addCatalogNode(target, entry)) {
                    recordModelChange();
                    canvas.rebuild();
                }
                refreshEditors();
            }
        });

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(980, 620));
        setLayout(new BorderLayout(8, 8));
        legacyBanner.setOpaque(true);
        legacyBanner.setBackground(new Color(255, 244, 204));
        legacyBanner.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        legacyBanner.setVisible(false);
        configurePreviewButtons();
        add(legacyBanner, BorderLayout.NORTH);
        add(buildMain(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
        wirePreviewSliceSync();
        wireButtons();
        refreshSourcePreview();
        refreshEditors();
        pack();
        sizeNearDesktop();
        applyInitialSplitLocations();
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                applyInitialSplitLocations();
                if (openVariationsOnStart) openVariationsDialog();
            }
        });
    }

    public static Result show(String channelLabel, File binFolder, int channelIndex,
                              String seedMacro, PreviewHandler previewHandler) {
        return show(channelLabel, binFolder, channelIndex, seedMacro, previewHandler, false);
    }

    public static Result show(String channelLabel, File binFolder, int channelIndex,
                              String seedMacro, PreviewHandler previewHandler,
                              boolean openVariationsOnStart) {
        if (GraphicsEnvironment.isHeadless()) return Result.cancel();
        final DagIR initialDag = loadInitialDag(binFolder, channelIndex, seedMacro);
        final SandboxDialog dialog = new SandboxDialog(
                channelLabel, binFolder, initialDag, previewHandler, openVariationsOnStart);
        dialog.setVisible(true);

        if (SwingUtilities.isEventDispatchThread()) {
            dialog.loop = java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
            dialog.loop.enter();
        } else {
            try {
                dialog.done.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Result.cancel();
            }
        }
        return dialog.result;
    }

    private static DagIR loadInitialDag(File binFolder, int channelIndex, String seedMacro) {
        // Lookup order: per-channel .bin/C{n}_Sandbox.dag.json, then seedMacro via IjmToDagLoader.
        // (Future: a shared .bin/Shared_Sandbox.dag.json could slot in between once a writer exists.)
        File channelDag = binFolder == null ? null
                : new File(binFolder, "C" + (channelIndex + 1) + "_Sandbox.dag.json");
        if (channelDag != null && channelDag.exists()) {
            try {
                return DagIRSerializer.fromJson(new String(Files.readAllBytes(channelDag.toPath()), StandardCharsets.UTF_8));
            } catch (Exception e) {
                IJ.log("WARNING: could not load " + channelDag.getName() + ": " + e.getMessage());
            }
        }
        return IjmToDagLoader.load(seedMacro);
    }

    private JPanel buildMain() {
        JPanel previews = new JPanel(new GridBagLayout());
        previews.setMinimumSize(new Dimension(0, 1));
        GridBagConstraints previewGbc = new GridBagConstraints();
        previewGbc.gridx = 0;
        previewGbc.gridy = 0;
        previewGbc.weightx = 1.0;
        previewGbc.weighty = 1.0;
        previewGbc.fill = GridBagConstraints.BOTH;
        previews.add(sourcePreview, previewGbc);
        previewGbc.gridy++;
        previewGbc.weighty = 0.0;
        previewGbc.fill = GridBagConstraints.HORIZONTAL;
        previewGbc.insets = new Insets(6, 0, 6, 0);
        previews.add(buildPreviewControls(), previewGbc);
        previewGbc.gridy++;
        previewGbc.weighty = 1.0;
        previewGbc.fill = GridBagConstraints.BOTH;
        previewGbc.insets = new Insets(0, 0, 0, 0);
        previews.add(outputPreview, previewGbc);

        JPanel catalogPanel = new JPanel(new BorderLayout(6, 6));
        catalogPanel.setMinimumSize(new Dimension(0, 1));
        catalogPanel.add(catalog, BorderLayout.CENTER);

        JScrollPane canvasScroll = new JScrollPane(canvas);
        canvasScroll.setBorder(BorderFactory.createEmptyBorder());
        canvasScroll.setMinimumSize(new Dimension(0, 1));

        JLabel intro = new JLabel("Use the + buttons or double-click grouped steps, or pick a step and click '+ Add step' on a branch.");
        intro.setOpaque(true);
        intro.setBackground(new Color(232, 244, 252));
        intro.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(180, 200, 220)),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));

        JPanel top = new JPanel(new BorderLayout(0, 0));
        top.add(intro, BorderLayout.NORTH);
        top.add(buildPrimaryChannelBar(), BorderLayout.SOUTH);

        JPanel left = new JPanel(new BorderLayout(0, 4));
        left.add(top, BorderLayout.NORTH);
        left.add(canvasScroll, BorderLayout.CENTER);

        centerRightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, catalogPanel);
        centerRightSplit.setResizeWeight(centerRightLeftFraction());

        mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, previews, centerRightSplit);
        mainSplit.setResizeWeight(PREVIEW_COLUMN_FRACTION);

        JPanel main = new JPanel(new BorderLayout());
        main.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        main.add(mainSplit, BorderLayout.CENTER);
        return main;
    }

    private JPanel buildPreviewControls() {
        JPanel controls = new JPanel(new BorderLayout(8, 0));
        controls.setOpaque(true);
        controls.setBackground(PREVIEW_ACCENT_LIGHT);
        controls.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(PREVIEW_ACCENT, 2),
                BorderFactory.createEmptyBorder(7, 8, 7, 8)));

        JLabel label = new JLabel("Preview");
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        label.setForeground(PREVIEW_ACCENT_DARK);
        controls.add(label, BorderLayout.WEST);

        JPanel buttonRows = new JPanel(new GridLayout(2, 1, 0, 6));
        buttonRows.setOpaque(false);

        JPanel previewButtons = new JPanel(new GridLayout(1, 3, 6, 0));
        previewButtons.setOpaque(false);
        previewButtons.add(previewSelected);
        previewButtons.add(previewFinal);
        previewButtons.add(largePreview);
        buttonRows.add(previewButtons);

        JPanel displayButtons = new JPanel(new GridLayout(1, 2, 6, 0));
        displayButtons.setOpaque(false);
        displayButtons.add(brightnessContrast);
        displayButtons.add(lutToggle);
        buttonRows.add(displayButtons);

        controls.add(buttonRows, BorderLayout.CENTER);
        return controls;
    }

    private JPanel buildPrimaryChannelBar() {
        primaryChannelBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        primaryChannelBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(210, 210, 210)),
                BorderFactory.createEmptyBorder(0, 4, 0, 4)));
        primaryChannelBar.add(new JLabel("Primary channel:"));
        primaryChannelBar.add(primaryChannelSelector);
        primaryChannelSelector.addActionListener(e -> {
            if (updatingPrimaryChannelSelector) return;
            int selected = primaryChannelSelector.getSelectedIndex();
            if (selected < 0) return;
            model.setPrimaryChannel(selected + 1);
            recordModelChange();
            canvas.rebuild();
            refreshEditors();
        });
        refreshPrimaryChannelSelector();
        return primaryChannelBar;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new GridBagLayout());
        footer.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 0, 0, 6);

        // Left cluster: variations, preset, then help.
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        footer.add(createVariations, gbc);
        gbc.gridx++;
        footer.add(showVariationLog, gbc);
        gbc.gridx++;
        footer.add(startFromPreset, gbc);
        gbc.gridx++;
        footer.add(help, gbc);

        // Status spacer expands to push the right cluster to the right.
        gbc.gridx++;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 8, 0, 8);
        footer.add(status, gbc);

        // Right cluster: cancel and save.
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(0, 0, 0, 6);
        gbc.gridx++;
        footer.add(cancel, gbc);
        gbc.gridx++;
        gbc.insets = new Insets(0, 0, 0, 0);
        footer.add(save, gbc);
        return footer;
    }

    private void configurePreviewButtons() {
        previewSelected.setText("Selected point");
        previewSelected.setToolTipText("Run the filter up to the selected step or merge.");
        stylePreviewButton(previewSelected, false);

        previewFinal.setText("Full filter");
        previewFinal.setToolTipText("Run the full filter and update the output preview.");
        stylePreviewButton(previewFinal, true);

        largePreview.setToolTipText("Open source and preview images side by side in a larger window.");
        stylePreviewButton(largePreview, false);

        brightnessContrast.setToolTipText("Adjust the source and preview display brightness and contrast.");
        stylePreviewButton(brightnessContrast, false);

        updateLutToggleButton();
        stylePreviewButton(lutToggle, false);
    }

    private static void stylePreviewButton(JButton button, boolean primary) {
        button.setOpaque(true);
        button.setFocusPainted(false);
        button.setFont(button.getFont().deriveFont(primary ? Font.BOLD : Font.PLAIN, 12f));
        button.setForeground(primary ? Color.WHITE : PREVIEW_ACCENT_DARK);
        button.setBackground(primary ? PREVIEW_ACCENT : Color.WHITE);
        button.setMargin(new Insets(5, 10, 5, 10));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(primary ? PREVIEW_ACCENT_DARK : PREVIEW_ACCENT, primary ? 2 : 1),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
    }

    private void showSandboxHelp() {
        String msg = "<html><body style='width:380px;'>"
                + "Build the channel's custom filter as a chain of steps. Use the "
                + "<b>+</b> buttons or double-click rows in the grouped step boxes to add "
                + "commands to the selected branch, or pick a step and click <b>+ Add step</b> on a "
                + "branch. Double-click or right-click a step in <b>Your filter</b> "
                + "to edit its settings. Calibrated spatial settings are entered in source-image units "
                + "and saved as pixels so later metadata loss does not change filtering. "
                + "Ctrl-click or Shift-click branches, then use "
                + "<b>Merge selected branches</b>. Double-click or right-click a merge "
                + "card to edit how branches combine."
                + "<br><br>"
                + "<b>Start from a preset...</b><br>"
                + "Replaces the current chain with one of the bundled filter presets "
                + "as a starting point."
                + "<br><br>"
                + "<b>Undo</b><br>"
                + "Restores the previous builder state. Ctrl+Z does the same thing."
                + "<br><br>"
                + "<b>Preview to selected point</b><br>"
                + "Runs the chain only up to the step or merge you have selected, so you can "
                + "see intermediate results in the embedded output preview."
                + "<br><br>"
                + "<b>Preview full filter</b><br>"
                + "Runs the entire chain on the sample image and updates the embedded output preview."
                + "<br><br>"
                + "<b>Large view</b><br>"
                + "Opens the source and preview images side by side in a larger window with synced Z sliders."
                + "<br><br>"
                + "<b>Brightness/Contrast</b><br>"
                + "Opens display controls for the source and preview panes. These controls change the "
                + "preview display only; they do not change the macro or image data."
                + "<br><br>"
                + "<b>Grey LUT / User LUT</b><br>"
                + "Temporarily switches both preview panes between grey and the image LUT selected in Fiji."
                + "<br><br>"
                + "<b>Save Macro</b><br>"
                + "Asks where to save the macro, saves the current chain, "
                + "and loads it in the main Macro Builder window."
                + "<br><br>"
                + "<b>Cancel</b><br>"
                + "Closes the builder without saving. You'll be asked to confirm if "
                + "you've made changes."
                + "</body></html>";
        JOptionPane.showMessageDialog(this, msg, "Filter Builder - Help",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void wireButtons() {
        previewSelected.addActionListener(e -> {
            refreshSourcePreview();
            preview(model.toPartialDag());
        });
        previewFinal.addActionListener(e -> {
            refreshSourcePreview();
            preview(model.toDag());
        });
        largePreview.addActionListener(e -> showLargePreview());
        brightnessContrast.addActionListener(e -> showDisplayControls());
        lutToggle.addActionListener(e -> toggleLutMode());
        createVariations.setToolTipText("Generate and compare variants of this visual pipeline");
        createVariations.addActionListener(e -> openVariationsDialog());
        showVariationLog.setToolTipText("Show variation actions from this builder session");
        showVariationLog.addActionListener(e -> variationLog.showViewer(this));
        startFromPreset.addActionListener(e -> startFromPreset());
        help.setToolTipText("What do these buttons do?");
        help.addActionListener(e -> showSandboxHelp());
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("control Z"), "undoBuilderChange");
        getRootPane().getActionMap().put("undoBuilderChange", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                undoLastChange();
            }
        });
        save.addActionListener(e -> {
            refreshSourcePreview();
            DagIR dag = model.toDag();
            String ijm = DagToIjmEmitter.emitReadable(dag);
            try {
                File saved = MacroFileSaver.promptAndSave(this, defaultMacroName(), ijm, dag);
                if (saved == null) return;
                result = new Result(dag, ijm, saved);
            } catch (Exception ex) {
                IJ.showMessage("Filter Builder", "Could not save macro:\n" + ex.getMessage());
                return;
            }
            close();
        });
        cancel.addActionListener(e -> {
            if (!confirmDiscardIfDirty()) return;
            result = Result.cancel();
            close();
        });
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowOpened(java.awt.event.WindowEvent e) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override public void run() {
                        catalog.focusSearch();
                    }
                });
            }
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                if (!confirmDiscardIfDirty()) return;
                result = Result.cancel();
                close();
            }
            @Override public void windowClosed(java.awt.event.WindowEvent e) {
                if (largePreviewDialog != null) {
                    largePreviewDialog.dispose();
                    largePreviewDialog = null;
                }
                if (displayDialog != null) {
                    displayDialog.dispose();
                    displayDialog = null;
                }
                if (previewHandler != null) previewHandler.close(previewImage);
                done.countDown();
                if (loop != null) loop.exit();
            }
        });
    }

    private void wirePreviewSliceSync() {
        sourcePreview.setZSliceChangeListener(new ImagePreviewPanel.ZSliceChangeListener() {
            @Override public void zSliceChanged(ImagePreviewPanel source, int zSlice) {
                syncPreviewSlices(zSlice);
            }
        });
        outputPreview.setZSliceChangeListener(new ImagePreviewPanel.ZSliceChangeListener() {
            @Override public void zSliceChanged(ImagePreviewPanel source, int zSlice) {
                syncPreviewSlices(zSlice);
            }
        });
    }

    private void syncPreviewSlices(int zSlice) {
        if (syncingPreviewSlices) return;
        syncingPreviewSlices = true;
        try {
            sourcePreview.setCurrentZ(zSlice);
            outputPreview.setCurrentZ(zSlice);
            if (largePreviewDialog != null && largePreviewDialog.isDisplayable()) {
                largePreviewDialog.setCurrentZ(zSlice);
            }
        } finally {
            syncingPreviewSlices = false;
        }
    }

    private void showLargePreview() {
        refreshSourcePreview();
        if (largePreviewDialog == null || !largePreviewDialog.isDisplayable()) {
            largePreviewDialog = new LargePreviewDialog(this);
            largePreviewDialog.setSliceListener(new LargePreviewDialog.SliceListener() {
                @Override public void zSliceChanged(int zSlice) {
                    syncPreviewSlices(zSlice);
                }
            });
            largePreviewDialog.setDisplayActionListener(new LargePreviewDialog.DisplayActionListener() {
                @Override public void adjustBrightnessContrastRequested() {
                    showDisplayControls(largePreviewDialog);
                }

                @Override public void lutToggleRequested() {
                    toggleLutMode();
                }
            });
        }
        refreshLargePreviewIfOpen();
        largePreviewDialog.setVisible(true);
        largePreviewDialog.toFront();
    }

    private void refreshLargePreviewIfOpen() {
        if (largePreviewDialog == null || !largePreviewDialog.isDisplayable()) return;
        largePreviewDialog.setImages(currentSourceDisplay(), previewImage, sourcePreview.getCurrentZ());
        largePreviewDialog.setDisplaySettings(sourceDisplaySettings, outputDisplaySettings);
        largePreviewDialog.setLutToggleText(lutToggle.getText(), lutToggle.getToolTipText());
    }

    private void showDisplayControls() {
        showDisplayControls(this);
    }

    private void showDisplayControls(Window owner) {
        refreshSourcePreview();
        if (displayDialog == null || !displayDialog.isDisplayable()
                || displayDialog.getOwner() != owner) {
            if (displayDialog != null) {
                displayDialog.dispose();
            }
            displayDialog = new PreviewDisplayDialog(owner);
            displayDialog.setListener(new PreviewDisplayDialog.Listener() {
                @Override public void sourceRangeChanged(double min, double max, boolean adjusting) {
                    sourceDisplaySettings = sourceDisplaySettings.withDisplayRange(min, max);
                    applyDisplaySettings();
                }

                @Override public void outputRangeChanged(double min, double max, boolean adjusting) {
                    outputDisplaySettings = outputDisplaySettings.withDisplayRange(min, max);
                    applyDisplaySettings();
                }
            });
        }
        refreshDisplayControlsIfOpen();
        displayDialog.showNear(owner);
    }

    private void refreshDisplayControlsIfOpen() {
        if (displayDialog == null || !displayDialog.isDisplayable()) return;
        displayDialog.setImages(currentSourceDisplay(), previewImage,
                sourceDisplaySettings, outputDisplaySettings);
    }

    private void toggleLutMode() {
        lutMode = lutMode == PreviewDisplaySettings.LutMode.GREY
                ? PreviewDisplaySettings.LutMode.USER
                : PreviewDisplaySettings.LutMode.GREY;
        sourceDisplaySettings = sourceDisplaySettings.withLutMode(lutMode);
        outputDisplaySettings = outputDisplaySettings.withLutMode(lutMode);
        applyDisplaySettings();
    }

    private void applyDisplaySettings() {
        sourcePreview.setDisplaySettings(sourceDisplaySettings);
        outputPreview.setDisplaySettings(outputDisplaySettings);
        updateLutToggleButton();
        refreshLargePreviewIfOpen();
        refreshDisplayButtons();
    }

    private void updateLutToggleButton() {
        boolean greySelected = lutMode == PreviewDisplaySettings.LutMode.GREY;
        lutToggle.setText(greySelected ? "User LUT" : "Grey LUT");
        lutToggle.setToolTipText(greySelected
                ? "Show the source and preview with the image LUT selected in Fiji."
                : "Show the source and preview in grey.");
        if (largePreviewDialog != null && largePreviewDialog.isDisplayable()) {
            largePreviewDialog.setLutToggleText(lutToggle.getText(), lutToggle.getToolTipText());
        }
    }

    private void openVariationsDialog() {
        refreshSourcePreview();
        ImagePlus source = currentSourceDisplay();
        if (source == null) {
            IJ.showMessage("Variations", "No source image is available for variation generation.");
            return;
        }
        final DagIR variationDag = model.toDag();
        if (Recorder.record) {
            Recorder.recordString("// macro-builder variation: opened chooser\n");
        }
        VariationChooserDialog dialog = new VariationChooserDialog(
                this,
                variationDag,
                source,
                results -> onVariationResults(sourceForVariationGrid(source, variationDag), results));
        dialog.setVisible(true);
    }

    private void onVariationResults(ImagePlus rawSource, List<VariantResult> results) {
        if (results == null || results.isEmpty()) {
            status.setText("No variations were generated.");
            return;
        }
        variationLog.recordGenerate(results);
        if (Recorder.record) {
            Recorder.recordString("// macro-builder variation: generated " + results.size() + " variant(s)\n");
        }
        VariationActionsBinder binder = new VariationActionsBinder(
                model,
                canvas,
                this,
                variationLog,
                stateDir,
                sourceTitle(rawSource),
                text -> {
                    recordModelChange();
                    status.setText(text == null ? " " : text);
                    refreshEditors();
                });
        VariantGridFrame frame = new VariantGridFrame(
                "Variations: " + sourceTitle(rawSource),
                rawSource,
                results);
        frame.setSessionLog(variationLog);
        frame.setActionListener(binder);
        frame.attachExporters(new MontageExporter(frame), new IjmClipboardExporter(frame));
        frame.setLocationRelativeTo(this);
        frame.setVisible(true);
        status.setText("Generated " + results.size() + " variation(s).");
    }

    private static String sourceTitle(ImagePlus source) {
        if (source == null || source.getTitle() == null || source.getTitle().trim().isEmpty()) {
            return "source";
        }
        return source.getTitle();
    }

    private String defaultMacroName() {
        ImagePlus source = currentSourceDisplay();
        String title = sourceTitle(source);
        return "source".equals(title) ? "Macro_Builder_Filter" : title + "_Macro";
    }

    private boolean confirmDiscardIfDirty() {
        String currentIjm = DagToIjmEmitter.emit(model.toDag());
        if (currentIjm.equals(initialIjm)) return true;
        int delta = Math.abs(countNodes(model) - initialNodeCount);
        String message = delta == 1
                ? "Discard your changes? You've modified 1 step."
                : "Discard your changes? You've modified " + delta + " step(s).";
        Object[] options = new Object[] { "Keep editing", "Discard changes" };
        int choice = JOptionPane.showOptionDialog(this,
                message,
                "Discard changes?",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[0]);
        return choice == JOptionPane.NO_OPTION;
    }

    private void refreshSourcePreview() {
        ImagePlus display = currentSourceDisplay();
        int before = model.channelCount;
        model.setChannelCount(channelCount(display));
        sourceDisplaySettings = normalizeDisplaySettings(display, sourceDisplaySettings);
        sourcePreview.setDisplaySettings(sourceDisplaySettings);
        sourcePreview.setImage(display);
        copySourceLutToPreviewOutput();
        outputPreview.refresh();
        syncPreviewSlices(sourcePreview.getCurrentZ());
        refreshLargePreviewIfOpen();
        refreshDisplayControlsIfOpen();
        refreshDisplayButtons();
        refreshPrimaryChannelSelector();
        if (before != model.channelCount) canvas.rebuild();
    }

    private PreviewDisplaySettings normalizeDisplaySettings(ImagePlus image,
                                                            PreviewDisplaySettings settings) {
        PreviewDisplaySettings base = settings == null
                ? PreviewDisplaySettings.defaultFor(lutMode)
                : settings.withLutMode(lutMode);
        if (base.hasDisplayRange() && displayRangeFits(image, base)) return base;
        double[] range = defaultDisplayRange(image);
        if (Double.isFinite(range[0]) && Double.isFinite(range[1]) && range[1] > range[0]) {
            return PreviewDisplaySettings.of(range[0], range[1], lutMode);
        }
        return PreviewDisplaySettings.defaultFor(lutMode);
    }

    private static boolean displayRangeFits(ImagePlus image, PreviewDisplaySettings settings) {
        if (image == null || settings == null || !settings.hasDisplayRange()) return false;
        HistogramPanel.Histogram histogram =
                HistogramPanel.calculateHistogram(image, HistogramPanel.DEFAULT_BIN_COUNT);
        if (histogram.isEmpty()) return true;
        return settings.getDisplayMin() >= histogram.getMinimum()
                && settings.getDisplayMax() <= histogram.getMaximum();
    }

    private static double[] defaultDisplayRange(ImagePlus image) {
        if (image == null) return new double[]{Double.NaN, Double.NaN};
        try {
            double min = image.getDisplayRangeMin();
            double max = image.getDisplayRangeMax();
            if (Double.isFinite(min) && Double.isFinite(max) && max > min) {
                return new double[]{min, max};
            }
        } catch (RuntimeException ignored) {
        }
        HistogramPanel.Histogram histogram =
                HistogramPanel.calculateHistogram(image, HistogramPanel.DEFAULT_BIN_COUNT);
        if (!histogram.isEmpty() && histogram.getMaximum() > histogram.getMinimum()) {
            return new double[]{histogram.getMinimum(), histogram.getMaximum()};
        }
        return new double[]{Double.NaN, Double.NaN};
    }

    private ImagePlus currentSourceDisplay() {
        return previewHandler == null ? null : previewHandler.getSourceForDisplay();
    }

    private void copySourceLutToPreviewOutput() {
        if (previewImage == null) return;
        LUT lut = selectedSourceLut();
        if (lut == null) return;
        try {
            previewImage.setLut((LUT) lut.clone());
        } catch (RuntimeException ignored) {
        }
    }

    private LUT selectedSourceLut() {
        ImagePlus source = currentSourceDisplay();
        if (source == null) return null;
        try {
            LUT[] luts = source.getLuts();
            int channel = Math.max(1, Math.min(model.primaryChannel, Math.max(1, source.getNChannels())));
            if (luts != null && channel - 1 < luts.length && luts[channel - 1] != null) {
                return luts[channel - 1];
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    static ImagePlus sourceForVariationGrid(ImagePlus source, DagIR dag) {
        if (source == null) return null;
        int maxChannels = Math.max(1, source.getNChannels());
        int channel = dag == null ? 1 : Math.max(1, Math.min(dag.primaryChannel, maxChannels));
        int z = Math.max(1, source.getNSlices());
        int t = Math.max(1, Math.min(source.getT(), Math.max(1, source.getNFrames())));
        String title = sourceTitle(source) + "-C" + channel + "-T" + t;
        try {
            ImagePlus oneTimepoint = new Duplicator().run(source, channel, channel, 1, z, t, t);
            if (oneTimepoint != null) {
                oneTimepoint.setTitle(title);
                return oneTimepoint;
            }
        } catch (RuntimeException ex) {
        }
        try {
            return FilterExecutor.duplicateChannel(source, channel, title);
        } catch (RuntimeException ex) {
            return source;
        }
    }

    private static int channelCount(ImagePlus image) {
        return image == null ? 1 : Math.max(1, image.getNChannels());
    }

    private void refreshPrimaryChannelSelector() {
        if (primaryChannelBar == null) return;
        updatingPrimaryChannelSelector = true;
        try {
            primaryChannelSelector.removeAllItems();
            for (int i = 1; i <= model.channelCount; i++) {
                primaryChannelSelector.addItem("C" + i);
            }
            primaryChannelSelector.setSelectedIndex(Math.max(0, model.primaryChannel - 1));
        } finally {
            updatingPrimaryChannelSelector = false;
        }
        primaryChannelBar.setVisible(model.channelCount > 1);
    }

    private static int countNodes(SandboxModel model) {
        int total = model.combiners.size();
        for (int i = 0; i < model.lines.size(); i++) {
            total += model.lines.get(i).nodes.size();
        }
        return total;
    }

    private void preview(final DagIR dag) {
        if (previewHandler == null) {
            IJ.showMessage("Sandbox Preview", "No preview image is available.");
            return;
        }
        setBusy(true, "Running preview...");
        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                ImagePlus source = null;
                ImagePlus rendered = null;
                try {
                    source = previewHandler.createSource();
                    if (source == null) throw new IllegalStateException("No preview source image is available.");
                    if ("legacy".equals(dag.executionTier)) {
                        rendered = FilterExecutor.runLegacyDagSandboxed(source, dag);
                    } else {
                        rendered = FilterExecutor.runDagThreadSafe(source, dag);
                    }
                    final ImagePlus previewResult = rendered;
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override public void run() {
                            try {
                                previewImage = previewHandler.showPreview(previewResult, previewImage);
                                copySourceLutToPreviewOutput();
                                outputDisplaySettings = normalizeDisplaySettings(previewImage, outputDisplaySettings);
                                outputPreview.setDisplaySettings(outputDisplaySettings);
                                outputPreview.setImage(previewImage);
                                syncPreviewSlices(sourcePreview.getCurrentZ());
                                refreshDisplayControlsIfOpen();
                                refreshDisplayButtons();
                                refreshLargePreviewIfOpen();
                                setBusy(false, "Preview complete.");
                            } catch (Exception ex) {
                                previewHandler.close(previewResult);
                                IJ.showMessage("Sandbox Preview", "Preview display failed:\n" + ex.getMessage());
                                setBusy(false, "Preview failed.");
                            }
                        }
                    });
                    rendered = null;
                } catch (final Exception ex) {
                    final String message = ex.getMessage();
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override public void run() {
                            IJ.showMessage("Sandbox Preview", "Preview failed:\n" + message);
                            setBusy(false, "Preview failed.");
                        }
                    });
                } finally {
                    if (previewHandler != null) {
                        previewHandler.close(source);
                        previewHandler.close(rendered);
                    }
                }
            }
        }, "sandbox-dag-preview");
        worker.setDaemon(true);
        worker.start();
    }

    private void setBusy(boolean busy, String message) {
        this.busy = busy;
        save.setEnabled(!busy);
        createVariations.setEnabled(!busy);
        showVariationLog.setEnabled(!busy);
        startFromPreset.setEnabled(!busy);
        refreshUndoButton();
        refreshPreviewButtons();
        refreshDisplayButtons();
        status.setText(message == null ? " " : message);
    }

    private void refreshPreviewButtons() {
        boolean selectedPreviewable = model.selected instanceof SandboxModel.Node
                || model.selected instanceof SandboxModel.CombinerNode;
        previewSelected.setEnabled(!busy && selectedPreviewable);
        previewFinal.setEnabled(!busy && (hasAnyNode(model) || !model.combiners.isEmpty()));
    }

    private void refreshDisplayButtons() {
        boolean hasAnyImage = currentSourceDisplay() != null || previewImage != null;
        largePreview.setEnabled(!busy && hasAnyImage);
        brightnessContrast.setEnabled(!busy && hasAnyImage);
        lutToggle.setEnabled(!busy && hasAnyImage);
    }

    private static boolean hasAnyNode(SandboxModel model) {
        for (int i = 0; i < model.lines.size(); i++) {
            if (!model.lines.get(i).nodes.isEmpty()) return true;
        }
        return false;
    }

    private void refreshEditors() {
        legacyBanner.setVisible(model.hasLegacyNode());
        refreshUndoButton();
        refreshPreviewButtons();
        refreshDisplayButtons();
    }

    private void recordModelChange() {
        if (applyingUndo) {
            refreshUndoButton();
            return;
        }
        undoHistory.record(model.toDag());
        refreshUndoButton();
    }

    private void undoLastChange() {
        if (busy || !undoHistory.canUndo()) return;
        DagIR previous = undoHistory.undo();
        if (previous == null) return;
        applyingUndo = true;
        try {
            model.replaceWith(previous);
            refreshPrimaryChannelSelector();
            canvas.rebuild();
            status.setText("Undid last builder change.");
        } finally {
            applyingUndo = false;
        }
        refreshEditors();
    }

    private void refreshUndoButton() {
        canvas.refreshUndoControl();
    }

    private void editNodeInline(SandboxModel.Node node) {
        if (node == null) return;
        model.selectNode(node);
        if (StepEditorDialog.show(this, node, currentSourceCalibration())) {
            recordModelChange();
            canvas.rebuild();
            refreshEditors();
        } else {
            refreshEditors();
        }
    }

    private void previewToNodeInline(SandboxModel.Node node) {
        if (node == null) return;
        model.selectNode(node);
        canvas.rebuild();
        refreshEditors();
        preview(model.toPartialDag());
    }

    private void editCombinerInline(SandboxModel.CombinerNode combiner) {
        if (combiner == null) return;
        model.selectCombiner(combiner);
        if (MergeEditorDialog.show(this, model, combiner)) {
            recordModelChange();
            canvas.rebuild();
            refreshEditors();
        } else {
            refreshEditors();
        }
    }

    private void previewToCombinerInline(SandboxModel.CombinerNode combiner) {
        if (combiner == null) return;
        model.selectCombiner(combiner);
        canvas.rebuild();
        refreshEditors();
        preview(model.toPartialDag());
    }

    private SandboxModel.Line resolveTargetLine() {
        Object sel = model.selected;
        if (sel instanceof SandboxModel.Node) {
            SandboxModel.Node node = (SandboxModel.Node) sel;
            for (int i = 0; i < model.lines.size(); i++) {
                SandboxModel.Line line = model.lines.get(i);
                if (line.nodes.contains(node)) return line;
            }
        }
        if (sel instanceof SandboxModel.Line) return (SandboxModel.Line) sel;
        if (!model.lines.isEmpty()) {
            SandboxModel.Line first = model.lines.get(0);
            model.selectLine(first, false, false);
            return first;
        }
        return null;
    }

    private void startFromPreset() {
        if (!confirmDiscardIfDirty()) return;
        String[] presets = NamedFilterLoader.FILTER_NAMES;
        String chosen = (String) JOptionPane.showInputDialog(this,
                "Choose a preset to start from:",
                "Start from a preset",
                JOptionPane.PLAIN_MESSAGE,
                null,
                presets,
                presets.length > 0 ? presets[0] : null);
        if (chosen == null) return;
        String content = NamedFilterLoader.loadFilterContent(chosen);
        if (content == null) {
            IJ.showMessage("Start from a preset", "Could not load preset: " + chosen);
            return;
        }
        DagIR dag = IjmToDagLoader.load(content);
        SandboxModel fresh = SandboxModel.fromDag(dag);
        fresh.setChannelCount(model.channelCount);
        model.lines.clear();
        model.lines.addAll(fresh.lines);
        model.combiners.clear();
        model.combiners.addAll(fresh.combiners);
        model.primaryChannel = fresh.primaryChannel;
        model.setChannelCount(fresh.channelCount);
        if (model.lines.isEmpty()) {
            model.selected = null;
            model.clearLineSelection();
        } else {
            model.selectLine(model.lines.get(0), false, false);
        }
        refreshPrimaryChannelSelector();
        canvas.rebuild();
        recordModelChange();
        refreshEditors();
        status.setText("Loaded preset: " + chosen);
    }

    private boolean addCatalogNode(SandboxModel.Line line, FilterCatalog.Entry entry) {
        if (line == null || entry == null || entry.stub) {
            if (entry == null) {
                status.setText("Pick a step from 'Available steps' first.");
            }
            return false;
        }
        if (!entry.legacy) {
            String args = ArgsEditorModel.storageArgsForDisplayDefaults(entry.defaultArgs, currentUnitContext());
            editNewNodeParameters(model.addNode(line, entry, args));
            return true;
        }
        if (previewHandler == null) {
            IJ.showMessage("Fiji Command", "No preview image is available for Fiji's parameter dialog.");
            return false;
        }
        ImagePlus source = null;
        try {
            source = previewHandler.createSource();
            RecorderParameterProbe.ProbeResult probe =
                    RecorderParameterProbe.probe(source, entry.commandName);
            if (probe.userCancelled) {
                if (probe.errorMessage.length() > 0) {
                    IJ.showMessage("Fiji Command", "Command was not added:\n" + probe.errorMessage);
                }
                return false;
            }
            editNewNodeParameters(model.addNode(line, entry, probe.optionsString));
            return true;
        } catch (Exception ex) {
            IJ.showMessage("Fiji Command", "Command was not added:\n" + ex.getMessage());
            return false;
        } finally {
            if (previewHandler != null) previewHandler.close(source);
        }
    }

    private void editNewNodeParameters(SandboxModel.Node node) {
        if (node == null) return;
        model.selectNode(node);
        StepEditorDialog.show(this, node, currentSourceCalibration());
        refreshEditors();
    }

    private Calibration currentSourceCalibration() {
        ImagePlus display = currentSourceDisplay();
        if (display == null || display.getCalibration() == null) return null;
        return display.getCalibration().copy();
    }

    private ArgsEditorModel.UnitContext currentUnitContext() {
        return ArgsEditorModel.UnitContext.fromCalibration(currentSourceCalibration());
    }

    private void close() {
        dispose();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private void sizeNearDesktop() {
        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        Dimension minimum = getMinimumSize();
        int targetWidth = (int) Math.round(bounds.width * INITIAL_DESKTOP_FRACTION);
        int targetHeight = (int) Math.round(bounds.height * INITIAL_DESKTOP_FRACTION);
        int width = Math.min(bounds.width, Math.max(minimum.width, targetWidth));
        int height = Math.min(bounds.height, Math.max(minimum.height, targetHeight));
        int x = bounds.x + Math.max(0, (bounds.width - width) / 2);
        int y = bounds.y + Math.max(0, (bounds.height - height) / 2);
        setBounds(x, y, width, height);
    }

    private void applyInitialSplitLocations() {
        if (mainSplit != null) {
            mainSplit.setDividerLocation(PREVIEW_COLUMN_FRACTION);
        }
        if (centerRightSplit != null) {
            centerRightSplit.setDividerLocation(centerRightLeftFraction());
        }
    }

    private static double centerRightLeftFraction() {
        double centerRightFraction = 1.0 - PREVIEW_COLUMN_FRACTION;
        if (centerRightFraction <= 0.0) return 0.85;
        double availableStepsWithinCenterRight = AVAILABLE_STEPS_FRACTION / centerRightFraction;
        if (availableStepsWithinCenterRight < 0.05) availableStepsWithinCenterRight = 0.05;
        if (availableStepsWithinCenterRight > 0.40) availableStepsWithinCenterRight = 0.40;
        return 1.0 - availableStepsWithinCenterRight;
    }
}
