# Inline Step Editing

## Why this stage exists

The current `Step settings` panel consumes space and forces users to select a step in the sandbox, then edit it somewhere else. This stage moves step parameter editing onto the step cards through double-click and right-click actions, making it possible to remove the separate setup box.

## Prerequisites

- `docs/build-macro-ui-rework/01_catalog-categories_COMPLETED.md`
- `docs/build-macro-ui-rework/02_sandbox-layout-preview_COMPLETED.md`

## Read first

- `docs/build-macro-ui-rework/00_overview.md`
- `AGENTS.md`
- `src/main/java/macro/builder/ui/sandbox/DagCanvasPanel.java:138-198` for current step card rendering and mouse handling.
- `src/main/java/macro/builder/ui/sandbox/NodeEditorPanel.java:31-75` for current parameter field rendering and live update behavior.
- `src/main/java/macro/builder/ui/sandbox/NodeEditorPanel.java:110-143` for current argument parsing and rendering.
- `src/main/java/macro/builder/ui/sandbox/SandboxDialog.java:79-120` for `NodeEditorPanel` construction.
- `src/main/java/macro/builder/ui/sandbox/SandboxDialog.java:414-418` for current editor refresh.
- `src/main/java/macro/builder/ui/sandbox/RecorderParameterProbe.java` for option tokenization.

## Scope

- Add double-click editing for step cards in `DagCanvasPanel`.
- Add right-click context menu actions for step cards: `Edit parameters`, `Preview to here`, and `Delete`.
- Reuse or extract `NodeEditorPanel` parameter parsing/rendering so the new dialog edits the same `node.args` string format.
- Add a raw options field for commands with no clean editable `key=value` parameters, especially plugin/legacy commands.
- Remove the separate `Step settings` panel from the main `SandboxDialog` layout after inline editing works.
- Keep single-step selection behavior for previewing up to the selected step.

## Out of scope

- Do not remove the merge editor panel yet; stage 04 owns inline merge editing.
- Do not add branch multi-selection; stage 05 owns it.
- Do not change macro argument syntax or DAG serialization.
- Do not change how legacy Fiji command parameters are captured when adding a command.

## Files touched

| path | action | reason |
| --- | --- | --- |
| `src/main/java/macro/builder/ui/sandbox/DagCanvasPanel.java` | MODIFY | Add double-click and right-click card actions. |
| `src/main/java/macro/builder/ui/sandbox/SandboxDialog.java` | MODIFY | Handle edit/preview callbacks and remove step settings from layout. |
| `src/main/java/macro/builder/ui/sandbox/NodeEditorPanel.java` | MODIFY | Extract reusable argument editor logic or convert it into dialog content. |
| `src/main/java/macro/builder/ui/sandbox/StepEditorDialog.java` | NEW | Modal editor for one step's parameters and raw option string. |
| `src/test/java/macro/builder/ui/sandbox/StepEditorDialogTest.java` | NEW | Test argument parse/render behavior if it can run headless. |

## Implementation sketch

Add callbacks to `DagCanvasPanel`:

```java
public interface NodeActionHandler {
    void editNode(SandboxModel.Line line, SandboxModel.Node node);
    void previewToNode(SandboxModel.Line line, SandboxModel.Node node);
}
```

Wire step card mouse behavior:

```java
@Override public void mouseClicked(MouseEvent e) {
    model.selected = node;
    selected();
    if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
        nodeActionHandler.editNode(line, node);
    }
}

private JPopupMenu buildNodeMenu(Line line, Node node) {
    JPopupMenu menu = new JPopupMenu();
    menu.add(menuItem("Edit parameters", () -> nodeActionHandler.editNode(line, node)));
    menu.add(menuItem("Preview to here", () -> nodeActionHandler.previewToNode(line, node)));
    menu.addSeparator();
    menu.add(menuItem("Delete", () -> {
        model.removeNode(line, node);
        changedAndSelected();
    }));
    return menu;
}
```

Create `StepEditorDialog` with an explicit return value:

```java
final class StepEditorDialog {
    static boolean show(Component parent, SandboxModel.Node node) {
        // Build fields from ArgsEditorModel.parse(node.args).
        // On OK, node.args = ArgsEditorModel.render(tokens) or raw options text.
        // Return true when node was changed.
    }
}
```

If the current `NodeEditorPanel` parse/render methods are private, move them into a package-private helper:

```java
final class ArgsEditorModel {
    static List<Token> parse(String args) { ... }
    static String render(List<Token> tokens) { ... }
}
```

In `SandboxDialog`, after a successful edit:

```java
if (StepEditorDialog.show(this, node)) {
    canvas.rebuild();
    refreshEditors();
}
```

For `Preview to here`, set `model.selected = node` and call `preview(model.toPartialDag())`.

## Exit gate

1. `.\mvnw.cmd -q test` passes.
2. Manual UI check: double-clicking a step opens parameter editing.
3. Manual UI check: right-clicking a step shows `Edit parameters`, `Preview to here`, and `Delete`.
4. Manual behavior check: editing `sigma=2 stack` to `sigma=4 stack` updates the generated macro after save.
5. Manual UI check: the separate `Step settings` box is no longer visible in the builder layout.

## Known risks

- Current drag/reorder behavior also uses mouse press/release on step cards. Make sure double-click and popup handling do not break reordering.
- Boolean flags such as `stack` and `white` are not `key=value` parameters. Preserve them during parse/render.
- Plugin options can contain bracketed values with spaces. Keep using `RecorderParameterProbe.tokenizeOptions` instead of naive string splitting.
