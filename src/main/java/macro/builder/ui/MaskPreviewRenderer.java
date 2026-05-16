package macro.builder.ui;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ColorProcessor;
import macro.builder.analysis.DetectedObject;
import macro.builder.analysis.GroundTruthReference;
import macro.builder.analysis.GroundTruthScorer;
import macro.builder.analysis.ShootoutSettings;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;

public final class MaskPreviewRenderer {
    private static final int LEGEND_HEIGHT = 28;
    private static final int BACKGROUND = 0x101010;
    private static final int MASK = 0x777777;
    private static final int TEXT = 0xf4f4f4;
    private static final Palette DEFAULT_PALETTE = new Palette(
            new Color(0, 180, 80),
            new Color(0, 190, 210),
            new Color(220, 45, 45),
            "Green: matched   Cyan: missed reference   Red: extra detection");
    private static final Palette ACCESSIBLE_PALETTE = new Palette(
            new Color(0, 114, 178),
            new Color(230, 159, 0),
            new Color(204, 121, 167),
            "Blue: matched   Orange: missed reference   Magenta: extra detection");

    private MaskPreviewRenderer() {
    }

    public static ImagePlus render(
            ImagePlus source,
            ImagePlus mask,
            GroundTruthReference reference,
            int[] perObjectStatus,
            ShootoutSettings settings,
            boolean accessiblePalette) {
        if (mask == null) {
            throw new IllegalArgumentException("mask must not be null");
        }
        if (reference == null) {
            throw new IllegalArgumentException("reference must not be null");
        }
        if (settings == null) {
            throw new IllegalArgumentException("settings must not be null");
        }

        GroundTruthScorer.ScoreSummary score = GroundTruthScorer.score(mask, reference, settings);
        Palette palette = accessiblePalette ? ACCESSIBLE_PALETTE : DEFAULT_PALETTE;
        int width = mask.getWidth();
        int height = mask.getHeight();
        int depth = mask.getStackSize();
        ImageStack out = new ImageStack(width, height + LEGEND_HEIGHT);

        for (int z = 0; z < depth; z++) {
            BufferedImage image = new BufferedImage(width, height + LEGEND_HEIGHT, BufferedImage.TYPE_INT_RGB);
            int[] rgb = ((java.awt.image.DataBufferInt) image.getRaster().getDataBuffer()).getData();
            fillBase(rgb, mask, z, width, height);
            drawDetections(rgb, width, height, z, score.detectedObjects, score.perObjectStatus, palette);
            drawMissedReferences(rgb, width, height, depth, z, score, palette);
            drawLegend(image, width, height, palette);
            out.addSlice(mask.getStack().getSliceLabel(z + 1), new ColorProcessor(image));
        }

        ImagePlus rendered = new ImagePlus("Macro Builder Count Mask Agreement", out);
        if (source != null && source.getCalibration() != null) {
            rendered.setCalibration(source.getCalibration().copy());
        } else if (mask.getCalibration() != null) {
            rendered.setCalibration(mask.getCalibration().copy());
        }
        return rendered;
    }

    private static void fillBase(int[] rgb, ImagePlus mask, int z, int width, int height) {
        Arrays.fill(rgb, BACKGROUND);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (mask.getStack().getProcessor(z + 1).getPixelValue(x, y) > 0.0f) {
                    rgb[y * width + x] = MASK;
                }
            }
        }
    }

    private static void drawDetections(
            int[] rgb,
            int width,
            int height,
            int z,
            List<DetectedObject> objects,
            int[] statuses,
            Palette palette) {
        for (int i = 0; i < objects.size(); i++) {
            int status = i < statuses.length ? statuses[i] : GroundTruthScorer.DETECTION_FP;
            Color color = status == GroundTruthScorer.DETECTION_TP ? palette.truePositive : palette.falsePositive;
            drawOutline(rgb, width, height, z, objects.get(i).pixels, color.getRGB() & 0x00ffffff);
        }
    }

    private static void drawMissedReferences(
            int[] rgb,
            int width,
            int height,
            int depth,
            int z,
            GroundTruthScorer.ScoreSummary score,
            Palette palette) {
        for (int i = 0; i < score.references.size(); i++) {
            if (i >= score.referenceStatus.length
                    || score.referenceStatus[i] != GroundTruthScorer.REFERENCE_FN) {
                continue;
            }
            GroundTruthReference.ReferenceObject object = score.references.get(i).object;
            if (object.kind == GroundTruthReference.ReferenceObject.Kind.POINT) {
                drawPoint(rgb, width, height, z, object, palette.falseNegative.getRGB() & 0x00ffffff);
            } else {
                int[] pixels = object.pixels(width, height, depth);
                drawOutline(rgb, width, height, z, pixels, palette.falseNegative.getRGB() & 0x00ffffff);
            }
        }
    }

    private static void drawOutline(int[] rgb, int width, int height, int z, int[] pixels, int color) {
        if (pixels == null || pixels.length == 0) {
            return;
        }
        int planeSize = width * height;
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int pz = pixel / planeSize;
            if (pz != z) {
                continue;
            }
            int planeIndex = pixel - pz * planeSize;
            int y = planeIndex / width;
            int x = planeIndex - y * width;
            if (isBoundary(pixels, pixel, x, y, width, height, planeSize)) {
                rgb[y * width + x] = color;
            }
        }
    }

    private static boolean isBoundary(int[] pixels, int pixel, int x, int y, int width, int height, int planeSize) {
        if (x <= 0 || y <= 0 || x >= width - 1 || y >= height - 1) {
            return true;
        }
        return Arrays.binarySearch(pixels, pixel - 1) < 0
                || Arrays.binarySearch(pixels, pixel + 1) < 0
                || Arrays.binarySearch(pixels, pixel - width) < 0
                || Arrays.binarySearch(pixels, pixel + width) < 0;
    }

    private static void drawPoint(
            int[] rgb,
            int width,
            int height,
            int z,
            GroundTruthReference.ReferenceObject object,
            int color) {
        if (object.z != z) {
            return;
        }
        int x = (int) Math.round(object.x);
        int y = (int) Math.round(object.y);
        setPixel(rgb, width, height, x, y, color);
        setPixel(rgb, width, height, x - 1, y, color);
        setPixel(rgb, width, height, x + 1, y, color);
        setPixel(rgb, width, height, x, y - 1, color);
        setPixel(rgb, width, height, x, y + 1, color);
    }

    private static void setPixel(int[] rgb, int width, int height, int x, int y, int color) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            rgb[y * width + x] = color;
        }
    }

    private static void drawLegend(BufferedImage image, int width, int imageHeight, Palette palette) {
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(BACKGROUND));
            g.fillRect(0, imageHeight, width, LEGEND_HEIGHT);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            int x = 6;
            int y = imageHeight + 17;
            drawSwatch(g, x, y - 9, palette.truePositive);
            x += 14;
            g.setColor(new Color(TEXT));
            g.drawString(palette.legend, x, y);
        } finally {
            g.dispose();
        }
    }

    private static void drawSwatch(Graphics2D g, int x, int y, Color color) {
        g.setColor(color);
        g.fillRect(x, y, 9, 9);
    }

    private static final class Palette {
        final Color truePositive;
        final Color falseNegative;
        final Color falsePositive;
        final String legend;

        Palette(Color truePositive, Color falseNegative, Color falsePositive, String legend) {
            this.truePositive = truePositive;
            this.falseNegative = falseNegative;
            this.falsePositive = falsePositive;
            this.legend = legend;
        }
    }
}
