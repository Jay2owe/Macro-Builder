# 02 - Saved Macro History

## Why this stage exists

The new center panel needs a combo box that can reload macros saved through the plugin. This makes the loaded macro a real selectable object, so the right-side actions in stage 03 can operate on older macros as well as the current session macro.

## Prerequisites

- `01_launcher-layout-shell.md` must be completed and renamed with `_COMPLETED`.

## Read first

- `docs/main-ui-rework/00_overview.md`
- `AGENTS.md`
- `src/main/java/macro/builder/Macro_Builder.java:65-90` for current state fields.
- `src/main/java/macro/builder/Macro_Builder.java:679-700` for `saveCurrentMacro`.
- `src/main/java/macro/builder/Macro_Builder.java:747-788` for current macro state loading and writing.
- `src/main/java/macro/builder/Macro_Builder.java:895-905` for file extension and DAG sidecar helpers.

## Scope

- Add a saved-macro combo box above the macro source and macro text area.
- Persist saved macro history under `~/.macro-builder`.
- Register macros when `Save macro...` succeeds.
- Load the selected `.ijm` file into `lastMacro`, `macroArea`, and local state.
- Load the matching `.dag.json` sidecar into `lastDag` when available.
- Handle missing saved files clearly and remove stale entries from history.

## Out of scope

- Reworking the save dialog itself beyond adding history registration.
- New right-side action behavior. Stage 03 owns this.
- Batch macro history. Only saved filter macros are in scope here.
- Builder UI changes.

## Files touched

| path | action | reason |
| --- | --- | --- |
| `src/main/java/macro/builder/Macro_Builder.java` | MODIFY | Add combo box UI, history model, persistence, and macro loading. |

## Implementation sketch

Add fields similar to:

```java
private final JComboBox<MacroHistoryEntry> savedMacroCombo =
        new JComboBox<MacroHistoryEntry>();
private final List<MacroHistoryEntry> savedMacroHistory =
        new ArrayList<MacroHistoryEntry>();
private final File macroHistoryFile = new File(stateDir, "saved-macros.tsv");
private boolean updatingSavedMacroCombo;
```

Add a small value type:

```java
private static final class MacroHistoryEntry {
    final File macroFile;
    final File dagFile;
    final String label;

    MacroHistoryEntry(File macroFile, File dagFile) {
        this.macroFile = macroFile;
        this.dagFile = dagFile;
        this.label = macroFile == null ? "" : macroFile.getName();
    }

    @Override public String toString() {
        return label;
    }
}
```

Persistence format can be one canonical macro path per line. If a display label is needed later, use tab-separated fields:

```text
C:\path\Macro_Builder_Filter.ijm
```

Load selected macro:

```java
private void loadSelectedSavedMacro() {
    if (updatingSavedMacroCombo) return;
    MacroHistoryEntry entry = (MacroHistoryEntry) savedMacroCombo.getSelectedItem();
    if (entry == null || entry.macroFile == null) return;
    if (!entry.macroFile.exists()) {
        IJ.showMessage("Macro Builder", "Saved macro could not be found:\n" + entry.macroFile.getAbsolutePath());
        removeMacroHistoryEntry(entry);
        return;
    }
    lastMacro = new String(Files.readAllBytes(entry.macroFile.toPath()), StandardCharsets.UTF_8);
    lastDag = entry.dagFile != null && entry.dagFile.exists()
            ? DagIRSerializer.fromJson(new String(Files.readAllBytes(entry.dagFile.toPath()), StandardCharsets.UTF_8))
            : null;
    lastMacroSource = "saved macro: " + entry.macroFile.getName();
    macroArea.setText(lastMacro);
    macroArea.setCaretPosition(0);
    writeState();
    refreshSourceLabel();
}
```

After `saveCurrentMacro` writes the `.ijm` and optional `.dag.json`, call:

```java
rememberSavedMacro(file);
```

Refresh the combo after `loadState()` so the UI starts populated.

## Exit gate

1. `./mvnw.cmd -q test` passes, or any failure is clearly unrelated and documented.
2. Saving a macro adds it to the combo box.
3. Restarting Macro Builder keeps the saved macro entry.
4. Selecting a saved macro loads its text into the macro area and updates `Macro source`.
5. Selecting a missing macro shows a clear message and removes the stale entry.
6. A saved visual-builder macro reloads its `.dag.json` sidecar when that sidecar exists.

## Known risks

- Combo box listeners can fire during model refresh. Use an `updatingSavedMacroCombo` guard.
- Paths can contain spaces and apostrophes. Store raw path lines and avoid shell-style quoting inside the history file.
- Loading a corrupt `.dag.json` should not prevent loading the `.ijm`; log the DAG problem and continue with `lastDag = null`.
