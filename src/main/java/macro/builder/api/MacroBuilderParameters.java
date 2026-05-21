package macro.builder.api;

import macro.builder.MacroOptionsParser;
import macro.builder.analysis.BatchMacroInput;
import macro.builder.analysis.BatchMacroRunner;
import macro.builder.analysis.BatchMacroScanner;
import macro.builder.analysis.BatchShootoutRunner;
import macro.builder.image.BioFormatsSeriesProvider;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MacroBuilderParameters {

    public static final String DEFAULT_FILENAME_REGEX =
            "(?i).*\\.(tif|tiff|png|jpg|jpeg|gif|bmp|ics|ids)";
    public static final String DEFAULT_CSV_NAME = "Macro_Builder_Batch_Run.csv";

    private final List<BatchMacroInput> inputs;
    private final String macro;
    private final File outputDirectory;
    private final File csvFile;
    private final BatchMacroRunner.Progress progress;

    private MacroBuilderParameters(Builder builder) {
        this.inputs = Collections.unmodifiableList(new ArrayList<BatchMacroInput>(builder.inputs));
        this.macro = requireMacro(builder.macro);
        this.outputDirectory = requireOutputDirectory(builder.outputDirectory);
        this.csvFile = builder.csvFile;
        this.progress = builder.progress;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static MacroBuilderParameters fromMacroOptions(String options) throws IOException {
        return fromMacroOptions(options, null);
    }

    public static MacroBuilderParameters fromMacroOptions(
            String options,
            BatchMacroRunner.Progress progress) throws IOException {
        MacroOptionsParser.Options parsed = MacroOptionsParser.parse(options);
        File macroFile = parsed.requiredFile("macro");
        File input = parsed.requiredFile("input");
        File output = parsed.requiredFile("output");
        String regex = parsed.get("regex", DEFAULT_FILENAME_REGEX);
        boolean recursive = parsed.booleanOption("recursive", true);

        Builder builder = builder()
                .macroFile(macroFile)
                .inputs(inputsFrom(input, parsed, regex, recursive))
                .outputDirectory(output)
                .csvFile(csvFile(output, parsed.get("csv", DEFAULT_CSV_NAME)))
                .progress(progress);
        return builder.build();
    }

    public List<BatchMacroInput> inputs() {
        return inputs;
    }

    public String macro() {
        return macro;
    }

    public File outputDirectory() {
        return outputDirectory;
    }

    public File csvFile() {
        return csvFile;
    }

    public BatchMacroRunner.Progress progress() {
        return progress;
    }

    public String toMacroOptions(File macroFile, File input) {
        return MacroOptionsParser.bracketedOption("macro", macroFile) + " "
                + MacroOptionsParser.bracketedOption("input", input) + " "
                + MacroOptionsParser.bracketedOption("output", outputDirectory);
    }

    private static List<BatchMacroInput> inputsFrom(
            File input,
            MacroOptionsParser.Options options,
            String regex,
            boolean recursive) {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        if (input.isDirectory()) {
            return new BatchMacroScanner().scanFolder(input, regex, recursive);
        }
        if (!input.isFile()) {
            throw new IllegalArgumentException("Input does not exist: " + input.getAbsolutePath());
        }
        if (BatchMacroScanner.isDirectImageFile(input)) {
            return Collections.singletonList(BatchMacroInput.file(input, input.getName()));
        }
        if (BatchShootoutRunner.isBioFormatsContainer(input)) {
            int series = Math.max(1, options.intOption("series", 1));
            List<BatchMacroInput> rows = new BioFormatsSeriesProvider().listSeries(input);
            if (series > rows.size()) {
                throw new IllegalArgumentException("Container series " + series
                        + " does not exist; Bio-Formats found " + rows.size() + " series.");
            }
            return Collections.singletonList(rows.get(series - 1));
        }
        throw new IllegalArgumentException("Unsupported input image type: " + input.getAbsolutePath());
    }

    private static File csvFile(File outputDirectory, String value) {
        if (value == null || value.trim().isEmpty() || "none".equalsIgnoreCase(value.trim())) {
            return null;
        }
        File file = new File(value);
        if (file.isAbsolute()) {
            return file;
        }
        return new File(outputDirectory, value);
    }

    private static String readFile(File file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("macroFile must not be null");
        }
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static String requireMacro(String macro) {
        if (macro == null || macro.trim().isEmpty()) {
            throw new IllegalArgumentException("macro must not be blank");
        }
        return macro;
    }

    private static File requireOutputDirectory(File outputDirectory) {
        if (outputDirectory == null) {
            throw new IllegalArgumentException("outputDirectory must not be null");
        }
        return outputDirectory;
    }

    public static final class Builder {
        private final List<BatchMacroInput> inputs = new ArrayList<BatchMacroInput>();
        private String macro;
        private File outputDirectory;
        private File csvFile;
        private BatchMacroRunner.Progress progress;

        private Builder() {
        }

        public Builder inputs(List<BatchMacroInput> inputs) {
            this.inputs.clear();
            if (inputs != null) {
                for (BatchMacroInput input : inputs) {
                    if (input != null) {
                        this.inputs.add(input);
                    }
                }
            }
            return this;
        }

        public Builder addInput(BatchMacroInput input) {
            if (input != null) {
                this.inputs.add(input);
            }
            return this;
        }

        public Builder macro(String macro) {
            this.macro = macro;
            return this;
        }

        public Builder macroFile(File macroFile) throws IOException {
            this.macro = readFile(macroFile);
            return this;
        }

        public Builder outputDirectory(File outputDirectory) {
            this.outputDirectory = outputDirectory;
            return this;
        }

        public Builder csvFile(File csvFile) {
            this.csvFile = csvFile;
            return this;
        }

        public Builder progress(BatchMacroRunner.Progress progress) {
            this.progress = progress;
            return this;
        }

        public MacroBuilderParameters build() {
            return new MacroBuilderParameters(this);
        }
    }
}
