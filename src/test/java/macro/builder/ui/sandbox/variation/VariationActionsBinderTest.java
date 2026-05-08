package macro.builder.ui.sandbox.variation;

import macro.builder.image.FilterMacroParser.OpType;
import macro.builder.image.dag.DagIR;
import macro.builder.image.dag.DagIRSerializer;
import macro.builder.image.dag.DagLine;
import macro.builder.image.dag.DagNode;
import macro.builder.image.variation.VariantPlan;
import macro.builder.ui.sandbox.SandboxModel;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VariationActionsBinderTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void promoteReplacesSandboxModelDagAndRecordsLog() {
        DagIR base = dag("sigma=1 stack");
        DagIR promoted = dag("sigma=2 stack");
        SandboxModel model = SandboxModel.fromDag(base);
        VariationSessionLog log = new VariationSessionLog();
        AtomicReference<String> status = new AtomicReference<String>();
        VariationActionsBinder binder = new VariationActionsBinder(
                model, null, null, log, temp.getRoot(), "source image", status::set);

        binder.onPromote(new VariantPlan("sigma=2", promoted, null));

        assertEquals(promoted, model.toDag());
        assertEquals("Promoted variation: sigma=2", status.get());
        assertEquals(1, log.entries().size());
        assertEquals("PROMOTE", log.entries().get(0).action);
    }

    @Test
    public void savePresetWritesDagAndMacroFilesUnderStateFolder() throws Exception {
        VariantPlan plan = new VariantPlan("sigma=2", dag("sigma=2 stack"), null);
        VariationSessionLog log = new VariationSessionLog();
        VariationActionsBinder binder = new VariationActionsBinder(
                SandboxModel.fromDag(dag("sigma=1 stack")),
                null,
                null,
                log,
                temp.getRoot(),
                "source image",
                null);

        File dagFile = binder.savePreset(plan, "My Preset");

        assertEquals(new File(new File(temp.getRoot(), "variation-presets"), "My_Preset.dag.json"),
                dagFile);
        File macroFile = new File(dagFile.getParentFile(), "My_Preset.ijm");
        assertTrue(dagFile.isFile());
        assertTrue(macroFile.isFile());
        assertEquals(plan.dag, DagIRSerializer.fromJson(new String(
                Files.readAllBytes(dagFile.toPath()), StandardCharsets.UTF_8)));
        assertTrue(new String(Files.readAllBytes(macroFile.toPath()), StandardCharsets.UTF_8)
                .contains("run(\"Gaussian Blur...\", \"sigma=2 stack\")"));
        assertEquals("SAVE_PRESET", log.entries().get(0).action);
    }

    @Test
    public void defaultPresetNameSanitizesUnsafeCharacters() {
        String name = VariationActionsBinder.defaultPresetName(
                "cell sample.tif", "sigma=2 / stack", new java.util.Date(0L));

        assertTrue(name.startsWith("cell_sample.tif_sigma=2_stack_"));
        assertTrue(name.matches("cell_sample\\.tif_sigma=2_stack_[0-9]{4}"));
    }

    private static DagIR dag(String args) {
        DagLine line = new DagLine("line_A",
                Collections.singletonList(new DagNode("n1", OpType.GAUSSIAN_BLUR, args)),
                1);
        return new DagIR(1, 1,
                Collections.singletonList(line),
                Collections.<macro.builder.image.dag.Combiner>emptyList(),
                "line_A",
                "native");
    }
}
