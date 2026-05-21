package macro.builder.ui;

import ij.IJ;
import macro.builder.image.dag.DagIR;
import macro.builder.image.dag.DagIRSerializer;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

public final class MacroFileSaver {

    private MacroFileSaver() {
    }

    public static File promptAndSave(Component parent, String defaultName, String macroText, DagIR dag)
            throws java.io.IOException {
        if (macroText == null || macroText.trim().isEmpty()) {
            throw new IllegalArgumentException("No macro is available to save.");
        }

        File file = promptForMacroFile(parent, "Save Macro", defaultName);
        if (file == null) return null;

        saveMacro(file, macroText, dag);
        return file;
    }

    public static File promptForMacroFile(Component parent, String title, String defaultName)
            throws java.io.IOException {
        String dialogTitle = title == null || title.trim().isEmpty() ? "Save Macro" : title;
        File macrosDir = defaultMacrosDir();

        Object[] options = new Object[] {
                "Fiji macros folder",
                "Choose another location",
                "Cancel"
        };
        int choice = JOptionPane.showOptionDialog(
                parent,
                locationPromptMessage(macrosDir),
                dialogTitle,
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);

        if (choice == 0) {
            if (macrosDir == null) {
                throw new java.io.IOException("Could not find Fiji's macros folder.");
            }
            return promptForFileInMacrosFolder(parent, dialogTitle, macrosDir, defaultName);
        }
        if (choice == 1) {
            return promptForFileWithChooser(parent, dialogTitle, defaultName);
        }
        return null;
    }

    private static File promptForFileInMacrosFolder(
            Component parent,
            String title,
            File macrosDir,
            String defaultName) throws java.io.IOException {
        while (true) {
            String input = (String) JOptionPane.showInputDialog(
                    parent,
                    "Macro name:",
                    title,
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    null,
                    defaultName(defaultName));
            if (input == null) return null;

            File file = macroFileForName(macrosDir, input);
            if (file == null) {
                JOptionPane.showMessageDialog(parent,
                        "Enter a macro name.",
                        title,
                        JOptionPane.WARNING_MESSAGE);
                continue;
            }

            if (!confirmOverwrite(parent, title, file)) continue;
            return file;
        }
    }

