package macro.builder.api;

import ij.ImagePlus;
import macro.builder.analysis.BatchMacroInput;
import macro.builder.analysis.BatchMacroScanner;
import macro.builder.analysis.BatchShootoutRunner;
import macro.builder.image.BioFormatsSeriesProvider;

import java.io.File;
import java.util.List;

/** Public facade for discovering image files and Bio-Formats container series. */
public final class MacroBuilderInputs {

    private MacroBuilderInputs() {
    }

    public static List<BatchMacroInput> scanFolder(
            File rootFolder,
            String filenameRegex,
            boolean recursive) {
        return new BatchMacroScanner().scanFolder(rootFolder, filenameRegex, recursive);
    }

    public static boolean isDirectImageFile(File file) {
        return BatchMacroScanner.isDirectImageFile(file);
    }

    public static boolean isBioFormatsContainer(File file) {
        return BatchShootoutRunner.isBioFormatsContainer(file);
    }

    public static boolean isBioFormatsAvailable() {
        return new BioFormatsSeriesProvider().isAvailable();
    }

    public static String bioFormatsUnavailableMessage() {
        return new BioFormatsSeriesProvider().unavailableMessage();
    }

    public static List<BatchMacroInput> listBioFormatsSeries(File container) {
        return new BioFormatsSeriesProvider().listSeries(container);
    }

    /**
     * Opens the selected Bio-Formats series. The caller owns the returned
     * ImagePlus and should close or flush it when finished.
     */
    public static ImagePlus openBioFormatsSeries(BatchMacroInput input) {
        return new BioFormatsSeriesProvider().openSeries(input);
    }
}
