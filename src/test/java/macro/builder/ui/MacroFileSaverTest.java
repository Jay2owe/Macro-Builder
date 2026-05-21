package macro.builder.ui;

import macro.builder.image.dag.DagIR;
import macro.builder.image.dag.DagIRSerializer;
import macro.builder.image.dag.DagLine;
import macro.builder.image.dag.DagNode;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MacroFileSaverTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void macroFileForNameCleansInvalidCharactersAndAddsExtension() throws Exception {
        File macrosDir = temp.newFolder("macros");

        File file = MacroFileSaver.macroFileForName(macrosDir, " My:Filter? ");

        assertEquals(new File(macrosDir, "My_Filter_.ijm"), file);
    }

    @Test
    public void ensureIjmExtensionAddsExtensionOnlyWhenMissing() throws Exception {
        File folder = temp.newFolder("exports");

        assertEquals(new File(folder, "Batch.ijm"),
                MacroFileSaver.ensureIjmExtension(new File(folder, "Batch")));
        assertEquals(new File(folder, "Batch.IJM"),
                MacroFileSaver.ensureIjmExtension(new File(folder, "Batch.IJM")));
    }

    @Test
    public void saveMacroWritesMacroAndDagSidecar() throws Exception {
        File macrosDir = temp.newFolder("macros");
        File macro = MacroFileSaver.macroFileForName(macrosDir, "My Filter");
        DagIR dag = emptyDag(2);

        MacroFileSaver.saveMacro(macro, "run(\"Gaussian Blur...\", \"sigma=2\");\n", dag);

        assertTrue(macro.isFile());
        assertEquals("run(\"Gaussian Blur...\", \"sigma=2\");\n",
                new String(Files.readAllBytes(macro.toPath()), StandardCharsets.UTF_8));
        File sidecar = MacroFileSaver.dagSidecarFor(macro);
        assertTrue(sidecar.isFile());
        assertEquals(2, DagIRSerializer.fromJson(new String(
                Files.readAllBytes(sidecar.toPath()), StandardCharsets.UTF_8)).primaryChannel);
    }

    @Test
    public void saveMacroWithoutDagDeletesStaleSidecar() throws Exception {
        File macrosDir = temp.newFolder("macros");
        File macro = MacroFileSaver.macroFileForName(macrosDir, "Recorded");
        File sidecar = MacroFileSaver.dagSidecarFor(macro);
        Files.write(sidecar.toPath(), "{}".getBytes(StandardCharsets.UTF_8));

        MacroFileSaver.saveMacro(macro, "run(\"Invert\");\n", null);

        assertTrue(macro.isFile());
        assertFalse(sidecar.exists());
    }

    @Test
    public void deleteDagSidecarRemovesStaleGraphFile() throws Exception {
        File macrosDir = temp.newFolder("macros");
        File macro = MacroFileSaver.macroFileForName(macrosDir, "Batch Wrapper");
        File sidecar = MacroFileSaver.dagSidecarFor(macro);
        Files.write(sidecar.toPath(), "{}".getBytes(StandardCharsets.UTF_8));

        MacroFileSaver.deleteDagSidecar(macro);

        assertFalse(sidecar.exists());
    }

    private static DagIR emptyDag(int primaryChannel) {
        return new DagIR(primaryChannel, 2,
                Collections.singletonList(new DagLine("line_A",
                        Collections.<DagNode>emptyList(), primaryChannel)),
                Collections.<macro.builder.image.dag.Combiner>emptyList(),
                "line_A",
                "native");
    }
}
