package macro.builder.api;

import macro.builder.analysis.BatchMacroResult;
import macro.builder.analysis.BatchMacroRunner;
import macro.builder.analysis.BatchShootoutResult;
import macro.builder.analysis.BatchShootoutRunner;

import java.io.IOException;
import java.util.List;

/**
 * Public Java facade for Macro Builder automation workflows.
 *
 * <p>These methods do not open Macro Builder Swing dialogs. They may open and
 * close ImageJ images internally while running macros and writing requested
 * output files.</p>
 */
public final class MacroBuilder {

    private MacroBuilder() {
    }

    public static MacroBuilderResult runBatch(MacroBuilderParameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("parameters must not be null");
        }
        List<BatchMacroResult> rows = new BatchMacroRunner().run(
                parameters.inputs(),
                parameters.macro(),
                parameters.outputDirectory(),
                parameters.progress());
        if (parameters.csvFile() != null) {
            try {
                BatchMacroRunner.writeCsv(parameters.csvFile(), rows);
            } catch (IOException ioe) {
                throw new RuntimeException("Could not write batch CSV: "
                        + parameters.csvFile().getAbsolutePath(), ioe);
            }
        }
        return new MacroBuilderResult(parameters.outputDirectory(), parameters.csvFile(), rows);
    }

    public static MacroBuilderResult runBatchFromMacroOptions(String options) throws IOException {
        return runBatch(MacroBuilderParameters.fromMacroOptions(options));
    }

    public static MacroBuilderBatchCountResult runBatchCount(
            MacroBuilderBatchCountParameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("parameters must not be null");
        }
        List<BatchShootoutResult> rows = new BatchShootoutRunner().run(
                parameters.files(),
                parameters.macro(),
                parameters.settings(),
                parameters.primaryChannel(),
                parameters.progress());
        if (parameters.csvFile() != null) {
            try {
                java.io.File parent = parameters.csvFile().getAbsoluteFile().getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("Could not create CSV folder: " + parent.getAbsolutePath());
                }
                java.nio.file.Files.write(
                        parameters.csvFile().toPath(),
                        BatchShootoutRunner.buildCsv(rows).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (IOException ioe) {
                throw new RuntimeException("Could not write batch count CSV: "
                        + parameters.csvFile().getAbsolutePath(), ioe);
            }
        }
        return new MacroBuilderBatchCountResult(parameters.csvFile(), rows);
    }

    public static MacroBuilderBatchCountResult runBatchCountFromMacroOptions(String options)
            throws IOException {
        return runBatchCount(MacroBuilderBatchCountParameters.fromMacroOptions(options));
    }
}
