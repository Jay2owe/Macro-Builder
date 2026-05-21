package macro.builder.ui.sandbox.variation;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/**
 * Headless unit tests for {@link SharedSliceDriver}.
 *
 * <p>Each test builds tiny in-memory {@link ImagePlus} stacks (no display, no
 * window) so the slice fan-out logic can be exercised without Fiji. The driver
 * itself never touches Swing; the repaint callback is captured as an int
 * counter so we can assert it fires once per registered tile per
 * {@code setSlice} call.
 */
public class SharedSliceDriverTest {

    @Test
    public void setSliceFansOutToAllRegisteredTiles() {
        SharedSliceDriver driver = new SharedSliceDriver();
        ImagePlus a = stack(8, 8, 10);
        ImagePlus b = stack(8, 8, 10);
        ImagePlus c = stack(8, 8, 10);
        AtomicInteger repaints = new AtomicInteger();
        Runnable bump = new Runnable() { @Override public void run() { repaints.incrementAndGet(); } };
        driver.register(a, bump);
        driver.register(b, bump);
        driver.register(c, bump);

        driver.setSlice(5);

        assertEquals(5, a.getCurrentSlice());
        assertEquals(5, b.getCurrentSlice());
        assertEquals(5, c.getCurrentSlice());
        assertEquals(5, driver.currentSlice());
        assertEquals(3, repaints.get());
    }

    @Test
    public void setSliceClampsAboveMaxSlice() {
        SharedSliceDriver driver = new SharedSliceDriver();
        ImagePlus a = stack(8, 8, 10);
        ImagePlus b = stack(8, 8, 10);
        driver.register(a, null);
        driver.register(b, null);

        driver.setSlice(1000);

        assertEquals(10, driver.currentSlice());
        assertEquals(10, a.getCurrentSlice());
        assertEquals(10, b.getCurrentSlice());
    }

    @Test
    public void setSliceClampsBelowOne() {
        SharedSliceDriver driver = new SharedSliceDriver();
        driver.register(stack(8, 8, 10), null);
        driver.setSlice(-7);
        assertEquals(1, driver.currentSlice());
    }

    @Test
    public void maxSliceIsMinimumAcrossRegisteredTiles() {
        // A variant whose pipeline consumed Z (e.g. Z-projection) collapses
        // the shared scroll range to that variant's slice count, per stage 06.
        SharedSliceDriver driver = new SharedSliceDriver();
        driver.register(stack(8, 8, 12), null);
        driver.register(stack(8, 8, 4), null);    // Z-projected variant
        driver.register(stack(8, 8, 12), null);
        assertEquals(4, driver.maxSlice());
    }

    @Test
    public void maxSliceIsOneWhenNoTilesRegistered() {
        assertEquals(1, new SharedSliceDriver().maxSlice());
    }

    @Test
    public void nullRepaintCallbackIsTolerated() {
        SharedSliceDriver driver = new SharedSliceDriver();
        ImagePlus a = stack(8, 8, 10);
        driver.register(a, null);
        driver.setSlice(3);   // must not throw
        assertEquals(3, a.getCurrentSlice());
    }

    private static ImagePlus stack(int w, int h, int slices) {
        ImageStack st = new ImageStack(w, h);
        for (int i = 0; i < slices; i++) st.addSlice("s" + i, new ByteProcessor(w, h));
        return new ImagePlus("test-" + slices, st);
    }
}
