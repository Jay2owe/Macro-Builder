package macro.builder.analysis;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MacroBatchCompatibilityTest {

    @Test
    public void detectsCommonUnsafeBatchPatterns() {
        String macro = ""
                + "selectWindow(\"Raw Image\");\n"
                + "waitForUser(\"Check this\");\n"
                + "open(\"C:/data/input.tif\");\n"
                + "saveAs(\"Tiff\", \"C:/data/output.tif\");\n"
                + "close(\"*\");\n";

        List<String> warnings = MacroBatchCompatibility.warnings(macro);

        assertEquals(5, warnings.size());
        assertTrue(warnings.get(0).contains("selectWindow(\"Raw Image\")"));
        assertTrue(warnings.get(1).contains("waitForUser"));
        assertTrue(warnings.get(2).contains("open"));
        assertTrue(warnings.get(3).contains("saveAs"));
        assertTrue(warnings.get(4).contains("close"));
    }

    @Test
    public void ignoresLineCommentsAndMacroBuilderTemporaryWindows() {
        String macro = ""
                + "// waitForUser(\"comment only\");\n"
                + "// open(\"C:/data/input.tif\");\n"
                + "selectWindow(\"Macro Builder Preview\");\n";

        assertTrue(MacroBatchCompatibility.warnings(macro).isEmpty());
    }
}