    private static File promptForFileWithChooser(Component parent, String title, String defaultName) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setSelectedFile(new File(defaultFileName(defaultName)));
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("ImageJ macro (*.ijm)", "ijm"));

        while (true) {
            if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return null;
            File file = ensureIjmExtension(chooser.getSelectedFile());
            if (file == null) return null;
            if (confirmOverwrite(parent, title, file)) return file;
            chooser.setSelectedFile(file);
        }
    }

    private static boolean confirmOverwrite(Component parent, String title, File file) {
        if (file == null || !file.exists()) return true;
        int overwrite = JOptionPane.showConfirmDialog(
                parent,
                "Replace the existing macro?\n" + file.getAbsolutePath(),
                title,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        return overwrite == JOptionPane.OK_OPTION;
    }

    public static File saveMacro(File macroFile, String macroText, DagIR dag) throws java.io.IOException {
        if (macroFile == null) {
            throw new IllegalArgumentException("No macro file was provided.");
        }
        if (macroText == null || macroText.trim().isEmpty()) {
            throw new IllegalArgumentException("No macro is available to save.");
        }

        File parent = macroFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new java.io.IOException("Could not create macros folder: " + parent.getAbsolutePath());
        }

        Files.write(macroFile.toPath(), macroText.getBytes(StandardCharsets.UTF_8));
        File dagFile = dagSidecarFor(macroFile);
        if (dag != null) {
            Files.write(dagFile.toPath(), DagIRSerializer.toJson(dag).getBytes(StandardCharsets.UTF_8));
        } else if (dagFile.exists()) {
            Files.delete(dagFile.toPath());
        }
        return macroFile;
    }

    public static void deleteDagSidecar(File macroFile) throws java.io.IOException {
        if (macroFile == null) return;
        File dagFile = dagSidecarFor(macroFile);
        if (dagFile.exists()) {
            Files.delete(dagFile.toPath());
        }
    }

    public static File macroFileForName(File macrosDir, String name) {
        if (macrosDir == null || name == null) return null;
        String cleaned = cleanFileName(name);
        if (cleaned.length() == 0) return null;
        String base = removeIjmExtension(cleaned).trim();
        if (base.length() == 0) return null;
        return new File(macrosDir, base + ".ijm");
    }

    public static File dagSidecarFor(File macroFile) {
        String name = macroFile.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return new File(macroFile.getParentFile(), base + ".dag.json");
    }

    public static File ensureIjmExtension(File file) {
        if (file == null) return null;
        String name = file.getName();
        if (name.toLowerCase(Locale.ROOT).endsWith(".ijm")) {
            return file;
        }
        File parent = file.getParentFile();
        return parent == null
                ? new File(file.getPath() + ".ijm")
                : new File(parent, name + ".ijm");
    }

    public static String describeSaveLocation(File macroFile) {
        if (macroFile == null) return "";
        if (isInDefaultMacrosDir(macroFile)) {
            return "to Fiji's macros folder";
        }
        File parent = macroFile.getAbsoluteFile().getParentFile();
        return parent == null ? "" : "to " + parent.getAbsolutePath();
    }

    public static File defaultMacrosDir() {
        String macros = IJ.getDirectory("macros");
        if (macros != null && macros.trim().length() > 0) {
            return new File(macros);
        }

        String imagej = IJ.getDirectory("imagej");
        if (imagej != null && imagej.trim().length() > 0) {
            return new File(new File(imagej), "macros");
        }

        String home = IJ.getDirectory("home");
        if (home == null || home.trim().length() == 0) {
            home = System.getProperty("user.home");
        }
        return home == null || home.trim().length() == 0 ? null : new File(home, "macros");
    }

    private static boolean isInDefaultMacrosDir(File macroFile) {
        if (macroFile == null) return false;
        File macrosDir = defaultMacrosDir();
        if (macrosDir == null) return false;
        return sameDirectory(macroFile.getAbsoluteFile().getParentFile(), macrosDir);
    }

    private static boolean sameDirectory(File first, File second) {
        if (first == null || second == null) return false;
        try {
            return first.getCanonicalFile().equals(second.getCanonicalFile());
        } catch (IOException ignored) {
            return first.getAbsoluteFile().equals(second.getAbsoluteFile());
        }
    }

    private static String locationPromptMessage(File macrosDir) {
        StringBuilder message = new StringBuilder();
        message.append("Where should Macro Builder save this macro?");
        if (macrosDir != null) {
            message.append("\n\nFiji macros folder:\n")
                    .append(macrosDir.getAbsolutePath());
        }
        return message.toString();
    }

    private static String defaultFileName(String defaultName) {
        return defaultName(defaultName) + ".ijm";
    }

    private static String defaultName(String defaultName) {
        String cleaned = cleanFileName(defaultName);
        return cleaned.length() == 0 ? "Macro_Builder_Filter" : removeIjmExtension(cleaned);
    }

    private static String cleanFileName(String name) {
        if (name == null) return "";
        String trimmed = name.trim();
        StringBuilder out = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (ch < 32 || ch == '\\' || ch == '/' || ch == ':' || ch == '*'
                    || ch == '?' || ch == '"' || ch == '<' || ch == '>' || ch == '|') {
                out.append('_');
            } else {
                out.append(ch);
            }
        }
        return out.toString().trim();
    }

    private static String removeIjmExtension(String name) {
        return name.toLowerCase(java.util.Locale.ROOT).endsWith(".ijm")
                ? name.substring(0, name.length() - 4)
                : name;
    }
}
