package macro.builder.api;

import macro.builder.analysis.BatchMacroExporter;
import macro.builder.analysis.ShootoutSettings;

import java.io.File;
import java.io.IOException;

/** Public facade for exporting reusable Macro Builder batch-count macros. */
public final class MacroBuilderBatchExport {

    private MacroBuilderBatchExport() {
    }

    public static BatchMacroExporter.ExportResult exportWrapperMacro(
            File wrapperFile,
            String macro,
            ShootoutSettings settings) throws IOException {
        return new BatchMacroExporter().export(wrapperFile, macro, settings);
    }

    public static BatchMacroExporter.ExportResult exportWrapperMacro(
            File wrapperFile,
            String macro,
            ShootoutSettings settings,
            int primaryChannel) throws IOException {
        return new BatchMacroExporter().export(wrapperFile, macro, settings, primaryChannel);
    }

    public static String buildWrapperMacro(
            String filterMacro,
            ShootoutSettings settings,
            int primaryChannel) {
        return BatchMacroExporter.buildWrapperMacro(filterMacro, settings, primaryChannel);
    }

    public static String buildWrapperMacro(File settingsFile) {
        return BatchMacroExporter.buildWrapperMacro(settingsFile);
    }

    public static BatchMacroExporter.ExportedSettings readSettings(File settingsFile)
            throws IOException {
        return BatchMacroExporter.readSettings(settingsFile);
    }

    public static String settingsToJson(BatchMacroExporter.ExportedSettings settings) {
        return BatchMacroExporter.toJson(settings);
    }
}
