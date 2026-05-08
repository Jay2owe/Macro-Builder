package macro.builder.ui.sandbox.variation;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.process.LUT;

/**
 * Copies a source {@link ImagePlus}'s display settings (LUT, min/max,
 * calibration, composite mode and channel activation state) onto a freshly
 * duplicated copy of a variant {@link ImagePlus}.
 *
 * <p>Without this, JND-level differences between variants get drowned in
 * display drift — one variant tile may auto-stretch to a different min/max
 * than the source, and the user can't tell whether they're seeing a real
 * pipeline difference or just a different histogram.
 *
 * <p>The variant is duplicated before any settings are applied, so the
 * uncaptioned {@code VariantResult.output} stays clean for downstream callers
 * (Promote, export). See stage 06's known risks for the contract.
 */
public final class DisplaySettingsCloner {

    private DisplaySettingsCloner() {}

    /**
     * Returns a duplicate of {@code variant} with display settings copied from
     * {@code source}. Both inputs are left untouched; the returned image is a
     * fresh {@link ImagePlus}.
     *
     * <p>Channel mismatches are tolerated: per-channel LUT/range copies stop
     * at {@code min(source.nChannels, variant.nChannels)}.
     */
    public static ImagePlus cloneFrom(ImagePlus source, ImagePlus variant) {
        if (source == null) throw new IllegalArgumentException("source must not be null");
        if (variant == null) throw new IllegalArgumentException("variant must not be null");
        ImagePlus copy = variant.duplicate();
        copy.setTitle(variant.getTitle());

        Calibration cal = source.getCalibration();
        if (cal != null) copy.setCalibration(cal.copy());

        boolean sourceComposite = source instanceof CompositeImage;
        boolean copyComposite = copy instanceof CompositeImage;
        if (sourceComposite && copyComposite) {
            copyComposite((CompositeImage) source, (CompositeImage) copy);
        } else {
            copySingleChannel(source, copy);
        }
        return copy;
    }

    private static void copySingleChannel(ImagePlus source, ImagePlus copy) {
        LUT[] luts = source.getLuts();
        if (luts != null && luts.length > 0 && luts[0] != null) {
            copy.setLut((LUT) luts[0].clone());
        }
        copy.setDisplayRange(source.getDisplayRangeMin(), source.getDisplayRangeMax());
    }

    private static void copyComposite(CompositeImage source, CompositeImage copy) {
        int channels = Math.min(source.getNChannels(), copy.getNChannels());
        if (channels <= 0) return;

        LUT[] sourceLuts = source.getLuts();
        if (sourceLuts != null && sourceLuts.length >= channels) {
            LUT[] copyLuts = new LUT[copy.getNChannels()];
            LUT[] existing = copy.getLuts();
            for (int i = 0; i < copyLuts.length; i++) {
                if (i < channels && sourceLuts[i] != null) {
                    copyLuts[i] = (LUT) sourceLuts[i].clone();
                } else if (existing != null && i < existing.length && existing[i] != null) {
                    copyLuts[i] = existing[i];
                } else {
                    copyLuts[i] = LUT.createLutFromColor(java.awt.Color.WHITE);
                }
            }
            copy.setLuts(copyLuts);
        }

        int rememberSource = source.getChannel();
        int rememberCopy = copy.getChannel();
        try {
            for (int ch = 1; ch <= channels; ch++) {
                source.setPositionWithoutUpdate(ch, source.getSlice(), source.getFrame());
                copy.setPositionWithoutUpdate(ch, copy.getSlice(), copy.getFrame());
                copy.setDisplayRange(source.getDisplayRangeMin(), source.getDisplayRangeMax());
            }
        } finally {
            source.setPositionWithoutUpdate(rememberSource, source.getSlice(), source.getFrame());
            copy.setPositionWithoutUpdate(rememberCopy, copy.getSlice(), copy.getFrame());
        }

        copy.setMode(source.getMode());
        boolean[] active = source.getActiveChannels();
        boolean[] copyActive = copy.getActiveChannels();
        if (active != null && copyActive != null) {
            int n = Math.min(active.length, copyActive.length);
            for (int i = 0; i < n; i++) copyActive[i] = active[i];
        }
        copy.updateAndDraw();
    }
}
