package macro.builder.ui;

import ij.ImagePlus;
import ij.gui.Plot;
import macro.builder.analysis.ShootoutContext;
import macro.builder.analysis.ShootoutResult;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class ChartRenderer {
    static final int DEFAULT_WIDTH = 640;
    static final int DEFAULT_HEIGHT = 80;

    private static final Color HISTOGRAM = new Color(64, 76, 90);
    private static final Color CURVE = new Color(36, 111, 160);
    private static final Color TESTED_THRESHOLD = new Color(33, 111, 219);
    private static final Color RECOMMENDED_THRESHOLD = new Color(218, 165, 32);

    private ChartRenderer() {
    }

    static BufferedImage renderHistogram(ShootoutContext context, List<ShootoutResult> results) {
        return renderHistogram(context, results, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    static BufferedImage renderHistogram(
            ShootoutContext context,
            List<ShootoutResult> results,
            int width,
            int height) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }

        int[] histogram = context.histogram == null ? new int[0] : context.histogram;
        Range xRange = contextRange(context);
        double[] x = histogramXValues(histogram.length, xRange);
        double[] y = histogramYValues(histogram);
        double yMax = paddedMaximum(y);

        Plot plot = new Plot("", "value (binned to 256)", "Pixel count");
        plot.setSize(safeWidth(width), safeHeight(height));
        plot.setLimits(xRange.minimum, xRange.maximum, 0.0, yMax);
        plot.setColor(HISTOGRAM);
        plot.setLineWidth(1);
        plot.add("line", x, y);

        List<ShootoutResult> rows = safeResults(results);
        for (ShootoutResult row : rows) {
            if (!hasThreshold(row)) {
                continue;
            }
            boolean recommended = row.recommended;
            plot.setColor(recommended ? RECOMMENDED_THRESHOLD : TESTED_THRESHOLD);
            plot.setLineWidth(recommended ? 2 : 1);
            plot.drawLine(row.thresholdValue.doubleValue(), 0.0, row.thresholdValue.doubleValue(), yMax);
            if (recommended) {
                labelRecommended(plot, row.thresholdValue.doubleValue(), yMax);
            }
        }

        return imageFrom(plot, safeWidth(width), safeHeight(height));
    }

    static BufferedImage renderCurve(ShootoutContext context, List<ShootoutResult> results) {
        return renderCurve(context, results, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    static BufferedImage renderCurve(
            ShootoutContext context,
            List<ShootoutResult> results,
            int width,
            int height) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }

        List<ShootoutResult> rows = successfulThresholdRows(results);
        Range xRange = curveRange(context, rows);
        double[] x = new double[Math.max(2, rows.size())];
        double[] y = new double[Math.max(2, rows.size())];
        if (rows.isEmpty()) {
            x[0] = xRange.minimum;
            x[1] = xRange.maximum;
            y[0] = 0.0;
            y[1] = 0.0;
        } else {
            for (int i = 0; i < rows.size(); i++) {
                ShootoutResult row = rows.get(i);
                x[i] = row.thresholdValue.doubleValue();
                y[i] = row.countSummary.count;
            }
            if (rows.size() == 1) {
                x[1] = x[0];
                y[1] = y[0];
            }
        }
        double yMax = paddedMaximum(y);

        Plot plot = new Plot("", "Threshold value", "Count");
        plot.setSize(safeWidth(width), safeHeight(height));
        plot.setLimits(xRange.minimum, xRange.maximum, 0.0, yMax);
        plot.setColor(CURVE);
        plot.setLineWidth(1);
        plot.add("line", x, y);

        ShootoutResult recommended = recommendedRow(rows);
        if (recommended != null && hasThreshold(recommended)) {
            double threshold = recommended.thresholdValue.doubleValue();
            plot.setColor(RECOMMENDED_THRESHOLD);
            plot.setLineWidth(2);
            plot.drawLine(threshold, 0.0, threshold, yMax);
            labelRecommended(plot, threshold, yMax);
        }

        return imageFrom(plot, safeWidth(width), safeHeight(height));
    }

    private static void labelRecommended(Plot plot, double threshold, double yMax) {
        plot.setJustification(Plot.CENTER);
        plot.setFontSize(9);
        plot.addText("recommended", threshold, yMax * 0.88);
    }

    private static BufferedImage imageFrom(Plot plot, int width, int height) {
        ImagePlus image = null;
        try {
            image = plot.getImagePlus();
            if (image == null) {
                throw new IllegalStateException("Plot did not produce an image");
            }
            BufferedImage rendered = image.getBufferedImage();
            return copyToSize(rendered, width, height);
        } finally {
            if (image != null) {
                image.flush();
            }
        }
    }

    private static BufferedImage copyToSize(BufferedImage source, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static double[] histogramXValues(int binCount, Range range) {
        int count = Math.max(1, binCount);
        double[] values = new double[count];
        if (count == 1) {
            values[0] = range.minimum;
            return values;
        }
        double span = range.maximum - range.minimum;
        for (int i = 0; i < count; i++) {
            values[i] = range.minimum + span * i / (double) (count - 1);
        }
        return values;
    }

    private static double[] histogramYValues(int[] histogram) {
        int count = Math.max(1, histogram.length);
        double[] values = new double[count];
        for (int i = 0; i < histogram.length; i++) {
            values[i] = histogram[i];
        }
        return values;
    }

    private static Range contextRange(ShootoutContext context) {
        double minimum = finiteOr(context.rangeMin, 0.0);
        double maximum = finiteOr(context.rangeMax, minimum);
        return paddedRange(minimum, maximum);
    }

    private static Range curveRange(ShootoutContext context, List<ShootoutResult> rows) {
        if (rows == null || rows.isEmpty()) {
            return contextRange(context);
        }
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        for (ShootoutResult row : rows) {
            double value = row.thresholdValue.doubleValue();
            if (value < minimum) {
                minimum = value;
            }
            if (value > maximum) {
                maximum = value;
            }
        }
        return paddedRange(minimum, maximum);
    }

    private static Range paddedRange(double minimum, double maximum) {
        if (!isFinite(minimum)) {
            minimum = 0.0;
        }
        if (!isFinite(maximum)) {
            maximum = minimum;
        }
        if (maximum < minimum) {
            double swap = minimum;
            minimum = maximum;
            maximum = swap;
        }
        double span = maximum - minimum;
        if (span <= 0.0) {
            double pad = Math.max(0.5, Math.abs(minimum) * 0.05);
            return new Range(minimum - pad, maximum + pad);
        }
        double pad = span * 0.02;
        return new Range(minimum - pad, maximum + pad);
    }

    private static double paddedMaximum(double[] values) {
        double maximum = 0.0;
        for (int i = 0; i < values.length; i++) {
            if (isFinite(values[i]) && values[i] > maximum) {
                maximum = values[i];
            }
        }
        if (maximum <= 0.0) {
            return 1.0;
        }
        return maximum * 1.08;
    }

    private static List<ShootoutResult> successfulThresholdRows(List<ShootoutResult> results) {
        List<ShootoutResult> rows = new ArrayList<ShootoutResult>();
        for (ShootoutResult row : safeResults(results)) {
            if (hasThreshold(row) && row.countSummary != null) {
                rows.add(row);
            }
        }
        Collections.sort(rows, new Comparator<ShootoutResult>() {
            @Override public int compare(ShootoutResult a, ShootoutResult b) {
                return Double.compare(a.thresholdValue.doubleValue(), b.thresholdValue.doubleValue());
            }
        });
        return rows;
    }

    private static ShootoutResult recommendedRow(List<ShootoutResult> rows) {
        for (ShootoutResult row : safeResults(rows)) {
            if (row.recommended) {
                return row;
            }
        }
        return null;
    }

    private static boolean hasThreshold(ShootoutResult row) {
        return row != null
                && row.isSuccess()
                && row.thresholdValue != null
                && isFinite(row.thresholdValue.doubleValue());
    }

    private static List<ShootoutResult> safeResults(List<ShootoutResult> results) {
        return results == null ? Collections.<ShootoutResult>emptyList() : results;
    }

    private static int safeWidth(int width) {
        return Math.max(1, width);
    }

    private static int safeHeight(int height) {
        return Math.max(1, height);
    }

    private static double finiteOr(double value, double fallback) {
        return isFinite(value) ? value : fallback;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static final class Range {
        final double minimum;
        final double maximum;

        Range(double minimum, double maximum) {
            this.minimum = minimum;
            this.maximum = maximum;
        }
    }
}
