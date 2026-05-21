package macro.builder.ui.sandbox.variation;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Headless unit tests for {@link CaptionBaker}.
 *
 * <p>We don't assert specific glyph shapes (brittle across font installations
 * and OSes); instead we check that the bottom-left caption region of an
 * all-white image contains some non-white pixels after baking, and that the
 * top-right region — far from where the caption is drawn — stays untouched.
 */
public class CaptionBakerTest {

    private static final int W = 64;
    private static final int H = 64;

    @Test
    public void bakingMarksPixelsInBottomLeftRegion() {
        ImagePlus imp = whiteImage(W, H);
        CaptionBaker.bakeAll(imp, "TEST");

        ByteProcessor bp = (ByteProcessor) imp.getProcessor();
        assertTrue("expected caption pixels to dirty the bottom-left",
                anyNonWhite(bp, 2, H - 24, 40, 22));
    }

    @Test
    public void bakingLeavesTopRightRegionUntouched() {
        ImagePlus imp = whiteImage(W, H);
        CaptionBaker.bakeAll(imp, "TEST");

        ByteProcessor bp = (ByteProcessor) imp.getProcessor();
        assertFalse("expected top-right corner to remain pristine white",
                anyNonWhite(bp, W - 16, 0, 16, 16));
    }

    @Test
    public void bakingAppliesToEverySliceOfAStack() {
        ImageStack stack = new ImageStack(W, H);
        for (int i = 0; i < 5; i++) {
            ByteProcessor bp = new ByteProcessor(W, H);
            bp.setColor(255);
            bp.fill();
            stack.addSlice("s" + i, bp);
        }
        ImagePlus imp = new ImagePlus("stack", stack);

        CaptionBaker.bakeAll(imp, "Z");

        for (int i = 1; i <= stack.size(); i++) {
            ByteProcessor bp = (ByteProcessor) stack.getProcessor(i);
            assertTrue("slice " + i + " should have caption pixels",
                    anyNonWhite(bp, 2, H - 24, 40, 22));
        }
    }

    @Test
    public void emptyOrNullCaptionIsANoOp() {
        ImagePlus imp = whiteImage(W, H);
        CaptionBaker.bakeAll(imp, "");
        CaptionBaker.bakeAll(imp, null);
        ByteProcessor bp = (ByteProcessor) imp.getProcessor();
        assertEquals("image should remain entirely white",
                0L, countNonWhite(bp, 0, 0, W, H));
    }

    private static ImagePlus whiteImage(int w, int h) {
        ByteProcessor bp = new ByteProcessor(w, h);
        bp.setColor(255);
        bp.fill();
        return new ImagePlus("white", bp);
    }

    private static boolean anyNonWhite(ByteProcessor bp, int x, int y, int w, int h) {
        return countNonWhite(bp, x, y, w, h) > 0;
    }

    private static long countNonWhite(ByteProcessor bp, int x, int y, int w, int h) {
        int x2 = Math.min(bp.getWidth(), x + w);
        int y2 = Math.min(bp.getHeight(), y + h);
        long count = 0;
        for (int yy = Math.max(0, y); yy < y2; yy++) {
            for (int xx = Math.max(0, x); xx < x2; xx++) {
                if ((bp.get(xx, yy) & 0xff) != 255) count++;
            }
        }
        return count;
    }
}
