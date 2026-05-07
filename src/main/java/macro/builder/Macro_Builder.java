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
        private static final Dimension TILE_SIZE = new Dimension(96, 104);
        private static final int TILE_ICON_SIZE = 44;
        private static final int LEFT_COLUMN_WIDTH = 230;
        private static final int RIGHT_COLUMN_WIDTH = 200;

        private final JDialog dialog = new JDialog((java.awt.Frame) null, "Macro Builder", false);
        private final JLabel imageLabel = new JLabel("No image selected.");
        private final JLabel sourceLabel = new JLabel("Macro source: none");
        private final JLabel statusLabel = new JLabel(" ");
        private final JProgressBar macroProgress = new JProgressBar(0, 100);
        private final JTextArea macroArea = new JTextArea();
        private final File stateDir = defaultStateDir();
        private final JButton openLastButton = new JButton("Open last image/container");

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

            JPanel shell = new JPanel(new BorderLayout(12, 0));
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
            Dimension columnSize = new Dimension(LEFT_COLUMN_WIDTH, TILE_SIZE.height * 2 + 34);
            panel.setPreferredSize(columnSize);
            panel.setMinimumSize(new Dimension(LEFT_COLUMN_WIDTH, 1));

            JPanel content = new JPanel(new BorderLayout(0, 8));
            JLabel title = new JLabel("Workflows");
            title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
            content.add(title, BorderLayout.NORTH);

            JPanel grid = new JPanel(new GridLayout(2, 2, 8, 8));
            grid.setPreferredSize(new Dimension(TILE_SIZE.width * 2 + 8, TILE_SIZE.height * 2 + 8));
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
            content.add(gridWrap, BorderLayout.CENTER);
            panel.add(content, BorderLayout.NORTH);
            return panel;
        }

        private JPanel buildMacroPanel() {
            macroArea.setEditable(false);
            macroArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            macroArea.setLineWrap(false);
            macroArea.setText("");

            JPanel panel = new JPanel(new BorderLayout(0, 8));
            JPanel imagePanel = new JPanel(new BorderLayout(0, 4));
            JLabel imageTitle = new JLabel("Selected image");
            imageTitle.setFont(imageTitle.getFont().deriveFont(Font.BOLD, 13f));
            imagePanel.add(imageTitle, BorderLayout.NORTH);
            imagePanel.add(imageLabel, BorderLayout.CENTER);

            JPanel imageButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            JButton current = createSecondaryButton("Use current Fiji image");
            current.addActionListener(e -> useCurrentImage(true));
            openLastButton.addActionListener(e -> openLastImageOrContainer());
            openLastButton.setMargin(new Insets(3, 8, 3, 8));
            openLastButton.setEnabled(false);
            imageButtons.add(current);
            imageButtons.add(openLastButton);
            imagePanel.add(imageButtons, BorderLayout.SOUTH);
            panel.add(imagePanel, BorderLayout.NORTH);

            JScrollPane scroll = new JScrollPane(macroArea);
            scroll.setBorder(BorderFactory.createTitledBorder("Last built macro"));
            JPanel macroPanel = new JPanel(new BorderLayout(0, 4));
            macroPanel.add(sourceLabel, BorderLayout.NORTH);
            macroPanel.add(scroll, BorderLayout.CENTER);
            panel.add(macroPanel, BorderLayout.CENTER);
            return panel;
        }

        private JPanel buildActionColumn() {
            JPanel panel = new JPanel(new BorderLayout(0, 8));
            panel.setPreferredSize(new Dimension(RIGHT_COLUMN_WIDTH, 1));
            panel.setMinimumSize(new Dimension(RIGHT_COLUMN_WIDTH, 1));

            JPanel content = new JPanel(new BorderLayout(0, 8));
            JLabel title = new JLabel("Last macro");
            title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
            content.add(title, BorderLayout.NORTH);

            JPanel buttons = new JPanel(new GridLayout(0, 1, 0, 6));
            JButton preview = createActionButton("Preview macro");
            JButton run = createActionButton("Run macro");
            JButton save = createActionButton("Save macro...");
            JButton saveBatch = createActionButton("Save batch macro...");
            JButton close = createActionButton("Close");
            preview.addActionListener(e -> previewLastMacro());
            run.addActionListener(e -> runLastMacroOnDuplicate());
            save.addActionListener(e -> saveCurrentMacro());
            saveBatch.addActionListener(e -> saveBatchMacro());
            close.addActionListener(e -> dialog.dispose());
            buttons.add(preview);
            buttons.add(run);
            buttons.add(save);
            buttons.add(saveBatch);
            buttons.add(close);
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
            button.setMargin(new Insets(6, 4, 6, 4));
            button.setToolTipText(tooltip);
            return button;
        }

        private JButton createSecondaryButton(String text) {
            JButton button = new JButton(text);
            button.setMargin(new Insets(3, 8, 3, 8));
            return button;
        }

        private JButton createActionButton(String text) {
            JButton button = new JButton(text);
            button.setMargin(new Insets(4, 8, 4, 8));
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
            macroArea.setText(lastMacro);
            macroArea.setCaretPosition(0);
            writeState();
            refreshSourceLabel();
            setStatus("Built filter macro. Use Save macro... to export it.");
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
                macroArea.setText(lastMacro);
                macroArea.setCaretPosition(0);
                writeState();
                refreshSourceLabel();
                setStatus("Recorded filter macro. Use Save macro... to export it.");
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
            if (lastMacro == null || lastMacro.trim().isEmpty()) {
                IJ.showMessage("Macro Builder", "No macro has been built or recorded yet.");
                return;
            }
            runMacroOnDuplicateAsync(lastMacro);
        }

        private void openCountTester() {
            if (lastMacro == null || lastMacro.trim().isEmpty()) {
                IJ.showMessage("Macro Builder", "No macro has been built or recorded yet.");
                return;
            }
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
            if (lastMacro == null || lastMacro.trim().isEmpty()) {
                IJ.showMessage("Macro Builder", "No macro has been built or recorded yet.");
                return;
            }
            previewMacroAsync(lastMacro);
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
            if (lastMacro == null || lastMacro.trim().isEmpty()) {
                IJ.showMessage("Macro Builder", "No macro has been built or recorded yet.");
                return;
            }
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Save Macro");
            chooser.setSelectedFile(new File("Macro_Builder_Filter.ijm"));
            chooser.addChoosableFileFilter(new FileNameExtensionFilter("ImageJ macro (*.ijm)", "ijm"));
            if (chooser.showSaveDialog(dialog) != JFileChooser.APPROVE_OPTION) return;
            File file = ensureExtension(chooser.getSelectedFile(), ".ijm");
            try {
                Files.write(file.toPath(), lastMacro.getBytes(StandardCharsets.UTF_8));
                if (lastDag != null) {
                    File dagFile = dagSidecarFor(file);
                    Files.write(dagFile.toPath(), DagIRSerializer.toJson(lastDag).getBytes(StandardCharsets.UTF_8));
                }
                setStatus("Saved " + file.getName() + ".");
            } catch (Exception ex) {
                IJ.showMessage("Macro Builder", "Could not save macro:\n" + cleanMessage(ex));
            }
        }

        private void saveBatchMacro() {
            if (lastMacro == null || lastMacro.trim().isEmpty()) {
                IJ.showMessage("Macro Builder", "No macro has been built or recorded yet.");
                return;
            }
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
                g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(0x4B5563));
                g2.drawLine(12, 34, 33, 13);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(29, 9, 38, 18);
                g2.setColor(new Color(0x2563EB));
                g2.fillRoundRect(9, 8, 7, 15, 4, 4);
                g2.setColor(new Color(0x1F2937));
                g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(14, 17, 34, 35);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(34, 35, 38, 31);
            }

            private void paintRecord(Graphics2D g2) {
                g2.setColor(new Color(0xFEE2E2));
                g2.fillOval(6, 6, 32, 32);
                g2.setColor(new Color(0xDC2626));
                g2.fillOval(12, 12, 20, 20);
                g2.setColor(new Color(0x991B1B));
                g2.setStroke(new BasicStroke(2f));
                g2.drawOval(12, 12, 20, 20);
            }

            private void paintCounts(Graphics2D g2) {
                g2.setColor(new Color(0xECFDF5));
                g2.fillOval(5, 5, 34, 34);
                g2.setColor(new Color(0x047857));
                g2.setStroke(new BasicStroke(2f));
                g2.drawOval(5, 5, 34, 34);
                g2.setColor(new Color(0x10B981));
                g2.fillOval(14, 13, 6, 6);
                g2.fillOval(25, 14, 5, 5);
                g2.fillOval(18, 25, 5, 5);
                g2.fillOval(29, 27, 4, 4);
                g2.setColor(new Color(0x065F46));
                g2.drawLine(12, 34, 34, 10);
            }

            private void paintOpenImage(Graphics2D g2) {
                g2.setColor(new Color(0xD97706));
                g2.fillRoundRect(7, 12, 15, 9, 4, 4);
                g2.setColor(new Color(0xF59E0B));
                g2.fillRoundRect(5, 17, 34, 21, 5, 5);
                g2.setColor(new Color(0xFFFFFF));
                g2.fillRect(18, 21, 15, 12);
                g2.setColor(new Color(0x2563EB));
                g2.fillOval(21, 23, 4, 4);
                g2.setColor(new Color(0x16A34A));
                g2.fillPolygon(new int[] {19, 26, 32}, new int[] {33, 27, 33}, 3);
                g2.setColor(new Color(0x92400E));
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(5, 17, 34, 21, 5, 5);
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
