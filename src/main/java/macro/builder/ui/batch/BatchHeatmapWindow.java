package macro.builder.ui.batch;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import macro.builder.analysis.BatchHeatmapModel;
import macro.builder.analysis.BatchMacroInput;
import macro.builder.analysis.ShootoutResult;
import macro.builder.analysis.ShootoutSettings;
import macro.builder.analysis.ThresholdShootoutRunner;
import macro.builder.image.BioFormatsSeriesProvider;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;

public final class BatchHeatmapWindow extends JFrame {

    private static final long DRILL_IN_MASK_CAP_BYTES = 256L * 1024L * 1024L;
    private static final int DEFAULT_WIDTH = 980;
    private static final int DEFAULT_HEIGHT = 680;

    private final BatchHeatmapModel model;
    private final String macro;
    private final ShootoutSettings settings;
    private final JComboBox<BatchHeatmapModel.MetricKind> metricCombo;
    private final JCheckBox normalisePerRow = new JCheckBox("Normalise per row");
    private final JLabel statusLabel = new JLabel(" ");
    private final HeatmapPanel heatmapPanel;
    private SwingWorker<ImagePlus, Void> drillWorker;

    public static void openCsvAsync(
            final Window owner,
            final File csvFile,
            final String macro,
            final ShootoutSettings settings) {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        if (csvFile == null) {
            return;
        }
        SwingWorker<BatchHeatmapModel, Void> worker = new SwingWorker<BatchHeatmapModel, Void>() {
            @Override protected BatchHeatmapModel doInBackground() throws Exception {
                return BatchHeatmapModel.fromCsv(csvFile);
            }

            @Override protected void done() {
                try {
                    openModel(owner, get(), macro, settings);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    showMessage(owner, "Could not open batch heatmap:\n" + cleanMessage(ex.getCause()));
                } catch (RuntimeException ex) {
                    showMessage(owner, "Could not open batch heatmap:\n" + cleanMessage(ex));
                }
            }
        };
        worker.execute();
    }

    public static void openModel(
            Window owner,
            BatchHeatmapModel model,
            String macro,
            ShootoutSettings settings) {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        BatchHeatmapWindow window = new BatchHeatmapWindow(owner, model, macro, settings);
        window.open(owner);
    }

    private BatchHeatmapWindow(
            Window owner,
            BatchHeatmapModel model,
            String macro,
            ShootoutSettings settings) {
        super("Batch Heatmap");
        this.model = model == null ? BatchHeatmapModel.fromCsvText("") : model;
        this.macro = macro == null ? "" : macro;
        this.settings = settings == null ? ShootoutSettings.defaults() : settings;
        this.metricCombo = new JComboBox<BatchHeatmapModel.MetricKind>(metricOptions(this.model));
        this.heatmapPanel = new HeatmapPanel(this.model, new HeatmapPanel.Listener() {
            @Override public void cellClicked(int row, int column) {
                drillInto(row, column);
            }
        });
        buildUi();
        restoreBounds(owner);
    }

    private void open(Window owner) {
        updateRender();
        setVisible(true);
        toFront();
    }

