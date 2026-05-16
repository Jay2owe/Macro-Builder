package macro.builder.ui;

import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import macro.builder.analysis.LiveMaskBuilder;
import macro.builder.analysis.ObjectCounter;
import macro.builder.analysis.ShootoutContext;
import macro.builder.analysis.ShootoutResult;
import macro.builder.analysis.ShootoutSettings;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public final class ScrubPane extends JDialog {
    private static final int THRESHOLD_TICKS = 1000;
    private static final int THRESHOLD_MAX = THRESHOLD_TICKS - 1;
    private static final long LIVE_PREVIEW_CAP_BYTES = 64L * 1024L * 1024L;

    private final ShootoutContext context;
    private final ShootoutSettings settings;
    private final ImagePlus processed;
    private final PinHandler pinHandler;
    private final ThresholdSlider thresholdSlider;
    private final JSlider sliceSlider;
    private final JLabel readout = new JLabel(" ");
    private final JButton pinButton = new JButton("Pin this value");
    private final Timer debounce;
    private final int activeFrame;
    private final int sliceCount;
    private final int previewWidth;
    private final int previewHeight;
    private final boolean downsampled;
    private final boolean quantizeInteger;

    private boolean adjusting;
    private boolean disposed;
    private int cachedPreviewSlice = -1;
    private ImageProcessor cachedPreviewSource;
    private ByteProcessor previewMask;
    private ImagePlus livePreview;
    private SwingWorker<CountResult, Void> countWorker;
    private int requestSerial;

    public ScrubPane(
            Window owner,
            ShootoutContext context,
            ShootoutSettings settings,
            List<ShootoutResult> rows,
            int activeSlice,
            int activeFrame,
            PinHandler pinHandler) {
        super(owner, "Scrub threshold", Dialog.ModalityType.MODELESS);
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (settings == null) {
            throw new IllegalArgumentException("settings must not be null");
        }
        this.context = context;
        this.settings = settings;
        this.processed = context.processed;
        this.pinHandler = pinHandler;
        this.sliceCount = Math.max(1, processed.getNSlices());
        this.activeFrame = clamp(activeFrame, 1, Math.max(1, processed.getNFrames()));
        this.quantizeInteger = !context.isFloat;

        PreviewSize previewSize = previewSize(processed.getWidth(), processed.getHeight());
        this.previewWidth = previewSize.width;
        this.previewHeight = previewSize.height;
        this.downsampled = previewSize.downsampled;
        debounce = new Timer(30, new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                repaintNow();
            }
        });
        debounce.setRepeats(false);

        thresholdSlider = new ThresholdSlider(0, THRESHOLD_MAX);
        thresholdSlider.setPreferredSize(new Dimension(360, 42));
        thresholdSlider.setAutoTicks(autoTicks(rows));
        setThresholdSliderValue(tickFor(initialThreshold(rows)));
        thresholdSlider.addChangeListener(new ChangeListener() {
            @Override public void stateChanged(ChangeEvent e) {
                if (!adjusting) {
                    debounce.restart();
                }
            }
        });

        if (sliceCount > 1) {
            sliceSlider = new JSlider(1, sliceCount, clamp(activeSlice, 1, sliceCount));
            sliceSlider.setPreferredSize(new Dimension(360, 36));
            sliceSlider.addChangeListener(new ChangeListener() {
                @Override public void stateChanged(ChangeEvent e) {
                    debounce.restart();
                }
            });
        } else {
            sliceSlider = null;
        }

        buildUi();
    }

    public void open() {
        pack();
        setLocationRelativeTo(getOwner());
        setVisible(true);
        debounce.restart();
    }

    public void setPinBusy(boolean busy) {
        pinButton.setEnabled(!busy && !disposed);
    }

    @Override public void dispose() {
        if (disposed) {
            super.dispose();
            return;
        }
        disposed = true;
        debounce.stop();
        if (countWorker != null && !countWorker.isDone()) {
            countWorker.cancel(true);
        }
        closeLivePreview();
        super.dispose();
    }

    private void buildUi() {
        setUndecorated(true);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                BorderFactory.createEmptyBorder(8, 10, 10, 10)));

        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.add(new JLabel("Scrub threshold"), BorderLayout.WEST);
        JButton close = new JButton("Close");
        close.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
        header.add(close, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        JPanel controls = new JPanel(new GridBagLayout());
        int row = 0;
        addRow(controls, row++, "Threshold", thresholdSlider);
        if (sliceSlider != null) {
            addRow(controls, row++, "Z-slice", sliceSlider);
        }
        GridBagConstraints readoutConstraints = constraints(row++);
        readoutConstraints.gridx = 0;
        readoutConstraints.gridwidth = 2;
        readoutConstraints.fill = GridBagConstraints.HORIZONTAL;
        controls.add(readout, readoutConstraints);

        JPanel pinPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        pinButton.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                if (pinHandler != null) {
                    pinHandler.pinThreshold(currentThreshold());
                }
            }
        });
        pinPanel.add(pinButton);
        GridBagConstraints pinConstraints = constraints(row);
        pinConstraints.gridx = 0;
        pinConstraints.gridwidth = 2;
        pinConstraints.fill = GridBagConstraints.HORIZONTAL;
        controls.add(pinPanel, pinConstraints);

        root.add(controls, BorderLayout.CENTER);
        setContentPane(root);
    }

    private static void addRow(JPanel panel, int row, String label, JSlider slider) {
        GridBagConstraints labelConstraints = constraints(row);
        labelConstraints.gridx = 0;
        panel.add(new JLabel(label), labelConstraints);

        GridBagConstraints sliderConstraints = constraints(row);
        sliderConstraints.gridx = 1;
        sliderConstraints.weightx = 1.0;
        sliderConstraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(slider, sliderConstraints);
    }

    private static GridBagConstraints constraints(int row) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = row;
        constraints.insets = new Insets(4, 4, 4, 4);
        constraints.anchor = GridBagConstraints.WEST;
        return constraints;
    }

    private void repaintNow() {
        if (disposed || !isDisplayable()) {
            return;
        }
        final int slice = currentSlice();
        final double threshold = currentThreshold();
        try {
            ImageProcessor previewSource = previewSource(slice);
            ensurePreviewMask(previewSource.getWidth(), previewSource.getHeight());
            LiveMaskBuilder.rebuildInPlace(previewMask, previewSource, threshold, context.rangeMax);
            showLivePreview();
            readout.setText(readoutText(threshold, null, "counting..."));
            startCountWorker(slice, threshold);
        } catch (RuntimeException ex) {
            readout.setText("Preview unavailable: " + cleanMessage(ex));
        }
    }

    private ImageProcessor previewSource(int slice) {
        if (cachedPreviewSource != null && cachedPreviewSlice == slice) {
            return cachedPreviewSource;
        }
        ImageProcessor full = fullProcessor(slice);
        if (downsampled) {
            ImageProcessor resized = full.duplicate().resize(previewWidth, previewHeight);
            cachedPreviewSource = resized == null ? full.duplicate() : resized;
        } else {
            cachedPreviewSource = full;
        }
        cachedPreviewSlice = slice;
        return cachedPreviewSource;
    }

    private ImageProcessor fullProcessor(int slice) {
        ImageStack stack = processed.getStack();
        int stackIndex;
        if (processed.getNChannels() * processed.getNSlices() * processed.getNFrames() == processed.getStackSize()) {
            stackIndex = processed.getStackIndex(1, clamp(slice, 1, sliceCount), activeFrame);
        } else {
            stackIndex = clamp(slice, 1, processed.getStackSize());
        }
        return stack.getProcessor(stackIndex);
    }

    private void ensurePreviewMask(int width, int height) {
        if (previewMask == null || previewMask.getWidth() != width || previewMask.getHeight() != height) {
            previewMask = new ByteProcessor(width, height);
        }
    }

    private void showLivePreview() {
        if (livePreview == null || livePreview.getWindow() == null) {
            livePreview = new ImagePlus("Macro Builder Live Mask Preview", previewMask);
            livePreview.show();
            if (livePreview.getWindow() != null) {
                WindowManager.setCurrentWindow(livePreview.getWindow());
            }
            return;
        }
        livePreview.setProcessor("Macro Builder Live Mask Preview", previewMask);
        livePreview.updateAndDraw();
    }

    private void startCountWorker(final int slice, final double threshold) {
        final int serial = ++requestSerial;
        if (countWorker != null && !countWorker.isDone()) {
            countWorker.cancel(true);
        }
        countWorker = new SwingWorker<CountResult, Void>() {
            @Override protected CountResult doInBackground() {
                ImageProcessor full = fullProcessor(slice);
                ByteProcessor countMask = new ByteProcessor(full.getWidth(), full.getHeight());
                LiveMaskBuilder.rebuildInPlace(countMask, full, threshold, context.rangeMax);
                ImagePlus countImage = new ImagePlus("Macro Builder Live Count", countMask);
                ObjectCounter.CountSummary summary = ObjectCounter.count(countImage, settings);
                countImage.flush();
                return new CountResult(serial, threshold, summary);
            }

            @Override protected void done() {
                if (countWorker == this) {
                    countWorker = null;
                }
                if (disposed || !isDisplayable() || isCancelled()) {
                    return;
                }
                try {
                    CountResult result = get();
                    if (result.serial == requestSerial) {
                        readout.setText(readoutText(result.threshold, result.summary, null));
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    readout.setText(readoutText(threshold, null, "count unavailable: "
                            + cleanMessage(ex.getCause())));
                }
            }
        };
        countWorker.execute();
    }

    private String readoutText(double threshold, ObjectCounter.CountSummary summary, String status) {
        StringBuilder sb = new StringBuilder();
        sb.append("Threshold ").append(formatNumber(threshold));
        if (summary != null) {
            sb.append(" | count ").append(summary.count);
            sb.append(" | coverage ").append(formatNumber(summary.coverage * 100.0)).append("%");
        } else if (status != null && !status.trim().isEmpty()) {
            sb.append(" | ").append(status.trim());
        }
        if (downsampled) {
            sb.append(" | downsampled for live view");
        }
        return sb.toString();
    }

    private int currentSlice() {
        return sliceSlider == null ? 1 : clamp(sliceSlider.getValue(), 1, sliceCount);
    }

    private double currentThreshold() {
        return thresholdFor(thresholdSlider.getValue());
    }

    private double thresholdFor(int tick) {
        double min = context.rangeMin;
        double max = context.rangeMax;
        if (!isFinite(min) || !isFinite(max) || max <= min) {
            return min;
        }
        int clamped = clamp(tick, 0, THRESHOLD_MAX);
        double value = min + (max - min) * clamped / (double) THRESHOLD_MAX;
        if (quantizeInteger) {
            value = Math.rint(value);
        }
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private int tickFor(double threshold) {
        double min = context.rangeMin;
        double max = context.rangeMax;
        if (!isFinite(min) || !isFinite(max) || max <= min) {
            return 0;
        }
        int tick = (int) Math.round((threshold - min) * THRESHOLD_MAX / (max - min));
        return clamp(tick, 0, THRESHOLD_MAX);
    }

    private void setThresholdSliderValue(int tick) {
        adjusting = true;
        try {
            thresholdSlider.getModel().setValue(clamp(tick, 0, THRESHOLD_MAX));
        } finally {
            adjusting = false;
        }
    }

    private double initialThreshold(List<ShootoutResult> rows) {
        if (rows != null) {
            for (ShootoutResult row : rows) {
                if (row != null && row.recommended && row.thresholdValue != null) {
                    return row.thresholdValue.doubleValue();
                }
            }
            for (ShootoutResult row : rows) {
                if (row != null && row.isSuccess() && row.thresholdValue != null) {
                    return row.thresholdValue.doubleValue();
                }
            }
        }
        if (isFinite(context.rangeMin) && isFinite(context.rangeMax)) {
            return context.rangeMin + (context.rangeMax - context.rangeMin) / 2.0;
        }
        return 0.0;
    }

    private List<Integer> autoTicks(List<ShootoutResult> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<Integer> ticks = new ArrayList<Integer>();
        for (ShootoutResult row : rows) {
            if (row != null
                    && row.isSuccess()
                    && row.source == ShootoutResult.Source.AUTO
                    && row.thresholdValue != null) {
                Integer tick = Integer.valueOf(tickFor(row.thresholdValue.doubleValue()));
                if (!ticks.contains(tick)) {
                    ticks.add(tick);
                }
            }
        }
        return ticks;
    }

    private static PreviewSize previewSize(int width, int height) {
        long pixels = (long) Math.max(1, width) * (long) Math.max(1, height);
        long estimatedBytes = 2L * pixels;
        if (estimatedBytes <= LIVE_PREVIEW_CAP_BYTES) {
            return new PreviewSize(width, height, false);
        }
        double scale = Math.sqrt(LIVE_PREVIEW_CAP_BYTES / (double) estimatedBytes);
        int previewWidth = Math.max(1, (int) Math.floor(width * scale));
        int previewHeight = Math.max(1, (int) Math.floor(height * scale));
        return new PreviewSize(previewWidth, previewHeight, true);
    }

    private void closeLivePreview() {
        if (livePreview == null) {
            return;
        }
        try {
            livePreview.changes = false;
            if (livePreview.getWindow() != null) {
                livePreview.close();
            } else {
                livePreview.flush();
            }
        } catch (Throwable ignored) {
        } finally {
            livePreview = null;
        }
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static String formatNumber(double value) {
        if (Double.isNaN(value)) return "";
        if (Double.isInfinite(value)) return value > 0.0 ? "Infinity" : "-Infinity";
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

    public interface PinHandler {
        void pinThreshold(double threshold);
    }

    private static final class PreviewSize {
        final int width;
        final int height;
        final boolean downsampled;

        PreviewSize(int width, int height, boolean downsampled) {
            this.width = width;
            this.height = height;
            this.downsampled = downsampled;
        }
    }

    private static final class CountResult {
        final int serial;
        final double threshold;
        final ObjectCounter.CountSummary summary;

        CountResult(int serial, double threshold, ObjectCounter.CountSummary summary) {
            this.serial = serial;
            this.threshold = threshold;
            this.summary = summary;
        }
    }

    private static final class ThresholdSlider extends JSlider {
        private List<Integer> autoTicks = Collections.emptyList();

        ThresholdSlider(int min, int max) {
            super(min, max);
            setPaintTrack(true);
        }

        void setAutoTicks(List<Integer> autoTicks) {
            this.autoTicks = autoTicks == null
                    ? Collections.<Integer>emptyList()
                    : new ArrayList<Integer>(autoTicks);
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (autoTicks.isEmpty()) {
                return;
            }
            int min = getMinimum();
            int max = getMaximum();
            int left = 14;
            int right = Math.max(left + 1, getWidth() - 14);
            int y0 = getHeight() - 14;
            int y1 = getHeight() - 4;
            Color old = g.getColor();
            g.setColor(new Color(30, 90, 170));
            for (Integer tick : autoTicks) {
                if (tick == null) {
                    continue;
                }
                int value = clamp(tick.intValue(), min, max);
                int x = left + (int) Math.round((right - left) * (value - min) / (double) (max - min));
                g.drawLine(x, y0, x, y1);
            }
            g.setColor(old);
        }
    }
}
