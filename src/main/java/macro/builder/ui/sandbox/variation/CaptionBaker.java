package macro.builder.ui.sandbox.variation;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;

import java.awt.Color;
import java.awt.Font;

/**
 * Bakes a single-line caption into the bottom-left corner of every slice of an
 * {@link ImagePlus}. The text is rendered as a 1-pixel black stroke under a
 * white fill so it remains readable over any image content.
 *
 * <p>The {@code ImagePlus} passed in is mutated. Callers MUST clone the
 * variant's {@link ImagePlus} before baking because the uncaptioned
 * {@code VariantResult.output} is reused by Promote and montage export, so it
 * must not be mutated in place.
 */
public final class CaptionBaker {

    private static final int MARGIN_PX = 6;
    private static final int FONT_PT = 14;

    private CaptionBaker() {}

    public static void bakeAll(ImagePlus imp, String caption) {
        if (imp == null) throw new IllegalArgumentException("imp must not be null");
        if (caption == null || caption.isEmpty()) return;
        ImageStack stack = imp.getStack();
        if (stack == null || stack.getSize() == 0) {
            ImageProcessor ip = imp.getProcessor();
            if (ip != null) bakeOne(ip, caption);
            imp.updateAndDraw();
            return;
        }
        for (int i = 1; i <= stack.size(); i++) {
            ImageProcessor ip = stack.getProcessor(i);
            bakeOne(ip, caption);
        }
        imp.updateAndDraw();
    }

    private static void bakeOne(ImageProcessor ip, String caption) {
        ip.setFont(new Font("SansSerif", Font.BOLD, FONT_PT));
        ip.setAntialiasedText(true);
        int x = MARGIN_PX;
        int y = ip.getHeight() - MARGIN_PX;
        // 1px black stroke around the glyphs.
        ip.setColor(Color.BLACK);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                ip.drawString(caption, x + dx, y + dy);
            }
        }
        // White fill on top.
        ip.setColor(Color.WHITE);
        ip.drawString(caption, x, y);
    }
}
