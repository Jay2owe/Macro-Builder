package macro.builder.ui.sandbox.variation;

import ij.ImagePlus;
import ij.process.FloatProcessor;
import macro.builder.image.FilterMacroParser.OpType;
import macro.builder.image.dag.DagIR;
import macro.builder.image.dag.DagLine;
import macro.builder.image.dag.DagNode;
import macro.builder.image.variation.VariantPlan;
import org.junit.Test;

import javax.swing.JLabel;
import java.awt.Component;
import java.awt.Container;
import java.util.Collections;

import static org.junit.Assert.assertTrue;

public class TilePanelTest {

    @Test
    public void appliedVariantShowsBadgeAndCaptionPrefix() {
        TilePanel tile = new TilePanel(
                new ImagePlus("variant", new FloatProcessor(1, 1)),
                "sigma=2",
                false,
                new VariantPlan("sigma=2", dag(), null));
        tile.setActions(new TileActionListener() {
            @Override public void onPromote(VariantPlan plan) {
                // Not used by this test.
            }

            @Override public void onSavePreset(VariantPlan plan) {
                // Not used by this test.
            }
        }, null);

        tile.setAppliedToBuilder(true);

        assertTrue(containsLabel(tile, "Applied"));
        assertTrue(containsLabel(tile, "Applied to builder: sigma=2"));
    }

    private static DagIR dag() {
        DagLine line = new DagLine("line_A",
                Collections.singletonList(new DagNode("n1", OpType.GAUSSIAN_BLUR, "sigma=2 stack")),
                1);
        return new DagIR(1, 1,
                Collections.singletonList(line),
                Collections.<macro.builder.image.dag.Combiner>emptyList(),
                "line_A",
                "native");
    }

    private static boolean containsLabel(Container container, String text) {
        for (Component child : container.getComponents()) {
            if (child instanceof JLabel && text.equals(((JLabel) child).getText())) {
                return true;
            }
            if (child instanceof Container && containsLabel((Container) child, text)) {
                return true;
            }
        }
        return false;
    }
}
