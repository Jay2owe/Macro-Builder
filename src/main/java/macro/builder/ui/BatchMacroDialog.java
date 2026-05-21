package macro.builder.ui;

import ij.IJ;
import macro.builder.analysis.BatchMacroInput;
import macro.builder.analysis.BatchMacroResult;
import macro.builder.analysis.BatchMacroRunner;
import macro.builder.analysis.BatchMacroScanner;
import macro.builder.image.BioFormatsSeriesProvider;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumnModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.regex.PatternSyntaxException;

public final class BatchMacroDialog {

    public static final String DEFAULT_FILENAME_REGEX =
            "(?i).*\\.(tif|tiff|png|jpg|jpeg|gif|bmp|ics|ids)";
    public static final String DEFAULT_CSV_NAME = "Macro_Builder_Batch_Run.csv";
    private static final String[] BIO_FORMATS_CONTAINER_EXTENSIONS = {
            "lif", "czi", "nd2", "oib", "oif", "lsm", "zvi", "ome",
            "ims", "vsi", "lei", "mvd2", "mrxs", "svs", "scn", "tif", "tiff"
    };

    private final String macro;
    private final JDialog dialog;
    private final BioFormatsSeriesProvider seriesProvider = new BioFormatsSeriesProvider();
    private final JRadioButton folderMode = new JRadioButton("Folder of loose images", true);
    private final JRadioButton containerMode = new JRadioButton("Container file");
    private final JTextField folderField = new JTextField(32);
    private final JTextField regexField = new JTextField(DEFAULT_FILENAME_REGEX, 32);
    private final JCheckBox recursive = new JCheckBox("Include subfolders", true);
    private final JTextField containerField = new JTextField(32);
    private final JTextField outputField = new JTextField(32);
    private final InputTableModel tableModel = new InputTableModel();
    private final JTable table = new JTable(tableModel);
    private final JLabel statusLabel = new JLabel("Choose an input folder or container, then preview rows.");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JButton folderButton = new JButton("Choose...");
    private final JButton previewButton = new JButton("Preview");
    private final JButton selectAllButton = new JButton("Select all");
    private final JButton selectNoneButton = new JButton("Select none");
    private final JButton containerButton = new JButton("Choose...");
    private final JButton listSeriesButton = new JButton("List series");
    private final JButton outputButton = new JButton("Choose...");
    private final JButton runButton = new JButton("Run");
    private final JButton cancelButton = new JButton("Cancel batch");

    private SwingWorker<BatchRunResult, Void> batchWorker;
    private volatile boolean batchCancelRequested;
    private boolean closed;
    private InputMode previewMode = InputMode.NONE;

    private BatchMacroDialog(Window owner, String macro) {
        this.macro = macro == null ? "" : macro;
        this.dialog = new JDialog(owner, "Run as Batch", Dialog.ModalityType.MODELESS);
        this.dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        this.dialog.setLayout(new BorderLayout(8, 8));
        buildUi();
    }

    public static void show(Window owner, String macro) {
        if (GraphicsEnvironment.isHeadless()) {
            IJ.log("Run as batch needs the Fiji desktop UI.");
            return;
        }
        new BatchMacroDialog(owner, macro).open();
    }

    private void open() {
        dialog.pack();
        Dimension preferred = dialog.getPreferredSize();
        dialog.setSize(Math.max(860, preferred.width), Math.max(540, preferred.height));
        dialog.setLocationRelativeTo(dialog.getOwner());
        dialog.setVisible(true);
        updateControlState();
    }

