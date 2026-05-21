package macro.builder.ui;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;
import org.junit.Test;

import javax.swing.JLabel;
import javax.swing.JSlider;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.IndexColorModel;
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

    @Test
    public void setCurrentZUpdatesSliderAndClampsToStackDepth() {
        ImagePreviewPanel panel = new ImagePreviewPanel("Source image");
        panel.setImage(stackImage("Sample Stack", 5, 4, 3));

        panel.setCurrentZ(2);

        assertEquals(2, panel.getCurrentZ());
        assertEquals(3, panel.getSliceCount());
        assertEquals(2, findSlider(panel).getValue());
        assertTrue(labelTexts(panel).contains("2/3"));

        panel.setCurrentZ(99);

        assertEquals(3, panel.getCurrentZ());
        assertEquals(3, findSlider(panel).getValue());
        assertTrue(labelTexts(panel).contains("3/3"));
    }

    @Test
    public void zSliceListenerFiresForSliderChangesButNotSyncedUpdates() {
        ImagePreviewPanel panel = new ImagePreviewPanel("Source image");
        panel.setImage(stackImage("Sample Stack", 5, 4, 3));
        final List<Integer> changes = new ArrayList<Integer>();
        panel.setZSliceChangeListener(new ImagePreviewPanel.ZSliceChangeListener() {
            @Override public void zSliceChanged(ImagePreviewPanel source, int zSlice) {
                changes.add(Integer.valueOf(zSlice));
            }
        });

        findSlider(panel).setValue(2);
        panel.setCurrentZ(3);

        assertEquals(1, changes.size());
        assertEquals(Integer.valueOf(2), changes.get(0));
        assertEquals(3, panel.getCurrentZ());
    }

    @Test
    public void setImagePreservesCurrentZWhenPreviewRefreshes() {
        ImagePreviewPanel panel = new ImagePreviewPanel("Source image");
        panel.setImage(stackImage("Sample Stack", 5, 4, 4));
        panel.setCurrentZ(3);

        panel.setImage(stackImage("Refreshed Stack", 5, 4, 4));

        assertEquals(3, panel.getCurrentZ());
        assertEquals(3, findSlider(panel).getValue());
        assertTrue(labelTexts(panel).contains("3/4"));
    }

    @Test
    public void displaySettingsApplyBrightnessContrastRange() {
        ImagePreviewPanel panel = new ImagePreviewPanel("Source image");
        panel.setImage(stackImage("Sample Stack", 5, 4, 1));
        panel.setDisplaySettings(PreviewDisplaySettings.of(
                10.0, 90.0, PreviewDisplaySettings.LutMode.USER));

        ImageProcessor rendered = panel.renderedProcessorForTest();

        assertNotNull(rendered);
        assertEquals(10.0, rendered.getMin(), 0.0001);
        assertEquals(90.0, rendered.getMax(), 0.0001);
    }

    @Test
    public void greyLutOverridesUserSelectedLutOnlyForPreviewRendering() {
        ImagePlus image = stackImage("Green Stack", 5, 4, 1);
        image.setLut(LUT.createLutFromColor(Color.GREEN));
        ImagePreviewPanel panel = new ImagePreviewPanel("Source image");
        panel.setImage(image);

        panel.setDisplaySettings(PreviewDisplaySettings.of(
                0.0, 255.0, PreviewDisplaySettings.LutMode.USER));
        IndexColorModel userLut = (IndexColorModel) panel.renderedProcessorForTest().getColorModel();
        assertEquals(0, userLut.getRed(128));
        assertEquals(128, userLut.getGreen(128));

        panel.setDisplaySettings(PreviewDisplaySettings.of(
                0.0, 255.0, PreviewDisplaySettings.LutMode.GREY));
        IndexColorModel greyLut = (IndexColorModel) panel.renderedProcessorForTest().getColorModel();

        assertEquals(128, greyLut.getRed(128));
        assertEquals(128, greyLut.getGreen(128));
        assertEquals(128, greyLut.getBlue(128));
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
