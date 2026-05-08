package macro.builder.ui.sandbox.variation;

import ij.ImagePlus;
import ij.gui.ImageCanvas;
import ij.plugin.ZProjector;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;

/**
 * One cell of the variant grid: a caption strip on top, an {@link ImageCanvas}
 * in the centre. The caption strip is a Swing {@link JLabel} mirrored from the
 * baked-in pixel caption, so the user sees the same label whether or not the
 * grid has been exported as PNG.
 *
 * <p>Per-tile MIP toggle: each tile holds the scrub {@link ImagePlus} and a
 * lazily-computed max-projection {@link ImagePlus}. Toggling MIP swaps the
 * visible {@code ImageCanvas} via {@link #setMipMode(boolean)}.
 *
 * <p>Stage 06 keeps the tile read-only — per-tile action buttons (Promote,
 * Save preset, X) land in stage 07 and must live in the caption strip
 * <em>above</em> the canvas, never floating over it. {@code ImageCanvas}
 * extends AWT {@code Canvas}; Swing's lightweight clipping doesn't apply, so
 * any widget overlapping the canvas region would be hidden.
 */
public final class TilePanel extends JPanel {

    private final ImagePlus scrubImp;
    private final ImageCanvas scrubCanvas;
    private final JPanel centre;
    private final JLabel captionLabel;

    private ImagePlus mipImp;        // lazy
    private ImageCanvas mipCanvas;   // lazy
    private boolean mipAttempted;
    private boolean mipMode;

    public TilePanel(ImagePlus imp, String caption, boolean isRaw) {
        super(new BorderLayout());
        if (imp == null) throw new IllegalArgumentException("imp must not be null");
        this.scrubImp = imp;
        this.scrubCanvas = new ImageCanvas(imp);

        captionLabel = new JLabel(caption == null ? "" : caption, SwingConstants.LEFT);
        captionLabel.setFont(captionLabel.getFont().deriveFont(Font.BOLD, 12f));
        captionLabel.setForeground(isRaw ? new Color(0x1f, 0x4f, 0x82) : new Color(0x33, 0x33, 0x33));
        captionLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        north.setOpaque(false);
        north.add(captionLabel);

        centre = new JPanel(new BorderLayout());
        centre.setBackground(Color.BLACK);
        centre.add(scrubCanvas, BorderLayout.CENTER);

        add(north, BorderLayout.NORTH);
        add(centre, BorderLayout.CENTER);
        setBorder(BorderFactory.createLineBorder(new Color(0xc0, 0xc0, 0xc0), 1));
        setBackground(Color.WHITE);
    }

    /** Build a tile that renders an error result instead of an image. */
    public static TilePanel forError(String caption, Throwable error) {
        return new TilePanel(caption, error);
    }

    private TilePanel(String caption, Throwable error) {
        super(new BorderLayout());
        this.scrubImp = null;
        this.scrubCanvas = null;

        captionLabel = new JLabel(caption == null ? "(error)" : caption, SwingConstants.LEFT);
        captionLabel.setFont(captionLabel.getFont().deriveFont(Font.BOLD, 12f));
        captionLabel.setForeground(new Color(0x99, 0x00, 0x00));
        captionLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        String msg = error == null ? "unknown error" : String.valueOf(error.getMessage());
        if (msg == null || msg.isEmpty()) msg = error == null ? "unknown error" : error.getClass().getSimpleName();
        JLabel body = new JLabel("<html><body style='width:160px'>" + escapeHtml(msg) + "</body></html>");
        body.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        body.setForeground(new Color(0x66, 0x00, 0x00));

        centre = new JPanel(new BorderLayout());
        centre.setBackground(new Color(0xff, 0xee, 0xee));
        centre.add(body, BorderLayout.CENTER);

        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        north.setOpaque(false);
        north.add(captionLabel);

        add(north, BorderLayout.NORTH);
        add(centre, BorderLayout.CENTER);
        setBorder(BorderFactory.createLineBorder(new Color(0x99, 0x00, 0x00), 2));
        setBackground(new Color(0xff, 0xf3, 0xf3));
    }

    public boolean hasImage() {
        return scrubImp != null;
    }

    /** The scrub {@link ImagePlus} (the captioned clone) — what the driver registers. */
    public ImagePlus getScrubImp() {
        return scrubImp;
    }

    /** The currently displayed {@link ImageCanvas}, or {@code null} for error tiles. */
    public ImageCanvas getActiveCanvas() {
        if (mipMode && mipCanvas != null) return mipCanvas;
        return scrubCanvas;
    }

    /**
     * Switch between scrub mode (slice scrollbar drives display) and MIP mode
     * (a static max-projection of the variant). No-op for 2-D inputs and
     * error tiles.
     */
    public void setMipMode(boolean on) {
        if (scrubImp == null) return;
        if (on == mipMode) return;
        if (on) {
            ensureMipBuilt();
            if (mipCanvas == null) return;
            centre.removeAll();
            centre.add(mipCanvas, BorderLayout.CENTER);
        } else {
            centre.removeAll();
            centre.add(scrubCanvas, BorderLayout.CENTER);
        }
        mipMode = on;
        centre.revalidate();
        centre.repaint();
    }

    private void ensureMipBuilt() {
        if (mipAttempted) return;
        mipAttempted = true;
        if (scrubImp == null || scrubImp.getNSlices() <= 1) return;
        try {
            ImagePlus projected = ZProjector.run(scrubImp, "max");
            if (projected == null) return;
            if (scrubImp.getCalibration() != null) {
                projected.setCalibration(scrubImp.getCalibration().copy());
            }
            projected.setDisplayRange(scrubImp.getDisplayRangeMin(), scrubImp.getDisplayRangeMax());
            mipImp = projected;
            mipCanvas = new ImageCanvas(projected);
        } catch (Throwable t) {
            mipImp = null;
            mipCanvas = null;
        }
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension base = super.getPreferredSize();
        return new Dimension(Math.max(180, base.width), Math.max(180, base.height));
    }

    private static String escapeHtml(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': b.append("&amp;"); break;
                case '<': b.append("&lt;"); break;
                case '>': b.append("&gt;"); break;
                case '"': b.append("&quot;"); break;
                default:  b.append(c);
            }
        }
        return b.toString();
    }
}
