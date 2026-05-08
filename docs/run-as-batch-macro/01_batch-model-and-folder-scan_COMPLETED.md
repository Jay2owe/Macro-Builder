# Batch model and folder scan

## Why this stage exists

The batch UI and runner need a shared, testable way to describe "what to run" before any image processing starts. This stage adds small data models plus the folder regular-expression scanner, so later UI and execution work can reuse one stable contract.

## Prerequisites

- none

## Read first

- `docs/run-as-batch-macro/00_overview.md`
- `AGENTS.md`
- `src/main/java/macro/builder/analysis/BatchShootoutRunner.java:17-120` for existing image extension handling, deduplication, and CSV style.
- `src/main/java/macro/builder/analysis/BatchShootoutRunner.java:204-230` for current sorted folder expansion and canonical path logic.
- `../CPC/src/main/java/cpc/CPCBatch.java:50-102` for the CPC batch dialog's regex preview behavior.
- `../CPC/src/main/java/cpc/CPCBatch.java:246-322` for CPC's `Pattern.compile(...)`, `Matcher.matches()`, sorted file scanning, and recursive folder walk.
- `src/test/java/macro/builder/analysis/BatchShootoutRunnerTest.java:90-123` for current batch scanning and window-cleanup test style.

## Scope

- Add a batch input model for ordinary files and future container series.
- Add a batch result model for later execution results and CSV rows.
- Add a folder scanner that finds ordinary image files by full-filename regular expression.
- Support non-recursive and recursive scans.
- Sort results deterministically.
- Deduplicate selected files by canonical path.
- Add unit tests for regex matching, recursive scanning, invalid regex, sorting, and deduplication.

## Out of scope

- Running macros on the scanned files. Stage 02 owns execution.
- Any Swing dialog or checkbox table. Stage 03 owns folder-mode UI.
- Bio-Formats container series listing. Stage 04 owns container support.
- Count settings, thresholding, or object counting. Those belong to the existing count workflow.

## Files touched

| path | action | reason |
|---|---|---|
| `src/main/java/macro/builder/analysis/BatchMacroInput.java` | NEW | Represents one batch item, either an ordinary file now or a container series later. |
| `src/main/java/macro/builder/analysis/BatchMacroResult.java` | NEW | Represents one execution result row for later CSV writing. |
| `src/main/java/macro/builder/analysis/BatchMacroScanner.java` | NEW | Scans folders with a full-filename regex and returns `BatchMacroInput` rows. |
| `src/test/java/macro/builder/analysis/BatchMacroScannerTest.java` | NEW | Verifies scanner behavior without needing Fiji windows. |

## Implementation sketch

Keep the models small and immutable. Use Java 8-compatible code because this project follows ImageJ/Fiji-era conventions.

```java
package macro.builder.analysis;

import java.io.File;

public final class BatchMacroInput {
    public enum Kind { FILE, CONTAINER_SERIES }

    public final Kind kind;
    public final File file;
    public final String relativePath;
    public final int seriesIndex;
    public final String seriesName;
    public final int width;
    public final int height;
    public final int channels;
    public final int slices;
    public final int frames;

    public static BatchMacroInput file(File file, String relativePath) { ... }

    public static BatchMacroInput containerSeries(
            File container,
            int seriesIndex,
            String seriesName,
            int width,
            int height,
            int channels,
            int slices,
            int frames) { ... }
}
```

```java
package macro.builder.analysis;

import java.io.File;

public final class BatchMacroResult {
    public enum Status { SUCCESS, FAILED, CANCELLED }

    public final BatchMacroInput input;
    public final File outputFile;
    public final Status status;
    public final String error;
}
```

Scanner shape:

```java
public final class BatchMacroScanner {
    public static final String[] DIRECT_IMAGE_EXTENSIONS = {
            "tif", "tiff", "png", "jpg", "jpeg", "gif", "bmp", "ics", "ids"
    };

    public List<BatchMacroInput> scanFolder(
            File rootFolder,
            String filenameRegex,
            boolean recursive) {
        Pattern pattern = Pattern.compile(filenameRegex);
        // Match with pattern.matcher(file.getName()).matches()
        // Include direct image files only.
        // Return sorted, deduplicated BatchMacroInput.file(...) rows.
    }

    public static boolean isDirectImageFile(File file) { ... }
}
```

Regex behavior must follow CPC's rule: the regex must match the entire filename. Use:

```java
Matcher matcher = pattern.matcher(file.getName());
if (!matcher.matches()) continue;
```

Suggested defaults for later UI:

```text
.*\.(tif|tiff|png|jpg|jpeg|gif|bmp|ics|ids)
```

The default should be case-insensitive either by compiling with `Pattern.CASE_INSENSITIVE` in the UI stage or by using this user-visible regex:

```text
(?i).*\.(tif|tiff|png|jpg|jpeg|gif|bmp|ics|ids)
```

Test examples:

```java
@Test
public void regexMustMatchWholeFilename() throws Exception { ... }

@Test
public void recursiveScanPreservesRelativePaths() throws Exception { ... }

@Test
public void invalidRegexIsReportedToCaller() throws Exception { ... }
```

## Exit gate

1. `.\mvnw.cmd test "-Denforcer.skip=true"` passes.
2. `BatchMacroScannerTest` proves full-filename regex matching, not substring matching.
3. `BatchMacroScannerTest` proves recursive scans include subfolder files and non-recursive scans do not.
4. `BatchMacroScannerTest` proves unsupported files such as `.txt` are skipped.
5. `BatchMacroScannerTest` proves duplicate file selections collapse to one canonical input.

## Known risks

- Regex syntax errors should not crash the UI later. Let `PatternSyntaxException` propagate from the scanner or wrap it in a clear `IllegalArgumentException`; do not silently return zero rows.
- Do not reuse `BatchShootoutResult` for macro execution. It carries count-specific fields and would blur the two workflows.
- Do not centralize image extension constants unless it is clearly cleaner. A broad refactor is not needed for this stage.
