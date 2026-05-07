package macro.builder;

import macro.builder.analysis.BatchMacroExporter;
import macro.builder.analysis.MacroBatchCompatibility;
import macro.builder.analysis.ShootoutSettings;
import macro.builder.image.FilterExecutor;
import macro.builder.image.dag.DagIR;
import macro.builder.image.dag.DagIRSerializer;
import macro.builder.ui.MacroPreviewHandler;
import macro.builder.ui.RecorderDialog;
import macro.builder.ui.ThresholdShootoutDialog;
import macro.builder.ui.sandbox.SandboxDialog;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.Duplicator;
import ij.plugin.PlugIn;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.List;
import java.util.Locale;

public class Macro_Builder implements PlugIn {

    @Override
    public void run(String arg) {
        if (GraphicsEnvironment.isHeadless()) {
            IJ.log("Macro Builder needs the Fiji desktop UI.");
            return;
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                new SessionDialog().open();
            }
        });
    }

    private static final class SessionDialog {
        private static final int TILE_GRID_GAP = 8;
        private static final Dimension TILE_SIZE = new Dimension(104, 112);
        private static final int TILE_ICON_SIZE = 54;
        private static final int LEFT_COLUMN_WIDTH = 230;
        private static final int RIGHT_COLUMN_WIDTH = 200;
        private static final int MACRO_ACTION_BUTTON_HEIGHT = 30;

        private final JDialog dialog = new JDialog((java.awt.Frame) null, "Macro Builder", false);
        private final JLabel imageLabel = new JLabel("No image selected.");
        private final JLabel sourceLabel = new JLabel("Macro source: none");
        private final JLabel statusLabel = new JLabel(" ");
        private final JProgressBar macroProgress = new JProgressBar(0, 100);
        private final JTextArea macroArea = new JTextArea();
        private final File stateDir = defaultStateDir();
        private final File macroHistoryFile = new File(stateDir, "saved-macros.tsv");
        private final JComboBox<MacroHistoryEntry> savedMacroCombo =
                new JComboBox<MacroHistoryEntry>();
        private final List<MacroHistoryEntry> savedMacroHistory =
                new ArrayList<MacroHistoryEntry>();
        private final List<JButton> macroActionButtons =
                new ArrayList<JButton>();
        private final JButton openLastButton = new JButton("Open last image/container");
        private boolean updatingSavedMacroCombo;

        private ImagePlus sourceImage;
        private ImagePlus macroPreview;
        private ImagePlus sandboxPreview;
        private ImagePlus recorderSample;
        private SwingWorker<ImagePlus, Void> macroWorker;
        private File lastOpenedImagePath;
        private String lastMacro;
        private String lastMacroSource = "none";
        private ShootoutSettings lastShootoutSettings = ShootoutSettings.defaults();
        private DagIR lastDag;
        private static final String LAST_OPENED_IMAGE_PATH_FILE = "last-opened-image-path.txt";
        private static final String[] BIO_FORMATS_CONTAINER_EXTENSIONS = {
                "lif", "czi", "nd2", "oib", "oif", "lsm", "zvi", "ome",
                "ims", "vsi", "lei", "mvd2", "mrxs", "svs", "scn"
        };

        SessionDialog() {
            buildUi();
            loadState();
            loadSavedMacroHistory();
            useCurrentImage(false);
        }

        void open() {
            dialog.pack();
            dialog.setSize(new Dimension(980, 560));
            dialog.setLocationRelativeTo(null);
            dialog.setVisible(true);
        }

        private void buildUi() {
            dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            dialog.setLayout(new BorderLayout(0, 0));

            JPanel shell = new JPanel(new BorderLayout(10, 0));
            shell.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));
            shell.add(buildWorkflowPanel(), BorderLayout.WEST);
            shell.add(buildMacroPanel(), BorderLayout.CENTER);
            shell.add(buildActionColumn(), BorderLayout.EAST);
            dialog.add(shell, BorderLayout.CENTER);

            JPanel footer = new JPanel(new BorderLayout(0, 3));
            footer.setBorder(BorderFactory.createEmptyBorder(0, 12, 10, 12));
            statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
            macroProgress.setStringPainted(true);
            macroProgress.setValue(0);
            macroProgress.setString("Idle");
            footer.add(statusLabel, BorderLayout.NORTH);
            footer.add(macroProgress, BorderLayout.SOUTH);
            dialog.add(footer, BorderLayout.SOUTH);

            dialog.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosed(java.awt.event.WindowEvent e) {
                    cancelMacroWorker();
                    closeImageQuietly(macroPreview);
                    closeImageQuietly(sandboxPreview);
                    closeImageQuietly(recorderSample);
                }
            });
        }

        private JPanel buildWorkflowPanel() {
            JPanel panel = new JPanel(new BorderLayout(0, 8));
            Dimension columnSize = new Dimension(LEFT_COLUMN_WIDTH,
                    TILE_SIZE.height * 2 + TILE_GRID_GAP + 142);
            panel.setPreferredSize(columnSize);
            panel.setMinimumSize(new Dimension(LEFT_COLUMN_WIDTH, 1));

            JPanel content = new JPanel(new BorderLayout(0, 10));
            content.add(buildImagePanel(), BorderLayout.NORTH);

            JPanel workflowContent = new JPanel(new BorderLayout(0, 8));
            JLabel title = new JLabel("Workflows");
            title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
            workflowContent.add(title, BorderLayout.NORTH);

            JPanel grid = new JPanel(new GridLayout(2, 2, TILE_GRID_GAP, TILE_GRID_GAP));
            grid.setPreferredSize(new Dimension(
                    TILE_SIZE.width * 2 + TILE_GRID_GAP,
                    TILE_SIZE.height * 2 + TILE_GRID_GAP));
            JButton buildTile = createWorkflowTile("Build Macro",
                    new WorkflowIcon(WorkflowIcon.BUILD), "Open the visual macro builder.");
            JButton recordTile = createWorkflowTile("Macro Recorder",
                    new WorkflowIcon(WorkflowIcon.RECORD), "Record filtering steps in Fiji.");
            JButton countTile = createWorkflowTile("Test Counts",
                    new WorkflowIcon(WorkflowIcon.COUNTS), "Test object counts with the current macro.");
            JButton openImageTile = createWorkflowTile("Open Image/\nContainer",
                    new WorkflowIcon(WorkflowIcon.OPEN_IMAGE), "Open an image, folder, or microscope container.");
            buildTile.addActionListener(e -> openSandbox());
            recordTile.addActionListener(e -> openRecorder());
            countTile.addActionListener(e -> openCountTester());
            openImageTile.addActionListener(e -> openImageFromDisk());
            grid.add(buildTile);
            grid.add(recordTile);
            grid.add(countTile);
            grid.add(openImageTile);
            JPanel gridWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            gridWrap.add(grid);
            workflowContent.add(gridWrap, BorderLayout.CENTER);
            content.add(workflowContent, BorderLayout.CENTER);
            panel.add(content, BorderLayout.NORTH);
            return panel;
        }

        private JPanel buildImagePanel() {
            JPanel imagePanel = new JPanel(new BorderLayout(0, 4));
            JLabel imageTitle = new JLabel("Selected image");
            imageTitle.setFont(imageTitle.getFont().deriveFont(Font.BOLD, 13f));
            imagePanel.add(imageTitle, BorderLayout.NORTH);
            imagePanel.add(imageLabel, BorderLayout.CENTER);

            JPanel imageButtons = new JPanel(new GridLayout(0, 1, 0, 4));
            JButton current = createSecondaryButton("Use current Fiji image");
            current.addActionListener(e -> useCurrentImage(true));
            openLastButton.addActionListener(e -> openLastImageOrContainer());
            openLastButton.setMargin(new Insets(3, 8, 3, 8));
            openLastButton.setEnabled(false);
            imageButtons.add(current);
            imageButtons.add(openLastButton);
            imagePanel.add(imageButtons, BorderLayout.SOUTH);
            return imagePanel;
        }

        private JPanel buildMacroPanel() {
            macroArea.setEditable(false);
            macroArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            macroArea.setLineWrap(false);
            macroArea.setText("");

            JPanel panel = new JPanel(new BorderLayout(0, 8));
            JScrollPane scroll = new JScrollPane(macroArea);
            scroll.setBorder(BorderFactory.createTitledBorder("Loaded Macro"));
            JPanel macroPanel = new JPanel(new BorderLayout(0, 4));
            JPanel macroHeader = new JPanel(new BorderLayout(6, 4));
            JLabel savedLabel = new JLabel("Load Saved Macro");
            savedMacroCombo.setEnabled(false);
            savedMacroCombo.setPrototypeDisplayValue(new MacroHistoryEntry("Choose saved macro..."));
            savedMacroCombo.addActionListener(e -> loadSelectedSavedMacro());
            macroHeader.add(savedLabel, BorderLayout.WEST);
            macroHeader.add(savedMacroCombo, BorderLayout.CENTER);
            macroHeader.add(sourceLabel, BorderLayout.SOUTH);
            refreshSavedMacroCombo(null);
            macroPanel.add(macroHeader, BorderLayout.NORTH);
            macroPanel.add(scroll, BorderLayout.CENTER);
            panel.add(macroPanel, BorderLayout.CENTER);
            return panel;
        }

        private JPanel buildActionColumn() {
            JPanel panel = new JPanel(new BorderLayout(0, 8));
            panel.setPreferredSize(new Dimension(RIGHT_COLUMN_WIDTH, 1));
            panel.setMinimumSize(new Dimension(RIGHT_COLUMN_WIDTH, 1));

            JPanel content = new JPanel(new BorderLayout(0, 8));
            JLabel title = new JLabel("Loaded Macro");
            title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
            content.add(title, BorderLayout.NORTH);

            macroActionButtons.clear();
            JPanel buttons = new JPanel(new GridLayout(0, 1, 0, 8));
            buttons.setPreferredSize(new Dimension(RIGHT_COLUMN_WIDTH,
                    MACRO_ACTION_BUTTON_HEIGHT * 5 + 8 * 4));
            JButton run = createMacroActionButton("Run as batch...");
            JButton saveBatch = createMacroActionButton("Save as batch macro...");
            JButton edit = createMacroActionButton("Edit Macro...");
            JButton variations = createMacroActionButton("Create Macro Variations...");
            JButton counts = createMacroActionButton("Test Counts...");
            run.addActionListener(e -> runAsBatchPlaceholder());
            saveBatch.addActionListener(e -> saveBatchMacro());
            edit.addActionListener(e -> editCurrentMacro());
            variations.addActionListener(e -> createMacroVariationsPlaceholder());
            counts.addActionListener(e -> openCountTester());
            buttons.add(run);
            buttons.add(saveBatch);
            buttons.add(edit);
            buttons.add(variations);
            buttons.add(counts);
            refreshMacroActionControls();
            content.add(buttons, BorderLayout.CENTER);
            panel.add(content, BorderLayout.NORTH);
            return panel;
        }

        private JButton createWorkflowTile(String text, Icon icon, String tooltip) {
            String label = text.replace("\n", "<br>");
            JButton button = new JButton("<html><center>" + label + "</center></html>", icon);
            button.setHorizontalTextPosition(SwingConstants.CENTER);
            button.setVerticalTextPosition(SwingConstants.BOTTOM);
            button.setPreferredSize(TILE_SIZE);
            button.setMinimumSize(TILE_SIZE);
            button.setMaximumSize(TILE_SIZE);
            button.setFocusPainted(false);
            button.setFont(button.getFont().deriveFont(11f));
            button.setIconTextGap(2);
            button.setMargin(new Insets(4, 4, 4, 4));
            button.setToolTipText(tooltip);
            return button;
        }

        private JButton createSecondaryButton(String text) {
            JButton button = new JButton(text);
            button.setMargin(new Insets(3, 8, 3, 8));
            return button;
        }

        private JButton createMacroActionButton(String text) {
            JButton button = new JButton(text);
            Dimension size = new Dimension(RIGHT_COLUMN_WIDTH, MACRO_ACTION_BUTTON_HEIGHT);
            button.setPreferredSize(size);
            button.setMinimumSize(size);
            button.setMaximumSize(size);
            button.setFont(button.getFont().deriveFont(11f));
            button.setMargin(new Insets(4, 6, 4, 6));
            button.setToolTipText(text);
            macroActionButtons.add(button);
            return button;
        }

        private void useCurrentImage(boolean warnIfMissing) {
            ImagePlus current = WindowManager.getCurrentImage();
            if (current == null) {
                if (warnIfMissing) {
                    IJ.showMessage("Macro Builder", "No Fiji image is currently active.");
                }
                refreshImageLabel();
                return;
            }
            sourceImage = current;
            refreshImageLabel();
        }

        private void openImageFromDisk() {
            JFileChooser chooser = createImageChooser();
            applyRememberedImagePath(chooser);
            if (chooser.showOpenDialog(dialog) != JFileChooser.APPROVE_OPTION) return;
            File selected = chooser.getSelectedFile();
            OpenAttempt opened = openImageOrContainer(selected);
            if (opened.cancelled) return;
            if (opened.image == null) {
                IJ.showMessage("Macro Builder", opened.message == null
                        ? "Fiji could not open that file as an image or stack.\n\n"
                                + "For microscope containers, this requires Fiji's Bio-Formats plugin."
                        : opened.message);
                return;
            }
            sourceImage = opened.image;
            rememberOpenedImagePath(selected);
            refreshImageLabel();
        }

        private void openLastImageOrContainer() {
            File remembered = existingLastOpenedImagePath();
            if (remembered == null) {
                clearRememberedImagePath();
                IJ.showMessage("Macro Builder", "The last opened image or container could not be found.");
                return;
            }

            OpenAttempt opened = openImageOrContainer(remembered);
            if (opened.cancelled) return;
            if (opened.image == null) {
                IJ.showMessage("Macro Builder", opened.message == null
                        ? "Fiji could not reopen the last image or container."
                        : opened.message);
                return;
            }
            sourceImage = opened.image;
            rememberOpenedImagePath(remembered);
            refreshImageLabel();
        }

        private JFileChooser createImageChooser() {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Open Image, Folder, or Container");
            chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            chooser.addChoosableFileFilter(new FileNameExtensionFilter(
                    "Image files", "tif", "tiff", "png", "jpg", "jpeg", "gif", "bmp", "ics", "ids"));
            chooser.addChoosableFileFilter(new FileNameExtensionFilter(
                    "Bio-Formats containers", "lif", "czi", "nd2", "oib", "oif", "lsm", "zvi", "ome",
                    "ims", "vsi", "lei", "mvd2", "mrxs", "svs", "scn"));
            return chooser;
        }

        private void applyRememberedImagePath(JFileChooser chooser) {
            File remembered = existingLastOpenedImagePath();
            if (chooser == null || remembered == null) return;
            File parent = remembered.getParentFile();
            if (parent != null && parent.isDirectory()) {
                chooser.setCurrentDirectory(parent);
            }
            chooser.setSelectedFile(remembered);
        }

        private OpenAttempt openImageOrContainer(File selected) {
            if (selected == null) return OpenAttempt.cancelled();
            if (shouldOpenWithBioFormatsChooser(selected)) {
                return openWithBioFormats(selected);
            }

            String path = selected.getAbsolutePath();
            ImagePlus opened = IJ.openImage(path);
            if (opened != null) {
                opened.show();
                setStatus("Opened " + selected.getName() + ".");
                return OpenAttempt.opened(opened);
            }

            return openWithBioFormats(selected);
        }

        private OpenAttempt openWithBioFormats(File selected) {
            String path = selected.getAbsolutePath();
            int[] beforeIds = WindowManager.getIDList();
            try {
                IJ.run("Bio-Formats Importer", "open=[" + path + "]");
            } catch (Throwable t) {
                IJ.log("Macro Builder: Bio-Formats fallback failed: " + cleanMessage(t));
                return OpenAttempt.failed("Fiji could not open that file with Bio-Formats.\n\n"
                        + "A normal Fiji installation includes Bio-Formats, but this Fiji instance may not.\n\n"
                        + cleanMessage(t));
            }

            List<ImagePlus> opened = findOpenedImages(beforeIds);
            if (opened.isEmpty()) {
                setStatus("No image was selected from " + selected.getName() + ".");
                return OpenAttempt.cancelled();
            }

            ImagePlus chosen = chooseOpenedImage(opened);
            if (chosen == null) {
                setStatus("No imported image was selected.");
                return OpenAttempt.cancelled();
            }
            closeUnchosenImages(opened, chosen);
            if (chosen.getWindow() == null) chosen.show();
            if (chosen.getWindow() != null) WindowManager.setCurrentWindow(chosen.getWindow());
            setStatus("Opened " + chosen.getTitle() + " from " + selected.getName() + " with Bio-Formats.");
            return OpenAttempt.opened(chosen);
        }

        private ImagePlus chooseOpenedImage(List<ImagePlus> opened) {
            if (opened.size() == 1) return opened.get(0);
            ImageChoice[] choices = new ImageChoice[opened.size()];
            for (int i = 0; i < opened.size(); i++) {
                choices[i] = new ImageChoice(opened.get(i));
            }
            Object selected = JOptionPane.showInputDialog(dialog,
                    "Choose the image Macro Builder should use:",
                    "Macro Builder",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    choices,
                    choices[0]);
            return selected instanceof ImageChoice ? ((ImageChoice) selected).image : null;
        }

        private static List<ImagePlus> findOpenedImages(int[] beforeIds) {
            List<ImagePlus> opened = new ArrayList<ImagePlus>();
            int[] afterIds = WindowManager.getIDList();
            if (afterIds == null) return opened;
            for (int afterId : afterIds) {
                if (!containsId(beforeIds, afterId)) {
                    ImagePlus imp = WindowManager.getImage(afterId);
                    if (imp != null) opened.add(imp);
                }
            }
            return opened;
        }

        private static void closeUnchosenImages(List<ImagePlus> opened, ImagePlus chosen) {
            for (ImagePlus imp : opened) {
                if (imp != chosen) closeImageQuietly(imp);
            }
        }

        private static boolean shouldOpenWithBioFormatsChooser(File selected) {
            if (selected.isDirectory()) return true;
            String name = selected.getName().toLowerCase(Locale.ROOT);
            if (name.endsWith(".ome.tif") || name.endsWith(".ome.tiff")) return true;
            for (String extension : BIO_FORMATS_CONTAINER_EXTENSIONS) {
                if (name.endsWith("." + extension)) return true;
            }
            return false;
        }

        private static boolean containsId(int[] ids, int id) {
            if (ids == null) return false;
            for (int existing : ids) {
                if (existing == id) return true;
            }
            return false;
        }

        private void refreshImageLabel() {
            if (sourceImage == null) {
                imageLabel.setText("No image selected.");
                return;
            }
            imageLabel.setText("Selected: " + describeImage(sourceImage));
        }

        private boolean ensureImage() {
            if (sourceImage == null || sourceImage.getStack() == null) {
                useCurrentImage(false);
            }
            if (sourceImage == null || sourceImage.getStack() == null) {
                IJ.showMessage("Macro Builder", "Open an image or stack first.");
                return false;
            }
            return true;
        }

        private void openSandbox() {
            if (!ensureImage()) return;
            if (!stateDir.exists() && !stateDir.mkdirs()) {
                IJ.showMessage("Macro Builder", "Could not create state folder:\n" + stateDir.getAbsolutePath());
                return;
            }
            SandboxDialog.Result result = SandboxDialog.show(
                    "Standalone image", stateDir, 0, lastMacro, createSandboxPreviewHandler());
            if (result == null || result.dag == null || result.ijmFallback == null) return;
            lastDag = result.dag;
            lastMacro = result.ijmFallback;
            lastMacroSource = "visual builder";
            clearSavedMacroSelection();
            macroArea.setText(lastMacro);
            macroArea.setCaretPosition(0);
            writeState();
            refreshSourceLabel();
            setStatus("Built filter macro. Macro actions are now available.");
        }

        private void openRecorder() {
            if (!ensureImage()) return;
            try {
                RecorderDialog.Result result = RecorderDialog.show(
                        "Standalone image",
                        createMacroPreviewHandler(),
                        createSampleSupplier(),
                        lastMacro);
                if (result == null || result.macroText == null || result.macroText.trim().isEmpty()) return;
                lastMacro = result.macroText;
                lastMacroSource = "recorder";
                lastDag = null;
                clearSavedMacroSelection();
                macroArea.setText(lastMacro);
                macroArea.setCaretPosition(0);
                writeState();
                refreshSourceLabel();
                setStatus("Recorded filter macro. Macro actions are now available.");
            } finally {
                closeImageQuietly(recorderSample);
                recorderSample = null;
            }
        }

        private RecorderDialog.SampleSupplier createSampleSupplier() {
            return new RecorderDialog.SampleSupplier() {
                @Override public ImagePlus openSample() {
                    if (!ensureImage()) return null;
                    closeImageQuietly(recorderSample);
                    recorderSample = duplicateImage(sourceImage, "Macro Builder Recorder Sample");
                    if (recorderSample != null) {
                        recorderSample.show();
                        if (recorderSample.getWindow() != null) {
                            WindowManager.setCurrentWindow(recorderSample.getWindow());
                        }
                    }
                    return recorderSample;
                }
            };
        }

        private MacroPreviewHandler createMacroPreviewHandler() {
            return new MacroPreviewHandler() {
                @Override public void preview(String macroContent) throws Exception {
                    previewMacroAsync(macroContent);
                }

                @Override public void cleanup() {
                    cancelMacroWorker();
                    closeImageQuietly(macroPreview);
                    macroPreview = null;
                }
            };
        }

        private SandboxDialog.PreviewHandler createSandboxPreviewHandler() {
            return new SandboxDialog.PreviewHandler() {
                @Override public ImagePlus createSource() {
                    if (!ensureImage()) return null;
                    return duplicateImage(sourceImage, "Macro Builder Sandbox Source");
                }

                @Override public ImagePlus showPreview(ImagePlus result, ImagePlus existingPreview) {
                    closeImageQuietly(existingPreview);
                    closeImageQuietly(sandboxPreview);
                    result.setTitle("Macro Builder Preview");
                    result.show();
                    sandboxPreview = result;
                    return result;
                }

                @Override public void close(ImagePlus imp) {
                    if (imp != sandboxPreview) closeImageQuietly(imp);
                }
            };
        }

        private void runLastMacroOnDuplicate() {
            if (!ensureImage()) return;
            if (!ensureMacroLoaded()) return;
            runMacroOnDuplicateAsync(lastMacro);
        }

        private void openCountTester() {
            if (!ensureMacroLoaded()) return;
            if (!ensureImage()) return;
            ThresholdShootoutDialog.show(dialog, sourceImage, lastMacro,
                    new ThresholdShootoutDialog.SettingsListener() {
                        @Override public void settingsChanged(ShootoutSettings settings) {
                            lastShootoutSettings = settings;
                        }
                    });
            setStatus("Opened count tester.");
        }

        private void previewLastMacro() {
            if (!ensureMacroLoaded()) return;
            previewMacroAsync(lastMacro);
        }

        private void editCurrentMacro() {
            if (!ensureMacroLoaded()) return;
            writeState();
            openSandbox();
        }

        private void runAsBatchPlaceholder() {
            if (!ensureMacroLoaded()) return;
            IJ.showMessage("Macro Builder",
                    "Run as batch is not implemented yet.\n\nUse Test Counts... > Run batch... for now.");
        }

        private void createMacroVariationsPlaceholder() {
            if (!ensureMacroLoaded()) return;
            IJ.showMessage("Macro Builder", "Create Macro Variations is not implemented yet.");
        }

        private boolean ensureMacroLoaded() {
            if (lastMacro == null || lastMacro.trim().isEmpty()) {
                IJ.showMessage("Macro Builder", "No macro has been built, recorded, or loaded yet.");
                return false;
            }
            return true;
        }

        private void previewMacroAsync(final String macroContent) {
            if (!ensureImage() || !ensureMacroIdle()) return;
            final ImagePlus selected = sourceImage;
            startMacroProgress("Previewing macro...");
            macroWorker = new SwingWorker<ImagePlus, Void>() {
                private ImagePlus work;

                @Override protected ImagePlus doInBackground() throws Exception {
                    work = duplicateImage(selected, "Macro Builder Preview Source");
                    if (work == null) {
                        throw new IllegalStateException("Could not duplicate the selected image.");
                    }
                    FilterExecutor.runThreadSafe(work, macroContent, createMacroProgress("Previewing macro"));
                    work.setTitle("Macro Builder Preview");
                    return work;
                }

                @Override protected void done() {
                    if (macroWorker == this) macroWorker = null;
                    try {
                        ImagePlus result = get();
                        closeImageQuietly(macroPreview);
                        result.show();
                        macroPreview = result;
                        finishMacroProgress("Preview complete.", true);
                        setStatus("Preview complete.");
                    } catch (CancellationException cancelled) {
                        closeImageQuietly(work);
                        finishMacroProgress("Preview cancelled.", false);
                        setStatus("Preview cancelled.");
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        closeImageQuietly(work);
                        finishMacroProgress("Preview interrupted.", false);
                        setStatus("Preview interrupted.");
                    } catch (ExecutionException ex) {
                        closeImageQuietly(work);
                        finishMacroProgress("Preview failed.", false);
                        IJ.showMessage("Macro Builder", "Preview failed:\n" + cleanMessage(ex.getCause()));
                    }
                }
            };
            macroWorker.execute();
        }

        private void runMacroOnDuplicateAsync(final String macroContent) {
            if (!ensureImage() || !ensureMacroIdle()) return;
            final ImagePlus selected = sourceImage;
            startMacroProgress("Running macro on duplicate...");
            macroWorker = new SwingWorker<ImagePlus, Void>() {
                private ImagePlus work;

                @Override protected ImagePlus doInBackground() throws Exception {
                    work = duplicateImage(selected, "Macro Builder Run Result");
                    if (work == null) {
                        throw new IllegalStateException("Could not duplicate the selected image.");
                    }
                    FilterExecutor.runThreadSafe(work, macroContent, createMacroProgress("Running macro"));
                    work.setTitle("Macro Builder Run Result");
                    return work;
                }

                @Override protected void done() {
                    if (macroWorker == this) macroWorker = null;
                    try {
                        ImagePlus result = get();
                        result.show();
                        finishMacroProgress("Run complete.", true);
                        setStatus("Run complete on duplicate image.");
                    } catch (CancellationException cancelled) {
                        closeImageQuietly(work);
                        finishMacroProgress("Run cancelled.", false);
                        setStatus("Run cancelled.");
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        closeImageQuietly(work);
                        finishMacroProgress("Run interrupted.", false);
                        setStatus("Run interrupted.");
                    } catch (ExecutionException ex) {
                        closeImageQuietly(work);
                        finishMacroProgress("Run failed.", false);
                        IJ.showMessage("Macro Builder", "Run failed:\n" + cleanMessage(ex.getCause()));
                    }
                }
            };
            macroWorker.execute();
        }

        private boolean ensureMacroIdle() {
            if (macroWorker != null && !macroWorker.isDone()) {
                setStatus("A macro is already running.");
                return false;
            }
            return true;
        }

        private void cancelMacroWorker() {
            if (macroWorker != null && !macroWorker.isDone()) {
                macroWorker.cancel(true);
            }
        }

        private FilterExecutor.Progress createMacroProgress(final String fallback) {
            return new FilterExecutor.Progress() {
                @Override public void setIndeterminate(String message) {
                    setMacroProgressIndeterminate(message == null ? fallback : message);
                }

                @Override public void setProgress(int completedSteps, int totalSteps, String message) {
                    int value = totalSteps <= 0
                            ? 0
                            : (int) Math.round(100.0 * completedSteps / totalSteps);
                    setMacroProgressValue(value, message == null ? fallback : message);
                }
            };
        }

        private void startMacroProgress(String text) {
            setMacroProgressIndeterminate(text);
            setStatus(text);
        }

        private void finishMacroProgress(String text, boolean success) {
            setMacroProgressValue(success ? 100 : 0, text);
        }

        private void setMacroProgressIndeterminate(final String text) {
            runOnEdt(new Runnable() {
                @Override public void run() {
                    macroProgress.setIndeterminate(true);
                    macroProgress.setString(text == null ? "Working..." : text);
                }
            });
        }

        private void setMacroProgressValue(final int value, final String text) {
            runOnEdt(new Runnable() {
                @Override public void run() {
                    macroProgress.setIndeterminate(false);
                    macroProgress.setValue(Math.max(0, Math.min(100, value)));
                    macroProgress.setString(text == null ? "" : text);
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

        private void saveCurrentMacro() {
            if (!ensureMacroLoaded()) return;
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Save Macro");
            chooser.setSelectedFile(new File("Macro_Builder_Filter.ijm"));
            chooser.addChoosableFileFilter(new FileNameExtensionFilter("ImageJ macro (*.ijm)", "ijm"));
            if (chooser.showSaveDialog(dialog) != JFileChooser.APPROVE_OPTION) return;
            File file = ensureExtension(chooser.getSelectedFile(), ".ijm");
            try {
                Files.write(file.toPath(), lastMacro.getBytes(StandardCharsets.UTF_8));
                File dagFile = dagSidecarFor(file);
                if (lastDag != null) {
                    Files.write(dagFile.toPath(), DagIRSerializer.toJson(lastDag).getBytes(StandardCharsets.UTF_8));
                } else if (dagFile.exists()) {
                    Files.delete(dagFile.toPath());
                }
                rememberSavedMacro(file);
                lastMacroSource = "saved macro: " + file.getName();
                refreshSourceLabel();
                setStatus("Saved " + file.getName() + ".");
            } catch (Exception ex) {
                IJ.showMessage("Macro Builder", "Could not save macro:\n" + cleanMessage(ex));
            }
        }

        private void saveBatchMacro() {
            if (!ensureMacroLoaded()) return;
            List<String> warnings = MacroBatchCompatibility.warnings(lastMacro);
            if (!warnings.isEmpty() && !confirmBatchWarnings(warnings)) {
                return;
            }

            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Save Batch Macro");
            chooser.setSelectedFile(new File(BatchMacroExporter.DEFAULT_WRAPPER_NAME));
            chooser.addChoosableFileFilter(new FileNameExtensionFilter("ImageJ macro (*.ijm)", "ijm"));
            if (chooser.showSaveDialog(dialog) != JFileChooser.APPROVE_OPTION) return;

            File file = ensureExtension(chooser.getSelectedFile(), ".ijm");
            try {
                BatchMacroExporter.ExportResult result = new BatchMacroExporter().export(
                        file,
                        lastMacro,
                        lastShootoutSettings == null ? ShootoutSettings.defaults() : lastShootoutSettings);
                setStatus("Saved " + result.wrapperMacro.getName()
                        + " with " + result.settingsJson.getName() + ".");
            } catch (Exception ex) {
                IJ.showMessage("Macro Builder", "Could not save batch macro:\n" + cleanMessage(ex));
            }
        }

        private boolean confirmBatchWarnings(List<String> warnings) {
            StringBuilder message = new StringBuilder();
            message.append("This macro may not be safe for batch use:\n\n");
            for (String warning : warnings) {
                message.append("- ").append(warning).append('\n');
            }
            message.append("\nSave the batch macro anyway?");
            int choice = JOptionPane.showConfirmDialog(
                    dialog,
                    message.toString(),
                    "Batch Compatibility Warning",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            return choice == JOptionPane.OK_OPTION;
        }

        private void loadSavedMacroHistory() {
            savedMacroHistory.clear();
            if (!macroHistoryFile.exists()) {
                refreshSavedMacroCombo(null);
                return;
            }
            try {
                List<String> lines = Files.readAllLines(macroHistoryFile.toPath(), StandardCharsets.UTF_8);
                for (String line : lines) {
                    String path = historyPathFromLine(line);
                    if (path == null) continue;
                    File macroFile = canonicalFile(new File(path));
                    if (!containsMacroHistoryFile(macroFile)) {
                        savedMacroHistory.add(new MacroHistoryEntry(macroFile));
                    }
                }
            } catch (Exception ex) {
                IJ.log("Macro Builder: could not load saved macro history: " + cleanMessage(ex));
            }
            refreshSavedMacroCombo(null);
        }

        private void rememberSavedMacro(File file) {
            if (file == null) return;
            File macroFile = canonicalFile(file);
            removeMacroHistoryFile(macroFile);
            savedMacroHistory.add(0, new MacroHistoryEntry(macroFile));
            writeSavedMacroHistory();
            refreshSavedMacroCombo(macroFile);
        }

        private void writeSavedMacroHistory() {
            try {
                if (!stateDir.exists() && !stateDir.mkdirs()) {
                    IJ.log("Macro Builder: could not create state folder: " + stateDir.getAbsolutePath());
                    return;
                }
                if (savedMacroHistory.isEmpty()) {
                    if (macroHistoryFile.exists()) Files.delete(macroHistoryFile.toPath());
                    return;
                }
                StringBuilder text = new StringBuilder();
                for (MacroHistoryEntry entry : savedMacroHistory) {
                    if (entry.macroFile == null) continue;
                    text.append(entry.macroFile.getAbsolutePath()).append(System.lineSeparator());
                }
                Files.write(macroHistoryFile.toPath(), text.toString().getBytes(StandardCharsets.UTF_8));
            } catch (Exception ex) {
                IJ.log("Macro Builder: could not write saved macro history: " + cleanMessage(ex));
            }
        }

        private void refreshSavedMacroCombo(File selectedFile) {
            updatingSavedMacroCombo = true;
            try {
                savedMacroCombo.removeAllItems();
                if (savedMacroHistory.isEmpty()) {
                    savedMacroCombo.addItem(new MacroHistoryEntry("No saved macros"));
                    savedMacroCombo.setEnabled(false);
                } else {
                    savedMacroCombo.addItem(new MacroHistoryEntry("Choose saved macro..."));
                    MacroHistoryEntry selectedEntry = null;
                    for (MacroHistoryEntry entry : savedMacroHistory) {
                        savedMacroCombo.addItem(entry);
                        if (selectedFile != null && sameFile(entry.macroFile, selectedFile)) {
                            selectedEntry = entry;
                        }
                    }
                    savedMacroCombo.setEnabled(true);
                    if (selectedEntry != null) {
                        savedMacroCombo.setSelectedItem(selectedEntry);
                    } else {
                        savedMacroCombo.setSelectedIndex(0);
                    }
                }
                refreshSavedMacroComboTooltip();
            } finally {
                updatingSavedMacroCombo = false;
            }
        }

        private void refreshSavedMacroComboTooltip() {
            Object selected = savedMacroCombo.getSelectedItem();
            if (selected instanceof MacroHistoryEntry
                    && ((MacroHistoryEntry) selected).macroFile != null) {
                savedMacroCombo.setToolTipText(((MacroHistoryEntry) selected).macroFile.getAbsolutePath());
            } else if (savedMacroHistory.isEmpty()) {
                savedMacroCombo.setToolTipText("No saved macros have been saved yet.");
            } else {
                savedMacroCombo.setToolTipText("Choose a saved macro to load.");
            }
        }

        private void clearSavedMacroSelection() {
            if (savedMacroCombo.getItemCount() == 0 || savedMacroHistory.isEmpty()) return;
            updatingSavedMacroCombo = true;
            try {
                savedMacroCombo.setSelectedIndex(0);
                refreshSavedMacroComboTooltip();
            } finally {
                updatingSavedMacroCombo = false;
            }
        }

        private void loadSelectedSavedMacro() {
            if (updatingSavedMacroCombo) return;
            refreshSavedMacroComboTooltip();
            Object selected = savedMacroCombo.getSelectedItem();
            if (!(selected instanceof MacroHistoryEntry)) return;
            MacroHistoryEntry entry = (MacroHistoryEntry) selected;
            if (entry.macroFile == null) return;

            if (!entry.macroFile.exists()) {
                IJ.showMessage("Macro Builder", "Saved macro could not be found:\n"
                        + entry.macroFile.getAbsolutePath()
                        + "\n\nIt has been removed from the saved macro list.");
                removeMacroHistoryEntry(entry);
                return;
            }

            try {
                lastMacro = new String(Files.readAllBytes(entry.macroFile.toPath()), StandardCharsets.UTF_8);
                lastDag = loadDagSidecar(entry);
                lastMacroSource = "saved macro: " + entry.macroFile.getName();
                macroArea.setText(lastMacro);
                macroArea.setCaretPosition(0);
                writeState();
                refreshSourceLabel();
                setStatus("Loaded saved macro " + entry.macroFile.getName() + ".");
            } catch (Exception ex) {
                IJ.showMessage("Macro Builder", "Could not load saved macro:\n"
                        + entry.macroFile.getAbsolutePath()
                        + "\n\n" + cleanMessage(ex));
            }
        }

        private DagIR loadDagSidecar(MacroHistoryEntry entry) {
            if (entry.dagFile == null || !entry.dagFile.exists()) return null;
            try {
                return DagIRSerializer.fromJson(new String(
                        Files.readAllBytes(entry.dagFile.toPath()), StandardCharsets.UTF_8));
            } catch (Exception ex) {
                IJ.log("Macro Builder: could not load DAG sidecar "
                        + entry.dagFile.getAbsolutePath() + ": " + cleanMessage(ex));
                return null;
            }
        }

        private void removeMacroHistoryEntry(MacroHistoryEntry entry) {
            if (entry == null || entry.macroFile == null) return;
            removeMacroHistoryFile(entry.macroFile);
            writeSavedMacroHistory();
            refreshSavedMacroCombo(null);
            setStatus("Removed missing saved macro from the list.");
        }

        private void removeMacroHistoryFile(File macroFile) {
            if (macroFile == null) return;
            for (int i = savedMacroHistory.size() - 1; i >= 0; i--) {
                if (sameFile(savedMacroHistory.get(i).macroFile, macroFile)) {
                    savedMacroHistory.remove(i);
                }
            }
        }

        private boolean containsMacroHistoryFile(File macroFile) {
            if (macroFile == null) return false;
            for (MacroHistoryEntry entry : savedMacroHistory) {
                if (sameFile(entry.macroFile, macroFile)) return true;
            }
            return false;
        }

        private static String historyPathFromLine(String line) {
            if (line == null) return null;
            int tab = line.indexOf('\t');
            String path = tab >= 0 ? line.substring(0, tab) : line;
            path = path.trim();
            return path.isEmpty() ? null : path;
        }

        private static File canonicalFile(File file) {
            if (file == null) return null;
            try {
                return file.getCanonicalFile();
            } catch (Exception ignored) {
                return file.getAbsoluteFile();
            }
        }

        private static boolean sameFile(File first, File second) {
            if (first == null || second == null) return false;
            String firstPath = canonicalFile(first).getAbsolutePath();
            String secondPath = canonicalFile(second).getAbsolutePath();
            return File.separatorChar == '\\'
                    ? firstPath.equalsIgnoreCase(secondPath)
                    : firstPath.equals(secondPath);
        }

        private void loadState() {
            File macroFile = new File(stateDir, "C1_Filters.ijm");
            if (macroFile.exists()) {
                try {
                    lastMacro = new String(Files.readAllBytes(macroFile.toPath()), StandardCharsets.UTF_8);
                    macroArea.setText(lastMacro);
                    macroArea.setCaretPosition(0);
                } catch (Exception ignored) {
                    lastMacro = null;
                }
            }
            File dagFile = new File(stateDir, "C1_Sandbox.dag.json");
            if (dagFile.exists()) {
                try {
                    lastDag = DagIRSerializer.fromJson(new String(
                            Files.readAllBytes(dagFile.toPath()), StandardCharsets.UTF_8));
                } catch (Exception ignored) {
                    lastDag = null;
                }
            }
            loadLastOpenedImagePath();
            lastMacroSource = lastMacro == null || lastMacro.trim().isEmpty() ? "none" : "loaded state";
            refreshSourceLabel();
        }

        private void writeState() {
            try {
                if (!stateDir.exists()) stateDir.mkdirs();
                if (lastMacro != null) {
                    Files.write(new File(stateDir, "C1_Filters.ijm").toPath(),
                            lastMacro.getBytes(StandardCharsets.UTF_8));
                }
                File dagFile = new File(stateDir, "C1_Sandbox.dag.json");
                if (lastDag != null) {
                    Files.write(dagFile.toPath(), DagIRSerializer.toJson(lastDag).getBytes(StandardCharsets.UTF_8));
                } else if (dagFile.exists()) {
                    Files.delete(dagFile.toPath());
                }
            } catch (Exception ex) {
                IJ.log("Macro Builder: could not write state: " + cleanMessage(ex));
            }
        }

        private void loadLastOpenedImagePath() {
            File stateFile = new File(stateDir, LAST_OPENED_IMAGE_PATH_FILE);
            if (!stateFile.exists()) {
                refreshLastOpenedImageControls();
                return;
            }
            try {
                String path = new String(Files.readAllBytes(stateFile.toPath()), StandardCharsets.UTF_8).trim();
                lastOpenedImagePath = path.isEmpty() ? null : new File(path);
            } catch (Exception ignored) {
                lastOpenedImagePath = null;
            }
            refreshLastOpenedImageControls();
        }

        private void rememberOpenedImagePath(File selected) {
            if (selected == null) return;
            try {
                lastOpenedImagePath = selected.getCanonicalFile();
            } catch (Exception ignored) {
                lastOpenedImagePath = selected.getAbsoluteFile();
            }
            writeLastOpenedImagePath();
            refreshLastOpenedImageControls();
        }

        private void writeLastOpenedImagePath() {
            try {
                if (!stateDir.exists() && !stateDir.mkdirs()) {
                    IJ.log("Macro Builder: could not create state folder: " + stateDir.getAbsolutePath());
                    return;
                }
                File stateFile = new File(stateDir, LAST_OPENED_IMAGE_PATH_FILE);
                if (lastOpenedImagePath == null) {
                    if (stateFile.exists()) Files.delete(stateFile.toPath());
                    return;
                }
                Files.write(stateFile.toPath(),
                        lastOpenedImagePath.getAbsolutePath().getBytes(StandardCharsets.UTF_8));
            } catch (Exception ex) {
                IJ.log("Macro Builder: could not remember last opened image: " + cleanMessage(ex));
            }
        }

        private File existingLastOpenedImagePath() {
            if (lastOpenedImagePath == null || !lastOpenedImagePath.exists()) return null;
            return lastOpenedImagePath;
        }

        private void clearRememberedImagePath() {
            lastOpenedImagePath = null;
            writeLastOpenedImagePath();
            refreshLastOpenedImageControls();
        }

        private void refreshLastOpenedImageControls() {
            File remembered = existingLastOpenedImagePath();
            openLastButton.setEnabled(remembered != null);
            openLastButton.setToolTipText(remembered == null
                    ? "No remembered image or container is available."
                    : remembered.getAbsolutePath());
        }

        private void setStatus(String text) {
            statusLabel.setText(text == null ? " " : text);
        }

        private void refreshSourceLabel() {
            sourceLabel.setText("Macro source: " + lastMacroSource);
            refreshMacroActionControls();
        }

        private void refreshMacroActionControls() {
            boolean hasMacro = lastMacro != null && !lastMacro.trim().isEmpty();
            for (JButton button : macroActionButtons) {
                button.setEnabled(hasMacro);
            }
        }

        private static String describeImage(ImagePlus imp) {
            if (imp == null) return "No image";
            String title = imp.getTitle() == null || imp.getTitle().trim().isEmpty()
                    ? "Untitled"
                    : imp.getTitle();
            return title + " (" + imp.getWidth() + " x " + imp.getHeight()
                    + ", C=" + Math.max(1, imp.getNChannels())
                    + ", Z=" + Math.max(1, imp.getNSlices())
                    + ", T=" + Math.max(1, imp.getNFrames()) + ")";
        }

        private static ImagePlus duplicateImage(ImagePlus source, String title) {
            if (source == null) return null;
            ImagePlus copy = new Duplicator().run(source,
                    1, Math.max(1, source.getNChannels()),
                    1, Math.max(1, source.getNSlices()),
                    1, Math.max(1, source.getNFrames()));
            if (copy != null) copy.setTitle(title);
            return copy;
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
            String name = file.getName().toLowerCase();
            if (name.endsWith(extension)) return file;
            return new File(file.getParentFile(), file.getName() + extension);
        }

        private static File dagSidecarFor(File macroFile) {
            String name = macroFile.getName();
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            return new File(macroFile.getParentFile(), base + ".dag.json");
        }

        private static File defaultStateDir() {
            String home = IJ.getDirectory("home");
            if (home == null || home.trim().isEmpty()) {
                home = System.getProperty("user.home");
            }
            return new File(home, ".macro-builder");
        }

        private static final class MacroHistoryEntry {
            final File macroFile;
            final File dagFile;
            final String label;

            MacroHistoryEntry(File macroFile) {
                this(macroFile,
                        macroFile == null ? null : dagSidecarFor(macroFile),
                        macroFile == null ? "" : macroFile.getName());
            }

            MacroHistoryEntry(String label) {
                this(null, null, label);
            }

            private MacroHistoryEntry(File macroFile, File dagFile, String label) {
                this.macroFile = macroFile;
                this.dagFile = dagFile;
                this.label = label == null || label.trim().isEmpty() ? "" : label;
            }

            @Override public String toString() {
                return label;
            }
        }

        private static final class WorkflowIcon implements Icon {
            static final int BUILD = 0;
            static final int RECORD = 1;
            static final int COUNTS = 2;
            static final int OPEN_IMAGE = 3;

            private final int type;

            WorkflowIcon(int type) {
                this.type = type;
            }

            @Override public int getIconWidth() {
                return TILE_ICON_SIZE;
            }

            @Override public int getIconHeight() {
                return TILE_ICON_SIZE;
            }

            @Override public void paintIcon(java.awt.Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.translate(x, y);
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                    double scale = TILE_ICON_SIZE / 42.0;
                    g2.scale(scale, scale);
                    if (type == BUILD) {
                        paintBuild(g2);
                    } else if (type == RECORD) {
                        paintRecord(g2);
                    } else if (type == COUNTS) {
                        paintCounts(g2);
                    } else {
                        paintOpenImage(g2);
                    }
                } finally {
                    g2.dispose();
                }
            }

            private void paintBuild(Graphics2D g2) {
                fillBadge(g2, new Color(0xEAF2FF), new Color(0xBFD6FF));

                Graphics2D hammer = (Graphics2D) g2.create();
                hammer.rotate(Math.toRadians(-42), 21, 21);
                hammer.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                hammer.setColor(new Color(0x334155));
                hammer.drawLine(20, 17, 20, 34);
                hammer.setColor(new Color(0x475569));
                hammer.fillRoundRect(10, 8, 22, 8, 4, 4);
                hammer.setColor(new Color(0x1E3A8A));
                hammer.fillRoundRect(17, 28, 6, 10, 4, 4);
                hammer.dispose();

                Graphics2D driver = (Graphics2D) g2.create();
                driver.rotate(Math.toRadians(42), 21, 21);
                driver.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                driver.setColor(new Color(0xF59E0B));
                driver.drawLine(21, 10, 21, 24);
                driver.setColor(new Color(0x1D4ED8));
                driver.fillRoundRect(17, 23, 8, 15, 5, 5);
                driver.setColor(new Color(0x0F172A));
                driver.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                driver.drawLine(17, 9, 25, 9);
                driver.dispose();
            }

            private void paintRecord(Graphics2D g2) {
                fillBadge(g2, new Color(0xFFF1F2), new Color(0xFECACA));
                g2.setColor(new Color(0xFCA5A5));
                g2.fillOval(8, 8, 26, 26);
                g2.setColor(new Color(0xEF4444));
                g2.fillOval(11, 11, 20, 20);
                g2.setColor(new Color(0xB91C1C));
                g2.setStroke(new BasicStroke(2f));
                g2.drawOval(11, 11, 20, 20);
                g2.setColor(new Color(0xFEE2E2));
                g2.fillOval(15, 14, 6, 6);
            }

            private void paintCounts(Graphics2D g2) {
                fillBadge(g2, new Color(0xECFDF5), new Color(0xA7F3D0));
                g2.setColor(new Color(0xFFFFFF));
                g2.fillRoundRect(9, 9, 24, 24, 5, 5);
                g2.setColor(new Color(0x047857));
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(9, 9, 24, 24, 5, 5);
                g2.setColor(new Color(0xD1FAE5));
                g2.drawLine(17, 10, 17, 32);
                g2.drawLine(25, 10, 25, 32);
                g2.drawLine(10, 17, 32, 17);
                g2.drawLine(10, 25, 32, 25);
                g2.setColor(new Color(0x10B981));
                g2.fillOval(12, 12, 5, 5);
                g2.fillOval(23, 13, 5, 5);
                g2.fillOval(18, 21, 5, 5);
                g2.fillOval(27, 27, 4, 4);
                g2.setColor(new Color(0x065F46));
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(11, 35, 16, 38);
                g2.drawLine(16, 38, 25, 31);
            }

            private void paintOpenImage(Graphics2D g2) {
                fillBadge(g2, new Color(0xFFF7ED), new Color(0xFED7AA));
                g2.setColor(new Color(0xFBBF24));
                g2.fillRoundRect(7, 13, 26, 20, 5, 5);
                g2.setColor(new Color(0xD97706));
                g2.fillRoundRect(9, 10, 13, 7, 4, 4);
                g2.setColor(new Color(0xF59E0B));
                g2.fillRoundRect(6, 16, 30, 19, 5, 5);
                g2.setColor(new Color(0x92400E));
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(6, 16, 30, 19, 5, 5);

                g2.setColor(new Color(0xFFFFFF));
                g2.fillRoundRect(17, 19, 15, 13, 3, 3);
                g2.setColor(new Color(0xCBD5E1));
                g2.drawRoundRect(17, 19, 15, 13, 3, 3);
                g2.setColor(new Color(0x2563EB));
                g2.fillOval(20, 22, 4, 4);
                g2.setColor(new Color(0x16A34A));
                g2.fillPolygon(new int[] {18, 24, 31}, new int[] {32, 26, 32}, 3);
                g2.setColor(new Color(0x64748B));
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(33, 10, 36, 13);
                g2.drawLine(36, 13, 36, 30);
                g2.drawLine(33, 32, 36, 30);
            }

            private void fillBadge(Graphics2D g2, Color fill, Color border) {
                g2.setColor(fill);
                g2.fillRoundRect(3, 3, 36, 36, 12, 12);
                g2.setColor(border);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(3, 3, 36, 36, 12, 12);
            }
        }

        private static final class OpenAttempt {
            final ImagePlus image;
            final boolean cancelled;
            final String message;

            private OpenAttempt(ImagePlus image, boolean cancelled, String message) {
                this.image = image;
                this.cancelled = cancelled;
                this.message = message;
            }

            static OpenAttempt opened(ImagePlus image) {
                return new OpenAttempt(image, false, null);
            }

            static OpenAttempt cancelled() {
                return new OpenAttempt(null, true, null);
            }

            static OpenAttempt failed(String message) {
                return new OpenAttempt(null, false, message);
            }
        }

        private static final class ImageChoice {
            final ImagePlus image;

            ImageChoice(ImagePlus image) {
                this.image = image;
            }

            @Override public String toString() {
                return describeImage(image);
            }
        }

        private static String cleanMessage(Throwable t) {
            if (t == null) return "";
            String message = t.getMessage();
            return message == null || message.trim().isEmpty()
                    ? t.getClass().getSimpleName()
                    : message;
        }
    }
}
