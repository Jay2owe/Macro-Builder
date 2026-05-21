package macro.builder.image;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Menus;
import ij.WindowManager;
import ij.plugin.ImageCalculator;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import org.junit.Test;

import java.awt.GraphicsEnvironment;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assume.assumeTrue;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Regression contract: the native Diffuse Object Filter path is compared
 * against the old ImageJ command pipeline on the same 64x64x8 synthetic stack.
 * Integer outputs consistently allow a max absolute difference of 1 LSB for
 * both 8-bit and 16-bit images; float output allows 1e-5.
 */
public class DiffuseObjectFilterTest {

    private static final int WIDTH = 64;
    private static final int HEIGHT = 64;
    private static final int SLICES = 8;
    private static final double INTEGER_TOLERANCE_LSB = 1.0;
    private static final double FLOAT_TOLERANCE = 1.0e-5;

    @Test
    public void nativePathMatchesMacroOn8Bit() {
        assumeReferenceMacroRuntime();
        String macro = diffuseMacro();
        ImagePlus reference = oldMacroPipeline(byteStack("reference-8"));
        ImagePlus actual = byteStack("actual-8");

        DiffuseObjectFilter.apply(actual, macro);

        assertStacksWithin(reference, actual, INTEGER_TOLERANCE_LSB);
    }

    @Test
    public void nativePathMatchesMacroOn16Bit() {
        assumeReferenceMacroRuntime();
        String macro = diffuseMacro();
        ImagePlus reference = oldMacroPipeline(shortStack("reference-16"));
        ImagePlus actual = shortStack("actual-16");

        DiffuseObjectFilter.apply(actual, macro);

        assertStacksWithin(reference, actual, INTEGER_TOLERANCE_LSB);
    }

    @Test
    public void nativePathMatchesMacroOnFloat() {
        assumeReferenceMacroRuntime();
        String macro = diffuseMacro();
        ImagePlus reference = oldMacroPipeline(floatStack("reference-float"));
        ImagePlus actual = floatStack("actual-float");

        DiffuseObjectFilter.apply(actual, macro);

        assertStacksWithin(reference, actual, FLOAT_TOLERANCE);
    }

    @Test
    public void doesNotShowInputWindow() {
        ImagePlus imp = byteStack("hidden");
        assertNull(imp.getWindow());

        DiffuseObjectFilter.apply(imp, diffuseMacro());

        assertNull(imp.getWindow());
    }

    @Test
    public void doesNotTouchWindowManager() {
        int[] before = windowIds();

        DiffuseObjectFilter.apply(byteStack("no-window-manager"), diffuseMacro());

        assertArrayEquals(before, windowIds());
    }

    @Test
    public void runsInParallelWithoutLock() throws Exception {
        assumeReferenceMacroRuntime();
        final String macro = diffuseMacro();
        final ImagePlus expectedA = oldMacroPipeline(byteStack("parallel-expected-a"));
        final ImagePlus expectedB = oldMacroPipeline(shortStack("parallel-expected-b"));
        ExecutorService pool = Executors.newFixedThreadPool(2);

        WindowManagerLock.LOCK.lock();
        try {
            Future<ImagePlus> first = pool.submit(new Callable<ImagePlus>() {
                @Override public ImagePlus call() {
                    ImagePlus imp = byteStack("parallel-a");
                    FilterExecutor.runThreadSafe(imp, macro);
                    return imp;
                }
            });
            Future<ImagePlus> second = pool.submit(new Callable<ImagePlus>() {
                @Override public ImagePlus call() {
                    ImagePlus imp = shortStack("parallel-b");
                    FilterExecutor.runThreadSafe(imp, macro);
                    return imp;
                }
            });

            assertStacksWithin(expectedA, first.get(10, TimeUnit.SECONDS), INTEGER_TOLERANCE_LSB);
            assertStacksWithin(expectedB, second.get(10, TimeUnit.SECONDS), INTEGER_TOLERANCE_LSB);
        } finally {
            WindowManagerLock.LOCK.unlock();
            pool.shutdownNow();
        }
        assertFalse("WindowManagerLock should not be held after parallel diffuse run",
                WindowManagerLock.LOCK.isLocked());
    }

    private static void assumeReferenceMacroRuntime() {
        assumeTrue("Reference macro comparison requires a non-headless ImageJ runtime",
                !GraphicsEnvironment.isHeadless());
        Hashtable commands = Menus.getCommands();
        assumeTrue("Reference macro comparison requires Gaussian Blur 3D and Median 3D commands",
                commands != null
                        && commands.containsKey("Gaussian Blur 3D...")
                        && commands.containsKey("Median 3D..."));
    }

    private static ImagePlus oldMacroPipeline(ImagePlus source) {
        ImagePlus small = duplicateStack(source, "DoG_small");
        IJ.run(small, "Gaussian Blur 3D...", "x=2 y=2 z=1");

        ImagePlus big = duplicateStack(source, "DoG_big");
        IJ.run(big, "Gaussian Blur 3D...", "x=15 y=15 z=4");

        ImagePlus result = new ImageCalculator().run("Subtract create stack", small, big);
        assertNotNull("ImageCalculator did not create DoG_result", result);
        result.setTitle("DoG_result");
        IJ.run(result, "Median 3D...", "x=1 y=1 z=1");
        return result;
    }

