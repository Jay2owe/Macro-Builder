package macro.builder.api;

import ij.ImagePlus;
import macro.builder.analysis.DetectedObject;
import macro.builder.analysis.ObjectCounter;
import macro.builder.analysis.ShootoutResult;
import macro.builder.analysis.ShootoutRun;
import macro.builder.analysis.ShootoutSettings;
import macro.builder.analysis.ThresholdShootoutRunner;
import macro.builder.image.FilterExecutor;

import java.util.List;

/** Public facade for object counting and single-image Test Counts workflows. */
public final class MacroBuilderCounting {

    private MacroBuilderCounting() {
    }

    public static List<ShootoutResult> runShootout(
            ImagePlus source,
            String macro,
            ShootoutSettings settings) {
        return new ThresholdShootoutRunner().run(source, macro, settings);
    }

    public static List<ShootoutResult> runShootout(
            ImagePlus source,
            String macro,
            ShootoutSettings settings,
            int primaryChannel,
            FilterExecutor.Progress progress) {
        return new ThresholdShootoutRunner().run(source, macro, settings, primaryChannel, progress);
    }

    public static ShootoutRun runShootoutWithContext(
            ImagePlus source,
            String macro,
            ShootoutSettings settings) {
        return new ThresholdShootoutRunner().runWithContext(source, macro, settings);
    }

    public static ShootoutRun runShootoutWithContext(
            ImagePlus source,
            String macro,
            ShootoutSettings settings,
            int primaryChannel,
            FilterExecutor.Progress progress) {
        return new ThresholdShootoutRunner().runWithContext(
                source,
                macro,
                settings,
                primaryChannel,
                progress);
    }

    public static ShootoutResult runOneVariant(
            ImagePlus source,
            String macro,
            ShootoutSettings settings,
            int primaryChannel,
            String variant,
            Double thresholdValue,
            FilterExecutor.Progress progress) {
        return new ThresholdShootoutRunner().runOneVariant(
                source,
                macro,
                settings,
                primaryChannel,
                variant,
                thresholdValue,
                progress);
    }

    public static ShootoutResult runOneVariant(
            ShootoutRun run,
            ShootoutSettings settings,
            String variant,
            Double thresholdValue) {
        if (run == null || run.context == null) {
            throw new IllegalArgumentException("run with retained context must not be null");
        }
        return new ThresholdShootoutRunner().runOneVariant(
                run.context,
                settings,
                variant,
                thresholdValue);
    }

    public static List<String> defaultAutoMethods() {
        return ThresholdShootoutRunner.defaultAutoMethods();
    }

    public static ObjectCounter.CountSummary countObjects(
            ImagePlus mask,
            ShootoutSettings settings) {
        return ObjectCounter.count(mask, settings);
    }

    public static List<DetectedObject> detectObjects(
            ImagePlus mask,
            ShootoutSettings settings) {
        return ObjectCounter.detect(mask, settings);
    }

    /**
     * Closes the retained processed image from {@link #runShootoutWithContext}.
     * Result mask previews are owned separately by the caller.
     */
    public static void closeShootoutRun(ShootoutRun run) {
        if (run != null && run.context != null) {
            closeImage(run.context.processed);
        }
    }

    /** Closes mask preview images returned in successful shootout rows. */
    public static void closeMaskPreviews(List<ShootoutResult> results) {
        if (results == null) {
            return;
        }
        for (ShootoutResult result : results) {
            if (result != null) {
                closeImage(result.maskPreview);
            }
        }
    }

    private static void closeImage(ImagePlus image) {
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
}
