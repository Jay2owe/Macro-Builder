package macro.builder.analysis;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class LiveMaskBuilderTest {

    @Test
    public void byteProcessorRebuildMatchesFreshMaskAndClearsOldPixels() {
        ByteProcessor source = new ByteProcessor(4, 2);
        source.set(0, 0, 0);
        source.set(1, 0, 99);
        source.set(2, 0, 100);
        source.set(3, 0, 101);
        source.set(0, 1, 200);
        source.set(1, 1, 255);
        source.set(2, 1, 50);
        source.set(3, 1, 150);

        assertMatchesFreshMask(source, 100.0, 200.0);
    }

    @Test
    public void shortProcessorRebuildMatchesFreshMaskAndClearsOldPixels() {
        ShortProcessor source = new ShortProcessor(4, 2);
        source.set(0, 0, 0);
        source.set(1, 0, 1999);
        source.set(2, 0, 2000);
        source.set(3, 0, 2001);
        source.set(0, 1, 3000);
        source.set(1, 1, 65535);
        source.set(2, 1, 1500);
        source.set(3, 1, 2500);

        assertMatchesFreshMask(source, 2000.0, 3000.0);
    }

    @Test
    public void floatProcessorRebuildMatchesFreshMaskAndTreatsNonFiniteAsBackground() {
        FloatProcessor source = new FloatProcessor(4, 2);
        float[] pixels = (float[]) source.getPixels();
        pixels[0] = Float.NaN;
        pixels[1] = Float.POSITIVE_INFINITY;
        pixels[2] = Float.NEGATIVE_INFINITY;
        pixels[3] = 1.25f;
        pixels[4] = 2.0f;
        pixels[5] = 3.5f;
        pixels[6] = 4.0f;
        pixels[7] = 5.0f;

        assertMatchesFreshMask(source, 2.0, 4.0);
    }

    private static void assertMatchesFreshMask(ImageProcessor source, double lower, double upper) {
        ByteProcessor reused = new ByteProcessor(source.getWidth(), source.getHeight());
        byte[] reusedPixels = (byte[]) reused.getPixels();
        for (int i = 0; i < reusedPixels.length; i++) {
            reusedPixels[i] = (byte) 255;
        }

        LiveMaskBuilder.rebuildInPlace(reused, source, lower, upper);

        ImagePlus fresh = ThresholdShootoutRunner.createMask(
                new ImagePlus("source", source.duplicate()),
                "fresh",
                lower,
                upper);
        assertArrayEquals((byte[]) fresh.getProcessor().getPixels(), (byte[]) reused.getPixels());
    }
}
