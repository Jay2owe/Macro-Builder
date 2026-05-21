package macro.builder.ui.sandbox;

import macro.builder.ui.ImagePreviewPanel;
import macro.builder.ui.PreviewDisplaySettings;
import ij.ImagePlus;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.Window;

final class LargePreviewDialog extends JDialog {

    private static final double INITIAL_DESKTOP_WIDTH_FRACTION = 0.82;
    private static final double INITIAL_DESKTOP_HEIGHT_FRACTION = 0.80;

    interface SliceListener {
        void zSliceChanged(int zSlice);
    }

    interface DisplayActionListener {
        void adjustBrightnessContrastRequested();
        void lutToggleRequested();
    }

    private final ImagePreviewPanel sourcePreview = new ImagePreviewPanel("Source image");
    private final ImagePreviewPanel outputPreview = new ImagePreviewPanel("Preview output");
    private final JButton brightnessContrast = new JButton("Brightness/Contrast");
    private final JButton lutToggle = new JButton("Grey LUT");
    private SliceListener sliceListener;
    private DisplayActionListener displayActionListener;
    private boolean syncingSlices;

    LargePreviewDialog(Window owner) {
        super(owner, "Large preview", ModalityType.MODELESS);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        add(buildPreviews(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
        wireSliceSync();
        wireDisplayActions();
        pack();
        setMinimumSize(new Dimension(820, 520));
        sizeNearDesktop();
    }

    void setSliceListener(SliceListener sliceListener) {
        this.sliceListener = sliceListener;
    }

    void setDisplayActionListener(DisplayActionListener displayActionListener) {
        this.displayActionListener = displayActionListener;
    }

    void setImages(ImagePlus sourceImage, ImagePlus outputImage, int zSlice) {
        sourcePreview.setImage(sourceImage);
        outputPreview.setImage(outputImage);
        setCurrentZ(zSlice);
    }

    void setDisplaySettings(PreviewDisplaySettings sourceSettings, PreviewDisplaySettings outputSettings) {
        sourcePreview.setDisplaySettings(sourceSettings);
        outputPreview.setDisplaySettings(outputSettings);
    }

    void setLutToggleText(String text, String tooltip) {
        lutToggle.setText(text == null || text.trim().isEmpty() ? "Grey LUT" : text);
        lutToggle.setToolTipText(tooltip);
    }

    void setCurrentZ(int zSlice) {
        if (syncingSlices) return;
        syncingSlices = true;
        try {
            sourcePreview.setCurrentZ(zSlice);
            outputPreview.setCurrentZ(zSlice);
        } finally {
            syncingSlices = false;
        }
    }

    private JPanel buildPreviews() {
        JPanel previews = new JPanel(new GridLayout(1, 2, 8, 0));
        previews.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        previews.setPreferredSize(new Dimension(1080, 620));
        previews.add(sourcePreview);
        previews.add(outputPreview);
        return previews;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        JPanel display = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        display.add(brightnessContrast);
        display.add(lutToggle);
        footer.add(display, BorderLayout.WEST);

        JPanel closePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        JButton close = new JButton("Close");
        close.addActionListener(e -> setVisible(false));
        closePanel.add(close);
        footer.add(closePanel, BorderLayout.EAST);
        return footer;
    }

    private void wireSliceSync() {
        ImagePreviewPanel.ZSliceChangeListener listener = new ImagePreviewPanel.ZSliceChangeListener() {
            @Override public void zSliceChanged(ImagePreviewPanel source, int zSlice) {
                setCurrentZ(zSlice);
                if (sliceListener != null) sliceListener.zSliceChanged(zSlice);
            }
        };
        sourcePreview.setZSliceChangeListener(listener);
        outputPreview.setZSliceChangeListener(listener);
    }

    private void wireDisplayActions() {
        brightnessContrast.addActionListener(e -> {
            if (displayActionListener != null) {
                displayActionListener.adjustBrightnessContrastRequested();
            }
        });
        lutToggle.addActionListener(e -> {
            if (displayActionListener != null) {
                displayActionListener.lutToggleRequested();
            }
        });
    }

    private void sizeNearDesktop() {
        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        Dimension minimum = getMinimumSize();
        Dimension packed = getSize();
        int targetWidth = (int) Math.round(bounds.width * INITIAL_DESKTOP_WIDTH_FRACTION);
        int targetHeight = (int) Math.round(bounds.height * INITIAL_DESKTOP_HEIGHT_FRACTION);
        int width = Math.min(bounds.width, Math.max(Math.max(minimum.width, packed.width), targetWidth));
        int height = Math.min(bounds.height, Math.max(Math.max(minimum.height, packed.height), targetHeight));
        int x = bounds.x + Math.max(0, (bounds.width - width) / 2);
        int y = bounds.y + Math.max(0, (bounds.height - height) / 2);
        setBounds(x, y, width, height);
    }
}
