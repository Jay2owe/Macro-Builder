package macro.builder;

import ij.IJ;
import ij.Macro;
import ij.plugin.PlugIn;
import macro.builder.analysis.BatchShootoutRunner;
import macro.builder.api.MacroBuilder;
import macro.builder.api.MacroBuilderBatchCountParameters;
import macro.builder.api.MacroBuilderBatchCountResult;

import java.awt.GraphicsEnvironment;
import java.io.File;

public class Macro_Builder_Batch_Count implements PlugIn {

    @Override
    public void run(String arg) {
        try {
            MacroBuilderBatchCountParameters parameters =
                    MacroBuilderBatchCountParameters.fromMacroOptions(
                            optionsText(arg), new StatusProgress());
            MacroBuilderBatchCountResult result = MacroBuilder.runBatchCount(parameters);
            IJ.showStatus("Macro Builder batch count complete: " + result.rows().size() + " row(s).");
            if (result.csvFile() != null) {
                IJ.log("Macro Builder batch count wrote " + result.csvFile().getAbsolutePath());
            }
        } catch (Exception ex) {
            reportFailure(ex);
        }
    }

    private static String optionsText(String arg) {
        if (arg != null && !arg.trim().isEmpty()) {
            return arg;
        }
        String options = Macro.getOptions();
        return options == null ? "" : options;
    }

    private static void reportFailure(Exception ex) {
        String message = cleanMessage(ex);
        IJ.log("Macro Builder batch count failed: " + message);
        if (!GraphicsEnvironment.isHeadless()) {
            IJ.showMessage("Macro Builder Batch Count", "Batch count failed:\n" + message);
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
        return message.trim().replace('\n', ' ').replace('\r', ' ');
    }

    private static final class StatusProgress implements BatchShootoutRunner.Progress {
        @Override public void onStarted(int totalFiles) {
            IJ.showStatus("Macro Builder batch count: " + totalFiles + " file(s).");
        }

        @Override public void onFileStarted(File file, int index, int totalFiles) {
            IJ.showStatus("Macro Builder batch count " + index + "/" + totalFiles + ": " + file.getName());
        }

        @Override public void onFileFinished(File file, int index, int totalFiles, int rowCount) {
            IJ.showStatus("Macro Builder batch count " + index + "/" + totalFiles
                    + " complete: " + rowCount + " row(s).");
        }

        @Override public boolean isCancelled() {
            return false;
        }
    }
}
