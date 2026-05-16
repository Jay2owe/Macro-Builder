package macro.builder.ui;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.Duplicator;
import macro.builder.analysis.BatchShootoutResult;
import macro.builder.analysis.BatchShootoutRunner;
import macro.builder.analysis.ObjectCounter;
import macro.builder.analysis.ShootoutResult;
import macro.builder.analysis.ShootoutSettings;
import macro.builder.analysis.ThresholdShootoutRunner;
import macro.builder.image.FilterExecutor;

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
import javax.swing.table.TableColumnModel;
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
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
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

    private final ImagePlus source;
    private final String macro;
    private final int primaryChannel;
    private final JDialog dialog;
    private final SettingsListener settingsListener;

    private final JComboBox<String> countingMode = new JComboBox<String>(new String[]{COUNT_2D, COUNT_3D});
    private final JComboBox<String> thresholdMode = new JComboBox<String>(
            new String[]{MODE_AUTO_GRID, MODE_AUTO, MODE_FIXED, MODE_AUTO_AND_FIXED});
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
    private final ResultTableModel tableModel = new ResultTableModel();
    private final JTable table = new JTable(tableModel);
    private final JButton runButton = new JButton("Run");
    private final JButton previewButton = new JButton("Open mask preview");
    private final JButton exportButton = new JButton("Export CSV...");
    private final JButton copyRecommendedButton = new JButton("Copy recommended value");
    private final JButton batchButton = new JButton("Run batch...");
    private final JButton cancelBatchButton = new JButton("Cancel batch");

    private List<ShootoutResult> results = Collections.emptyList();
    private ImagePlus activeMaskPreview;
    private SwingWorker<List<ShootoutResult>, Void> worker;
    private SwingWorker<BatchRunResult, Void> batchWorker;
    private volatile boolean batchCancelRequested;
    private boolean closed;

    private ThresholdShootoutDialog(
            Window owner,
            ImagePlus source,
            String macro,
            int primaryChannel,
            SettingsListener settingsListener) {
        this.source = source;
        this.macro = macro == null ? "" : macro;
        this.primaryChannel = Math.max(1, primaryChannel);
        this.settingsListener = settingsListener;
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
        new ThresholdShootoutDialog(owner, source, macro, 1, null).open();
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
        new ThresholdShootoutDialog(owner, source, macro, primaryChannel, settingsListener).open();
    }

    private void open() {
        dialog.pack();
        Dimension preferred = dialog.getPreferredSize();
        dialog.setSize(Math.max(860, preferred.width), Math.max(560, preferred.height));
        dialog.setLocationRelativeTo(dialog.getOwner());
        dialog.setVisible(true);
        updateControlState();
    }

    private void buildUi() {
        JPanel settings = new JPanel(new GridBagLayout());
        settings.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(10, 12, 0, 12),
                BorderFactory.createTitledBorder("Settings")));

        int row = 0;
        addSettingRow(settings, row++, "Counting mode:", countingMode);
        addThresholdModeRow(settings, row++);
        addSettingRow(settings, row++, "Auto methods:", autoMethods);
        addSettingRow(settings, row++, "Fixed thresholds:", fixedThresholds);
        addHelpRow(settings, row++, ShootoutSettings.FIXED_THRESHOLD_HELP);
        addSizeRow(settings, row++);
        addCheckboxRow(settings, row++, brightObjects);
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

        dialog.add(settings, BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) updateControlState();
            }
        });
        configureColumns(table.getColumnModel());
        table.getColumnModel().getColumn(0).setCellRenderer(new RecommendedVariantRenderer(tableModel));
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 12, 0, 12),
                BorderFactory.createTitledBorder("Results")));
        dialog.add(scroll, BorderLayout.CENTER);

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
                closeImageQuietly(activeMaskPreview);
                activeMaskPreview = null;
                closeResultImages(results);
            }
        });
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
        setPreferredWidth(columns, 0, 150);
        setPreferredWidth(columns, 1, 120);
        setPreferredWidth(columns, 2, 110);
        setPreferredWidth(columns, 3, 70);
        setPreferredWidth(columns, 4, 90);
        setPreferredWidth(columns, 5, 90);
        setPreferredWidth(columns, 6, 140);
        setPreferredWidth(columns, 7, 220);
    }

    private static void setPreferredWidth(TableColumnModel columns, int index, int width) {
        if (index < columns.getColumnCount()) {
            columns.getColumn(index).setPreferredWidth(width);
        }
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

        closeImageQuietly(activeMaskPreview);
        activeMaskPreview = null;
        closeResultImages(results);
        results = Collections.emptyList();
        tableModel.setResults(results);
        rangeLabel.setText("Macro output range: running...");
        statusLabel.setText("Running count shootout...");
        setProgressIndeterminate("Running count macro...");
        worker = new SwingWorker<List<ShootoutResult>, Void>() {
            @Override protected List<ShootoutResult> doInBackground() {
                return new ThresholdShootoutRunner().run(
                        source,
                        macro,
                        settings,
                        primaryChannel,
                        createMacroProgress("Running count macro"));
            }

            @Override protected void done() {
                onShootoutDone(this);
            }
        };
        updateControlState();
        worker.execute();
    }

    private void onShootoutDone(SwingWorker<List<ShootoutResult>, Void> finishedWorker) {
        List<ShootoutResult> rows;
        boolean failed = false;
        try {
            rows = finishedWorker.get();
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
            return;
        }

        results = rows == null ? Collections.<ShootoutResult>emptyList() : rows;
        tableModel.setResults(results);
        updateRangeLabel(results);
        if (!results.isEmpty()) {
            table.setRowSelectionInterval(0, 0);
        }
        if (!failed
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
        updateControlState();
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
                brightObjects.isSelected());
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
        ImagePlus preview = new Duplicator().run(result.maskPreview);
        if (preview == null) {
            IJ.showMessage("Test Counts", "Could not duplicate the selected mask preview.");
            return;
        }
        preview.setTitle("Macro Builder Count Mask - " + result.variant);
        preview.show();
        if (preview.getWindow() != null) {
            WindowManager.setCurrentWindow(preview.getWindow());
        }
        activeMaskPreview = preview;
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
            statusLabel.setText("Saved " + file.getName() + ".");
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
        final ShootoutSettings settings = baseSettings.withChannelsToSweep(selectedChannels);
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
        csv.append("Variant,Count mode,Threshold value,Count,Mean size,Coverage %,Range,Status\n");
        for (ShootoutResult row : rows) {
            String[] values = new String[]{
                    row.variant,
                    countModeLabel(row.countingMode),
                    row.thresholdValue == null ? "" : formatNumber(row.thresholdValue.doubleValue()),
                    row.countSummary == null ? "" : Integer.toString(row.countSummary.count),
                    row.countSummary == null ? "" : formatNumber(row.countSummary.meanSize),
                    row.countSummary == null ? "" : formatNumber(row.countSummary.coverage * 100.0),
                    rangeText(row),
                    statusText(row)
            };
            for (int i = 0; i < values.length; i++) {
                if (i > 0) csv.append(',');
                csv.append(csvEscape(values[i]));
            }
            csv.append('\n');
        }
        return csv.toString();
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
        runButton.setEnabled(!busy);
        exportButton.setEnabled(!busy && !results.isEmpty());
        copyRecommendedButton.setEnabled(!busy
                && recommendedResult() != null
                && recommendedResult().thresholdValue != null);
        batchButton.setEnabled(!busy);
        cancelBatchButton.setEnabled(isBatchBusy() && !batchCancelRequested);

        ShootoutResult selected = selectedResult();
        previewButton.setEnabled(!busy && selected != null && selected.isSuccess() && selected.maskPreview != null);
    }

    private boolean isBusy() {
        return isShootoutBusy() || isBatchBusy();
    }

    private boolean isShootoutBusy() {
        return worker != null && !worker.isDone();
    }

    private boolean isBatchBusy() {
        return batchWorker != null && !batchWorker.isDone();
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
        String formatted = String.format(Locale.US, "%.4f", value);
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

    private static final class ResultTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = new String[]{
                "Variant", "Count mode", "Threshold value", "Count",
                "Mean size", "Coverage %", "Range", "Status"
        };

        private List<ShootoutResult> rows = Collections.emptyList();

        void setResults(List<ShootoutResult> rows) {
            this.rows = rows == null ? Collections.<ShootoutResult>emptyList() : rows;
            fireTableDataChanged();
        }

        ShootoutResult resultAt(int row) {
            if (row < 0 || row >= rows.size()) return null;
            return rows.get(row);
        }

        @Override public int getRowCount() {
            return rows.size();
        }

        @Override public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override public Object getValueAt(int rowIndex, int columnIndex) {
            ShootoutResult row = rows.get(rowIndex);
            ObjectCounter.CountSummary count = row.countSummary;
            switch (columnIndex) {
                case 0: return row.variant;
                case 1: return countModeLabel(row.countingMode);
                case 2: return row.thresholdValue == null ? "" : formatNumber(row.thresholdValue.doubleValue());
                case 3: return count == null ? "" : Integer.toString(count.count);
                case 4: return count == null ? "" : formatNumber(count.meanSize);
                case 5: return count == null ? "" : formatNumber(count.coverage * 100.0);
                case 6: return rangeText(row);
                case 7: return statusText(row);
                default: return "";
            }
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
                if (result != null && result.recommended) {
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
