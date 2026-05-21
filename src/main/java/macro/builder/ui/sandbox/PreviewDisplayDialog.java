package macro.builder.ui.sandbox;

import ij.ImagePlus;
import macro.builder.ui.MinMaxControlPanel;
import macro.builder.ui.PreviewDisplaySettings;

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

final class PreviewDisplayDialog extends JDialog {

    interface Listener {
        void sourceRangeChanged(double min, double max, boolean adjusting);
        void outputRangeChanged(double min, double max, boolean adjusting);
    }

    private final MinMaxControlPanel sourceControls =
            new MinMaxControlPanel("Source brightness/contrast", false);
    private final MinMaxControlPanel outputControls =
            new MinMaxControlPanel("Preview brightness/contrast", false);

    private Listener listener;
    private boolean updating;

    PreviewDisplayDialog(Window owner) {
        super(owner, "Preview Brightness/Contrast", ModalityType.MODELESS);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        add(buildControls(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
        setMinimumSize(new Dimension(420, 560));
        wireControls();
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    void setImages(ImagePlus source, ImagePlus output,
                   PreviewDisplaySettings sourceSettings,
                   PreviewDisplaySettings outputSettings) {
        updating = true;
        try {
            sourceControls.setImage(source);
            if (sourceSettings != null && sourceSettings.hasDisplayRange()) {
                sourceControls.setRange(sourceSettings.getDisplayMin(), sourceSettings.getDisplayMax());
            }
            sourceControls.setEnabled(hasUsableImage(source));

            outputControls.setImage(output);
            if (outputSettings != null && outputSettings.hasDisplayRange()) {
                outputControls.setRange(outputSettings.getDisplayMin(), outputSettings.getDisplayMax());
            }
            outputControls.setEnabled(hasUsableImage(output));
        } finally {
            updating = false;
        }
    }

    void showNear(Window owner) {
        pack();
        positionNear(owner);
        setVisible(true);
        toFront();
        requestFocus();
    }

    private JPanel buildControls() {
        JPanel controls = new JPanel(new GridLayout(2, 1, 0, 8));
        controls.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        controls.add(sourceControls);
        controls.add(outputControls);
        return controls;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton close = new JButton("Close");
        close.addActionListener(e -> setVisible(false));
        footer.add(close);
        return footer;
    }

    private void wireControls() {
        sourceControls.setListener(new MinMaxControlPanel.Listener() {
            @Override public void rangeChanged(double min, double max, boolean adjusting) {
                if (!updating && listener != null) listener.sourceRangeChanged(min, max, adjusting);
            }

            @Override public void autoRequested() {
                if (!updating && listener != null) {
                    listener.sourceRangeChanged(sourceControls.getMinValue(), sourceControls.getMaxValue(), false);
                }
            }

            @Override public void resetRequested() {
                if (!updating && listener != null) {
                    listener.sourceRangeChanged(sourceControls.getMinValue(), sourceControls.getMaxValue(), false);
                }
            }

            @Override public void setRequested() {
                if (!updating && listener != null) {
                    listener.sourceRangeChanged(sourceControls.getMinValue(), sourceControls.getMaxValue(), false);
                }
            }
        });
        outputControls.setListener(new MinMaxControlPanel.Listener() {
            @Override public void rangeChanged(double min, double max, boolean adjusting) {
                if (!updating && listener != null) listener.outputRangeChanged(min, max, adjusting);
            }

            @Override public void autoRequested() {
                if (!updating && listener != null) {
                    listener.outputRangeChanged(outputControls.getMinValue(), outputControls.getMaxValue(), false);
                }
            }

            @Override public void resetRequested() {
                if (!updating && listener != null) {
                    listener.outputRangeChanged(outputControls.getMinValue(), outputControls.getMaxValue(), false);
                }
            }

            @Override public void setRequested() {
                if (!updating && listener != null) {
                    listener.outputRangeChanged(outputControls.getMinValue(), outputControls.getMaxValue(), false);
                }
            }
        });
    }

    private void positionNear(Window owner) {
        if (owner == null || !owner.isShowing()) {
            setLocationRelativeTo(owner);
            return;
        }
        Rectangle ownerBounds = owner.getBounds();
        Rectangle screen = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        Dimension size = getSize();
        int gap = 8;
        int x = ownerBounds.x + ownerBounds.width + gap;
        if (x + size.width > screen.x + screen.width) {
            x = ownerBounds.x - size.width - gap;
        }
        if (x < screen.x) {
            x = Math.max(screen.x, Math.min(ownerBounds.x, screen.x + screen.width - size.width));
        }
        int y = ownerBounds.y;
        if (y + size.height > screen.y + screen.height) {
            y = screen.y + Math.max(0, screen.height - size.height);
        }
        y = Math.max(screen.y, y);
        setLocation(x, y);
    }

    private static boolean hasUsableImage(ImagePlus image) {
        try {
            return image != null && image.getStack() != null && image.getStackSize() > 0;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
