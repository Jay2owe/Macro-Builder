package macro.builder.ui.sandbox.variation;

import ij.IJ;
import ij.plugin.frame.Recorder;
import macro.builder.image.dag.DagIRSerializer;
import macro.builder.image.dag.DagToIjmEmitter;
import macro.builder.image.variation.VariantPlan;
import macro.builder.ui.sandbox.DagCanvasPanel;
import macro.builder.ui.sandbox.SandboxModel;

import javax.swing.JOptionPane;
import java.awt.Component;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Consumer;

public final class VariationActionsBinder implements TileActionListener {

    private final SandboxModel model;
    private final DagCanvasPanel canvas;
    private final Component owner;
    private final VariationSessionLog sessionLog;
    private final File presetDir;
    private final Consumer<String> statusSink;
    private final String sourceTitle;

    public VariationActionsBinder(SandboxModel model,
                                  DagCanvasPanel canvas,
                                  Component owner,
                                  VariationSessionLog sessionLog,
                                  File stateDir,
                                  String sourceTitle,
                                  Consumer<String> statusSink) {
        if (model == null) throw new IllegalArgumentException("model must not be null");
        this.model = model;
        this.canvas = canvas;
        this.owner = owner;
        this.sessionLog = sessionLog;
        this.presetDir = new File(stateDir == null ? defaultStateDir() : stateDir, "variation-presets");
        this.statusSink = statusSink;
        this.sourceTitle = sourceTitle == null || sourceTitle.trim().isEmpty() ? "source" : sourceTitle;
    }

    @Override
    public void onPromote(VariantPlan plan) {
        if (plan == null || plan.dag == null) return;
        model.replaceWith(plan.dag);
        if (canvas != null) canvas.rebuild();
        if (sessionLog != null) sessionLog.recordPromote(plan);
        if (Recorder.record) {
            Recorder.recordString("// macro-builder variation: promoted " + plan.label + "\n");
            Recorder.recordString(DagToIjmEmitter.emit(plan.dag));
        }
        showStatus("Promoted variation: " + plan.label);
    }

    @Override
    public void onSavePreset(VariantPlan plan) {
        if (plan == null || plan.dag == null) return;
        String defaultName = defaultPresetName(sourceTitle, plan.label, new Date());
        String name = (String) JOptionPane.showInputDialog(owner,
                "Preset name:",
                "Save variation preset",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                defaultName);
        if (name == null || name.trim().isEmpty()) return;
        try {
            savePreset(plan, name.trim());
        } catch (Exception ex) {
            IJ.showMessage("Variations", "Could not save preset:\n" + ex.getMessage());
        }
    }

    public File savePreset(VariantPlan plan, String name) throws java.io.IOException {
        if (plan == null || plan.dag == null) throw new IllegalArgumentException("plan must not be null");
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("name must not be empty");
        if (!presetDir.exists() && !presetDir.mkdirs()) {
            throw new java.io.IOException("Could not create " + presetDir.getAbsolutePath());
        }
        String safe = sanitize(name);
        File dagFile = new File(presetDir, safe + ".dag.json");
        File macroFile = new File(presetDir, safe + ".ijm");
        Files.write(dagFile.toPath(), DagIRSerializer.toJson(plan.dag).getBytes(StandardCharsets.UTF_8));
        Files.write(macroFile.toPath(), DagToIjmEmitter.emit(plan.dag).getBytes(StandardCharsets.UTF_8));
        if (sessionLog != null) sessionLog.recordSavePreset(plan, safe);
        if (Recorder.record) {
            Recorder.recordString("// macro-builder variation: saved preset " + safe + "\n");
        }
        showStatus("Saved variation preset: " + safe);
        return dagFile;
    }

    static String defaultPresetName(String sourceTitle, String label, Date date) {
        String time = new SimpleDateFormat("HHmm").format(date == null ? new Date() : date);
        return sanitize(sourceTitle) + "_" + sanitize(label) + "_" + time;
    }

    static String sanitize(String value) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) return "variation";
        v = v.replaceAll("[^A-Za-z0-9._=-]+", "_");
        v = v.replaceAll("_+", "_");
        while (v.startsWith("_")) v = v.substring(1);
        while (v.endsWith("_")) v = v.substring(0, v.length() - 1);
        return v.length() == 0 ? "variation" : v;
    }

    private void showStatus(String text) {
        if (statusSink != null) statusSink.accept(text);
    }

    private static File defaultStateDir() {
        String home = IJ.getDirectory("home");
        if (home == null || home.trim().isEmpty()) home = System.getProperty("user.home");
        return new File(home, ".macro-builder");
    }
}
