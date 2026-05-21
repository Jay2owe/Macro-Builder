package macro.builder.api;

import macro.builder.analysis.BatchShootoutResult;
import macro.builder.analysis.BatchShootoutRunner;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MacroBuilderBatchCountResult {

    private final File csvFile;
    private final List<BatchShootoutResult> rows;

    MacroBuilderBatchCountResult(File csvFile, List<BatchShootoutResult> rows) {
        this.csvFile = csvFile;
        this.rows = rows == null
                ? Collections.<BatchShootoutResult>emptyList()
                : Collections.unmodifiableList(new ArrayList<BatchShootoutResult>(rows));
    }

    public File csvFile() {
        return csvFile;
    }

    public List<BatchShootoutResult> rows() {
        return rows;
    }

    public int successCount() {
        int count = 0;
        for (BatchShootoutResult row : rows) {
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
        return BatchShootoutRunner.buildCsv(rows);
    }
}