    private void buildUi() {
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        JPanel controls = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 4));
        controls.setBorder(BorderFactory.createEmptyBorder(8, 10, 0, 10));
        controls.add(new JLabel("Colour by"));
        controls.add(metricCombo);
        controls.add(normalisePerRow);
        add(controls, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(heatmapPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        add(scrollPane, BorderLayout.CENTER);

        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 12, 8, 12));
        add(statusLabel, BorderLayout.SOUTH);

        metricCombo.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                updateRender();
            }
        });
        normalisePerRow.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                updateRender();
            }
        });
        if (metricCombo.getItemCount() == 0) {
            metricCombo.setEnabled(false);
        }

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                saveBounds();
            }

            @Override public void windowClosed(WindowEvent e) {
                saveBounds();
                if (drillWorker != null && !drillWorker.isDone()) {
                    drillWorker.cancel(true);
                }
            }
        });
    }

    private void updateRender() {
        BatchHeatmapModel.MetricKind metric = selectedMetric();
        double[][] matrix = model.matrix(metric);
        int cellWidth = cellWidth();
        int cellHeight = cellHeight();
        HeatmapRenderer.RenderedHeatmap rendered = HeatmapRenderer.renderData(
                matrix,
                ViridisPalette.INSTANCE,
                cellWidth,
                cellHeight,
                normalisePerRow.isSelected());
        heatmapPanel.setRendered(rendered, metric, cellWidth, cellHeight);
        String suffix = rendered.rowGroupSize > 1
                ? " Rows grouped by " + rendered.rowGroupSize + " to stay within the render memory cap."
                : "";
        statusLabel.setText(model.rowCount() + " row(s), " + model.columnCount()
                + " variant(s)." + suffix);
    }

    private BatchHeatmapModel.MetricKind selectedMetric() {
        Object selected = metricCombo.getSelectedItem();
        return selected instanceof BatchHeatmapModel.MetricKind
                ? (BatchHeatmapModel.MetricKind) selected
                : BatchHeatmapModel.MetricKind.COUNT;
    }

    private int cellWidth() {
        int columns = Math.max(1, model.columnCount());
        if (columns > 80) {
            return 18;
        }
        if (columns > 40) {
            return 28;
        }
        if (columns > 20) {
            return 44;
        }
        return 82;
    }

    private int cellHeight() {
        int rows = model.rowCount();
        if (rows > 1000) {
            return HeatmapRenderer.MIN_CELL_SIZE;
        }
        if (rows > 300) {
            return 6;
        }
        return 18;
    }

    private void drillInto(int row, int column) {
        if (drillWorker != null && !drillWorker.isDone()) {
            statusLabel.setText("Drill-in is already running.");
            return;
        }
        final BatchHeatmapModel.Cell cell = model.cellAt(row, column);
        if (cell == null) {
            statusLabel.setText("No batch result for that cell.");
            return;
        }
        final File file = new File(cell.filePath);
        if (!file.isFile()) {
            showMessage(this, "file not found: " + cell.filePath);
            return;
        }
        statusLabel.setText("Opening " + file.getName() + " / " + cell.variant + "...");
        drillWorker = new SwingWorker<ImagePlus, Void>() {
            @Override protected ImagePlus doInBackground() throws Exception {
                return runDrillIn(cell, file);
            }

            @Override protected void done() {
                onDrillInDone(this, cell);
            }
        };
        drillWorker.execute();
    }

    private ImagePlus runDrillIn(BatchHeatmapModel.Cell cell, File file) {
        ImagePlus opened = null;
        try {
            opened = openCellImage(cell, file);
            if (opened == null || opened.getStack() == null) {
                throw new IllegalStateException("Fiji could not open this image file.");
            }
            long estimate = maskEstimateBytes(opened);
            if (estimate > DRILL_IN_MASK_CAP_BYTES) {
                throw new IllegalStateException("too large for drill-in preview: mask estimate "
                        + bytesText(estimate) + " exceeds the 256 MiB cap");
            }
            ShootoutResult result = new ThresholdShootoutRunner().runOneVariant(
                    opened,
                    macro,
                    settings,
                    cell.channelIndex,
                    cell.variant,
                    cell.thresholdValue,
                    null);
            if (result == null) {
                throw new IllegalStateException("No mask was produced.");
            }
            if (!result.isSuccess()) {
                throw new IllegalStateException(result.error);
            }
            if (result.maskPreview == null) {
                throw new IllegalStateException("The selected variant did not produce a mask preview.");
            }
            result.maskPreview.setTitle("Batch heatmap mask - " + cell.variant);
            return result.maskPreview;
        } finally {
            closeImageQuietly(opened);
        }
    }

    private ImagePlus openCellImage(BatchHeatmapModel.Cell cell, File file) {
        if (cell.seriesIndex >= 0) {
            BatchMacroInput input = BatchMacroInput.containerSeries(
                    file,
                    cell.seriesIndex,
                    "",
                    0,
                    0,
                    0,
                    0,
                    0);
            return new BioFormatsSeriesProvider().openSeries(input);
        }
        return IJ.openImage(file.getAbsolutePath());
    }

    private void onDrillInDone(
            SwingWorker<ImagePlus, Void> finishedWorker,
            BatchHeatmapModel.Cell cell) {
        ImagePlus mask = null;
        String failure = null;
        try {
            mask = finishedWorker.get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            failure = "Drill-in interrupted.";
        } catch (ExecutionException ex) {
            failure = "Drill-in failed: " + cleanMessage(ex.getCause());
        } catch (RuntimeException ex) {
            failure = "Drill-in failed: " + cleanMessage(ex);
        }
        if (finishedWorker == drillWorker) {
            drillWorker = null;
        }
        if (!isDisplayable()) {
            closeImageQuietly(mask);
            return;
        }
        if (failure != null) {
            showMessage(this, failure);
            statusLabel.setText(failure);
            return;
        }
        mask.show();
        if (mask.getWindow() != null) {
            WindowManager.setCurrentWindow(mask.getWindow());
        }
        statusLabel.setText("Opened mask for " + cell.variant + ".");
    }

    private void restoreBounds(Window owner) {
        Preferences prefs = prefs();
        int width = Math.max(360, prefs.getInt("width", DEFAULT_WIDTH));
        int height = Math.max(300, prefs.getInt("height", DEFAULT_HEIGHT));
        int x = prefs.getInt("x", Integer.MIN_VALUE);
        int y = prefs.getInt("y", Integer.MIN_VALUE);
        setSize(width, height);
        if (x != Integer.MIN_VALUE && y != Integer.MIN_VALUE) {
            setLocation(x, y);
        } else if (owner != null) {
            setLocationRelativeTo(owner);
        } else {
            setLocationByPlatform(true);
        }
    }

    private void saveBounds() {
        Rectangle bounds = getBounds();
        Preferences prefs = prefs();
        prefs.putInt("x", bounds.x);
        prefs.putInt("y", bounds.y);
        prefs.putInt("width", Math.max(320, bounds.width));
        prefs.putInt("height", Math.max(240, bounds.height));
    }

    private static Preferences prefs() {
        return Preferences.userNodeForPackage(BatchHeatmapWindow.class);
    }

    private static BatchHeatmapModel.MetricKind[] metricOptions(BatchHeatmapModel model) {
        List<BatchHeatmapModel.MetricKind> metrics = model.availableMetrics();
        return metrics.toArray(new BatchHeatmapModel.MetricKind[metrics.size()]);
    }

    private static long maskEstimateBytes(ImagePlus image) {
        if (image == null) {
            return 0L;
        }
        long pixels = safeMultiply(image.getWidth(), image.getHeight());
        pixels = safeMultiply(pixels, Math.max(1, image.getStackSize()));
        return pixels;
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

    private static String bytesText(long bytes) {
        long mib = 1024L * 1024L;
        long rounded = (bytes + mib - 1L) / mib;
        return rounded + " MiB";
    }

    private static void closeImageQuietly(ImagePlus image) {
        if (image == null) {
            return;
        }
        try {
            image.changes = false;
            if (image.getWindow() != null) {
                image.close();
            } else {
                image.flush();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void showMessage(final Window owner, final String message) {
        Runnable show = new Runnable() {
            @Override public void run() {
                JOptionPane.showMessageDialog(owner, message, "Batch Heatmap", JOptionPane.INFORMATION_MESSAGE);
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            show.run();
        } else {
            SwingUtilities.invokeLater(show);
        }
    }

    private static String cleanMessage(Throwable ex) {
        if (ex == null) {
            return "Unknown error";
        }
        String message = ex.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return ex.getClass().getSimpleName();
        }
        return cleanMessage(message);
    }

    private static String cleanMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "Unknown error";
        }
        return message.trim().replace('\n', ' ').replace('\r', ' ');
    }

    private static String formatNumber(double value) {
        if (Double.isNaN(value)) {
            return "";
        }
        if (Double.isInfinite(value)) {
            return value > 0.0 ? "Infinity" : "-Infinity";
        }
        if (value == Math.rint(value) && Math.abs(value) < 1000000000000000.0) {
            return Long.toString(Math.round(value));
        }
        String formatted = String.format(Locale.ROOT, "%.4f", value);
        while (formatted.indexOf('.') >= 0 && formatted.endsWith("0")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        if (formatted.endsWith(".")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        return formatted;
    }

    private static final class HeatmapPanel extends JPanel {
        private static final int TOP = 64;
        private static final int LEFT = 260;
        private static final int RIGHT = 76;
        private static final int BOTTOM = 18;

        private final BatchHeatmapModel model;
        private final Listener listener;
        private HeatmapRenderer.RenderedHeatmap rendered;
        private BatchHeatmapModel.MetricKind metric = BatchHeatmapModel.MetricKind.COUNT;
        private int cellWidth = 82;
        private int cellHeight = 18;
        private int highlightedRow = -1;
        private int highlightedColumn = -1;

        HeatmapPanel(BatchHeatmapModel model, Listener listener) {
            this.model = model;
            this.listener = listener;
            setOpaque(true);
            setBackground(Color.WHITE);
            setToolTipText("");
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    handleClick(e.getX(), e.getY());
                }
            });
        }

        void setRendered(
                HeatmapRenderer.RenderedHeatmap rendered,
                BatchHeatmapModel.MetricKind metric,
                int cellWidth,
                int cellHeight) {
            this.rendered = rendered;
            this.metric = metric == null ? BatchHeatmapModel.MetricKind.COUNT : metric;
            this.cellWidth = Math.max(HeatmapRenderer.MIN_CELL_SIZE, cellWidth);
            this.cellHeight = Math.max(HeatmapRenderer.MIN_CELL_SIZE, cellHeight);
            revalidate();
            repaint();
        }

        @Override public Dimension getPreferredSize() {
            int imageWidth = rendered == null || rendered.image == null ? 1 : rendered.image.getWidth();
            int imageHeight = rendered == null || rendered.image == null ? 1 : rendered.image.getHeight();
            return new Dimension(LEFT + imageWidth + RIGHT, TOP + imageHeight + BOTTOM);
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                paintAxisBackgrounds(g);
                paintColumnLabels(g);
                paintRowLabels(g);
                paintImage(g);
                paintHighlights(g);
                paintScale(g);
            } finally {
                g.dispose();
            }
        }

        @Override public String getToolTipText(MouseEvent event) {
            int displayRow = displayRowAt(event.getY());
            int column = columnAt(event.getX());
            if (displayRow >= 0 && column >= 0) {
                int row = originalRow(displayRow);
                BatchHeatmapModel.Cell cell = model.cellAt(row, column);
                if (cell == null) {
                    return "No value";
                }
                double value = valueAt(displayRow, column);
                return model.rowLabel(row) + " / " + cell.variant + ": " + formatNumber(value);
            }
            if (event.getX() < LEFT && displayRow >= 0) {
                int row = originalRow(displayRow);
                return model.rowKey(row).filePath;
            }
            return null;
        }

        private void paintAxisBackgrounds(Graphics2D g) {
            g.setColor(new Color(248, 250, 252));
            g.fillRect(0, 0, getWidth(), TOP);
            g.fillRect(0, TOP, LEFT, Math.max(0, getHeight() - TOP));
            g.setColor(new Color(203, 213, 225));
            g.drawLine(LEFT - 1, 0, LEFT - 1, getHeight());
            g.drawLine(0, TOP - 1, getWidth(), TOP - 1);
        }

        private void paintImage(Graphics2D g) {
            if (rendered == null || rendered.image == null) {
                return;
            }
            g.drawImage(rendered.image, LEFT, TOP, null);
            if (cellWidth >= 10 && cellHeight >= 10) {
                g.setColor(new Color(255, 255, 255, 90));
                int rows = displayRows();
                int columns = model.columnCount();
                for (int r = 1; r < rows; r++) {
                    int y = TOP + r * cellHeight;
                    g.drawLine(LEFT, y, LEFT + columns * cellWidth, y);
                }
                for (int c = 1; c < columns; c++) {
                    int x = LEFT + c * cellWidth;
                    g.drawLine(x, TOP, x, TOP + rows * cellHeight);
                }
            }
        }

        private void paintColumnLabels(Graphics2D g) {
            FontMetrics fm = g.getFontMetrics();
            g.setColor(new Color(15, 23, 42));
            for (int c = 0; c < model.columnCount(); c++) {
                int x = LEFT + c * cellWidth + 3;
                String text = truncate(model.columnLabel(c), fm, Math.max(8, cellWidth - 6));
                g.drawString(text, x, TOP - 14);
            }
        }

        private void paintRowLabels(Graphics2D g) {
            if (rendered == null) {
                return;
            }
            FontMetrics fm = g.getFontMetrics();
            g.setColor(new Color(15, 23, 42));
            int rows = displayRows();
            boolean dense = cellHeight < 10;
            int step = dense ? Math.max(1, 12 / Math.max(1, cellHeight)) : 1;
            for (int r = 0; r < rows; r += step) {
                int sourceRow = originalRow(r);
                String text = groupedLabel(sourceRow, r);
                int y = TOP + r * cellHeight + Math.max(fm.getAscent(), (cellHeight + fm.getAscent()) / 2 - 2);
                g.drawString(truncate(text, fm, LEFT - 12), 8, y);
            }
        }

        private void paintHighlights(Graphics2D g) {
            if (rendered == null || rendered.image == null) {
                return;
            }
            int imageWidth = rendered.image.getWidth();
            int imageHeight = rendered.image.getHeight();
            g.setStroke(new BasicStroke(2f));
            if (highlightedRow >= 0 && highlightedRow < displayRows()) {
                int y = TOP + highlightedRow * cellHeight;
                g.setColor(new Color(250, 204, 21, 80));
                g.fillRect(LEFT, y, imageWidth, cellHeight);
                g.setColor(new Color(180, 83, 9));
                g.drawRect(LEFT, y, imageWidth - 1, cellHeight - 1);
            }
            if (highlightedColumn >= 0 && highlightedColumn < model.columnCount()) {
                int x = LEFT + highlightedColumn * cellWidth;
                g.setColor(new Color(14, 165, 233, 65));
                g.fillRect(x, TOP, cellWidth, imageHeight);
                g.setColor(new Color(2, 132, 199));
                g.drawRect(x, TOP, cellWidth - 1, imageHeight - 1);
            }
        }

        private void paintScale(Graphics2D g) {
            if (rendered == null || rendered.image == null) {
                return;
            }
            int x = LEFT + rendered.image.getWidth() + 18;
            int y = TOP;
            int height = Math.min(180, Math.max(24, rendered.image.getHeight()));
            int width = 14;
            for (int i = 0; i < height; i++) {
                double fraction = 1.0 - i / (double) Math.max(1, height - 1);
                g.setColor(new Color(ViridisPalette.INSTANCE.colour(fraction)));
                g.drawLine(x, y + i, x + width, y + i);
            }
            g.setColor(new Color(15, 23, 42));
            g.drawRect(x, y, width, height);
            g.drawString(metric.label(), x - 4, Math.max(12, y - 8));
            g.drawString(formatNumber(rendered.maximum), x + width + 5, y + 10);
            g.drawString(formatNumber(rendered.minimum), x + width + 5, y + height);
        }

        private void handleClick(int x, int y) {
            int displayRow = displayRowAt(y);
            int column = columnAt(x);
            if (x < LEFT && displayRow >= 0) {
                highlightedRow = displayRow;
                repaint();
                return;
            }
            if (y < TOP && column >= 0) {
                highlightedColumn = column;
                repaint();
                return;
            }
            if (displayRow >= 0 && column >= 0 && listener != null) {
                highlightedRow = displayRow;
                highlightedColumn = column;
                repaint();
                listener.cellClicked(originalRow(displayRow), column);
            }
        }

        private int displayRowAt(int y) {
            if (rendered == null || y < TOP) {
                return -1;
            }
            int row = (y - TOP) / cellHeight;
            return row >= 0 && row < displayRows() ? row : -1;
        }

        private int columnAt(int x) {
            if (x < LEFT) {
                return -1;
            }
            int column = (x - LEFT) / cellWidth;
            return column >= 0 && column < model.columnCount() ? column : -1;
        }

        private int displayRows() {
            return rendered == null || rendered.matrix == null ? 0 : rendered.matrix.length;
        }

        private int originalRow(int displayRow) {
            int group = rendered == null ? 1 : Math.max(1, rendered.rowGroupSize);
            return Math.min(model.rowCount() - 1, displayRow * group);
        }

        private String groupedLabel(int sourceRow, int displayRow) {
            String label = model.rowLabel(sourceRow);
            int group = rendered == null ? 1 : Math.max(1, rendered.rowGroupSize);
            if (group <= 1) {
                return label;
            }
            int start = displayRow * group;
            int end = Math.min(model.rowCount(), start + group);
            return label + " (+" + Math.max(0, end - start - 1) + " rows)";
        }

        private double valueAt(int displayRow, int column) {
            if (rendered == null || rendered.matrix == null
                    || displayRow < 0 || displayRow >= rendered.matrix.length
                    || column < 0 || column >= rendered.matrix[displayRow].length) {
                return Double.NaN;
            }
            return rendered.matrix[displayRow][column];
        }

        private static String truncate(String text, FontMetrics fm, int maxWidth) {
            String value = text == null ? "" : text;
            if (fm.stringWidth(value) <= maxWidth) {
                return value;
            }
            String suffix = "...";
            int suffixWidth = fm.stringWidth(suffix);
            int limit = Math.max(1, maxWidth - suffixWidth);
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < value.length(); i++) {
                String next = out.toString() + value.charAt(i);
                if (fm.stringWidth(next) > limit) {
                    break;
                }
                out.append(value.charAt(i));
            }
            return out.toString() + suffix;
        }

        interface Listener {
            void cellClicked(int row, int column);
        }
    }
}