    private void buildUi() {
        JPanel settings = new JPanel(new GridBagLayout());
        settings.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(10, 12, 0, 12),
                BorderFactory.createTitledBorder("Batch input")));

        int row = 0;
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        folderMode.setOpaque(false);
        containerMode.setOpaque(false);
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(folderMode);
        modeGroup.add(containerMode);
        modePanel.add(folderMode);
        modePanel.add(containerMode);
        addSettingRow(settings, row++, "Run on:", modePanel);
        addPathRow(settings, row++, "Input folder:", folderField, folderButton);
        addSettingRow(settings, row++, "Filename regex:", regexField);
        addCheckboxRow(settings, row++, recursive);

        JPanel previewPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        previewPanel.add(previewButton);
        previewPanel.add(selectAllButton);
        previewPanel.add(selectNoneButton);
        addSettingRow(settings, row++, "", previewPanel);
        addPathRow(settings, row++, "Container file:", containerField, containerButton);
        JPanel containerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        containerPanel.add(listSeriesButton);
        addSettingRow(settings, row++, "", containerPanel);
        addPathRow(settings, row, "Output folder:", outputField, outputButton);
        dialog.add(settings, BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        tableModel.addTableModelListener(e -> updateControlState());
        configureColumns(table.getColumnModel());
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 12, 0, 12),
                BorderFactory.createTitledBorder("Batch inputs")));
        dialog.add(scroll, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(BorderFactory.createEmptyBorder(0, 12, 10, 12));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        progressBar.setStringPainted(true);
        progressBar.setString("Idle");
        JPanel statusPanel = new JPanel(new BorderLayout(0, 3));
        statusPanel.add(statusLabel, BorderLayout.NORTH);
        statusPanel.add(progressBar, BorderLayout.SOUTH);
        footer.add(statusPanel, BorderLayout.NORTH);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton closeButton = new JButton("Close");
        right.add(cancelButton);
        right.add(closeButton);
        right.add(runButton);
        footer.add(right, BorderLayout.EAST);
        dialog.add(footer, BorderLayout.SOUTH);

        folderButton.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                chooseFolder(folderField, "Choose Input Folder");
            }
        });
        containerButton.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                chooseContainerFile();
            }
        });
        outputButton.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                chooseFolder(outputField, "Choose Output Folder");
            }
        });
        previewButton.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                previewInputs();
            }
        });
        listSeriesButton.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                previewContainerSeries();
            }
        });
        selectAllButton.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                tableModel.setAllSelected(true);
                updateControlState();
            }
        });
        selectNoneButton.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                tableModel.setAllSelected(false);
                updateControlState();
            }
        });
        runButton.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                runBatch();
            }
        });
        cancelButton.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                cancelBatch();
            }
        });
        closeButton.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });
        folderMode.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                onInputModeChanged();
            }
        });
        containerMode.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                onInputModeChanged();
            }
        });

        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                closed = true;
                batchCancelRequested = true;
            }
        });
    }

    private static void addPathRow(
            JPanel panel,
            int row,
            String label,
            JTextField field,
            JButton button) {
        JPanel fieldPanel = new JPanel(new BorderLayout(6, 0));
        fieldPanel.add(field, BorderLayout.CENTER);
        fieldPanel.add(button, BorderLayout.EAST);
        addSettingRow(panel, row, label, fieldPanel);
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

    private static void addCheckboxRow(JPanel panel, int row, JCheckBox checkbox) {
        checkbox.setOpaque(false);
        GridBagConstraints constraints = baseConstraints(row);
        constraints.gridx = 1;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(checkbox, constraints);
    }

    private static GridBagConstraints baseConstraints(int row) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = row;
        constraints.insets = new Insets(3, 4, 3, 4);
        constraints.anchor = GridBagConstraints.WEST;
        return constraints;
    }

    private static void configureColumns(TableColumnModel columns) {
        setPreferredWidth(columns, 0, 55);
        setPreferredWidth(columns, 1, 260);
        setPreferredWidth(columns, 2, 300);
        setPreferredWidth(columns, 3, 70);
        setPreferredWidth(columns, 4, 100);
    }

    private static void setPreferredWidth(TableColumnModel columns, int index, int width) {
        if (index < columns.getColumnCount()) {
            columns.getColumn(index).setPreferredWidth(width);
        }
    }

    private void chooseFolder(JTextField target, String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        File current = currentDirectory(target.getText());
        if (current != null) {
            chooser.setCurrentDirectory(current);
        }
        if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            if (selected != null) {
                target.setText(selected.getAbsolutePath());
                if (target == folderField) {
                    clearPreviewRows();
                }
                updateControlState();
            }
        }
    }

    private void onInputModeChanged() {
        clearPreviewRows();
        statusLabel.setText(selectedInputMode() == InputMode.CONTAINER
                ? "Choose a container file, then list series."
                : "Choose an input folder, then preview rows.");
        setProgressValue(0, "Idle");
        updateControlState();
    }

    private void chooseContainerFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose Bio-Formats Container");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.addChoosableFileFilter(new FileNameExtensionFilter(
                "Bio-Formats containers", BIO_FORMATS_CONTAINER_EXTENSIONS));
        File current = currentDirectory(containerField.getText());
        if (current != null) {
            chooser.setCurrentDirectory(current);
        }
        if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            if (selected != null) {
                containerField.setText(selected.getAbsolutePath());
                clearPreviewRows();
                updateControlState();
            }
        }
    }

    private void previewInputs() {
        if (isBusy()) return;
        clearPreviewRows();

        final File inputFolder;
        try {
            inputFolder = inputFolderFromText(folderField.getText());
        } catch (IllegalArgumentException ex) {
            IJ.showMessage("Run as Batch", cleanMessage(ex));
            return;
        }

        try {
            List<BatchMacroInput> inputs = new BatchMacroScanner().scanFolder(
                    inputFolder,
                    regexField.getText() == null ? "" : regexField.getText().trim(),
                    recursive.isSelected());
            previewMode = InputMode.FOLDER;
            tableModel.setInputs(inputs, true);
            statusLabel.setText(inputs.size() + " matching file(s).");
            setProgressValue(0, "Preview ready.");
            updateControlState();
        } catch (PatternSyntaxException ex) {
            IJ.showMessage("Run as Batch", "Invalid filename regex:\n" + cleanMessage(ex));
        } catch (IllegalArgumentException ex) {
            IJ.showMessage("Run as Batch", cleanMessage(ex));
        }
    }

    private void previewContainerSeries() {
        if (isBusy()) return;
        clearPreviewRows();

        final File container;
        try {
            container = containerFileFromText(containerField.getText());
        } catch (IllegalArgumentException ex) {
            IJ.showMessage("Run as Batch", cleanMessage(ex));
            return;
        }

        try {
            List<BatchMacroInput> inputs = seriesProvider.listSeries(container);
            previewMode = InputMode.CONTAINER;
            tableModel.setInputs(inputs, true);
            statusLabel.setText(inputs.size() + " image series found in " + container.getName() + ".");
            setProgressValue(0, "Container preview ready.");
            updateControlState();
        } catch (RuntimeException ex) {
            tableModel.setInputs(Collections.<BatchMacroInput>emptyList(), false);
            IJ.showMessage("Run as Batch", cleanMessage(ex));
            statusLabel.setText("Could not list container series.");
            setProgressValue(0, "Preview failed.");
            updateControlState();
        }
    }

    private void clearPreviewRows() {
        previewMode = InputMode.NONE;
        tableModel.setInputs(Collections.<BatchMacroInput>emptyList(), false);
    }

    private void runBatch() {
        if (isBusy()) return;
        if (macro.trim().isEmpty()) {
            IJ.showMessage("Run as Batch", "No macro has been built, recorded, or loaded yet.");
            return;
        }

        final List<BatchMacroInput> selected = tableModel.selectedInputs();
        if (selected.isEmpty()) {
            IJ.showMessage("Run as Batch", "Tick at least one file or container series to run.");
            return;
        }
        boolean containerSelected = selectedInputMode() == InputMode.CONTAINER;
        if (previewMode != selectedInputMode()
                || !inputsMatchMode(selected, containerSelected)) {
            IJ.showMessage("Run as Batch", "Preview inputs for the selected batch mode before running.");
            return;
        }

        final File outputFolder;
        try {
            outputFolder = outputFolderFromText(outputField.getText());
        } catch (IllegalArgumentException ex) {
            IJ.showMessage("Run as Batch", cleanMessage(ex));
            return;
        }

        final File csvFile = new File(outputFolder, DEFAULT_CSV_NAME);
        batchCancelRequested = false;
        statusLabel.setText("Starting batch run...");
        setProgressIndeterminate("Starting batch...");
        batchWorker = new SwingWorker<BatchRunResult, Void>() {
            @Override protected BatchRunResult doInBackground() throws Exception {
                List<BatchMacroResult> rows = new BatchMacroRunner().run(
                        selected,
                        macro,
                        outputFolder,
                        new BatchMacroRunner.Progress() {
                            @Override public void onStarted(int totalItems) {
                                setBatchProgress(0, totalItems,
                                        "Batch run: " + totalItems + " item(s).");
                                setBatchStatus("Batch run: " + totalItems + " item(s).");
                            }

                            @Override public void onItemStarted(
                                    BatchMacroInput input,
                                    int index,
                                    int totalItems) {
                                setBatchProgress(index - 1, totalItems,
                                        "Batch " + index + "/" + totalItems + ": "
                                                + inputDisplayName(input));
                                setBatchStatus("Batch " + index + "/" + totalItems + ": "
                                        + inputDisplayName(input));
                            }

                            @Override public void onItemProgress(
                                    BatchMacroInput input,
                                    int index,
                                    int totalItems,
                                    String message) {
                                String text = message == null || message.trim().isEmpty()
                                        ? "Running macro..."
                                        : message;
                                setBatchProgress(index - 1, totalItems,
                                        "Batch " + index + "/" + totalItems + ": " + text);
                            }

                            @Override public void onItemFinished(
                                    BatchMacroInput input,
                                    int index,
                                    int totalItems,
                                    BatchMacroResult result) {
                                setBatchProgress(index, totalItems,
                                        "Batch " + index + "/" + totalItems + " complete.");
                                setBatchStatus("Batch " + index + "/" + totalItems
                                        + " complete: " + statusText(result) + ".");
                            }

                            @Override public boolean isCancelled() {
                                return batchCancelRequested;
                            }
                        });
                BatchMacroRunner.writeCsv(csvFile, rows);
                return new BatchRunResult(rows, csvFile, batchCancelRequested);
            }

            @Override protected void done() {
                onBatchDone(this);
            }
        };
        updateControlState();
        batchWorker.execute();
    }

    private void cancelBatch() {
        if (!isBusy()) return;
        batchCancelRequested = true;
        statusLabel.setText("Cancelling after the current file...");
        setProgressIndeterminate("Cancelling after current file...");
        updateControlState();
    }

    private void onBatchDone(SwingWorker<BatchRunResult, Void> finishedWorker) {
        BatchRunResult result = null;
        String failure = null;
        try {
            result = finishedWorker.get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            failure = "Batch run interrupted.";
            setProgressValue(0, "Interrupted.");
        } catch (ExecutionException ex) {
            failure = "Batch run failed: " + cleanMessage(ex.getCause());
            setProgressValue(0, "Failed.");
        }

        boolean wasCancelled = batchCancelRequested || (result != null && result.cancelled);
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

        String prefix = wasCancelled ? "Batch cancelled" : "Batch complete";
        statusLabel.setText(prefix + ": " + result.rows.size()
                + " row(s) saved to " + result.csvFile.getName() + ".");
        setProgressValue(wasCancelled ? progressBar.getValue() : 100, prefix + ".");
        updateControlState();
    }

    private void setBatchStatus(final String text) {
        if (closed || text == null) return;
        runOnEdt(new Runnable() {
            @Override public void run() {
                if (!closed && dialog.isDisplayable()) {
                    statusLabel.setText(text);
                }
            }
        });
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

    private void updateControlState() {
        boolean busy = isBusy();
        boolean folderSelected = selectedInputMode() == InputMode.FOLDER;
        boolean containerSelected = selectedInputMode() == InputMode.CONTAINER;
        folderMode.setEnabled(!busy);
        containerMode.setEnabled(!busy);
        folderField.setEnabled(!busy && folderSelected);
        folderButton.setEnabled(!busy && folderSelected);
        regexField.setEnabled(!busy && folderSelected);
        recursive.setEnabled(!busy && folderSelected);
        previewButton.setEnabled(!busy && folderSelected);
        containerField.setEnabled(!busy && containerSelected);
        containerButton.setEnabled(!busy && containerSelected);
        listSeriesButton.setEnabled(!busy && containerSelected);
        selectAllButton.setEnabled(!busy && tableModel.getRowCount() > 0);
        selectNoneButton.setEnabled(!busy && tableModel.getRowCount() > 0);
        outputField.setEnabled(!busy);
        outputButton.setEnabled(!busy);
        table.setEnabled(!busy);
        List<BatchMacroInput> selected = tableModel.selectedInputs();
        runButton.setEnabled(!busy && previewMode == selectedInputMode()
                && inputsMatchMode(selected, containerSelected));
        cancelButton.setEnabled(busy && !batchCancelRequested);
    }

    private InputMode selectedInputMode() {
        return containerMode.isSelected() ? InputMode.CONTAINER : InputMode.FOLDER;
    }

    static boolean inputsMatchMode(List<BatchMacroInput> inputs, boolean containerMode) {
        if (inputs == null || inputs.isEmpty()) {
            return false;
        }
        BatchMacroInput.Kind expected = containerMode
                ? BatchMacroInput.Kind.CONTAINER_SERIES
                : BatchMacroInput.Kind.FILE;
        for (BatchMacroInput input : inputs) {
            if (input == null || input.kind != expected) {
                return false;
            }
        }
        return true;
    }

    private boolean isBusy() {
        return batchWorker != null && !batchWorker.isDone();
    }

    static File inputFolderFromText(String text) {
        File folder = fileFromText(text, "Choose an input folder.");
        if (!folder.isDirectory()) {
            throw new IllegalArgumentException("Input folder does not exist: "
                    + folder.getAbsolutePath());
        }
        return folder;
    }

    static File containerFileFromText(String text) {
        File file = fileFromText(text, "Choose a container file.");
        if (!file.isFile()) {
            throw new IllegalArgumentException("Container file does not exist: "
                    + file.getAbsolutePath());
        }
        return file;
    }

    static File outputFolderFromText(String text) {
        File folder = fileFromText(text, "Choose an output folder.");
        if (folder.exists() && !folder.isDirectory()) {
            throw new IllegalArgumentException("Output path is not a folder: "
                    + folder.getAbsolutePath());
        }
        return folder;
    }

    private static File fileFromText(String text, String emptyMessage) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(emptyMessage);
        }
        return new File(trimmed).getAbsoluteFile();
    }

    private static File currentDirectory(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) return null;
        File file = new File(trimmed);
        if (file.isDirectory()) return file;
        File parent = file.getParentFile();
        return parent != null && parent.isDirectory() ? parent : null;
    }

    private static String statusText(BatchMacroResult result) {
        if (result == null || result.status == null) return "unknown";
        if (result.isSuccess()) return "success";
        return result.status.name().toLowerCase(Locale.ROOT);
    }

    private static String inputDisplayName(BatchMacroInput input) {
        if (input == null) return "";
        if (input.kind == BatchMacroInput.Kind.CONTAINER_SERIES) {
            String label = "Series " + (input.seriesIndex + 1);
            if (input.seriesName != null && input.seriesName.trim().length() > 0) {
                label += ": " + input.seriesName.trim();
            }
            return label + " from " + input.file.getName();
        }
        return input.file.getName();
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

    private static final class BatchRunResult {
        final List<BatchMacroResult> rows;
        final File csvFile;
        final boolean cancelled;

        BatchRunResult(List<BatchMacroResult> rows, File csvFile, boolean cancelled) {
            this.rows = rows == null ? Collections.<BatchMacroResult>emptyList() : rows;
            this.csvFile = csvFile;
            this.cancelled = cancelled;
        }
    }

    private enum InputMode {
        NONE,
        FOLDER,
        CONTAINER
    }

    static final class InputTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = new String[]{"Run", "Input", "Location", "Type", "Size"};

        private List<BatchMacroInput> inputs = Collections.emptyList();
        private List<Boolean> selected = Collections.emptyList();

        void setInputs(List<BatchMacroInput> inputs, boolean selectedByDefault) {
            this.inputs = inputs == null
                    ? Collections.<BatchMacroInput>emptyList()
                    : new ArrayList<BatchMacroInput>(inputs);
            this.selected = new ArrayList<Boolean>(this.inputs.size());
            for (int i = 0; i < this.inputs.size(); i++) {
                this.selected.add(Boolean.valueOf(selectedByDefault));
            }
            fireTableDataChanged();
        }

        void setAllSelected(boolean value) {
            for (int i = 0; i < selected.size(); i++) {
                selected.set(i, Boolean.valueOf(value));
            }
            fireTableDataChanged();
        }

        List<BatchMacroInput> selectedInputs() {
            if (inputs.isEmpty()) return Collections.emptyList();
            List<BatchMacroInput> rows = new ArrayList<BatchMacroInput>();
            for (int i = 0; i < inputs.size(); i++) {
                if (Boolean.TRUE.equals(selected.get(i))) {
                    rows.add(inputs.get(i));
                }
            }
            return rows;
        }

        @Override public int getRowCount() {
            return inputs.size();
        }

        @Override public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? Boolean.class : String.class;
        }

        @Override public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0;
        }

        @Override public Object getValueAt(int rowIndex, int columnIndex) {
            BatchMacroInput input = inputs.get(rowIndex);
            switch (columnIndex) {
                case 0: return selected.get(rowIndex);
                case 1: return fileDisplay(input);
                case 2: return folderDisplay(input);
                case 3: return typeDisplay(input);
                case 4: return sizeDisplay(input);
                default: return "";
            }
        }

        @Override public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex != 0 || rowIndex < 0 || rowIndex >= selected.size()) {
                return;
            }
            selected.set(rowIndex, Boolean.valueOf(Boolean.TRUE.equals(aValue)));
            fireTableCellUpdated(rowIndex, columnIndex);
        }

        private static String fileDisplay(BatchMacroInput input) {
            if (input != null && input.kind == BatchMacroInput.Kind.CONTAINER_SERIES) {
                String label = "Series " + (input.seriesIndex + 1);
                if (input.seriesName != null && input.seriesName.trim().length() > 0) {
                    label += ": " + input.seriesName.trim();
                }
                return label;
            }
            String relative = normalizedRelativePath(input);
            int slash = relative.lastIndexOf('/');
            return slash < 0 ? relative : relative.substring(slash + 1);
        }

        private static String folderDisplay(BatchMacroInput input) {
            if (input != null && input.kind == BatchMacroInput.Kind.CONTAINER_SERIES) {
                return input.file == null ? "" : input.file.getName();
            }
            String relative = normalizedRelativePath(input);
            int slash = relative.lastIndexOf('/');
            return slash < 0 ? "" : relative.substring(0, slash);
        }

        private static String typeDisplay(BatchMacroInput input) {
            if (input == null) return "";
            if (input.kind == BatchMacroInput.Kind.CONTAINER_SERIES) {
                return "Bio-Formats";
            }
            String extension = extension(input.file == null ? "" : input.file.getName());
            return extension.toUpperCase(Locale.ROOT);
        }

        private static String sizeDisplay(BatchMacroInput input) {
            if (input == null || input.kind != BatchMacroInput.Kind.CONTAINER_SERIES) {
                return "";
            }
            if (input.width <= 0 || input.height <= 0) {
                return "";
            }
            return input.width + " x " + input.height
                    + ", C=" + Math.max(1, input.channels)
                    + ", Z=" + Math.max(1, input.slices)
                    + ", T=" + Math.max(1, input.frames);
        }

        private static String normalizedRelativePath(BatchMacroInput input) {
            if (input == null) return "";
            String relative = input.relativePath == null || input.relativePath.trim().isEmpty()
                    ? input.file.getName()
                    : input.relativePath;
            return relative.replace('\\', '/');
        }

        private static String extension(String name) {
            String text = name == null ? "" : name;
            int dot = text.lastIndexOf('.');
            return dot < 0 || dot == text.length() - 1 ? "" : text.substring(dot + 1);
        }
    }
}
