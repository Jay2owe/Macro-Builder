package macro.builder.ui;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Roi;
import ij.io.FileInfo;
import ij.plugin.Duplicator;
import ij.plugin.frame.RoiManager;
import macro.builder.Macro_Builder;
import macro.builder.analysis.BatchShootoutResult;
import macro.builder.analysis.BatchShootoutRunner;
import macro.builder.analysis.ConsensusMaskBuilder;
import macro.builder.analysis.GroundTruthLoader;
import macro.builder.analysis.GroundTruthReference;
import macro.builder.analysis.MethodsParagraphWriter;
import macro.builder.analysis.ObjectCounter;
import macro.builder.analysis.ShootoutContext;
import macro.builder.analysis.ShootoutRun;
import macro.builder.analysis.ShootoutResult;
import macro.builder.analysis.ShootoutSettings;
import macro.builder.analysis.TestCountsManifest;
import macro.builder.analysis.ThresholdShootoutRunner;
import macro.builder.image.FilterExecutor;
import macro.builder.image.dag.DagIR;
import macro.builder.image.dag.DagToIjmEmitter;
import macro.builder.macro.MacroApplier;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public final class ThresholdShootoutDialog {

    private static final String COUNT_2D = "2D particles";
    private static final String COUNT_3D = "3D stack objects";
    private static final String MODE_AUTO = "Auto threshold shootout";
    private static final String MODE_FIXED = "Fixed numeric threshold";
    private static final String MODE_AUTO_AND_FIXED = "Auto methods + fixed thresholds";
    private static final String MODE_AUTO_GRID = "Auto grid (recommended)";
    private static final String F1_TOOLTIP =
            "F1 uses greedy IoU matching from highest overlap down, with each reference and detection claimed once.";
    private static final String SEPARATION_TOOLTIP =
            "How cleanly this threshold splits the bright and dim parts of the image. 0 means total overlap; 1 means perfectly separated.";
    private static final String DISTINCTNESS_TOOLTIP =
            "How different the two groups look as distributions. 0 means identical; 1 means as distinct as possible.";
    private static final String FRAGILITY_TOOLTIP =
            "How much the count changes if the threshold or image brightness moves slightly. Lower is steadier.";
    private static final String AGREEMENT_TOOLTIP =
            "How much this method overlaps with the majority of the other methods. Lower means this method picked different objects.";

    private final ImagePlus source;
    private String macro;
    private DagIR dag;
    private final File macroFile;
    private final int primaryChannel;
    private final JDialog dialog;
    private final SettingsListener settingsListener;
    private final MacroEditHandler macroEditHandler;

    private final JComboBox<String> countingMode = new JComboBox<String>(new String[]{COUNT_2D, COUNT_3D});
    private final JComboBox<String> thresholdMode = new JComboBox<String>(
            new String[]{MODE_AUTO_GRID, MODE_AUTO, MODE_FIXED, MODE_AUTO_AND_FIXED});
    private final JButton loadReferenceButton = new JButton("Load reference...");
    private final JButton clearReferenceButton = new JButton("Clear");
    private final JLabel referenceLabel = new JLabel("no reference");
    private final JCheckBox accessiblePalette = new JCheckBox("Colour-blind-safe preview colours");
    private final JCheckBox showQualityColumns = new JCheckBox("Show quality columns");
    private final JCheckBox runFragilityChecks = new JCheckBox("Run fragility checks", true);
    private final JSpinner gridSteps = new JSpinner(new SpinnerNumberModel(
            ShootoutSettings.DEFAULT_GRID_STEPS,
            ShootoutSettings.MIN_GRID_STEPS,
            ShootoutSettings.MAX_GRID_STEPS,
            1));
    private final JLabel gridWarning = new JLabel("Below 6 grid steps, recommendations are less reliable.");
    private final JTextField autoMethods = new JTextField(join(ShootoutSettings.defaultAutoMethods()), 32);
    private final JTextField fixedThresholds = new JTextField("", 18);
    private final JTextField minSize = new JTextField("0", 8);
    private final JTextField maxSize = new JTextField("Infinity", 8);
    private final JCheckBox brightObjects = new JCheckBox("Bright objects on dark background", true);
    private final JLabel rangeLabel = new JLabel("Macro output range: not run yet.");
    private final JLabel statusLabel = new JLabel(" ");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final ChartPanel chartPanel = new ChartPanel();
    private final ResultTableModel tableModel = new ResultTableModel();
    private final JTable table = new JTable(tableModel);
    private final JButton runButton = new JButton("Run");
    private final JButton previewButton = new JButton("Open mask preview");
    private final JButton scrubButton = new JButton("Scrub...");
    private final JButton applyToMacroButton = new JButton("Apply to macro");
    private final JButton copyMethodsButton = new JButton("Copy methods paragraph");
    private final JButton consensusButton = new JButton("Show consensus mask");
    private final JButton exportButton = new JButton("Export CSV...");
    private final JButton copyRecommendedButton = new JButton("Copy recommended value");
    private final JButton batchButton = new JButton("Run batch...");
    private final JButton cancelBatchButton = new JButton("Cancel batch");

    private List<ShootoutResult> results = Collections.emptyList();
    private GroundTruthReference groundTruthReference;
    private ShootoutRun activeShootoutRun;
    private ShootoutSettings activeSettings;
    private ImagePlus activeMaskPreview;
    private ScrubPane scrubPane;
    private ImagePlus activeConsensusMask;
    private ImagePlus activeConsensusPreview;
    private SwingWorker<ShootoutUiResult, Void> worker;
    private SwingWorker<ShootoutResult, Void> pinWorker;
    private SwingWorker<BatchRunResult, Void> batchWorker;
    private SwingWorker<GroundTruthReference, Void> referenceWorker;
    private SwingWorker<File, Void> sidecarWorker;
    private volatile boolean batchCancelRequested;
    private boolean closed;
    private String agreementStatusMessage;
    private File exportedCsvFile;
    private File sidecarFile;
    // TODO: Replay from .testcounts.json in a later stage.
    private File groundTruthFile;
    private File referenceFileInFlight;

    private ThresholdShootoutDialog(
            Window owner,
            ImagePlus source,
            String macro,
            DagIR dag,
            File macroFile,
            int primaryChannel,
            SettingsListener settingsListener,
            MacroEditHandler macroEditHandler) {
        this.source = source;
        this.macro = macro == null ? "" : macro;
        this.dag = dag;
        this.macroFile = macroFile;
        this.primaryChannel = Math.max(1, primaryChannel);
        this.settingsListener = settingsListener;
        this.macroEditHandler = macroEditHandler;
        this.dialog = new JDialog(owner, "Test Counts", Dialog.ModalityType.MODELESS);
        this.dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        this.dialog.setLayout(new BorderLayout(8, 8));
        buildUi();
    }

    public static void show(Window owner, ImagePlus source, String macro) {
        if (GraphicsEnvironment.isHeadless()) {
            IJ.log("Test Counts needs the Fiji desktop UI.");
            return;
        }
        new ThresholdShootoutDialog(owner, source, macro, null, null, 1, null, null).open();
    }

    public static void show(Window owner, ImagePlus source, String macro, SettingsListener settingsListener) {
        show(owner, source, macro, 1, settingsListener);
    }

    public static void show(
            Window owner,
            ImagePlus source,
            String macro,
            int primaryChannel,
            SettingsListener settingsListener) {
        if (GraphicsEnvironment.isHeadless()) {
            IJ.log("Test Counts needs the Fiji desktop UI.");
            return;
        }
        new ThresholdShootoutDialog(owner, source, macro, null, null,
                primaryChannel, settingsListener, null).open();
    }

    public static void show(
            Window owner,
            ImagePlus source,
            String macro,
            DagIR dag,
            File macroFile,
            int primaryChannel,
            SettingsListener settingsListener,
            MacroEditHandler macroEditHandler) {
        if (GraphicsEnvironment.isHeadless()) {
            IJ.log("Test Counts needs the Fiji desktop UI.");
            return;
        }
        new ThresholdShootoutDialog(owner, source, macro, dag, macroFile,
                primaryChannel, settingsListener, macroEditHandler).open();
    }

    private void open() {
        dialog.pack();
        Dimension preferred = dialog.getPreferredSize();
        dialog.setSize(Math.max(860, preferred.width), Math.max(560, preferred.height));
        dialog.setLocationRelativeTo(dialog.getOwner());
        dialog.setVisible(true);
        offerRoiManagerReference();
        updateControlState();
    }

    private void buildUi() {
        JPanel north = new JPanel(new BorderLayout(0, 6));
        north.setBorder(BorderFactory.createEmptyBorder(10, 12, 0, 12));
        north.add(buildReferencePanel(), BorderLayout.NORTH);

        JPanel settings = new JPanel(new GridBagLayout());
        settings.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 0, 0, 0),
                BorderFactory.createTitledBorder("Settings")));

        int row = 0;
        addSettingRow(settings, row++, "Counting mode:", countingMode);
        addThresholdModeRow(settings, row++);
        addSettingRow(settings, row++, "Auto methods:", autoMethods);
        addSettingRow(settings, row++, "Fixed thresholds:", fixedThresholds);
        addHelpRow(settings, row++, ShootoutSettings.FIXED_THRESHOLD_HELP);
        addSizeRow(settings, row++);
        addCheckboxRow(settings, row++, brightObjects);
        addCheckboxRow(settings, row++, showQualityColumns);
        addCheckboxRow(settings, row++, runFragilityChecks);
        addRangeRow(settings, row);

        thresholdMode.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                updateControlState();
            }
        });
        gridSteps.addChangeListener(new ChangeListener() {
            @Override public void stateChanged(ChangeEvent e) {
                updateControlState();
            }
        });
        showQualityColumns.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                setTableResults(results, groundTruthReference != null);
            }
        });
        runFragilityChecks.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                setTableResults(results, groundTruthReference != null);
            }
        });

        north.add(settings, BorderLayout.CENTER);
        dialog.add(north, BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) updateControlState();
            }
        });
        configureTableColumns();
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 12, 0, 12),
                BorderFactory.createTitledBorder("Results")));
        JPanel center = new JPanel(new BorderLayout(0, 0));
        center.add(chartPanel, BorderLayout.NORTH);
        center.add(scroll, BorderLayout.CENTER);
        dialog.add(center, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(BorderFactory.createEmptyBorder(0, 12, 10, 12));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        progressBar.setStringPainted(true);
        progressBar.setString("Idle");
        progressBar.setValue(0);
        JPanel statusPanel = new JPanel(new BorderLayout(0, 3));
        statusPanel.add(statusLabel, BorderLayout.NORTH);
        statusPanel.add(progressBar, BorderLayout.SOUTH);
        footer.add(statusPanel, BorderLayout.NORTH);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.add(previewButton);
        if (!GraphicsEnvironment.isHeadless()) {
            left.add(scrubButton);
        }
        left.add(applyToMacroButton);
        left.add(copyMethodsButton);
        left.add(consensusButton);
        left.add(exportButton);
        left.add(copyRecommendedButton);
        left.add(batchButton);
        left.add(cancelBatchButton);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton closeButton = new JButton("Close");
        runButton.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                runShootout();
            }
        });
        previewButton.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                openMaskPreview();
            }
        });
        scrubButton.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                openScrubPane();
            }
        });
        applyToMacroButton.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                applySelectedToMacro();
            }
        });
        copyMethodsButton.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                copyMethodsParagraph();
            }
        });
        consensusButton.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                openConsensusMask();
            }
        });
        exportButton.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                exportCsv();
            }
        });
        copyRecommendedButton.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                copyRecommendedValue();
            }
        });
        batchButton.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                runBatchShootout();
            }
        });
        cancelBatchButton.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                cancelBatchShootout();
            }
        });
        closeButton.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });
        right.add(closeButton);
        right.add(runButton);

        footer.add(left, BorderLayout.WEST);
        footer.add(right, BorderLayout.EAST);
        dialog.add(footer, BorderLayout.SOUTH);

        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                closed = true;
                batchCancelRequested = true;
                if (pinWorker != null && !pinWorker.isDone()) {
                    pinWorker.cancel(true);
                }
                closeScrubPane();
                closeImageQuietly(activeMaskPreview);
                activeMaskPreview = null;
                closeImageQuietly(activeConsensusPreview);
                activeConsensusPreview = null;
                closeImageQuietly(activeConsensusMask);
                activeConsensusMask = null;
                closeResultImages(results);
                closeShootoutRun(activeShootoutRun);
                activeShootoutRun = null;
                activeSettings = null;
                if (sidecarWorker != null && !sidecarWorker.isDone()) {
                    sidecarWorker.cancel(true);
                }
            }
        });
    }

    private JPanel buildReferencePanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        panel.setBorder(BorderFactory.createTitledBorder("Reference"));
        loadReferenceButton.setToolTipText("Accepted formats: RoiSet.zip, Cell Counter XML, x,y CSV, label-image TIFF.");
        loadReferenceButton.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                loadReferenceFromFile();
            }
        });
        clearReferenceButton.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                setGroundTruthReference(null);
                statusLabel.setText("Reference cleared.");
            }
        });
        accessiblePalette.setOpaque(false);
        panel.add(loadReferenceButton);
        panel.add(referenceLabel);
        panel.add(clearReferenceButton);
        panel.add(accessiblePalette);
        return panel;
    }

    private static void addSettingRow(JPanel panel, int row, String label, java.awt.Component component) {
        GridBagConstraints labelConstraints = baseConstraints(row);
        labelConstraints.gridx = 0;
        labelConstraints.weightx = 0.0;
        JLabel jLabel = new JLabel(label);
        jLabel.setFont(jLabel.getFont().deriveFont(Font.PLAIN, 12f));
        panel.add(jLabel, labelConstraints);

        GridBagConstraints fieldConstraints = baseConstraints(row);
        fieldConstraints.gridx = 1;
        fieldConstraints.weightx = 1.0;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(component, fieldConstraints);
    }

    private void addThresholdModeRow(JPanel panel, int row) {
        JPanel fields = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        fields.add(thresholdMode);
        fields.add(new JLabel("Grid steps:"));
        fields.add(gridSteps);
        gridWarning.setFont(gridWarning.getFont().deriveFont(Font.PLAIN, 11f));
        gridWarning.setVisible(false);
        fields.add(gridWarning);
        addSettingRow(panel, row, "Threshold mode:", fields);
    }

    private void addSizeRow(JPanel panel, int row) {
        JPanel fields = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        fields.add(new JLabel("Min:"));
        fields.add(minSize);
        fields.add(new JLabel("Max:"));
        fields.add(maxSize);
        addSettingRow(panel, row, "Size filter:", fields);
    }

    private static void addCheckboxRow(JPanel panel, int row, JCheckBox checkbox) {
        checkbox.setOpaque(false);
        GridBagConstraints constraints = baseConstraints(row);
        constraints.gridx = 1;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(checkbox, constraints);
    }

    private void addRangeRow(JPanel panel, int row) {
        rangeLabel.setFont(rangeLabel.getFont().deriveFont(Font.BOLD, 12f));
        GridBagConstraints constraints = baseConstraints(row);
        constraints.gridx = 0;
        constraints.gridwidth = 2;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(rangeLabel, constraints);
    }

    private static void addHelpRow(JPanel panel, int row, String text) {
        JLabel help = new JLabel("<html><body style='width:620px;'>" + escapeHtml(text) + "</body></html>");
        help.setFont(help.getFont().deriveFont(Font.PLAIN, 11f));
        GridBagConstraints constraints = baseConstraints(row);
        constraints.gridx = 1;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(help, constraints);
    }

    private static GridBagConstraints baseConstraints(int row) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = row;
        constraints.insets = new Insets(3, 4, 3, 4);
        constraints.anchor = GridBagConstraints.WEST;
        return constraints;
    }

    private static void configureColumns(TableColumnModel columns) {
        for (int i = 0; i < columns.getColumnCount(); i++) {
            columns.getColumn(i).setPreferredWidth(preferredColumnWidth(columns.getColumn(i).getHeaderValue()));
        }
    }

    private void configureTableColumns() {
        configureColumns(table.getColumnModel());
        if (table.getColumnModel().getColumnCount() > 0) {
            table.getColumnModel().getColumn(0).setCellRenderer(new RecommendedVariantRenderer(tableModel));
        }
        if (table.getColumnModel().getColumnCount() > 0) {
            ScoreRenderer scoreRenderer = new ScoreRenderer();
            JTableHeader header = table.getTableHeader();
            TableCellRenderer defaultRenderer = header.getDefaultRenderer();
            for (int i = 0; i < table.getColumnModel().getColumnCount(); i++) {
                String name = table.getColumnName(i);
                if ("Fragility".equals(name)) {
                    table.getColumnModel().getColumn(i).setCellRenderer(new FragilityBarRenderer());
                } else if (isScoreColumnName(name)) {
                    table.getColumnModel().getColumn(i).setCellRenderer(scoreRenderer);
                }
                String tooltip = tooltipForColumnName(name);
                if (tooltip != null) {
                    table.getColumnModel().getColumn(i).setHeaderRenderer(
                            new TooltipHeaderRenderer(defaultRenderer, tooltip));
                }
            }
        }
        TableRowSorter<ResultTableModel> sorter = new TableRowSorter<ResultTableModel>(tableModel);
        Comparator<Object> scoreComparator = new Comparator<Object>() {
            @Override public int compare(Object a, Object b) {
                double left = sortableScore(a);
                double right = sortableScore(b);
                if (Double.isNaN(left) && Double.isNaN(right)) return 0;
                if (Double.isNaN(left)) return 1;
                if (Double.isNaN(right)) return -1;
                return Double.compare(left, right);
            }
        };
        for (int i = 0; i < tableModel.getColumnCount(); i++) {
            Class<?> columnClass = tableModel.getColumnClass(i);
            if (Number.class.isAssignableFrom(columnClass)
                    || FragilityBarRenderer.Value.class.isAssignableFrom(columnClass)) {
                sorter.setComparator(i, scoreComparator);
            }
        }
        table.setRowSorter(sorter);
    }

    private static int preferredColumnWidth(Object headerValue) {
        String name = headerValue == null ? "" : headerValue.toString();
        if ("Variant".equals(name)) return 150;
        if ("Count mode".equals(name)) return 120;
        if ("Threshold value".equals(name)) return 110;
        if ("Count".equals(name)) return 70;
        if ("Mean size".equals(name)) return 90;
        if ("Coverage %".equals(name)) return 90;
        if ("Range".equals(name)) return 140;
        if ("Status".equals(name)) return 220;
        if ("Fragility".equals(name)) return 130;
        if ("Agreement".equals(name)) return 100;
        return 90;
    }

    private static double sortableScore(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof FragilityBarRenderer.Value) {
            FragilityBarRenderer.Value fragility = (FragilityBarRenderer.Value) value;
            return fragility.hasData() ? fragility.score : Double.NaN;
        }
        return Double.NaN;
    }

    private static boolean isScoreColumnName(String name) {
        return "precision".equals(name)
                || "recall".equals(name)
                || "f1".equals(name)
                || "Separation".equals(name)
                || "Distinctness".equals(name)
                || "Agreement".equals(name);
    }

    private static String tooltipForColumnName(String name) {
        if ("f1".equals(name)) return F1_TOOLTIP;
        if ("Separation".equals(name)) return SEPARATION_TOOLTIP;
        if ("Distinctness".equals(name)) return DISTINCTNESS_TOOLTIP;
        if ("Fragility".equals(name)) return FRAGILITY_TOOLTIP;
        if ("Agreement".equals(name)) return AGREEMENT_TOOLTIP;
        return null;
    }

    private void runShootout() {
        if (isBusy()) return;
        if (source == null || source.getStack() == null) {
            IJ.showMessage("Test Counts", "Open an image or stack first.");
            return;
        }

        final ShootoutSettings settings;
        try {
            settings = buildSettings();
        } catch (IllegalArgumentException ex) {
            IJ.showMessage("Test Counts", cleanMessage(ex));
            return;
        }
        notifySettings(settings);
        activeSettings = settings;

        closeScrubPane();
        closeImageQuietly(activeMaskPreview);
        activeMaskPreview = null;
        closeImageQuietly(activeConsensusPreview);
        activeConsensusPreview = null;
        closeImageQuietly(activeConsensusMask);
        activeConsensusMask = null;
        closeResultImages(results);
        closeShootoutRun(activeShootoutRun);
        activeShootoutRun = null;
        agreementStatusMessage = null;
        results = Collections.emptyList();
        setTableResults(results, settings.groundTruthReference != null);
        chartPanel.hideForRun();
        rangeLabel.setText("Macro output range: running...");
        statusLabel.setText("Running count shootout...");
        setProgressIndeterminate("Running count macro...");
        final int chartWidth = chartPanel.chartWidth();
        final int chartHeight = chartPanel.chartHeight();
        worker = new SwingWorker<ShootoutUiResult, Void>() {
            @Override protected ShootoutUiResult doInBackground() {
                ThresholdShootoutRunner runner = new ThresholdShootoutRunner();
                ShootoutRun run = runner.runWithContext(
                        source,
                        macro,
                        settings,
                        primaryChannel,
                        createMacroProgress("Running count macro"));
                ImagePlus consensus = runner.takeConsensusMask();
                String agreementStatus = runner.takeAgreementStatusMessage();
                ChartImages charts = renderCharts(run, chartWidth, chartHeight);
                return new ShootoutUiResult(run, charts, consensus, agreementStatus);
            }

            @Override protected void done() {
                onShootoutDone(this);
            }
        };
        updateControlState();
        worker.execute();
    }

    private void onShootoutDone(SwingWorker<ShootoutUiResult, Void> finishedWorker) {
        ShootoutUiResult result = null;
        List<ShootoutResult> rows;
        boolean failed = false;
        try {
            result = finishedWorker.get();
            rows = result == null ? Collections.<ShootoutResult>emptyList() : result.rows();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            rows = Collections.emptyList();
            statusLabel.setText("Count shootout interrupted.");
            setProgressValue(0, "Interrupted.");
            failed = true;
        } catch (ExecutionException ex) {
            rows = Collections.emptyList();
            statusLabel.setText("Count shootout failed: " + cleanMessage(ex.getCause()));
            setProgressValue(0, "Failed.");
            failed = true;
        }

        if (finishedWorker == worker) {
            worker = null;
        }

        if (closed || !dialog.isDisplayable()) {
            closeResultImages(rows);
            if (result != null) {
                closeImageQuietly(result.consensusMask);
                closeShootoutRun(result.run);
            }
            return;
        }

        if (result != null) {
            activeShootoutRun = result.run;
            activeConsensusMask = result.consensusMask;
            agreementStatusMessage = result.agreementStatusMessage;
        }
        results = rows == null ? Collections.<ShootoutResult>emptyList() : rows;
        setTableResults(results, activeSettings != null && activeSettings.groundTruthReference != null);
        updateRangeLabel(results);
        if (!results.isEmpty()) {
            table.setRowSelectionInterval(0, 0);
        }
        if (!failed && agreementStatusMessage != null && !agreementStatusMessage.trim().isEmpty()) {
            statusLabel.setText(agreementStatusMessage);
        } else if (!failed
                && selectedThresholdMode() == ShootoutSettings.ThresholdMode.AUTO_GRID
                && hasSuccessfulRows(results)
                && recommendedResult() == null) {
            statusLabel.setText("no stable plateau found");
        } else if (statusLabel.getText() == null || statusLabel.getText().trim().isEmpty()
                || statusLabel.getText().startsWith("Running")) {
            statusLabel.setText(results.size() + " result row(s).");
        }
        if (!failed) {
            setProgressValue(100, "Count shootout complete.");
        }
        updateChartsLater(result, failed);
        updateControlState();
    }

    private ChartImages renderCharts(ShootoutRun run, int width, int height) {
        if (run == null || run.context == null || GraphicsEnvironment.isHeadless()) {
            return ChartImages.hidden();
        }
        try {
            BufferedImage histogram = ChartRenderer.renderHistogram(run.context, run.results, width, height);
            BufferedImage curve = ChartRenderer.renderCurve(run.context, run.results, width, height);
            return new ChartImages(histogram, curve);
        } catch (Throwable ignored) {
            return ChartImages.hidden();
        }
    }

    private void updateChartsLater(final ShootoutUiResult result, final boolean failed) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                if (closed || !dialog.isDisplayable() || failed || result == null || !result.charts.visible) {
                    chartPanel.hideForRun();
                    return;
                }
                chartPanel.setImages(result.charts.histogram, result.charts.curve);
            }
        });
    }

    private void setTableResults(List<ShootoutResult> rows, boolean showScores) {
        tableModel.setResults(
                rows,
                showScores,
                showQualityColumns.isSelected(),
                runFragilityChecks.isSelected(),
                agreementColumnAvailable(rows));
        configureTableColumns();
    }

    private ShootoutSettings buildSettings() {
        ShootoutSettings.CountingMode countMode = COUNT_3D.equals(countingMode.getSelectedItem())
                ? ShootoutSettings.CountingMode.OBJECTS_3D
                : ShootoutSettings.CountingMode.PARTICLES_2D;
        ShootoutSettings.ThresholdMode mode = selectedThresholdMode();
        List<String> methods = parseAutoMethods();
        List<Double> fixedValues = ShootoutSettings.parseFixedThresholds(fixedThresholds.getText());
        if (usesFixed(mode) && fixedValues.isEmpty()) {
            throw new IllegalArgumentException("Enter one or more fixed thresholds, for example 2000,5000.");
        }
        int gridStepCount = ((Number) gridSteps.getValue()).intValue();
        double min = parseSize(minSize.getText(), "Minimum size", false);
        double max = parseSize(maxSize.getText(), "Maximum size", true);
        return new ShootoutSettings(
                countMode,
                mode,
                methods,
                fixedValues,
                gridStepCount,
                min,
                max,
                brightObjects.isSelected())
                .withGroundTruthReference(groundTruthReference)
                .withRunFragilityChecks(runFragilityChecks.isSelected());
    }

    private ShootoutSettings.ThresholdMode selectedThresholdMode() {
        Object selected = thresholdMode.getSelectedItem();
        if (MODE_FIXED.equals(selected)) {
            return ShootoutSettings.ThresholdMode.FIXED_VALUES;
        }
        if (MODE_AUTO_AND_FIXED.equals(selected)) {
            return ShootoutSettings.ThresholdMode.AUTO_AND_FIXED;
        }
        if (MODE_AUTO_GRID.equals(selected)) {
            return ShootoutSettings.ThresholdMode.AUTO_GRID;
        }
        return ShootoutSettings.ThresholdMode.AUTO_METHODS;
    }

    private List<String> parseAutoMethods() {
        String text = autoMethods.getText();
        if (text == null || text.trim().isEmpty()) {
            return ShootoutSettings.defaultAutoMethods();
        }
        String[] parts = text.split(",");
        List<String> methods = new ArrayList<String>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("Auto method names must not be blank.");
            }
            methods.add(trimmed);
        }
        return methods;
    }

    private static double parseSize(String text, String label, boolean allowInfinity) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty() && allowInfinity) {
            return Double.POSITIVE_INFINITY;
        }
        if (trimmed.isEmpty()) {
            return 0.0;
        }
        double value;
        try {
            value = Double.parseDouble(trimmed);
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException(label + " is not a number.");
        }
        if (Double.isNaN(value) || (!allowInfinity && Double.isInfinite(value))) {
            throw new IllegalArgumentException(label + " must be a finite number.");
        }
        if (value < 0.0) {
            throw new IllegalArgumentException(label + " must be zero or greater.");
        }
        return value;
    }

    private void updateRangeLabel(List<ShootoutResult> rows) {
        for (ShootoutResult row : rows) {
            if (isFinite(row.imageMinimum) && isFinite(row.imageMaximum)) {
                rangeLabel.setText("Macro output range: "
                        + formatNumber(row.imageMinimum) + "-" + formatNumber(row.imageMaximum));
                return;
            }
        }
        rangeLabel.setText("Macro output range: unavailable.");
    }

    private void loadReferenceFromFile() {
        if (isBusy()) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Load Reference");
        chooser.addChoosableFileFilter(new FileNameExtensionFilter(
                "Reference files (*.zip, *.xml, *.csv, *.tif, *.tiff)",
                "zip", "xml", "csv", "tif", "tiff"));
        if (chooser.showOpenDialog(dialog) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        final File file = chooser.getSelectedFile();
        referenceFileInFlight = file;
        statusLabel.setText("Loading reference...");
        referenceWorker = new SwingWorker<GroundTruthReference, Void>() {
            @Override protected GroundTruthReference doInBackground() {
                return GroundTruthLoader.load(file);
            }

            @Override protected void done() {
                onReferenceLoaded(this);
            }
        };
        updateControlState();
        referenceWorker.execute();
    }

    private void onReferenceLoaded(SwingWorker<GroundTruthReference, Void> finishedWorker) {
        try {
            setGroundTruthReference(finishedWorker.get(), referenceFileInFlight);
            statusLabel.setText("Reference loaded.");
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            statusLabel.setText("Reference load interrupted.");
        } catch (ExecutionException ex) {
            groundTruthFile = null;
            IJ.showMessage("Test Counts", "Could not load reference:\n" + cleanMessage(ex.getCause()));
        } finally {
            if (finishedWorker == referenceWorker) {
                referenceWorker = null;
            }
            referenceFileInFlight = null;
            updateControlState();
        }
    }

    private void offerRoiManagerReference() {
        if (GraphicsEnvironment.isHeadless() || groundTruthReference != null) {
            return;
        }
        RoiManager manager = RoiManager.getInstance2();
        if (manager == null || manager.getCount() <= 0) {
            return;
        }
        int count = manager.getCount();
        int answer = JOptionPane.showConfirmDialog(
                dialog,
                "Use the " + count + " ROIs in the ROI Manager?",
                "Reference detected",
                JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        Roi[] rois = manager.getRoisAsArray();
        setGroundTruthReference(GroundTruthLoader.fromRois("ROI Manager", rois), null);
        statusLabel.setText("Reference loaded from ROI Manager.");
    }

    private void setGroundTruthReference(GroundTruthReference reference) {
        setGroundTruthReference(reference, null);
    }

    private void setGroundTruthReference(GroundTruthReference reference, File sourceFile) {
        groundTruthReference = reference;
        if (reference == null || reference.isEmpty()) {
            referenceLabel.setText("no reference");
            groundTruthReference = null;
            groundTruthFile = null;
        } else {
            referenceLabel.setText(reference.size() + " objects loaded");
            groundTruthFile = sourceFile;
        }
        if (activeSettings != null) {
            activeSettings = activeSettings.withGroundTruthReference(groundTruthReference);
        }
        setTableResults(results, groundTruthReference != null);
        updateControlState();
    }

    private void openMaskPreview() {
        ShootoutResult result = selectedResult();
        if (result == null) {
            IJ.showMessage("Test Counts", "Select a result row first.");
            return;
        }
        if (!result.isSuccess() || result.maskPreview == null) {
            IJ.showMessage("Test Counts", "The selected row does not have a mask preview.");
            return;
        }
        closeImageQuietly(activeMaskPreview);
        ImagePlus preview;
        if (activeSettings != null
                && activeSettings.groundTruthReference != null
                && result.perObjectStatus != null
                && isFinite(result.f1)) {
            preview = MaskPreviewRenderer.render(
                    source,
                    result.maskPreview,
                    activeSettings.groundTruthReference,
                    result.perObjectStatus,
                    activeSettings,
                    accessiblePalette.isSelected());
        } else {
            preview = new Duplicator().run(result.maskPreview);
        }
        if (preview == null) {
            IJ.showMessage("Test Counts", "Could not duplicate the selected mask preview.");
            return;
        }
        preview.setTitle((result.perObjectStatus == null
                ? "Macro Builder Count Mask - "
                : "Macro Builder Count Agreement - ") + result.variant);
        preview.show();
        if (preview.getWindow() != null) {
            WindowManager.setCurrentWindow(preview.getWindow());
        }
        activeMaskPreview = preview;
    }

    private void openScrubPane() {
        if (!canOpenScrubPane()) {
            IJ.showMessage("Test Counts", "Run Test Counts successfully before scrubbing thresholds.");
            return;
        }
        if (scrubPane != null && scrubPane.isDisplayable()) {
            scrubPane.toFront();
            return;
        }
        scrubPane = new ScrubPane(
                dialog,
                activeShootoutRun.context,
                activeSettings,
                results,
                activeSliceForScrub(),
                activeFrameForScrub(),
                new ScrubPane.PinHandler() {
                    @Override public void pinThreshold(double threshold) {
                        pinScrubbedThreshold(threshold);
                    }
                });
        scrubPane.open();
    }

    private void pinScrubbedThreshold(final double threshold) {
        if (isBusy() || activeShootoutRun == null || activeShootoutRun.context == null || activeSettings == null) {
            return;
        }
        final ShootoutSettings pinnedSettings;
        try {
            pinnedSettings = activeSettings.withAdditionalFixed(threshold);
        } catch (IllegalArgumentException ex) {
            IJ.showMessage("Test Counts", cleanMessage(ex));
            return;
        }
        final ShootoutContext pinContext = activeShootoutRun.context;
        activeSettings = pinnedSettings;
        appendFixedThresholdText(threshold);
        notifySettings(pinnedSettings);
        if (scrubPane != null) {
            scrubPane.setPinBusy(true);
        }
        statusLabel.setText("Pinning threshold " + formatNumber(threshold) + "...");
        setProgressIndeterminate("Pinning threshold...");

        pinWorker = new SwingWorker<ShootoutResult, Void>() {
            @Override protected ShootoutResult doInBackground() {
                return new ThresholdShootoutRunner().runOneVariant(
                        pinContext,
                        pinnedSettings,
                        threshold);
            }

            @Override protected void done() {
                onPinDone(this);
            }
        };
        updateControlState();
        pinWorker.execute();
    }

    private void onPinDone(SwingWorker<ShootoutResult, Void> finishedWorker) {
        ShootoutResult row = null;
        String failure = null;
        try {
            row = finishedWorker.get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            failure = "Pin interrupted.";
        } catch (ExecutionException ex) {
            failure = "Pin failed: " + cleanMessage(ex.getCause());
        }

        if (finishedWorker == pinWorker) {
            pinWorker = null;
        }
        if (scrubPane != null) {
            scrubPane.setPinBusy(false);
        }
        if (closed || !dialog.isDisplayable()) {
            closeImageQuietly(row == null ? null : row.maskPreview);
            return;
        }

        if (failure != null) {
            statusLabel.setText(failure);
            setProgressValue(0, "Pin failed.");
            updateControlState();
            return;
        }

        List<ShootoutResult> updated = new ArrayList<ShootoutResult>(results);
        updated.add(row);
        results = updated;
        setTableResults(results, activeSettings != null && activeSettings.groundTruthReference != null);
        selectModelRow(results.size() - 1);
        statusLabel.setText("Pinned " + formatNumber(row.thresholdValue.doubleValue()) + ".");
        setProgressValue(100, "Pinned.");
        updateControlState();
    }

    private void openConsensusMask() {
        if (!agreementColumnAvailable(results)) {
            IJ.showMessage("Test Counts", "At least 3 successful methods are needed for a consensus mask.");
            return;
        }
        if (activeConsensusMask == null) {
            String message = agreementStatusMessage == null || agreementStatusMessage.trim().isEmpty()
                    ? "The consensus mask is not available for this run."
                    : agreementStatusMessage;
            IJ.showMessage("Test Counts", message);
            return;
        }
        closeImageQuietly(activeConsensusPreview);
        ImagePlus preview = new Duplicator().run(activeConsensusMask);
        if (preview == null) {
            IJ.showMessage("Test Counts", "Could not duplicate the consensus mask.");
            return;
        }
        preview.setTitle(ConsensusMaskBuilder.CONSENSUS_TITLE);
        preview.show();
        if (preview.getWindow() != null) {
            WindowManager.setCurrentWindow(preview.getWindow());
        }
        activeConsensusPreview = preview;
    }

    private void applySelectedToMacro() {
        ShootoutResult selected = selectedResult();
        if (selected == null || !selected.isSuccess()) {
            IJ.showMessage("Test Counts", "Select one successful result row first.");
            return;
        }
        ShootoutSettings settings = settingsForManifest();
        String newMacro;
        DagIR newDag = null;
        try {
            if (dag != null) {
                newDag = MacroApplier.applyToDag(dag, selected, settings);
                newMacro = DagToIjmEmitter.emit(newDag);
            } else {
                newMacro = MacroApplier.applyToIjm(
                        macro,
                        selected,
                        settings,
                        MacroApplier.rangeFor(selected));
            }
        } catch (RuntimeException ex) {
            IJ.showMessage("Test Counts", "Could not apply the selected threshold:\n" + cleanMessage(ex));
            return;
        }

        macro = newMacro;
        dag = newDag;
        if (macroEditHandler != null) {
            macroEditHandler.macroEdited(newMacro, newDag);
        }
        statusLabel.setText("Applied " + selected.variant + " to the loaded macro.");
        writeSidecarAsync(null, selected, newMacro);
    }

    private void copyMethodsParagraph() {
        ShootoutResult selected = selectedOrRecommendedResult();
        if (selected == null) {
            IJ.showMessage("Test Counts", "Select a successful result row first.");
            return;
        }
        try {
            TestCountsManifest manifest = buildManifest(
                    selected,
                    macro,
                    TestCountsManifest.SourceRef.inMemory(sourceTitle()),
                    null);
            String paragraph = MethodsParagraphWriter.write(manifest);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new StringSelection(paragraph),
                    null);
            statusLabel.setText("Copied methods paragraph.");
        } catch (RuntimeException ex) {
            IJ.showMessage("Test Counts", "Could not copy the methods paragraph:\n" + cleanMessage(ex));
        }
    }

    private void exportCsv() {
        if (results.isEmpty()) {
            IJ.showMessage("Test Counts", "Run a count shootout before exporting.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Count Shootout CSV");
        chooser.setSelectedFile(new File("Macro_Builder_Count_Shootout.csv"));
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("CSV files (*.csv)", "csv"));
        if (chooser.showSaveDialog(dialog) != JFileChooser.APPROVE_OPTION) return;

        File file = ensureExtension(chooser.getSelectedFile(), ".csv");
        try {
            Files.write(file.toPath(), buildCsv(results).getBytes(StandardCharsets.UTF_8));
            exportedCsvFile = file;
            statusLabel.setText("Saved " + file.getName() + ".");
            writeSidecarAsync(file, selectedOrRecommendedResult(), macro);
        } catch (Exception ex) {
            IJ.showMessage("Test Counts", "Could not export CSV:\n" + cleanMessage(ex));
        }
    }

    private void copyRecommendedValue() {
        ShootoutResult row = recommendedResult();
        if (row == null || row.thresholdValue == null) {
            IJ.showMessage("Test Counts", "Run an auto grid shootout with a recommended row first.");
            return;
        }
        String value = formatNumber(row.thresholdValue.doubleValue());
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value), null);
            statusLabel.setText("Copied recommended value " + value + ".");
        } catch (RuntimeException ex) {
            IJ.showMessage("Test Counts", "Could not copy the recommended value:\n" + cleanMessage(ex));
        }
    }

    private void writeSidecarAsync(File csvFile, ShootoutResult chosen, String macroSnapshot) {
        if (results.isEmpty()) {
            return;
        }
        final File target = resolveSidecarFile(csvFile);
        if (target == null) {
            return;
        }
        final ShootoutResult chosenSnapshot = chosen;
        final String macroText = macroSnapshot == null ? "" : macroSnapshot;
        final List<ShootoutResult> rowSnapshot = new ArrayList<ShootoutResult>(results);
        final ShootoutSettings settingsSnapshot = settingsForManifest();
        final File imageFile = sourceImageFile(source);
        final String imageTitle = sourceTitle();
        final File truthFile = groundTruthFile;
        final TestCountsManifest.SourceRef quickGroundTruth =
                truthFile == null ? null : TestCountsManifest.SourceRef.file(truthFile, "");

        sidecarWorker = new SwingWorker<File, Void>() {
            @Override protected File doInBackground() throws Exception {
                TestCountsManifest.SourceRef imageRef;
                if (imageFile != null && imageFile.isFile()) {
                    imageRef = TestCountsManifest.SourceRef.file(
                            imageFile,
                            TestCountsManifest.sha256(imageFile));
                } else {
                    imageRef = TestCountsManifest.SourceRef.inMemory(imageTitle);
                }

                TestCountsManifest.SourceRef groundTruthRef = quickGroundTruth;
                if (truthFile != null && truthFile.isFile()) {
                    groundTruthRef = TestCountsManifest.SourceRef.file(
                            truthFile,
                            TestCountsManifest.sha256(truthFile));
                }

                TestCountsManifest manifest = TestCountsManifest.builder()
                        .pluginVersion(Macro_Builder.getPluginVersion())
                        .fijiVersion(TestCountsManifest.detectFijiVersion())
                        .imageSource(imageRef)
                        .macroText(macroText)
                        .settings(settingsSnapshot)
                        .results(rowSnapshot)
                        .chosenVariant(chosenSnapshot)
                        .groundTruth(groundTruthRef)
                        .build();
                File parent = target.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                Files.write(target.toPath(), manifest.toJson().getBytes(StandardCharsets.UTF_8));
                return target;
            }

            @Override protected void done() {
                if (sidecarWorker == this) {
                    sidecarWorker = null;
                }
                if (closed || !dialog.isDisplayable()) {
                    return;
                }
                try {
                    File written = get();
                    statusLabel.setText("Updated " + written.getName() + ".");
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    statusLabel.setText("Sidecar write interrupted.");
                } catch (ExecutionException ex) {
                    IJ.showMessage("Test Counts", "Could not write sidecar:\n"
                            + target.getAbsolutePath() + "\n\n" + cleanMessage(ex.getCause()));
                } catch (RuntimeException ex) {
                    IJ.showMessage("Test Counts", "Could not write sidecar:\n"
                            + target.getAbsolutePath() + "\n\n" + cleanMessage(ex));
                }
            }
        };
        sidecarWorker.execute();
    }

    private TestCountsManifest buildManifest(
            ShootoutResult chosen,
            String macroText,
            TestCountsManifest.SourceRef imageRef,
            TestCountsManifest.SourceRef groundTruthRef) {
        return TestCountsManifest.builder()
                .pluginVersion(Macro_Builder.getPluginVersion())
                .fijiVersion(TestCountsManifest.detectFijiVersion())
                .imageSource(imageRef)
                .macroText(macroText)
                .settings(settingsForManifest())
                .results(results)
                .chosenVariant(chosen)
                .groundTruth(groundTruthRef)
                .build();
    }

    private File resolveSidecarFile(File csvFile) {
        if (sidecarFile != null) {
            return sidecarFile;
        }
        File base = csvFile != null ? csvFile : exportedCsvFile;
        if (base == null) {
            base = macroFile;
        }
        if (base == null) {
            base = new File("Macro_Builder_Count_Shootout.csv");
        }
        sidecarFile = sidecarFor(base);
        return sidecarFile;
    }

    private static File sidecarFor(File base) {
        File parent = base.getParentFile();
        String name = base.getName();
        int dot = name.lastIndexOf('.');
        String prefix = dot > 0 ? name.substring(0, dot) : name;
        return new File(parent == null ? new File(".") : parent, prefix + ".testcounts.json");
    }

    private ShootoutSettings settingsForManifest() {
        if (activeSettings != null) {
            return activeSettings;
        }
        try {
            return buildSettings();
        } catch (IllegalArgumentException ignored) {
            return ShootoutSettings.defaults();
        }
    }

    private ShootoutResult selectedOrRecommendedResult() {
        ShootoutResult selected = selectedResult();
        if (selected != null && selected.isSuccess()) {
            return selected;
        }
        ShootoutResult recommended = recommendedResult();
        if (recommended != null && recommended.isSuccess()) {
            return recommended;
        }
        for (ShootoutResult row : results) {
            if (row != null && row.isSuccess()) {
                return row;
            }
        }
        return null;
    }

    private boolean canOpenScrubPane() {
        return activeShootoutRun != null
                && activeShootoutRun.context != null
                && activeShootoutRun.context.processed != null
                && activeSettings != null
                && isFinite(activeShootoutRun.context.rangeMin)
                && isFinite(activeShootoutRun.context.rangeMax)
                && activeShootoutRun.context.rangeMax >= activeShootoutRun.context.rangeMin
                && hasSuccessfulRows(results);
    }

    private int activeSliceForScrub() {
        int slice = source == null ? 1 : source.getSlice();
        int maxSlice = activeShootoutRun == null || activeShootoutRun.context == null
                ? 1
                : Math.max(1, activeShootoutRun.context.processed.getNSlices());
        return Math.max(1, Math.min(maxSlice, slice));
    }

    private int activeFrameForScrub() {
        int frame = source == null ? 1 : source.getFrame();
        int maxFrame = activeShootoutRun == null || activeShootoutRun.context == null
                ? 1
                : Math.max(1, activeShootoutRun.context.processed.getNFrames());
        return Math.max(1, Math.min(maxFrame, frame));
    }

    private void appendFixedThresholdText(double threshold) {
        String value = formatNumber(threshold);
        String text = fixedThresholds.getText();
        if (text == null || text.trim().isEmpty()) {
            fixedThresholds.setText(value);
        } else {
            fixedThresholds.setText(text.trim() + "," + value);
        }
    }

    private void selectModelRow(int modelRow) {
        if (modelRow < 0 || modelRow >= tableModel.getRowCount()) {
            return;
        }
        int viewRow = table.convertRowIndexToView(modelRow);
        if (viewRow >= 0) {
            table.setRowSelectionInterval(viewRow, viewRow);
        }
    }

    private File sourceImageFile(ImagePlus image) {
        if (image == null) {
            return null;
        }
        File fromOriginal = fileFromInfo(image.getOriginalFileInfo());
        if (fromOriginal != null) {
            return fromOriginal;
        }
        return fileFromInfo(image.getFileInfo());
    }

    private static File fileFromInfo(FileInfo info) {
        if (info == null || info.fileName == null || info.fileName.trim().isEmpty()) {
            return null;
        }
        File file = info.directory == null || info.directory.trim().isEmpty()
                ? new File(info.fileName)
                : new File(info.directory, info.fileName);
        return file.isFile() ? file : null;
    }

    private String sourceTitle() {
        if (source == null || source.getTitle() == null || source.getTitle().trim().isEmpty()) {
            return "untitled";
        }
        return source.getTitle();
    }

    private void runBatchShootout() {
        if (isBusy()) return;

        final ShootoutSettings baseSettings;
        try {
            baseSettings = buildSettings();
        } catch (IllegalArgumentException ex) {
            IJ.showMessage("Test Counts", cleanMessage(ex));
            return;
        }

        List<File> selectedInputs = chooseBatchInputs();
        if (selectedInputs.isEmpty()) return;
        final List<File> batchFiles = BatchShootoutRunner.collectBatchFiles(selectedInputs);
        if (batchFiles.isEmpty()) {
            IJ.showMessage("Test Counts", "No ordinary image files or Bio-Formats containers were selected.");
            return;
        }

        List<Integer> selectedChannels = chooseBatchChannels();
        if (selectedChannels == null) return;
        final ShootoutSettings settings = baseSettings
                .withGroundTruthReference(null)
                .withChannelsToSweep(selectedChannels);
        notifySettings(settings);

        final File csvFile = chooseBatchCsvFile();
        if (csvFile == null) return;

        batchCancelRequested = false;
        statusLabel.setText("Starting batch count shootout...");
        setProgressIndeterminate("Starting batch...");
        batchWorker = new SwingWorker<BatchRunResult, Void>() {
            @Override protected BatchRunResult doInBackground() throws Exception {
                List<BatchShootoutResult> rows = new BatchShootoutRunner().run(
                        batchFiles,
                        macro,
                        settings,
                        firstChannel(selectedChannels),
                        new BatchShootoutRunner.Progress() {
                            @Override public void onStarted(int totalFiles) {
                                setBatchProgress(0, totalFiles, "Batch count shootout: "
                                        + totalFiles + " file(s).");
                                setBatchStatus("Batch count shootout: " + totalFiles + " file(s).");
                            }

                            @Override public void onFileStarted(File file, int index, int totalFiles) {
                                setBatchProgress(index - 1, totalFiles,
                                        "Batch " + index + "/" + totalFiles + ": " + file.getName());
                                setBatchStatus("Batch " + index + "/" + totalFiles + ": " + file.getName());
                            }

                            @Override public void onChannelStarted(
                                    File file,
                                    int index,
                                    int totalFiles,
                                    int seriesIndex,
                                    int totalSeries,
                                    int channel) {
                                String name = file == null ? "batch item" : file.getName();
                                String text = "file " + index + "/" + totalFiles
                                        + ", series " + seriesIndex + "/" + totalSeries
                                        + ", channel C" + channel + ": " + name;
                                setBatchProgress(index - 1, totalFiles, text);
                                setBatchStatus(text);
                            }

                            @Override public void onFileFinished(
                                    File file,
                                    int index,
                                    int totalFiles,
                                    int rowCount) {
                                setBatchProgress(index, totalFiles,
                                        "Batch " + index + "/" + totalFiles + " complete.");
                                setBatchStatus("Batch " + index + "/" + totalFiles
                                        + " complete: " + rowCount + " row(s).");
                            }

                            @Override public boolean isCancelled() {
                                return batchCancelRequested;
                            }
                        });
                Files.write(csvFile.toPath(), BatchShootoutRunner.buildCsv(rows).getBytes(StandardCharsets.UTF_8));
                return new BatchRunResult(rows, csvFile, batchCancelRequested);
            }

            @Override protected void done() {
                onBatchShootoutDone(this);
            }
        };
        updateControlState();
        batchWorker.execute();
    }

    private List<File> chooseBatchInputs() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose Batch Images or Folder");
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setMultiSelectionEnabled(true);
        chooser.addChoosableFileFilter(new FileNameExtensionFilter(
                "Image files (*.tif, *.tiff, *.png, *.jpg, *.gif, *.bmp, *.ics, *.ids)",
                BatchShootoutRunner.DIRECT_IMAGE_EXTENSIONS));
        if (chooser.showOpenDialog(dialog) != JFileChooser.APPROVE_OPTION) {
            return Collections.emptyList();
        }

        File[] selected = chooser.getSelectedFiles();
        List<File> inputs = new ArrayList<File>();
        if (selected != null && selected.length > 0) {
            Collections.addAll(inputs, selected);
        } else if (chooser.getSelectedFile() != null) {
            inputs.add(chooser.getSelectedFile());
        }
        return inputs;
    }

    private List<Integer> chooseBatchChannels() {
        int maxChannels = source == null ? primaryChannel : Math.max(primaryChannel, source.getNChannels());
        maxChannels = Math.max(1, maxChannels);

        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(new JLabel("Primary channel: C" + primaryChannel), BorderLayout.NORTH);

        JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        final List<JCheckBox> boxes = new ArrayList<JCheckBox>();
        for (int channel = 1; channel <= maxChannels; channel++) {
            JCheckBox box = new JCheckBox("C" + channel, channel == primaryChannel);
            box.setOpaque(false);
            boxes.add(box);
            chips.add(box);
        }
        panel.add(chips, BorderLayout.CENTER);

        while (true) {
            int choice = JOptionPane.showConfirmDialog(
                    dialog,
                    panel,
                    "Channels to sweep",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return null;
            }

            List<Integer> selected = new ArrayList<Integer>();
            for (int i = 0; i < boxes.size(); i++) {
                if (boxes.get(i).isSelected()) {
                    selected.add(Integer.valueOf(i + 1));
                }
            }
            if (!selected.isEmpty()) {
                return selected;
            }
            IJ.showMessage("Test Counts", "Select at least one channel to sweep.");
        }
    }

    private File chooseBatchCsvFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Batch Count Shootout CSV");
        chooser.setSelectedFile(new File("Macro_Builder_Batch_Count_Shootout.csv"));
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("CSV files (*.csv)", "csv"));
        if (chooser.showSaveDialog(dialog) != JFileChooser.APPROVE_OPTION) return null;
        return ensureExtension(chooser.getSelectedFile(), ".csv");
    }

    private void cancelBatchShootout() {
        if (!isBatchBusy()) return;
        batchCancelRequested = true;
        statusLabel.setText("Cancelling batch after the current file...");
        setProgressIndeterminate("Cancelling batch...");
        updateControlState();
    }

    private void onBatchShootoutDone(SwingWorker<BatchRunResult, Void> finishedWorker) {
        BatchRunResult result = null;
        String failure = null;
        try {
            result = finishedWorker.get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            failure = "Batch count shootout interrupted.";
            setProgressValue(0, "Interrupted.");
        } catch (ExecutionException ex) {
            failure = "Batch count shootout failed: " + cleanMessage(ex.getCause());
            setProgressValue(0, "Failed.");
        }

        if (finishedWorker == batchWorker) {
            batchWorker = null;
        }
        batchCancelRequested = false;

        if (closed || !dialog.isDisplayable()) {
            return;
        }

        if (failure != null) {
            statusLabel.setText(failure);
            updateControlState();
            return;
        }

        String prefix = result.cancelled ? "Batch cancelled" : "Batch complete";
        statusLabel.setText(prefix + ": " + result.rows.size()
                + " row(s) saved to " + result.csvFile.getName() + ".");
        setProgressValue(result.cancelled ? progressBar.getValue() : 100, prefix + ".");
        updateControlState();
    }

    private void setBatchStatus(final String text) {
        if (closed || text == null) return;
        if (SwingUtilities.isEventDispatchThread()) {
            statusLabel.setText(text);
            return;
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                if (!closed && dialog.isDisplayable()) {
                    statusLabel.setText(text);
                }
            }
        });
    }

    private FilterExecutor.Progress createMacroProgress(final String fallback) {
        return new FilterExecutor.Progress() {
            @Override public void setIndeterminate(String message) {
                setProgressIndeterminate(message == null ? fallback : message);
            }

            @Override public void setProgress(int completedSteps, int totalSteps, String message) {
                int value = totalSteps <= 0
                        ? 0
                        : (int) Math.round(100.0 * completedSteps / totalSteps);
                setProgressValue(value, message == null ? fallback : message);
            }
        };
    }

    private void setBatchProgress(final int completed, final int total, final String message) {
        int value = total <= 0 ? 0 : (int) Math.round(100.0 * completed / total);
        setProgressValue(value, message);
    }

    private void setProgressIndeterminate(final String text) {
        runOnEdt(new Runnable() {
            @Override public void run() {
                if (closed || !dialog.isDisplayable()) return;
                progressBar.setIndeterminate(true);
                progressBar.setString(text == null ? "Working..." : text);
            }
        });
    }

    private void setProgressValue(final int value, final String text) {
        runOnEdt(new Runnable() {
            @Override public void run() {
                if (closed || !dialog.isDisplayable()) return;
                progressBar.setIndeterminate(false);
                progressBar.setValue(Math.max(0, Math.min(100, value)));
                progressBar.setString(text == null ? "" : text);
            }
        });
    }

    private static void runOnEdt(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
    }

    private static String buildCsv(List<ShootoutResult> rows) {
        StringBuilder csv = new StringBuilder();
        csv.append("Variant,Count mode,Threshold value,Count,Mean size,Coverage %,Range,Status,")
                .append("precision,recall,f1,separation,distinctness,")
                .append("fragility_score,fragility_range_min,fragility_range_max,agreement_score\n");
        for (ShootoutResult row : rows) {
            String[] values = new String[]{
                    row.variant,
                    countModeLabel(row.countingMode),
                    row.thresholdValue == null ? "" : formatNumber(row.thresholdValue.doubleValue()),
                    row.countSummary == null ? "" : Integer.toString(row.countSummary.count),
                    row.countSummary == null ? "" : formatNumber(row.countSummary.meanSize),
                    row.countSummary == null ? "" : formatNumber(row.countSummary.coverage * 100.0),
                    rangeText(row),
                    statusText(row),
                    formatNumber(row.precision),
                    formatNumber(row.recall),
                    formatNumber(row.f1),
                    formatNumber(row.separationScore),
                    formatNumber(row.distinctnessScore),
                    formatNumber(row.fragilityScore),
                    fragilityRangeMinimum(row),
                    fragilityRangeMaximum(row),
                    formatAgreementScore(row.agreementScore)
            };
            for (int i = 0; i < values.length; i++) {
                if (i > 0) csv.append(',');
                csv.append(csvEscape(values[i]));
            }
            csv.append('\n');
        }
        return csv.toString();
    }

    private static String fragilityRangeMinimum(ShootoutResult row) {
        if (!hasFragilityRange(row)) {
            return "";
        }
        int min = row.countSummary.count;
        for (int count : row.fragilityCountRange) {
            if (count < min) {
                min = count;
            }
        }
        return Integer.toString(min);
    }

    private static String fragilityRangeMaximum(ShootoutResult row) {
        if (!hasFragilityRange(row)) {
            return "";
        }
        int max = row.countSummary.count;
        for (int count : row.fragilityCountRange) {
            if (count > max) {
                max = count;
            }
        }
        return Integer.toString(max);
    }

    private static boolean hasFragilityRange(ShootoutResult row) {
        return row != null
                && row.countSummary != null
                && row.fragilityCountRange != null
                && isFinite(row.fragilityScore);
    }

    private static String formatAgreementScore(double value) {
        if (Double.isNaN(value)) return "NaN";
        return formatNumber(value);
    }

    private ShootoutResult selectedResult() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) return null;
        int modelRow = table.convertRowIndexToModel(viewRow);
        return tableModel.resultAt(modelRow);
    }

    private ShootoutResult recommendedResult() {
        for (ShootoutResult row : results) {
            if (row != null && row.recommended) {
                return row;
            }
        }
        return null;
    }

    private static boolean hasSuccessfulRows(List<ShootoutResult> rows) {
        if (rows == null) return false;
        for (ShootoutResult row : rows) {
            if (row != null && row.isSuccess()) {
                return true;
            }
        }
        return false;
    }

    private static boolean agreementColumnAvailable(List<ShootoutResult> rows) {
        if (rows == null) return false;
        int successes = 0;
        for (ShootoutResult row : rows) {
            if (row != null && row.isSuccess()) {
                successes++;
                if (successes >= ConsensusMaskBuilder.MIN_SUCCESSFUL_MASKS) {
                    return true;
                }
            }
        }
        return false;
    }

    private static FragilityBarRenderer.Value fragilityValue(ShootoutResult row) {
        if (!hasFragilityRange(row)) {
            return FragilityBarRenderer.Value.empty();
        }
        return FragilityBarRenderer.Value.of(
                row.fragilityScore,
                row.countSummary.count,
                row.fragilityCountRange);
    }

    private static boolean isPinnedResult(ShootoutResult row) {
        return row != null
                && row.source == ShootoutResult.Source.FIXED
                && row.variant != null
                && row.variant.startsWith("Pinned ");
    }

    private void updateControlState() {
        boolean busy = isBusy();
        ShootoutSettings.ThresholdMode mode = selectedThresholdMode();
        countingMode.setEnabled(!busy);
        thresholdMode.setEnabled(!busy);
        autoMethods.setEnabled(!busy && usesAuto(mode));
        fixedThresholds.setEnabled(!busy && usesFixed(mode));
        gridSteps.setEnabled(!busy && mode == ShootoutSettings.ThresholdMode.AUTO_GRID);
        gridWarning.setVisible(mode == ShootoutSettings.ThresholdMode.AUTO_GRID
                && ((Number) gridSteps.getValue()).intValue() < 6);
        minSize.setEnabled(!busy);
        maxSize.setEnabled(!busy);
        brightObjects.setEnabled(!busy && usesAuto(mode));
        showQualityColumns.setEnabled(!busy);
        runFragilityChecks.setEnabled(!busy);
        loadReferenceButton.setEnabled(!busy);
        clearReferenceButton.setEnabled(!busy && groundTruthReference != null);
        accessiblePalette.setEnabled(!busy && groundTruthReference != null);
        runButton.setEnabled(!busy);
        exportButton.setEnabled(!busy && !results.isEmpty());
        copyRecommendedButton.setEnabled(!busy
                && recommendedResult() != null
                && recommendedResult().thresholdValue != null);
        batchButton.setEnabled(!busy);
        cancelBatchButton.setEnabled(isBatchBusy() && !batchCancelRequested);

        ShootoutResult selected = selectedResult();
        previewButton.setEnabled(!busy && selected != null && selected.isSuccess() && selected.maskPreview != null);
        scrubButton.setEnabled(!busy && canOpenScrubPane());
        applyToMacroButton.setEnabled(!busy && selected != null && selected.isSuccess());
        copyMethodsButton.setEnabled(!busy && selectedOrRecommendedResult() != null);
        boolean enoughAgreementRows = agreementColumnAvailable(results);
        consensusButton.setEnabled(!busy && enoughAgreementRows && activeConsensusMask != null);
        if (!enoughAgreementRows) {
            consensusButton.setToolTipText("At least 3 successful methods are needed for a consensus mask.");
        } else if (activeConsensusMask == null && agreementStatusMessage != null
                && !agreementStatusMessage.trim().isEmpty()) {
            consensusButton.setToolTipText(agreementStatusMessage);
        } else {
            consensusButton.setToolTipText(null);
        }
    }

    private boolean isBusy() {
        return isShootoutBusy() || isPinBusy() || isBatchBusy() || isReferenceBusy();
    }

    private boolean isShootoutBusy() {
        return worker != null && !worker.isDone();
    }

    private boolean isPinBusy() {
        return pinWorker != null && !pinWorker.isDone();
    }

    private boolean isBatchBusy() {
        return batchWorker != null && !batchWorker.isDone();
    }

    private boolean isReferenceBusy() {
        return referenceWorker != null && !referenceWorker.isDone();
    }

    private static boolean usesAuto(ShootoutSettings.ThresholdMode mode) {
        return mode == ShootoutSettings.ThresholdMode.AUTO_METHODS
                || mode == ShootoutSettings.ThresholdMode.AUTO_AND_FIXED;
    }

    private static boolean usesFixed(ShootoutSettings.ThresholdMode mode) {
        return mode == ShootoutSettings.ThresholdMode.FIXED_VALUES
                || mode == ShootoutSettings.ThresholdMode.AUTO_AND_FIXED;
    }

    private void notifySettings(ShootoutSettings settings) {
        if (settingsListener != null && settings != null) {
            settingsListener.settingsChanged(settings);
        }
    }

    private static void closeResultImages(List<ShootoutResult> rows) {
        if (rows == null) return;
        for (ShootoutResult row : rows) {
            if (row != null) {
                closeImageQuietly(row.maskPreview);
            }
        }
    }

    private void closeScrubPane() {
        if (scrubPane != null) {
            scrubPane.dispose();
            scrubPane = null;
        }
    }

    private static void closeShootoutRun(ShootoutRun run) {
        if (run != null && run.context != null) {
            closeImageQuietly(run.context.processed);
        }
    }

    private static void closeImageQuietly(ImagePlus imp) {
        if (imp == null) return;
        try {
            imp.changes = false;
            if (imp.getWindow() != null) {
                imp.close();
            } else {
                imp.flush();
            }
        } catch (Throwable ignored) {
        }
    }

    private static File ensureExtension(File file, String extension) {
        if (file == null) return null;
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(extension)) return file;
        return new File(file.getParentFile(), file.getName() + extension);
    }

    private static String join(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(values.get(i));
        }
        return sb.toString();
    }

    private static int firstChannel(List<Integer> channels) {
        if (channels == null || channels.isEmpty() || channels.get(0) == null) {
            return 1;
        }
        return Math.max(1, channels.get(0).intValue());
    }

    private static String countModeLabel(ShootoutSettings.CountingMode mode) {
        return mode == ShootoutSettings.CountingMode.OBJECTS_3D ? COUNT_3D : COUNT_2D;
    }

    private static String rangeText(ShootoutResult row) {
        if (row == null || !isFinite(row.imageMinimum) || !isFinite(row.imageMaximum)) {
            return "";
        }
        return formatNumber(row.imageMinimum) + "-" + formatNumber(row.imageMaximum);
    }

    private static String statusText(ShootoutResult row) {
        if (row == null) return "";
        if (row.isSuccess()) return "OK";
        return "Failed: " + cleanMessage(row.error);
    }

    private static String formatNumber(double value) {
        if (Double.isNaN(value)) return "";
        if (Double.isInfinite(value)) return value > 0.0 ? "Infinity" : "-Infinity";
        if (value == Math.rint(value) && Math.abs(value) < 1000000000000000.0) {
            return Long.toString(Math.round(value));
        }
        String formatted = String.format(Locale.ROOT, "%.4f", value);
        while (formatted.indexOf('.') >= 0 && formatted.endsWith("0")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        if (formatted.endsWith(".")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        return formatted;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static String csvEscape(String value) {
        String text = value == null ? "" : value;
        boolean quote = text.indexOf(',') >= 0 || text.indexOf('"') >= 0
                || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0;
        if (!quote) return text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String cleanMessage(Throwable ex) {
        if (ex == null) return "Unknown error";
        String message = ex.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return ex.getClass().getSimpleName();
        }
        return cleanMessage(message);
    }

    private static String cleanMessage(String message) {
        if (message == null || message.trim().isEmpty()) return "Unknown error";
        return message.trim().replace('\n', ' ').replace('\r', ' ');
    }

    public interface SettingsListener {
        void settingsChanged(ShootoutSettings settings);
    }

    public interface MacroEditHandler {
        void macroEdited(String newIjm, DagIR newDag);
    }

    private static final class BatchRunResult {
        final List<BatchShootoutResult> rows;
        final File csvFile;
        final boolean cancelled;

        BatchRunResult(List<BatchShootoutResult> rows, File csvFile, boolean cancelled) {
            this.rows = rows == null ? Collections.<BatchShootoutResult>emptyList() : rows;
            this.csvFile = csvFile;
            this.cancelled = cancelled;
        }
    }

    private static final class ShootoutUiResult {
        final ShootoutRun run;
        final ChartImages charts;
        final ImagePlus consensusMask;
        final String agreementStatusMessage;

        ShootoutUiResult(
                ShootoutRun run,
                ChartImages charts,
                ImagePlus consensusMask,
                String agreementStatusMessage) {
            this.run = run;
            this.charts = charts == null ? ChartImages.hidden() : charts;
            this.consensusMask = consensusMask;
            this.agreementStatusMessage = agreementStatusMessage;
        }

        List<ShootoutResult> rows() {
            return run == null ? Collections.<ShootoutResult>emptyList() : run.results;
        }
    }

    private static final class ChartImages {
        final BufferedImage histogram;
        final BufferedImage curve;
        final boolean visible;

        ChartImages(BufferedImage histogram, BufferedImage curve) {
            this.histogram = histogram;
            this.curve = curve;
            this.visible = histogram != null && curve != null;
        }

        static ChartImages hidden() {
            return new ChartImages(null, null);
        }
    }

    private static final class ResultTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = new String[]{
                "Variant", "Count mode", "Threshold value", "Count",
                "Mean size", "Coverage %", "Range", "Status",
                "precision", "recall", "f1", "Separation", "Distinctness", "Fragility", "Agreement"
        };

        private List<ShootoutResult> rows = Collections.emptyList();
        private boolean showScores;
        private boolean showQualityScores;
        private boolean showFragilityValues;
        private boolean showAgreementColumn;

        void setResults(
                List<ShootoutResult> rows,
                boolean showScores,
                boolean showQualityScores,
                boolean showFragilityValues,
                boolean showAgreementColumn) {
            this.rows = rows == null ? Collections.<ShootoutResult>emptyList() : rows;
            boolean structureChanged = this.showScores != showScores
                    || this.showQualityScores != showQualityScores
                    || this.showAgreementColumn != showAgreementColumn;
            this.showScores = showScores;
            this.showQualityScores = showQualityScores;
            this.showFragilityValues = showFragilityValues;
            this.showAgreementColumn = showAgreementColumn;
            if (structureChanged) {
                fireTableStructureChanged();
            } else {
                fireTableDataChanged();
            }
        }

        ShootoutResult resultAt(int row) {
            if (row < 0 || row >= rows.size()) return null;
            return rows.get(row);
        }

        @Override public int getRowCount() {
            return rows.size();
        }

        @Override public int getColumnCount() {
            return 9 + (showScores ? 3 : 0) + (showQualityScores ? 2 : 0)
                    + (showAgreementColumn ? 1 : 0);
        }

        @Override public String getColumnName(int column) {
            return COLUMNS[modelColumn(column)];
        }

        @Override public Class<?> getColumnClass(int columnIndex) {
            int modelColumn = modelColumn(columnIndex);
            if (modelColumn == 13) {
                return FragilityBarRenderer.Value.class;
            }
            return modelColumn >= 8 ? Double.class : String.class;
        }

        @Override public Object getValueAt(int rowIndex, int columnIndex) {
            ShootoutResult row = rows.get(rowIndex);
            ObjectCounter.CountSummary count = row.countSummary;
            switch (modelColumn(columnIndex)) {
                case 0: return row.variant;
                case 1: return countModeLabel(row.countingMode);
                case 2: return row.thresholdValue == null ? "" : formatNumber(row.thresholdValue.doubleValue());
                case 3: return count == null ? "" : Integer.toString(count.count);
                case 4: return count == null ? "" : formatNumber(count.meanSize);
                case 5: return count == null ? "" : formatNumber(count.coverage * 100.0);
                case 6: return rangeText(row);
                case 7: return statusText(row);
                case 8: return isFinite(row.precision) ? Double.valueOf(row.precision) : null;
                case 9: return isFinite(row.recall) ? Double.valueOf(row.recall) : null;
                case 10: return isFinite(row.f1) ? Double.valueOf(row.f1) : null;
                case 11: return isFinite(row.separationScore) ? Double.valueOf(row.separationScore) : null;
                case 12: return isFinite(row.distinctnessScore) ? Double.valueOf(row.distinctnessScore) : null;
                case 13: return showFragilityValues ? fragilityValue(row) : FragilityBarRenderer.Value.empty();
                case 14: return isFinite(row.agreementScore) ? Double.valueOf(row.agreementScore) : null;
                default: return "";
            }
        }

        private int modelColumn(int visibleColumn) {
            if (visibleColumn < 8) {
                return visibleColumn;
            }
            int offset = 8;
            if (showScores) {
                if (visibleColumn < offset + 3) {
                    return visibleColumn;
                }
                offset += 3;
            }
            if (showQualityScores && visibleColumn < offset + 2) {
                return 11 + visibleColumn - offset;
            }
            if (showQualityScores) {
                offset += 2;
            }
            if (visibleColumn == offset) {
                return 13;
            }
            return 14;
        }
    }

    private static final class ScoreRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(
                JTable table,
                Object value,
                boolean isSelected,
                boolean hasFocus,
                int row,
                int column) {
            Component component = super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            if (component instanceof JLabel) {
                JLabel label = (JLabel) component;
                label.setHorizontalAlignment(JLabel.RIGHT);
                label.setText(value instanceof Number
                        ? formatNumber(((Number) value).doubleValue())
                        : "");
            }
            return component;
        }
    }

    private static final class TooltipHeaderRenderer implements TableCellRenderer {
        private final TableCellRenderer delegate;
        private final String tooltip;

        TooltipHeaderRenderer(TableCellRenderer delegate, String tooltip) {
            this.delegate = delegate;
            this.tooltip = tooltip;
        }

        @Override public Component getTableCellRendererComponent(
                JTable table,
                Object value,
                boolean isSelected,
                boolean hasFocus,
                int row,
                int column) {
            Component component = delegate.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            if (component instanceof JLabel) {
                ((JLabel) component).setToolTipText(tooltip);
            }
            return component;
        }
    }

    private static final class RecommendedVariantRenderer extends DefaultTableCellRenderer {
        private final ResultTableModel model;

        RecommendedVariantRenderer(ResultTableModel model) {
            this.model = model;
        }

        @Override public Component getTableCellRendererComponent(
                JTable table,
                Object value,
                boolean isSelected,
                boolean hasFocus,
                int row,
                int column) {
            Component component = super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            if (component instanceof JLabel) {
                JLabel label = (JLabel) component;
                int modelRow = table.convertRowIndexToModel(row);
                ShootoutResult result = model.resultAt(modelRow);
                String text = value == null ? "" : value.toString();
                if (isPinnedResult(result)) {
                    String prefix = result.recommended ? "&#9733; " : "";
                    label.setText("<html>" + prefix + escapeHtml(text)
                            + " <span style='font-size:9px;color:#555;'>pinned</span></html>");
                    label.setToolTipText(result.recommended ? result.recommendationReason : "Pinned threshold");
                } else if (result != null && result.recommended) {
                    label.setText("\u2605 " + text);
                    label.setToolTipText(result.recommendationReason);
                } else {
                    label.setText(text);
                    label.setToolTipText(null);
                }
            }
            return component;
        }
    }
}
