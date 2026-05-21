package macro.builder.ui.batch;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public final class HeatmapRenderer {

    public static final int MISSING_RGB = 0xE5E7EB;
    public static final long DEFAULT_RENDER_CAP_BYTES = 64L * 1024L * 1024L;
    public static final int MIN_CELL_SIZE = 2;

    private HeatmapRenderer() {
    }

    public static BufferedImage render(double[][] matrix, ViridisPalette palette, int cellWidth, int cellHeight) {
        return render(matrix, palette, cellWidth, cellHeight, false);
    }

    public static BufferedImage render(
            double[][] matrix,
            ViridisPalette palette,
            int cellWidth,
            int cellHeight,
            boolean normalisePerRow) {
        return renderData(matrix, palette, cellWidth, cellHeight, normalisePerRow).image;
    }

    public static RenderedHeatmap renderData(
            double[][] matrix,
            ViridisPalette palette,
            int cellWidth,
            int cellHeight,
            boolean normalisePerRow) {
        ViridisPalette lookup = palette == null ? ViridisPalette.INSTANCE : palette;
        double[][] source = normalisePerRow ? normaliseRows(matrix) : copy(matrix);
        int safeCellWidth = Math.max(MIN_CELL_SIZE, cellWidth);
        int safeCellHeight = Math.max(MIN_CELL_SIZE, cellHeight);
        int groupSize = rowGroupSize(source, safeCellWidth, safeCellHeight, DEFAULT_RENDER_CAP_BYTES);
        double[][] display = groupRows(source, groupSize);
        Range range = range(display);
        BufferedImage image = paint(display, lookup, safeCellWidth, safeCellHeight, range);
        return new RenderedHeatmap(image, display, groupSize, range.minimum, range.maximum);
    }

    static int rowGroupSize(double[][] matrix, int cellWidth, int cellHeight, long capBytes) {
        int rows = rowCount(matrix);
        int columns = columnCount(matrix);
        if (rows <= 0 || columns <= 0) {
            return 1;
        }
        long cap = Math.max(1L, capBytes);
        int safeCellWidth = Math.max(MIN_CELL_SIZE, cellWidth);
        int safeCellHeight = Math.max(MIN_CELL_SIZE, cellHeight);
        int groupSize = 1;
        while (groupSize < rows
                && estimateBytes(rows, columns, safeCellWidth, safeCellHeight, groupSize) > cap) {
            groupSize++;
        }
        return Math.max(1, groupSize);
    }

    private static BufferedImage paint(
            double[][] matrix,
            ViridisPalette palette,
            int cellWidth,
            int cellHeight,
            Range range) {
        int rows = rowCount(matrix);
        int columns = columnCount(matrix);
        int width = Math.max(1, columns * cellWidth);
        int height = Math.max(1, rows * cellHeight);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < columns; c++) {
                    double value = valueAt(matrix, r, c);
                    int rgb = colourFor(value, range, palette);
                    g.setColor(new Color(rgb));
                    g.fillRect(c * cellWidth, r * cellHeight, cellWidth, cellHeight);
                }
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    private static int colourFor(double value, Range range, ViridisPalette palette) {
        if (!isFinite(value)) {
            return MISSING_RGB;
        }
        if (!isFinite(range.minimum) || !isFinite(range.maximum)) {
            return palette.colour(0.5);
        }
        double span = range.maximum - range.minimum;
        double fraction = span <= 0.0 ? 0.5 : (value - range.minimum) / span;
        return palette.colour(fraction);
    }

    private static double[][] groupRows(double[][] matrix, int groupSize) {
        int rows = rowCount(matrix);
        int columns = columnCount(matrix);
        if (rows <= 0 || columns <= 0) {
            return new double[0][0];
        }
        int safeGroup = Math.max(1, groupSize);
        if (safeGroup == 1) {
            return copyRectangular(matrix, rows, columns);
        }
        int groupedRows = (rows + safeGroup - 1) / safeGroup;
        double[][] grouped = new double[groupedRows][columns];
        for (int r = 0; r < groupedRows; r++) {
            int start = r * safeGroup;
            int end = Math.min(rows, start + safeGroup);
            for (int c = 0; c < columns; c++) {
                double sum = 0.0;
                int count = 0;
                for (int sourceRow = start; sourceRow < end; sourceRow++) {
                    double value = valueAt(matrix, sourceRow, c);
                    if (isFinite(value)) {
                        sum += value;
                        count++;
                    }
                }
                grouped[r][c] = count == 0 ? Double.NaN : sum / count;
            }
        }
        return grouped;
    }

    private static double[][] normaliseRows(double[][] matrix) {
        double[][] out = copy(matrix);
        for (int r = 0; r < out.length; r++) {
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (int c = 0; c < out[r].length; c++) {
                double value = out[r][c];
                if (isFinite(value)) {
                    if (value < min) {
                        min = value;
                    }
                    if (value > max) {
                        max = value;
                    }
                }
            }
            if (min == Double.POSITIVE_INFINITY) {
                continue;
            }
            double span = max - min;
            for (int c = 0; c < out[r].length; c++) {
                if (isFinite(out[r][c])) {
                    out[r][c] = span <= 0.0 ? 0.5 : (out[r][c] - min) / span;
                }
            }
        }
        return out;
    }

    private static Range range(double[][] matrix) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int r = 0; r < matrix.length; r++) {
            for (int c = 0; c < matrix[r].length; c++) {
                double value = matrix[r][c];
                if (isFinite(value)) {
                    if (value < min) {
                        min = value;
                    }
                    if (value > max) {
                        max = value;
                    }
                }
            }
        }
        if (min == Double.POSITIVE_INFINITY) {
            return new Range(Double.NaN, Double.NaN);
        }
        return new Range(min, max);
    }

    private static double valueAt(double[][] matrix, int row, int column) {
        if (matrix == null || row < 0 || row >= matrix.length
                || matrix[row] == null || column < 0 || column >= matrix[row].length) {
            return Double.NaN;
        }
        return matrix[row][column];
    }

    private static int rowCount(double[][] matrix) {
        return matrix == null ? 0 : matrix.length;
    }

    private static int columnCount(double[][] matrix) {
        if (matrix == null) {
            return 0;
        }
        int columns = 0;
        for (int r = 0; r < matrix.length; r++) {
            if (matrix[r] != null && matrix[r].length > columns) {
                columns = matrix[r].length;
            }
        }
        return columns;
    }

    private static double[][] copy(double[][] matrix) {
        if (matrix == null) {
            return new double[0][0];
        }
        double[][] out = new double[matrix.length][];
        for (int r = 0; r < matrix.length; r++) {
            out[r] = matrix[r] == null ? new double[0] : matrix[r].clone();
        }
        return out;
    }

    private static double[][] copyRectangular(double[][] matrix, int rows, int columns) {
        double[][] out = new double[rows][columns];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                out[r][c] = valueAt(matrix, r, c);
            }
        }
        return out;
    }

    private static long estimateBytes(int rows, int columns, int cellWidth, int cellHeight, int groupSize) {
        long matrixBytes = safeMultiply(8L, safeMultiply(rows, columns));
        long displayRows = (rows + groupSize - 1L) / groupSize;
        long pixels = safeMultiply(displayRows, safeMultiply(columns, safeMultiply(cellWidth, cellHeight)));
        long imageBytes = safeMultiply(4L, pixels);
        return saturatedAdd(matrixBytes, imageBytes);
    }

    private static long safeMultiply(long a, long b) {
        if (a == 0L || b == 0L) {
            return 0L;
        }
        if (a > Long.MAX_VALUE / b) {
            return Long.MAX_VALUE;
        }
        return a * b;
    }

    private static long saturatedAdd(long a, long b) {
        if (Long.MAX_VALUE - a < b) {
            return Long.MAX_VALUE;
        }
        return a + b;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    public static final class RenderedHeatmap {
        public final BufferedImage image;
        public final double[][] matrix;
        public final int rowGroupSize;
        public final double minimum;
        public final double maximum;

        RenderedHeatmap(
                BufferedImage image,
                double[][] matrix,
                int rowGroupSize,
                double minimum,
                double maximum) {
            this.image = image;
            this.matrix = matrix;
            this.rowGroupSize = rowGroupSize;
            this.minimum = minimum;
            this.maximum = maximum;
        }
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
