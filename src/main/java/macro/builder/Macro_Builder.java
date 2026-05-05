package macro.builder;

import macro.builder.image.FilterExecutor;
import macro.builder.image.dag.DagIR;
import macro.builder.image.dag.DagIRSerializer;
import macro.builder.ui.MacroPreviewHandler;
import macro.builder.ui.RecorderDialog;
import macro.builder.ui.sandbox.SandboxDialog;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.Duplicator;
import ij.plugin.PlugIn;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

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
        private final JDialog dialog = new JDialog((java.awt.Frame) null, "Macro Builder", false);
        private final JLabel imageLabel = new JLabel("No image selected.");
        private final JLabel sourceLabel = new JLabel("Macro source: none");
        private final JLabel statusLabel = new JLabel(" ");
        private final JTextArea macroArea = new JTextArea();
        private final File stateDir = defaultStateDir();

        private ImagePlus sourceImage;
        private ImagePlus macroPreview;
        private ImagePlus sandboxPreview;
        private ImagePlus recorderSample;
        private String lastMacro;
        private String lastMacroSource = "none";
        private DagIR lastDag;

        SessionDialog() {
            buildUi();
            loadState();
            useCurrentImage(false);
        }

        void open() {
            dialog.pack();
            dialog.setSize(new Dimension(780, 450));
            dialog.setLocationRelativeTo(null);
            dialog.setVisible(true);
        }

        private void buildUi() {
            dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            dialog.setLayout(new BorderLayout(8, 8));

            JPanel top = new JPanel(new BorderLayout(8, 6));
            top.setBorder(BorderFactory.createEmptyBorder(10, 12, 0, 12));
            JLabel header = new JLabel("Build a filter macro from one open image or image stack.");
            header.setFont(header.getFont().deriveFont(Font.BOLD, 13f));
            top.add(header, BorderLayout.NORTH);
            top.add(imageLabel, BorderLayout.CENTER);

            JPanel imageButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            JButton current = new JButton("Use current Fiji image");
            JButton open = new JButton("Open image/stack...");
            current.addActionListener(e -> useCurrentImage(true));
            open.addActionListener(e -> openImageFromDisk());
            imageButtons.add(current);
            imageButtons.add(open);
            top.add(imageButtons, BorderLayout.SOUTH);
            dialog.add(top, BorderLayout.NORTH);

            macroArea.setEditable(false);
            macroArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            macroArea.setLineWrap(false);
            macroArea.setText("");
            JScrollPane scroll = new JScrollPane(macroArea);
            scroll.setBorder(BorderFactory.createTitledBorder("Last built macro"));
            JPanel macroPanel = new JPanel(new BorderLayout(0, 4));
            macroPanel.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
            macroPanel.add(sourceLabel, BorderLayout.NORTH);
            macroPanel.add(scroll, BorderLayout.CENTER);
            dialog.add(macroPanel, BorderLayout.CENTER);

            JPanel footer = new JPanel(new BorderLayout());
            footer.setBorder(BorderFactory.createEmptyBorder(0, 12, 10, 12));
            statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
            footer.add(statusLabel, BorderLayout.NORTH);

            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            JButton build = new JButton("Build step-by-step");
            JButton record = new JButton("Record in Fiji");
            JButton preview = new JButton("Preview macro");
            JButton run = new JButton("Run macro on current image");
            build.addActionListener(e -> openSandbox());
            record.addActionListener(e -> openRecorder());
            preview.addActionListener(e -> previewLastMacro());
            run.addActionListener(e -> runLastMacroOnDuplicate());
            left.add(build);
            left.add(record);
            left.add(preview);
            left.add(run);

            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            JButton save = new JButton("Save macro...");
            JButton close = new JButton("Close");
            save.addActionListener(e -> saveCurrentMacro());
            close.addActionListener(e -> dialog.dispose());
            right.add(save);
            right.add(close);

            footer.add(left, BorderLayout.WEST);
            footer.add(right, BorderLayout.EAST);
            dialog.add(footer, BorderLayout.SOUTH);

            dialog.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosed(java.awt.event.WindowEvent e) {
                    closeImageQuietly(macroPreview);
                    closeImageQuietly(sandboxPreview);
                    closeImageQuietly(recorderSample);
                }
            });
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
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Open Image or Stack");
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            chooser.addChoosableFileFilter(new FileNameExtensionFilter(
                    "Image files", "tif", "tiff", "png", "jpg", "jpeg", "gif", "bmp", "ics", "ids"));
            chooser.addChoosableFileFilter(new FileNameExtensionFilter(
                    "Bio-Formats containers", "lif", "czi", "nd2", "oib", "oif", "lsm", "zvi", "ome"));
            if (chooser.showOpenDialog(dialog) != JFileChooser.APPROVE_OPTION) return;
            File selected = chooser.getSelectedFile();
            ImagePlus opened = openImageOrContainer(selected);
            if (opened == null) {
                IJ.showMessage("Macro Builder", "Fiji could not open that file as an image or stack.\n\n"
                        + "For microscope containers, this requires Fiji's Bio-Formats plugin. "
                        + "Some files may also ask you to choose a series before opening.");
                return;
            }
            sourceImage = opened;
            refreshImageLabel();
        }

        private ImagePlus openImageOrContainer(File selected) {
            String path = selected.getAbsolutePath();
            ImagePlus opened = IJ.openImage(path);
            if (opened != null) {
                opened.show();
                setStatus("Opened " + selected.getName() + ".");
                return opened;
            }

            opened = openWithBioFormats(path);
            if (opened != null) {
                setStatus("Opened " + selected.getName() + " with Bio-Formats.");
            }
            return opened;
        }

        private ImagePlus openWithBioFormats(String path) {
            ImagePlus before = WindowManager.getCurrentImage();
            int[] beforeIds = WindowManager.getIDList();
            try {
                IJ.run("Bio-Formats Importer",
                        "open=[" + path + "] autoscale color_mode=Default view=Hyperstack stack_order=XYCZT");
            } catch (Throwable t) {
                IJ.log("Macro Builder: Bio-Formats fallback failed: " + cleanMessage(t));
                return null;
            }

            ImagePlus current = WindowManager.getCurrentImage();
            if (current != null && current != before) {
                return current;
            }
            return findOpenedImage(beforeIds);
        }

        private static ImagePlus findOpenedImage(int[] beforeIds) {
            int[] afterIds = WindowManager.getIDList();
            if (afterIds == null) return null;
            for (int i = afterIds.length - 1; i >= 0; i--) {
                if (!containsId(beforeIds, afterIds[i])) {
                    return WindowManager.getImage(afterIds[i]);
                }
            }
            return null;
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
            imageLabel.setText("Selected: " + sourceImage.getTitle()
                    + " (" + sourceImage.getWidth() + " x " + sourceImage.getHeight()
                    + ", slices=" + sourceImage.getStackSize() + ")");
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
                    if (!ensureImage()) return;
                    ImagePlus work = duplicateImage(sourceImage, "Macro Builder Preview Source");
                    try {
                        FilterExecutor.runThreadSafe(work, macroContent);
                        closeImageQuietly(macroPreview);
                        work.setTitle("Macro Builder Preview");
                        work.show();
                        macroPreview = work;
                        work = null;
                    } finally {
                        closeImageQuietly(work);
                    }
                }

                @Override public void cleanup() {
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
            ImagePlus work = duplicateImage(sourceImage, "Macro Builder Run Result");
            if (work == null) {
                IJ.showMessage("Macro Builder", "Could not duplicate the selected image.");
                return;
            }
            try {
                FilterExecutor.runThreadSafe(work, lastMacro);
                work.setTitle("Macro Builder Run Result");
                work.show();
                setStatus("Run complete on duplicate image.");
            } catch (Exception ex) {
                closeImageQuietly(work);
                IJ.showMessage("Macro Builder", "Run failed:\n" + cleanMessage(ex));
            }
        }

        private void previewLastMacro() {
            if (lastMacro == null || lastMacro.trim().isEmpty()) {
                IJ.showMessage("Macro Builder", "No macro has been built or recorded yet.");
                return;
            }
            try {
                createMacroPreviewHandler().preview(lastMacro);
                setStatus("Preview complete.");
            } catch (Exception ex) {
                IJ.showMessage("Macro Builder", "Preview failed:\n" + cleanMessage(ex));
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

        private void setStatus(String text) {
            statusLabel.setText(text == null ? " " : text);
        }

        private void refreshSourceLabel() {
            sourceLabel.setText("Macro source: " + lastMacroSource);
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

        private static String cleanMessage(Throwable t) {
            if (t == null) return "";
            String message = t.getMessage();
            return message == null || message.trim().isEmpty()
                    ? t.getClass().getSimpleName()
                    : message;
        }
    }
}
