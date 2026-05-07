package macro.builder.ui;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import org.junit.Test;

import javax.swing.JLabel;
import javax.swing.JSlider;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ImagePreviewPanelTest {

    @Test
    public void setImageUpdatesLabelsAndSliceSlider() {
        ImagePreviewPanel panel = new ImagePreviewPanel("Source image");
        panel.setImage(stackImage("Sample Stack", 5, 4, 2));

        List<String> labels = labelTexts(panel);
        assertTrue(labels.contains("Sample Stack"));
        assertTrue(labels.contains("5 x 4, C=1, Z=2, T=1"));
        assertTrue(labels.contains("1/2"));

        JSlider slider = findSlider(panel);
        assertNotNull(slider);
        assertEquals(1, slider.getMinimum());
        assertEquals(2, slider.getMaximum());
        assertEquals(1, slider.getValue());
        assertTrue(slider.isEnabled());
    }

    @Test
    public void clearingImageDisablesSliceSlider() {
        ImagePreviewPanel panel = new ImagePreviewPanel("Preview output");
        panel.setImage(stackImage("Sample Stack", 5, 4, 2));

        panel.setImage(null);

        List<String> labels = labelTexts(panel);
        assertTrue(labels.contains("No image selected."));

        JSlider slider = findSlider(panel);
        assertNotNull(slider);
        assertEquals(1, slider.getMinimum());
        assertEquals(1, slider.getMaximum());
        assertEquals(1, slider.getValue());
        assertFalse(slider.isEnabled());
    }

    private static ImagePlus stackImage(String title, int width, int height, int slices) {
        ImageStack stack = new ImageStack(width, height);
        for (int i = 0; i < slices; i++) {
            stack.addSlice(new ByteProcessor(width, height));
        }
        return new ImagePlus(title, stack);
    }

    private static List<String> labelTexts(Container root) {
        List<String> labels = new ArrayList<String>();
        collectLabelTexts(root, labels);
        return labels;
    }

    private static void collectLabelTexts(Container root, List<String> labels) {
        for (int i = 0; i < root.getComponentCount(); i++) {
            Component component = root.getComponent(i);
            if (component instanceof JLabel) {
                labels.add(((JLabel) component).getText());
            }
            if (component instanceof Container) {
                collectLabelTexts((Container) component, labels);
            }
        }
    }

    private static JSlider findSlider(Container root) {
        for (int i = 0; i < root.getComponentCount(); i++) {
            Component component = root.getComponent(i);
            if (component instanceof JSlider) return (JSlider) component;
            if (component instanceof Container) {
                JSlider nested = findSlider((Container) component);
                if (nested != null) return nested;
            }
        }
        return null;
    }
}
