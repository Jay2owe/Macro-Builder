package macro.builder.analysis;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

public final class LiveMaskBuilder {

    private LiveMaskBuilder() {
    }

    public static ImagePlus build(ImagePlus source, double lower, double upper) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        return ThresholdShootoutRunner.createMask(source, "Live mask", lower, upper);
    }

    public static void rebuildInPlace(ByteProcessor mask, ImageProcessor src, double lower, double upper) {
        if (mask == null) {
            throw new IllegalArgumentException("mask must not be null");
        }
        if (src == null) {
            throw new IllegalArgumentException("src must not be null");
        }
        if (mask.getWidth() != src.getWidth() || mask.getHeight() != src.getHeight()) {
            throw new IllegalArgumentException("mask and src dimensions must match");
        }

        Object input = src.getPixels();
        byte[] output = (byte[]) mask.getPixels();
        if (input instanceof byte[]) {
            rebuildFromByte(output, (byte[]) input, lower, upper);
        } else if (input instanceof short[]) {
            rebuildFromShort(output, (short[]) input, lower, upper);
        } else if (input instanceof float[]) {
            rebuildFromFloat(output, (float[]) input, lower, upper);
        } else {
            rebuildFromProcessor(output, src, lower, upper);
        }
    }

    private static void rebuildFromByte(byte[] output, byte[] input, double lower, double upper) {
        int lo = lowerByteBound(lower);
        int hi = upperByteBound(upper);
        boolean usable = lo <= hi;
        for (int i = 0; i < input.length; i++) {
            int value = input[i] & 0xff;
            output[i] = (byte) (usable && value >= lo && value <= hi ? 255 : 0);
        }
    }

    private static void rebuildFromShort(byte[] output, short[] input, double lower, double upper) {
        int lo = lowerShortBound(lower);
        int hi = upperShortBound(upper);
        boolean usable = lo <= hi;
        for (int i = 0; i < input.length; i++) {
            int value = input[i] & 0xffff;
            output[i] = (byte) (usable && value >= lo && value <= hi ? 255 : 0);
        }
    }

    private static void rebuildFromFloat(byte[] output, float[] input, double lower, double upper) {
        boolean usable = isFinite(lower) && isFinite(upper) && lower <= upper;
        for (int i = 0; i < input.length; i++) {
            float value = input[i];
            output[i] = (byte) (usable && isFinite(value) && value >= lower && value <= upper ? 255 : 0);
        }
    }

    private static void rebuildFromProcessor(byte[] output, ImageProcessor src, double lower, double upper) {
        boolean usable = isFinite(lower) && isFinite(upper) && lower <= upper;
        int width = src.getWidth();
        int index = 0;
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < width; x++) {
                double value = src.getPixelValue(x, y);
                output[index++] = (byte) (usable && isFinite(value) && value >= lower && value <= upper ? 255 : 0);
            }
        }
    }

    private static int lowerByteBound(double value) {
        if (!isFinite(value)) {
            return 256;
        }
        if (value <= 0.0) {
            return 0;
        }
        if (value > 255.0) {
            return 256;
        }
        return (int) Math.ceil(value);
    }

    private static int upperByteBound(double value) {
        if (!isFinite(value)) {
            return -1;
        }
        if (value < 0.0) {
            return -1;
        }
        if (value >= 255.0) {
            return 255;
        }
        return (int) Math.floor(value);
    }

    private static int lowerShortBound(double value) {
        if (!isFinite(value)) {
            return 65536;
        }
        if (value <= 0.0) {
            return 0;
        }
        if (value > 65535.0) {
            return 65536;
        }
        return (int) Math.ceil(value);
    }

    private static int upperShortBound(double value) {
        if (!isFinite(value)) {
            return -1;
        }
        if (value < 0.0) {
            return -1;
        }
        if (value >= 65535.0) {
            return 65535;
        }
        return (int) Math.floor(value);
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
