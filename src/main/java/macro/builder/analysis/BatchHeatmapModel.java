package macro.builder.analysis;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BatchHeatmapModel {

    public enum MetricKind {
        COUNT("Count", "count"),
        F1("F1", "f1"),
        AGREEMENT("Agreement", "agreement_score"),
        FRAGILITY("Fragility", "fragility_score");

        private final String label;
        private final String csvHeader;

        MetricKind(String label, String csvHeader) {
            this.label = label;
            this.csvHeader = csvHeader;
        }

        public String label() {
            return label;
        }

        String csvHeader() {
            return csvHeader;
        }

        @Override public String toString() {
            return label;
        }
    }

    private final List<RowKey> rowKeys;
    private final List<String> rowLabels;
    private final List<String> columnLabels;
    private final double[][] countValues;
    private final double[][] f1Values;
    private final double[][] agreementValues;
    private final double[][] fragilityValues;
    private final double[][] thresholdValues;
    private final boolean[][] present;
    private final EnumSet<MetricKind> availableMetrics;

    private BatchHeatmapModel(
            List<RowKey> rowKeys,
            List<String> columnLabels,
            double[][] countValues,
            double[][] f1Values,
            double[][] agreementValues,
            double[][] fragilityValues,
            double[][] thresholdValues,
            boolean[][] present) {
        this.rowKeys = Collections.unmodifiableList(new ArrayList<RowKey>(rowKeys));
        this.rowLabels = Collections.unmodifiableList(labelsFor(rowKeys));
        this.columnLabels = Collections.unmodifiableList(new ArrayList<String>(columnLabels));
        this.countValues = copy(countValues);
        this.f1Values = copy(f1Values);
        this.agreementValues = copy(agreementValues);
        this.fragilityValues = copy(fragilityValues);
        this.thresholdValues = copy(thresholdValues);
        this.present = copy(present);
        this.availableMetrics = detectAvailableMetrics();
    }

    public static BatchHeatmapModel fromCsv(File csvFile) throws IOException {
        if (csvFile == null) {
            throw new IllegalArgumentException("csvFile must not be null");
        }
        byte[] bytes = Files.readAllBytes(csvFile.toPath());
        return fromCsvText(new String(bytes, StandardCharsets.UTF_8));
    }

    public static BatchHeatmapModel fromCsvText(String csvText) {
        List<List<String>> table = parseCsv(csvText == null ? "" : csvText);
        if (table.isEmpty() || isBlankRow(table.get(0))) {
            return empty();
        }

        List<String> headers = table.get(0);
        Map<String, Integer> indexes = headerIndexes(headers);
        int fileIndex = requiredIndex(indexes, "file");
        int variantIndex = requiredIndex(indexes, "variant");
        int seriesIndex = optionalIndex(indexes, "series_index");
        int channelIndex = optionalIndex(indexes, "channel_index");
        int thresholdIndex = optionalIndex(indexes, "threshold_value");

        LinkedHashMap<RowKey, Integer> rowMap = new LinkedHashMap<RowKey, Integer>();
        LinkedHashMap<String, Integer> columnMap = new LinkedHashMap<String, Integer>();
        List<InputRow> rows = new ArrayList<InputRow>();

        for (int i = 1; i < table.size(); i++) {
            List<String> row = table.get(i);
            if (isBlankRow(row)) {
                continue;
            }
            String file = cell(row, fileIndex).trim();
            String variant = cell(row, variantIndex).trim();
            if (file.length() == 0 || variant.length() == 0) {
                continue;
            }
            int parsedSeries = parseInteger(cell(row, seriesIndex), -1);
            int parsedChannel = parseInteger(cell(row, channelIndex), 1);
            RowKey key = new RowKey(file, parsedSeries, Math.max(1, parsedChannel));
            Integer rowNumber = rowMap.get(key);
            if (rowNumber == null) {
                rowNumber = Integer.valueOf(rowMap.size());
                rowMap.put(key, rowNumber);
            }
            Integer columnNumber = columnMap.get(variant);
            if (columnNumber == null) {
                columnNumber = Integer.valueOf(columnMap.size());
                columnMap.put(variant, columnNumber);
            }
            rows.add(new InputRow(
                    rowNumber.intValue(),
                    columnNumber.intValue(),
                    parseDouble(cell(row, optionalIndex(indexes, MetricKind.COUNT.csvHeader()))),
                    parseDouble(cell(row, optionalIndex(indexes, MetricKind.F1.csvHeader()))),
                    parseDouble(cell(row, optionalIndex(indexes, MetricKind.AGREEMENT.csvHeader()))),
                    parseDouble(cell(row, optionalIndex(indexes, MetricKind.FRAGILITY.csvHeader()))),
                    parseDouble(cell(row, thresholdIndex))));
        }

        return build(new ArrayList<RowKey>(rowMap.keySet()),
                new ArrayList<String>(columnMap.keySet()),
                rows);
    }

    public static BatchHeatmapModel fromResults(List<BatchShootoutResult> results) {
        if (results == null || results.isEmpty()) {
            return empty();
        }
        LinkedHashMap<RowKey, Integer> rowMap = new LinkedHashMap<RowKey, Integer>();
        LinkedHashMap<String, Integer> columnMap = new LinkedHashMap<String, Integer>();
        List<InputRow> rows = new ArrayList<InputRow>();
        for (BatchShootoutResult result : results) {
            if (result == null || result.filePath.trim().isEmpty() || result.variant.trim().isEmpty()) {
                continue;
            }
            RowKey key = new RowKey(
                    result.filePath,
                    result.seriesIndex,
                    Math.max(1, result.channelIndex));
            Integer rowNumber = rowMap.get(key);
            if (rowNumber == null) {
                rowNumber = Integer.valueOf(rowMap.size());
                rowMap.put(key, rowNumber);
            }
            Integer columnNumber = columnMap.get(result.variant);
            if (columnNumber == null) {
                columnNumber = Integer.valueOf(columnMap.size());
                columnMap.put(result.variant, columnNumber);
            }
            double count = result.countSummary == null
                    ? Double.NaN
                    : result.countSummary.count;
            double threshold = result.thresholdValue == null
                    ? Double.NaN
                    : result.thresholdValue.doubleValue();
            rows.add(new InputRow(
                    rowNumber.intValue(),
                    columnNumber.intValue(),
                    count,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    threshold));
        }
        return build(new ArrayList<RowKey>(rowMap.keySet()),
                new ArrayList<String>(columnMap.keySet()),
                rows);
    }

    public int rowCount() {
        return rowKeys.size();
    }

    public int columnCount() {
        return columnLabels.size();
    }

    public List<String> rowLabels() {
        return rowLabels;
    }

    public List<String> columnLabels() {
        return columnLabels;
    }

    public String rowLabel(int row) {
        return rowLabels.get(row);
    }

    public String columnLabel(int column) {
        return columnLabels.get(column);
    }

    public RowKey rowKey(int row) {
        return rowKeys.get(row);
    }

    public List<MetricKind> availableMetrics() {
        List<MetricKind> metrics = new ArrayList<MetricKind>();
        for (MetricKind metric : MetricKind.values()) {
            if (availableMetrics.contains(metric)) {
                metrics.add(metric);
            }
        }
        return Collections.unmodifiableList(metrics);
    }

    public double[][] matrix(MetricKind metric) {
        return matrix(metric, false);
    }

    public double[][] matrix(MetricKind metric, boolean normalisePerRow) {
        double[][] values = copy(valuesFor(metric == null ? MetricKind.COUNT : metric));
        return normalisePerRow ? normaliseRows(values) : values;
    }

    public Cell cellAt(int row, int column) {
        if (row < 0 || row >= rowCount() || column < 0 || column >= columnCount()) {
            return null;
        }
        if (!present[row][column]) {
            return null;
        }
        RowKey key = rowKeys.get(row);
        double threshold = thresholdValues[row][column];
        return new Cell(
                row,
                column,
                key.filePath,
                key.seriesIndex,
                key.channelIndex,
                columnLabels.get(column),
                isFinite(threshold) ? Double.valueOf(threshold) : null);
    }

    public long estimatedMatrixBytes() {
        long rows = rowCount();
        long columns = columnCount();
        if (rows <= 0 || columns <= 0) {
            return 0L;
        }
        return safeMultiply(8L, safeMultiply(rows, columns));
    }

    private static BatchHeatmapModel empty() {
        return build(Collections.<RowKey>emptyList(), Collections.<String>emptyList(),
                Collections.<InputRow>emptyList());
    }

    private static BatchHeatmapModel build(
            List<RowKey> rowKeys,
            List<String> columnLabels,
            List<InputRow> inputRows) {
        int rows = rowKeys.size();
        int columns = columnLabels.size();
        double[][] counts = nanMatrix(rows, columns);
        double[][] f1 = nanMatrix(rows, columns);
        double[][] agreement = nanMatrix(rows, columns);
        double[][] fragility = nanMatrix(rows, columns);
        double[][] thresholds = nanMatrix(rows, columns);
        boolean[][] present = new boolean[rows][columns];
        for (InputRow row : inputRows) {
            if (row.row < 0 || row.row >= rows || row.column < 0 || row.column >= columns) {
                continue;
            }
            present[row.row][row.column] = true;
            counts[row.row][row.column] = row.count;
            f1[row.row][row.column] = row.f1;
            agreement[row.row][row.column] = row.agreement;
            fragility[row.row][row.column] = row.fragility;
            thresholds[row.row][row.column] = row.threshold;
        }
        return new BatchHeatmapModel(rowKeys, columnLabels, counts, f1, agreement, fragility, thresholds, present);
    }

    private EnumSet<MetricKind> detectAvailableMetrics() {
        EnumSet<MetricKind> metrics = EnumSet.noneOf(MetricKind.class);
        for (MetricKind metric : MetricKind.values()) {
            if (hasFinite(valuesFor(metric))) {
                metrics.add(metric);
            }
        }
        return metrics;
    }

    private double[][] valuesFor(MetricKind metric) {
        if (metric == MetricKind.F1) {
            return f1Values;
        }
        if (metric == MetricKind.AGREEMENT) {
            return agreementValues;
        }
        if (metric == MetricKind.FRAGILITY) {
            return fragilityValues;
        }
        return countValues;
    }

    private static List<String> labelsFor(List<RowKey> keys) {
        List<String> labels = new ArrayList<String>(keys.size());
        for (RowKey key : keys) {
            labels.add(key.label());
        }
        return labels;
    }

    private static Map<String, Integer> headerIndexes(List<String> headers) {
        Map<String, Integer> indexes = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            if (header != null) {
                indexes.put(header.trim().toLowerCase(Locale.ROOT), Integer.valueOf(i));
            }
        }
        return indexes;
    }

    private static int requiredIndex(Map<String, Integer> indexes, String header) {
        int index = optionalIndex(indexes, header);
        if (index < 0) {
            throw new IllegalArgumentException("Batch CSV is missing required column: " + header);
        }
        return index;
    }

    private static int optionalIndex(Map<String, Integer> indexes, String header) {
        if (header == null) {
            return -1;
        }
        Integer index = indexes.get(header.toLowerCase(Locale.ROOT));
        return index == null ? -1 : index.intValue();
    }

    private static String cell(List<String> row, int index) {
        if (row == null || index < 0 || index >= row.size()) {
            return "";
        }
        String value = row.get(index);
        return value == null ? "" : value;
    }

    private static int parseInteger(String text, int fallback) {
        String value = text == null ? "" : text.trim();
        if (value.length() == 0) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double parseDouble(String text) {
        String value = text == null ? "" : text.trim();
        if (value.length() == 0) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return Double.NaN;
        }
    }

    private static List<List<String>> parseCsv(String csv) {
        String text = stripBom(csv == null ? "" : csv);
        List<List<String>> rows = new ArrayList<List<String>>();
        List<String> row = new ArrayList<String>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(ch);
                }
                continue;
            }

            if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<String>();
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
            } else {
                field.append(ch);
            }
        }
        row.add(field.toString());
        if (!isBlankRow(row) || rows.isEmpty()) {
            rows.add(row);
        }
        return rows;
    }

    private static String stripBom(String text) {
        if (text != null && text.length() > 0 && text.charAt(0) == '\ufeff') {
            return text.substring(1);
        }
        return text;
    }

    private static boolean isBlankRow(List<String> row) {
        if (row == null || row.isEmpty()) {
            return true;
        }
        for (String value : row) {
            if (value != null && value.trim().length() > 0) {
                return false;
            }
        }
        return true;
    }

    private static double[][] nanMatrix(int rows, int columns) {
        double[][] matrix = new double[rows][columns];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                matrix[r][c] = Double.NaN;
            }
        }
        return matrix;
    }

    private static double[][] copy(double[][] matrix) {
        if (matrix == null) {
            return new double[0][0];
        }
        double[][] out = new double[matrix.length][];
        for (int i = 0; i < matrix.length; i++) {
            out[i] = matrix[i] == null ? new double[0] : matrix[i].clone();
        }
        return out;
    }

    private static boolean[][] copy(boolean[][] matrix) {
        if (matrix == null) {
            return new boolean[0][0];
        }
        boolean[][] out = new boolean[matrix.length][];
        for (int i = 0; i < matrix.length; i++) {
            out[i] = matrix[i] == null ? new boolean[0] : matrix[i].clone();
        }
        return out;
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
                if (!isFinite(out[r][c])) {
                    continue;
                }
                out[r][c] = span <= 0.0 ? 0.5 : (out[r][c] - min) / span;
            }
        }
        return out;
    }

    private static boolean hasFinite(double[][] matrix) {
        for (int r = 0; r < matrix.length; r++) {
            for (int c = 0; c < matrix[r].length; c++) {
                if (isFinite(matrix[r][c])) {
                    return true;
                }
            }
        }
        return false;
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

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    public static final class RowKey {
        public final String filePath;
        public final int seriesIndex;
        public final int channelIndex;

        RowKey(String filePath, int seriesIndex, int channelIndex) {
            this.filePath = filePath == null ? "" : filePath;
            this.seriesIndex = seriesIndex;
            this.channelIndex = Math.max(1, channelIndex);
        }

        String label() {
            File file = new File(filePath);
            String name = file.getName();
            if (name == null || name.trim().isEmpty()) {
                name = filePath;
            }
            StringBuilder label = new StringBuilder(name);
            if (seriesIndex >= 0) {
                label.append(" | series ").append(seriesIndex);
            }
            label.append(" | C").append(channelIndex);
            return label.toString();
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof RowKey)) {
                return false;
            }
            RowKey row = (RowKey) other;
            return filePath.equals(row.filePath)
                    && seriesIndex == row.seriesIndex
                    && channelIndex == row.channelIndex;
        }

        @Override public int hashCode() {
            int result = filePath.hashCode();
            result = 31 * result + seriesIndex;
            result = 31 * result + channelIndex;
            return result;
        }
    }

    public static final class Cell {
        public final int row;
        public final int column;
        public final String filePath;
        public final int seriesIndex;
        public final int channelIndex;
        public final String variant;
        public final Double thresholdValue;

        Cell(
                int row,
                int column,
                String filePath,
                int seriesIndex,
                int channelIndex,
                String variant,
                Double thresholdValue) {
            this.row = row;
            this.column = column;
            this.filePath = filePath;
            this.seriesIndex = seriesIndex;
            this.channelIndex = channelIndex;
            this.variant = variant;
            this.thresholdValue = thresholdValue;
        }
    }

    private static final class InputRow {
        final int row;
        final int column;
        final double count;
        final double f1;
        final double agreement;
        final double fragility;
        final double threshold;

        InputRow(
                int row,
                int column,
                double count,
                double f1,
                double agreement,
                double fragility,
                double threshold) {
            this.row = row;
            this.column = column;
            this.count = count;
            this.f1 = f1;
            this.agreement = agreement;
            this.fragility = fragility;
            this.threshold = threshold;
        }
    }
}
