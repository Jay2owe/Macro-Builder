package macro.builder.api;

import macro.builder.analysis.BatchMacroResult;
import macro.builder.analysis.BatchMacroRunner;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MacroBuilderResult {

    private final File outputDirectory;
    private final File csvFile;
    private final List<BatchMacroResult> rows;

    MacroBuilderResult(File outputDirectory, File csvFile, List<BatchMacroResult> rows) {
        this.outputDirectory = outputDirectory;
        this.csvFile = csvFile;
        this.rows = rows == null
                ? Collections.<BatchMacroResult>emptyList()
                : Collections.unmodifiableList(new ArrayList<BatchMacroResult>(rows));
    }

    public File outputDirectory() {
        return outputDirectory;
    }

    public File csvFile() {
        return csvFile;
    }

    public List<BatchMacroResult> rows() {
        return rows;
    }

    public int successCount() {
        int count = 0;
        for (BatchMacroResult row : rows) {
            if (row != null && row.isSuccess()) {
                count++;
            }
        }
        return count;
    }

    public int failureCount() {
        return rows.size() - successCount();
    }

    public boolean successful() {
        return failureCount() == 0;
    }

    public String csv() {
        return BatchMacroRunner.buildCsv(rows);
    }
}
