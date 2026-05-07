# Branch Multi-Select and Merge Selected

## Why this stage exists

Users should be able to select several branches directly and click merge, instead of creating a merge and then manually choosing inputs in a separate panel. The DAG already supports combiners with more than two inputs, so this stage adds the missing branch selection workflow.

## Prerequisites

- `docs/build-macro-ui-rework/01_catalog-categories_COMPLETED.md`
- `docs/build-macro-ui-rework/02_sandbox-layout-preview_COMPLETED.md`
- `docs/build-macro-ui-rework/03_inline-step-editing_COMPLETED.md`
- `docs/build-macro-ui-rework/04_inline-merge-editing_COMPLETED.md`

## Read first

- `docs/build-macro-ui-rework/00_overview.md`
- `AGENTS.md`
- `src/main/java/macro/builder/ui/sandbox/SandboxModel.java:20-24` for current single selection state.
- `src/main/java/macro/builder/ui/sandbox/SandboxModel.java:50-70` for DAG output behavior.
- `src/main/java/macro/builder/ui/sandbox/SandboxModel.java:151-158` for current default two-branch merge creation.
- `src/main/java/macro/builder/ui/sandbox/DagCanvasPanel.java:85-136` for branch panel rendering and click selection.
- `src/main/java/macro/builder/ui/sandbox/DagCanvasPanel.java:211-241` for the `+ Merge branches` button.
- `src/test/java/macro/builder/image/dag/DagIRRoundTripTest.java:13-49` for existing DAG combiner round-trip tests.

## Scope

- Add branch multi-selection state to `SandboxModel`.
- Plain click selects one branch.
- Ctrl-click toggles a branch in the selection.
- Shift-click selects a contiguous branch range from the anchor branch.
- Update branch border styling so multi-selected branches are visually clear.
- Change the merge button behavior to merge selected branches when two or more branches are selected.
- Preserve the current fallback behavior: if fewer than two branches are selected, create the same default merge from the first two branches.
- Create combiners with selected branch IDs in visual left-to-right order.

## Out of scope

- Do not add new merge operations; stage 04 keeps operation editing available after merge creation.
- Do not change the saved DAG format.
- Do not add drag selection or marquee selection.
- Do not change node selection or step editing behavior except where needed to clear branch multi-selection.

## Files touched

| path | action | reason |
| --- | --- | --- |
| `src/main/java/macro/builder/ui/sandbox/SandboxModel.java` | MODIFY | Store and manipulate branch multi-selection and create selected-branch combiners. |
| `src/main/java/macro/builder/ui/sandbox/DagCanvasPanel.java` | MODIFY | Handle Ctrl/Shift click selection and merge selected branches. |
| `src/test/java/macro/builder/ui/sandbox/SandboxModelTest.java` | NEW | Test branch selection and selected-branch combiner inputs. |
| `src/test/java/macro/builder/image/dag/DagIRRoundTripTest.java` | MODIFY | Add coverage for a combiner with more than two inputs if missing. |

## Implementation sketch

Add selection state:

```java
final LinkedHashSet<Line> selectedLines = new LinkedHashSet<Line>();
Line selectionAnchorLine;
```

Keep `Object selected` for selected node/combiner/primary line because preview and existing editing logic depend on it.

Add methods:

```java
void selectLine(Line line, boolean ctrl, boolean shift) {
    if (line == null) return;
    if (shift && selectionAnchorLine != null) {
        selectedLines.clear();
        int a = lines.indexOf(selectionAnchorLine);
        int b = lines.indexOf(line);
        if (a >= 0 && b >= 0) {
            int start = Math.min(a, b);
            int end = Math.max(a, b);
            for (int i = start; i <= end; i++) selectedLines.add(lines.get(i));
        }
    } else if (ctrl) {
        if (!selectedLines.remove(line)) selectedLines.add(line);
        selectionAnchorLine = line;
    } else {
        selectedLines.clear();
        selectedLines.add(line);
        selectionAnchorLine = line;
    }
    selected = line;
}

List<Line> selectedLinesInVisualOrder() {
    List<Line> out = new ArrayList<Line>();
    for (Line line : lines) {
        if (selectedLines.contains(line)) out.add(line);
    }
    return out;
}
```

Update line click handling:

```java
@Override public void mouseClicked(MouseEvent e) {
    model.selectLine(line, e.isControlDown(), e.isShiftDown());
    selected();
}
```

Add selected merge creation:

```java
void addCombinerForSelectedLines() {
    List<Line> selected = selectedLinesInVisualOrder();
    if (selected.size() < 2) {
        addCombiner();
        return;
    }
    List<String> inputs = new ArrayList<String>();
    for (Line line : selected) inputs.add(line.id);
    CombinerNode combiner = new CombinerNode("combiner_" + nextCombiner++, CombinerOp.AND, inputs);
    combiners.add(combiner);
    this.selected = combiner;
    selectedLines.clear();
}
```

Change the button label to reflect the selected state:

```java
JButton addCombiner = new JButton(model.selectedLinesInVisualOrder().size() >= 2
        ? "+ Merge selected branches"
        : "+ Merge branches");
```

## Exit gate

1. `.\mvnw.cmd -q test` passes.
2. `SandboxModelTest` proves Ctrl toggles branch selection.
3. `SandboxModelTest` proves Shift selects a branch range.
4. `SandboxModelTest` proves merge-selected creates combiner inputs in visual order.
5. Manual UI check: selected branches have a clear border or background.
6. Manual UI check: selecting branches 1, 3, and 4 then clicking merge creates one merge card using those branches.

## Known risks

- Clicking a node inside a branch should not unexpectedly toggle branch selection. Keep branch selection on the branch panel background/header, not on step cards.
- Deleting a branch must remove it from `selectedLines` and from any merge inputs, matching current combiner cleanup.
- Shift selection depends on a stable anchor. Update the anchor only on plain click and Ctrl-click, not when a Shift range is applied.
