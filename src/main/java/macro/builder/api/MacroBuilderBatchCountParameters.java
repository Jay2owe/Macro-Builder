package macro.builder.api;

import macro.builder.MacroOptionsParser;
import macro.builder.analysis.BatchMacroExporter;
import macro.builder.analysis.BatchShootoutRunner;
import macro.builder.analysis.ShootoutSettings;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MacroBuilderBatchCountParameters {

    private final List<File> files;
    private final String macro;
    private final ShootoutSettings settings;
    private final int primaryChannel;
    private final File csvFile;
    private final BatchShootoutRunner.Progress progress;

    private MacroBuilderBatchCountParameters(Builder builder) {
        this.files = Collections.unmodifiableList(new ArrayList<File>(builder.files));
        this.macro = builder.macro == null ? "" : builder.macro;
        if (builder.settings == null) {
            throw new IllegalArgumentException("settings must not be null");
        }
        this.settings = builder.settings;
        this.primaryChannel = Math.max(1, builder.primaryChannel);
        this.csvFile = builder.csvFile;
        this.progress = builder.progress;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static MacroBuilderBatchCountParameters fromMacroOptions(String options) throws IOException {
        return fromMacroOptions(options, null);
    }

    public static MacroBuilderBatchCountParameters fromMacroOptions(
            String options,
            BatchShootoutRunner.Progress progress) throws IOException {
        MacroOptionsParser.Options parsed = MacroOptionsParser.parse(options);
        File settingsFile = parsed.requiredFile("settings");
        File input = parsed.requiredFile("input");
        File output = parsed.requiredFile("output");
        if (!output.exists() && !output.mkdirs()) {
            throw new IllegalArgumentException("Could not create output folder: " + output.getAbsolutePath());
        }
        if (!output.isDirectory()) {
            throw new IllegalArgumentException("Output is not a folder: " + output.getAbsolutePath());
        }

        BatchMacroExporter.ExportedSettings exportedSettings =
                BatchMacroExporter.readSettings(settingsFile);
        File macroFile = exportedSettings.macroFile();
        String macro = new String(Files.readAllBytes(macroFile.toPath()), StandardCharsets.UTF_8);
        return builder()
                .addFile(input)
                .macro(macro)
                .settings(exportedSettings.settings)
                .primaryChannel(exportedSettings.primaryChannel)
                .csvFile(new File(output, exportedSettings.resultsFile))
                .progress(progress)
                .build();
    }

    public List<File> files() {
        return files;
    }

    public String macro() {
        return macro;
    }

    public ShootoutSettings settings() {
        return settings;
    }

    public int primaryChannel() {
        return primaryChannel;
    }

    public File csvFile() {
        return csvFile;
    }

    public BatchShootoutRunner.Progress progress() {
        return progress;
    }

    public static final class Builder {
        private final List<File> files = new ArrayList<File>();
        private String macro;
        private ShootoutSettings settings;
        private int primaryChannel = 1;
        private File csvFile;
        private BatchShootoutRunner.Progress progress;

        private Builder() {
        }

        public Builder files(List<File> files) {
            this.files.clear();
            if (files != null) {
                for (File file : files) {
                    if (file != null) {
                        this.files.add(file);
                    }
                }
            }
            return this;
        }

        public Builder addFile(File file) {
            if (file != null) {
                this.files.add(file);
            }
            return this;
        }

        public Builder macro(String macro) {
            this.macro = macro;
            return this;
        }

        public Builder settings(ShootoutSettings settings) {
            this.settings = settings;
            return this;
        }

        public Builder primaryChannel(int primaryChannel) {
            this.primaryChannel = primaryChannel;
            return this;
        }

        public Builder csvFile(File csvFile) {
            this.csvFile = csvFile;
            return this;
        }

        public Builder progress(BatchShootoutRunner.Progress progress) {
            this.progress = progress;
            return this;
        }

        public MacroBuilderBatchCountParameters build() {
            return new MacroBuilderBatchCountParameters(this);
        }
    }
}
