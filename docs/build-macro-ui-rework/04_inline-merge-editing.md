# Inline Merge Editing

## Why this stage exists

Merge configuration currently lives in a separate `Merge branches` panel, which has the same space and indirection problem as step settings. This stage lets users edit merge cards directly, so the builder can keep command categories visible and reserve the sandbox for the built macro graph.

## Prerequisites

- `docs/build-macro-ui-rework/01_catalog-categories_COMPLETED.md`
- `docs/build-macro-ui-rework/02_sandbox-layout-preview_COMPLETED.md`
- `docs/build-macro-ui-rework/03_inline-step-editing_COMPLETED.md`

## Read first

- `docs/build-macro-ui-rework/00_overview.md`
- `AGENTS.md`
- `src/main/java/macro/builder/ui/sandbox/DagCanvasPanel.java:211-276` for current merge row and merge card rendering.
- `src/main/java/macro/builder/ui/sandbox/CombinerEditorPanel.java:31-80` for current merge operation/input editing.
- `src/main/java/macro/builder/ui/sandbox/SandboxModel.java:151-165` for adding/removing combiners.
- `src/main/java/macro/builder/image/dag/CombinerOp.java` for supported merge operations.
- `src/main/java/macro/builder/image/dag/DagToIjmEmitter.java:115-129` for emitted ImageJ calculator operation names.

## Scope

- Add double-click editing for merge cards.
- Add right-click context menu actions for merge cards: `Edit merge`, `Preview to merge`, and `Delete`.
- Move merge operation/input editing into a dialog or popup launched from the merge card.
- Reuse the same validation rule as today: a merge must have at least two inputs.
- Remove the separate `CombinerEditorPanel` from the main builder layout after inline merge editing works.

## Out of scope

- Do not add branch Ctrl/Shift multi-selection in this stage; stage 05 owns it.
- Do not change `CombinerOp` semantics.
- Do not change DAG serialization or emitted macro format beyond preserving existing behavior.
- Do not add new merge operation types.

## Files touched

| path | action | reason |
| --- | --- | --- |
| `src/main/java/macro/builder/ui/sandbox/DagCanvasPanel.java` | MODIFY | Add merge card double-click and right-click actions. |
| `src/main/java/macro/builder/ui/sandbox/SandboxDialog.java` | MODIFY | Handle merge edit/preview callbacks and remove merge editor from layout. |
| `src/main/java/macro/builder/ui/sandbox/CombinerEditorPanel.java` | MODIFY / DELETE | Extract reusable merge editor content or delete after replacement. |
| `src/main/java/macro/builder/ui/sandbox/MergeEditorDialog.java` | NEW | Modal editor for one combiner's operation and inputs. |
| `src/test/java/macro/builder/ui/sandbox/MergeEditorDialogTest.java` | NEW | Test merge validation logic if it can run headless. |

## Implementation sketch

Add callbacks alongside stage 03 node actions:

```java
public interface CombinerActionHandler {
    void editCombiner(SandboxModel.CombinerNode combiner);
    void previewToCombiner(SandboxModel.CombinerNode combiner);
}
```

Right-click menu:

```java
private JPopupMenu buildCombinerMenu(CombinerNode combiner) {
    JPopupMenu menu = new JPopupMenu();
    menu.add(menuItem("Edit merge", () -> combinerActionHandler.editCombiner(combiner)));
    menu.add(menuItem("Preview to merge", () -> combinerActionHandler.previewToCombiner(combiner)));
    menu.addSeparator();
    menu.add(menuItem("Delete", () -> {
        model.removeCombiner(combiner);
        changedAndSelected();
    }));
    return menu;
}
```

Create `MergeEditorDialog`:

```java
final class MergeEditorDialog {
    static boolean show(Component parent, SandboxModel model, SandboxModel.CombinerNode combiner) {
        JComboBox<CombinerOp> op = new JComboBox<CombinerOp>(CombinerOp.values());
        // Build one checkbox per model line.
        // Disable OK until at least two inputs are selected.
        // On OK, update combiner.op and combiner.inputs.
    }
}
```

In `SandboxDialog`:

```java
private void editCombiner(SandboxModel.CombinerNode combiner) {
    if (MergeEditorDialog.show(this, model, combiner)) {
        canvas.rebuild();
        refreshEditors();
    }
}

private void previewCombiner(SandboxModel.CombinerNode combiner) {
    model.selected = combiner;
    preview(model.toPartialDag());
}
```

Once inline merge editing works, remove `CombinerEditorPanel` from `buildMain()` and from constructor fields. If deleting the file creates too much churn, leave it unused only if there is a clear TODO in stage 06 to remove it.

## Exit gate

1. `.\mvnw.cmd -q test` passes.
2. `.\mvnw.cmd -q -DskipTests compile` passes with no unused imports.
3. Manual UI check: double-clicking a merge card opens operation/input editing.
4. Manual UI check: right-clicking a merge card shows `Edit merge`, `Preview to merge`, and `Delete`.
5. Manual behavior check: the UI prevents saving a merge with fewer than two inputs.
6. Manual UI check: the separate `Merge branches` editor box is no longer visible.

## Known risks

- Existing `CombinerEditorPanel` silently reselects a checkbox if fewer than two inputs would remain. A dialog should make this clearer by disabling OK or showing a short validation message.
- Previewing to a merge depends on `SandboxModel.toPartialDag()` handling selected combiners. Keep that behavior intact.
- Do not delete shared helper logic if stage 05 will need it for merge-selected behavior.
