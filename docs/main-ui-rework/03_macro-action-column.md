# 03 - Macro Action Column

## Why this stage exists

The right side of the launcher should be dedicated to actions for the currently loaded macro. This stage makes those actions visible and consistent whether the macro came from the current session or from the saved-macro combo box.

## Prerequisites

- `01_launcher-layout-shell.md` must be completed and renamed with `_COMPLETED`.
- `02_saved-macro-history.md` must be completed and renamed with `_COMPLETED`.

## Read first

- `docs/main-ui-rework/00_overview.md`
- `AGENTS.md`
- `src/main/java/macro/builder/Macro_Builder.java:104-190` for the current rebuilt layout.
- `src/main/java/macro/builder/Macro_Builder.java:395-430` for builder and recorder save-back behavior.
- `src/main/java/macro/builder/Macro_Builder.java:490-520` for run, count, and preview macro behavior.
- `src/main/java/macro/builder/Macro_Builder.java:679-729` for save and batch macro export.

## Scope

- Add a fixed-width right column for last-macro actions.
- Add plain text buttons with the requested labels:
  - `Run as batch...`
  - `Save as batch macro...`
  - `Edit Macro...`
  - `Create Macro Variations...`
  - `Test Counts...`
- Wire existing implemented actions.
- Add clear placeholders for actions that do not have a direct implementation yet.
- Disable macro actions when no macro is loaded.

## Out of scope

- Implementing real macro variation generation. This button can be a placeholder here.
- Building a full direct batch runner. Placeholder is acceptable here.
- Changing the `ThresholdShootoutDialog` batch workflow.
- Builder UI redesign.

## Files touched

| path | action | reason |
| --- | --- | --- |
| `src/main/java/macro/builder/Macro_Builder.java` | MODIFY | Add right-column buttons, enabled-state refresh, and action handlers. |

## Implementation sketch

Add fields so enabled state can be refreshed from build, record, load, and save flows:

```java
private final List<JButton> macroActionButtons = new ArrayList<JButton>();
```

Build the right column with uniform buttons:

```java
private JPanel buildMacroActionsPanel() {
    JPanel panel = new JPanel(new GridLayout(0, 1, 0, 8));
    panel.setPreferredSize(new Dimension(RIGHT_COLUMN_WIDTH, 1));

    JButton runBatch = createMacroActionButton("Run as batch...");
    JButton saveBatch = createMacroActionButton("Save as batch macro...");
    JButton edit = createMacroActionButton("Edit Macro...");
    JButton variations = createMacroActionButton("Create Macro Variations...");
    JButton counts = createMacroActionButton("Test Counts...");

    runBatch.addActionListener(e -> runAsBatchPlaceholder());
    saveBatch.addActionListener(e -> saveBatchMacro());
    edit.addActionListener(e -> editCurrentMacro());
    variations.addActionListener(e -> createMacroVariationsPlaceholder());
    counts.addActionListener(e -> openCountTester());

    panel.add(runBatch);
    panel.add(saveBatch);
    panel.add(edit);
    panel.add(variations);
    panel.add(counts);
    return panel;
}
```

Use existing builder logic for `Edit Macro...`:

```java
private void editCurrentMacro() {
    if (!ensureMacroLoaded()) return;
    openSandbox();
}
```

Add a helper used by every macro action:

```java
private boolean ensureMacroLoaded() {
    if (lastMacro == null || lastMacro.trim().isEmpty()) {
        IJ.showMessage("Macro Builder", "No macro has been built, recorded, or loaded yet.");
        return false;
    }
    return true;
}
```

Placeholder methods:

```java
private void runAsBatchPlaceholder() {
    if (!ensureMacroLoaded()) return;
    IJ.showMessage("Macro Builder", "Run as batch is not implemented yet. Use Test Counts... > Run batch... for now.");
}

private void createMacroVariationsPlaceholder() {
    if (!ensureMacroLoaded()) return;
    IJ.showMessage("Macro Builder", "Create Macro Variations is not implemented yet.");
}
```

Refresh button enabled state after `loadState`, `openSandbox`, `openRecorder`, and saved macro selection:

```java
private void refreshMacroActionControls() {
    boolean hasMacro = lastMacro != null && !lastMacro.trim().isEmpty();
    for (JButton button : macroActionButtons) button.setEnabled(hasMacro);
}
```

## Exit gate

1. `./mvnw.cmd -q test` passes, or any failure is clearly unrelated and documented.
2. The right column shows all five requested text buttons in the requested order.
3. Buttons are disabled when no macro is loaded.
4. `Save as batch macro...` uses the existing batch macro export flow.
5. `Edit Macro...` opens the existing visual builder seeded with the current macro.
6. `Test Counts...` opens the existing count tester.
7. Placeholder buttons show clear messages and do not crash.

## Known risks

- `Edit Macro...` depends on a selected source image. The existing image warning is acceptable.
- If `openSandbox` prefers old state DAG files over the current loaded macro, adjust the load order so the current macro and sidecar are respected.