    private static void assertStacksWithin(ImagePlus expected, ImagePlus actual, double tolerance) {
        assertEquals(expected.getWidth(), actual.getWidth());
        assertEquals(expected.getHeight(), actual.getHeight());
        assertEquals(expected.getStackSize(), actual.getStackSize());

        ImageStack expectedStack = expected.getStack();
        ImageStack actualStack = actual.getStack();
        for (int s = 1; s <= expectedStack.getSize(); s++) {
            ImageProcessor expectedProcessor = expectedStack.getProcessor(s);
            ImageProcessor actualProcessor = actualStack.getProcessor(s);
            int pixels = expectedProcessor.getWidth() * expectedProcessor.getHeight();
            double maxDiff = 0.0;
            int maxIndex = -1;
            for (int i = 0; i < pixels; i++) {
                double diff = Math.abs(expectedProcessor.getf(i) - actualProcessor.getf(i));
                if (diff > maxDiff) {
                    maxDiff = diff;
                    maxIndex = i;
                }
            }
            if (maxDiff > tolerance) {
                fail("slice " + s + " max abs diff " + maxDiff
                        + " at pixel " + maxIndex + " exceeds tolerance " + tolerance);
            }
        }
    }

    private static ImagePlus duplicateStack(ImagePlus source, String title) {
        ImageStack src = source.getStack();
        ImageStack copy = new ImageStack(source.getWidth(), source.getHeight());
        for (int s = 1; s <= src.getSize(); s++) {
            copy.addSlice(src.getSliceLabel(s), src.getProcessor(s).duplicate());
        }
        ImagePlus out = new ImagePlus(title, copy);
        out.setDimensions(1, SLICES, 1);
        return out;
    }

    private static ImagePlus byteStack(String title) {
        ImageStack stack = new ImageStack(WIDTH, HEIGHT);
        for (int z = 0; z < SLICES; z++) {
            ByteProcessor bp = new ByteProcessor(WIDTH, HEIGHT);
            byte[] pixels = (byte[]) bp.getPixels();
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    pixels[y * WIDTH + x] = (byte) syntheticIntegerValue(x, y, z, 255);
                }
            }
            stack.addSlice("z" + z, bp);
        }
        ImagePlus imp = new ImagePlus(title, stack);
        imp.setDimensions(1, SLICES, 1);
        return imp;
    }

    private static ImagePlus shortStack(String title) {
        ImageStack stack = new ImageStack(WIDTH, HEIGHT);
        for (int z = 0; z < SLICES; z++) {
            ShortProcessor sp = new ShortProcessor(WIDTH, HEIGHT);
            short[] pixels = (short[]) sp.getPixels();
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    pixels[y * WIDTH + x] = (short) syntheticIntegerValue(x, y, z, 60000);
                }
            }
            stack.addSlice("z" + z, sp);
        }
        ImagePlus imp = new ImagePlus(title, stack);
        imp.setDimensions(1, SLICES, 1);
        return imp;
    }

    private static ImagePlus floatStack(String title) {
        ImageStack stack = new ImageStack(WIDTH, HEIGHT);
        for (int z = 0; z < SLICES; z++) {
            FloatProcessor fp = new FloatProcessor(WIDTH, HEIGHT);
            float[] pixels = (float[]) fp.getPixels();
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    pixels[y * WIDTH + x] = syntheticFloatValue(x, y, z);
                }
            }
            stack.addSlice("z" + z, fp);
        }
        ImagePlus imp = new ImagePlus(title, stack);
        imp.setDimensions(1, SLICES, 1);
        return imp;
    }

    private static int syntheticIntegerValue(int x, int y, int z, int maximum) {
        int gradient = (x * 17 + y * 31 + z * 47) % (maximum / 2);
        int firstSpot = spotBoost(x, y, z, 21, 19, 2, maximum / 2);
        int secondSpot = spotBoost(x, y, z, 43, 41, 5, maximum / 3);
        return Math.min(maximum, gradient + firstSpot + secondSpot);
    }

    private static int spotBoost(int x, int y, int z,
                                 int centerX, int centerY, int centerZ,
                                 int amplitude) {
        int dx = x - centerX;
        int dy = y - centerY;
        int dz = z - centerZ;
        int distance = dx * dx + dy * dy + dz * dz * 10;
        if (distance > 180) return 0;
        return amplitude * (180 - distance) / 180;
    }

    private static float syntheticFloatValue(int x, int y, int z) {
        double wave = Math.sin(x * 0.17 + z * 0.31) * 240.0
                + Math.cos(y * 0.13 - z * 0.19) * 180.0;
        double firstSpot = spotBoost(x, y, z, 20, 18, 2, 3500);
        double secondSpot = spotBoost(x, y, z, 44, 43, 5, 2200);
        return (float) (wave + firstSpot + secondSpot + z * 75.0);
    }

    private static String diffuseMacro() {
        InputStream in = DiffuseObjectFilterTest.class.getResourceAsStream(
                "/named-filters/diffuse_object_filter.ijm");
        assertNotNull("Missing diffuse_object_filter.ijm resource", in);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            String macro = new String(out.toByteArray(), StandardCharsets.UTF_8);
            assertTrue(DiffuseObjectFilter.matches(macro));
            return macro;
        } catch (IOException e) {
            throw new AssertionError(e);
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static int[] windowIds() {
        int[] ids = WindowManager.getIDList();
        if (ids == null) return new int[0];
        int[] copy = Arrays.copyOf(ids, ids.length);
        Arrays.sort(copy);
        return copy;
    }
}
