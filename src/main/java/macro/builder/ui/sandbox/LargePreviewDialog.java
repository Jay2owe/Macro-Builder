package macro.builder.ui.sandbox;

import macro.builder.ui.ImagePreviewPanel;
import ij.ImagePlus;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
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

    private final ImagePreviewPanel sourcePreview = new ImagePreviewPanel("Source image");
    private final ImagePreviewPanel outputPreview = new ImagePreviewPanel("Preview output");
    private SliceListener sliceListener;
    private boolean syncingSlices;

    LargePreviewDialog(Window owner) {
        super(owner, "Large preview", ModalityType.MODELESS);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        add(buildPreviews(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
        wireSliceSync();
        pack();
        setMinimumSize(new Dimension(820, 520));
        sizeNearDesktop();
    }

    void setSliceListener(SliceListener sliceListener) {
        this.sliceListener = sliceListener;
    }

    void setImages(ImagePlus sourceImage, ImagePlus outputImage, int zSlice) {
        sourcePreview.setImage(sourceImage);
        outputPreview.setImage(outputImage);
        setCurrentZ(zSlice);
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
        JPanel footer = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 8));
        JButton close = new JButton("Close");
        close.addActionListener(e -> setVisible(false));
        footer.add(close);
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
