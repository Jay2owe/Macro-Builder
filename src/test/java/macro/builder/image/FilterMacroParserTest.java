package macro.builder.image;

import macro.builder.image.FilterMacroParser.Op;
import macro.builder.image.FilterMacroParser.OpType;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FilterMacroParserTest {

    @Test
    public void parsesKnownFastOperationsAndParameters() {
        List<Op> ops = FilterMacroParser.parseString(
                "// comments are ignored\n"
                        + "run(\"Gaussian Blur...\", \"sigma=2 stack\");\n"
                        + "run(\"Median...\", \"radius=2 stack\");\n");

        assertEquals(2, ops.size());
        assertEquals(OpType.GAUSSIAN_BLUR, ops.get(0).type);
        assertEquals(2.0, ops.get(0).getParam("sigma"), 0.0001);
        assertTrue(ops.get(0).hasFlag("stack"));

        assertEquals(OpType.MEDIAN, ops.get(1).type);
        assertEquals(2.0, ops.get(1).getParam("radius"), 0.0001);
        assertTrue(ops.get(1).hasFlag("stack"));
    }

    @Test
    public void preservesUnknownOperationsForLegacyFallback() {
        List<Op> ops = FilterMacroParser.parseString(
                "run(\"Totally Custom Filter...\", \"alpha=1 stack\");\n");

        assertEquals(1, ops.size());
        assertEquals(OpType.UNKNOWN, ops.get(0).type);
        assertEquals("run(\"Totally Custom Filter...\", \"alpha=1 stack\");", ops.get(0).args);
    }
}
