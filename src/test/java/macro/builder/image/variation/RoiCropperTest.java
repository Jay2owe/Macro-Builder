package macro.builder.image.variation;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.process.ShortProcessor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class RoiCropperTest {

    @Test
    public void cropPreservesRoiBoundsOnSingleChannelStack() {
        ImagePlus source = stack16x16(4);
        Roi roi = new Roi(3, 4, 6, 7);

        ImagePlus cropped = RoiCropper.cropToRoi(source, roi);

        assertNotNull(cropped);
        assertEquals(6, cropped.getWidth());
        assertEquals(7, cropped.getHeight());
        assertEquals(4, cropped.getStackSize());
    }

    @Test
    public void cropPreservesPixelValuesAtRoiOrigin() {
        // 16×16 stack where pixel(x,y) = x + y*100 lets us verify the crop's
        // pixel grid corresponds to the requested ROI bounds.
        ImagePlus source = encodedStack16x16(2);
        Roi roi = new Roi(3, 5, 4, 4);

        ImagePlus cropped = RoiCropper.cropToRoi(source, roi);

        assertEquals(4, cropped.getWidth());
        assertEquals(4, cropped.getHeight());
        // Cropped (0,0) should equal source (3,5) on every slice.
        for (int s = 1; s <= cropped.getStackSize(); s++) {
            float origin = cropped.getStack().getProcessor(s).getf(0, 0);
            float expected = source.getStack().getProcessor(s).getf(3, 5);
            assertEquals("slice " + s, expected, origin, 0.0001f);
        }
    }

    @Test
    public void calibrationIsCopiedAndIndependent() {
        ImagePlus source = stack16x16(2);
        Calibration sourceCal = new Calibration();
        sourceCal.pixelWidth = 0.123;
        sourceCal.pixelHeight = 0.123;
        sourceCal.pixelDepth = 0.5;
        sourceCal.setUnit("mm");
        source.setCalibration(sourceCal);

        ImagePlus cropped = RoiCropper.cropToRoi(source, new Roi(0, 0, 8, 8));

        Calibration croppedCal = cropped.getCalibration();
        assertNotNull(croppedCal);
        assertEquals(0.123, croppedCal.pixelWidth, 0.0);
        assertEquals(0.123, croppedCal.pixelHeight, 0.0);
        assertEquals(0.5, croppedCal.pixelDepth, 0.0);
        assertEquals("mm", croppedCal.getUnit());

        // Mutating the crop's calibration must not affect the source.
        croppedCal.pixelWidth = 99.0;
        assertEquals(0.123, source.getCalibration().pixelWidth, 0.0);
    }

    @Test
    public void sourceRoiIsRestoredAfterReturn() {
        ImagePlus source = stack16x16(1);
        Roi previous = new Roi(1, 1, 2, 2);
        source.setRoi(previous);

        RoiCropper.cropToRoi(source, new Roi(5, 5, 4, 4));

        Roi after = source.getRoi();
        assertNotNull("source ROI should be restored", after);
        assertEquals(previous.getBounds(), after.getBounds());
    }

    @Test
    public void sourceRoiClearsAfterReturnIfThereWasNoneBefore() {
        ImagePlus source = stack16x16(1);
        assertNull(source.getRoi());

        RoiCropper.cropToRoi(source, new Roi(2, 2, 4, 4));

        assertNull("source ROI should remain null", source.getRoi());
    }

    @Test
    public void roiBoundsAreClampedToImageDimensions() {
        ImagePlus source = stack16x16(1);
        // Roi extends past the right edge — must be clamped to image bounds.
        Roi roi = new Roi(12, 12, 20, 20);

        ImagePlus cropped = RoiCropper.cropToRoi(source, roi);

        assertEquals(4, cropped.getWidth());
        assertEquals(4, cropped.getHeight());
    }

    @Test
    public void nullRoiReturnsSourceUnchanged() {
        ImagePlus source = stack16x16(2);
        ImagePlus cropped = RoiCropper.cropToRoi(source, null);
        assertSame(source, cropped);
    }

    @Test
    public void croppedIsDistinctImagePlusFromSource() {
        ImagePlus source = stack16x16(1);
        ImagePlus cropped = RoiCropper.cropToRoi(source, new Roi(2, 2, 4, 4));
        assertNotSame(source, cropped);
        assertTrue("cropped width must be smaller than source",
                cropped.getWidth() < source.getWidth());
    }

    private static ImagePlus stack16x16(int slices) {
        ImageStack stack = new ImageStack(16, 16);
        for (int i = 0; i < slices; i++) {
            stack.addSlice("slice " + (i + 1), new ShortProcessor(16, 16));
        }
        return new ImagePlus("test-16x16", stack);
    }

    private static ImagePlus encodedStack16x16(int slices) {
        ImageStack stack = new ImageStack(16, 16);
        for (int i = 0; i < slices; i++) {
            ShortProcessor sp = new ShortProcessor(16, 16);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    sp.set(x, y, (i + 1) * 1000 + y * 100 + x);
                }
            }
            stack.addSlice(sp);
        }
        return new ImagePlus("encoded-16x16", stack);
    }

}
