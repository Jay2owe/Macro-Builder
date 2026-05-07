# Categorized Step Catalog

## Why this stage exists

The builder currently shows one flat `Available steps` list, so filters, 3D operations, binary operations, image type conversion, and plugin commands all compete for the same space. This stage creates the category structure and per-row add buttons that the later layout stages depend on.

## Prerequisites

None.

## Read first

- `docs/build-macro-ui-rework/00_overview.md`
- `AGENTS.md`
- `src/main/java/macro/builder/ui/sandbox/FilterCatalog.java:45-110` for current list setup, search, selection, and double-click add behavior.
- `src/main/java/macro/builder/ui/sandbox/FilterCatalog.java:111-146` for current native tier-one entries.
- `src/main/java/macro/builder/ui/sandbox/FilterCatalog.java:195-220` for Fiji menu command discovery.
- `src/main/java/macro/builder/ui/sandbox/SandboxDialog.java:91-113` for how the catalog add listener currently adds to the selected branch.
- `src/main/java/macro/builder/image/FilterMacroParser.java:37-93` for supported native command types.
- `src/main/java/macro/builder/image/dag/DagToIjmEmitter.java:81-110` for command names emitted from native types.

## Scope

- Replace the single `JList<Entry>` presentation in `FilterCatalog` with grouped command sections.
- Add a visible `+` button on every command row.
- Keep search support; filtering should hide non-matching rows or sections without removing entries from the backing list.
- Add missing supported 3D native entries: `Gaussian Blur 3D`, `Median 3D`, and `Minimum 3D`.
- Keep double-click add behavior only if it remains natural after the row buttons are added.
- Preserve the existing `AddRequestListener` contract so `SandboxDialog` can still add entries through `addCatalogNode`.

## Out of scope

- Do not rework the full `SandboxDialog` layout; stage 02 owns layout and embedded previews.
- Do not move parameter editing into step cards; stage 03 owns inline step editing.
- Do not change branch merge behavior; stages 04 and 05 own merge editing and multi-selection.
- Do not add new macro execution features beyond exposing already-supported 3D commands.

## Files touched

| path | action | reason |
| --- | --- | --- |
| `src/main/java/macro/builder/ui/sandbox/FilterCatalog.java` | MODIFY | Replace flat list UI with grouped category panels and row add buttons. |
| `src/test/java/macro/builder/ui/sandbox/FilterCatalogTest.java` | NEW | Verify category assignment, search, and 3D catalog entries. |

## Implementation sketch

Keep `FilterCatalog.Entry` as the object passed back to `SandboxDialog`, but add a UI grouping field or helper:

```java
enum CatalogGroup {
    FILTERS("Filters"),
    THREE_D("3D"),
    BINARY("Binary"),
    IMAGE_TYPE("Image type"),
    PLUGINS("Plugins"),
    FIJI_COMMANDS("Fiji commands");

    final String title;
}
```

Suggested grouping:

```java
private CatalogGroup groupFor(Entry entry) {
    if (entry.legacy && entry.menuPath.startsWith("Plugins")) return CatalogGroup.PLUGINS;
    if (entry.legacy) return CatalogGroup.FIJI_COMMANDS;
    switch (entry.type) {
        case GAUSSIAN_BLUR_3D:
        case MEDIAN_3D:
        case MINIMUM_3D:
            return CatalogGroup.THREE_D;
        case DILATE:
        case ERODE:
        case OPEN:
        case CLOSE_:
        case FILL_HOLES:
        case SKELETONIZE:
        case AUTO_LOCAL_THRESHOLD:
            return CatalogGroup.BINARY;
        case CONVERT_8BIT:
        case CONVERT_16BIT:
        case CONVERT_32BIT:
            return CatalogGroup.IMAGE_TYPE;
        default:
            return CatalogGroup.FILTERS;
    }
}
```

Add the missing 3D entries in `seedTierOne()`:

```java
add("3D", "Gaussian Blur 3D", OpType.GAUSSIAN_BLUR_3D, "x=2 y=2 z=1");
add("3D", "Median 3D", OpType.MEDIAN_3D, "x=2 y=2 z=1");
add("3D", "Minimum 3D", OpType.MINIMUM_3D, "x=2 y=2 z=1");
```

Build rows with a label and a local add button:

```java
private JPanel buildEntryRow(final Entry entry) {
    JPanel row = new JPanel(new BorderLayout(6, 0));
    row.add(new JLabel(entry.label + " " + entry.badge()), BorderLayout.CENTER);
    JButton add = new JButton("+");
    add.setToolTipText("Add " + entry.label + " to the selected branch");
    add.addActionListener(e -> {
        if (addListener != null) addListener.onAddRequested(entry);
    });
    row.add(add, BorderLayout.EAST);
    return row;
}
```

Tests should use same-package access where possible. Keep tests headless-safe by passing deterministic tier-two entries through the package-private `FilterCatalog(List<Entry> tierTwoEntries)` constructor.

## Exit gate

1. `.\mvnw.cmd -q test` passes.
2. `FilterCatalogTest` proves the 3D native entries exist and are grouped under `3D`.
3. Manual UI check: the builder shows separate boxes for filters, 3D, binary, plugins, and image type conversion.
4. Manual UI check: clicking a row `+` adds that command to the active branch.
5. Search still finds commands by label, category, menu path, and badge.

## Known risks

- A grouped panel can become too tall for the right column. Put the grouped catalog inside a scroll pane and collapse empty sections when search is active.
- Fiji menu discovery can be empty in headless tests. Use injected tier-two entries in tests instead of depending on a live Fiji menu bar.
- If `Plugins` commands are too noisy, keep the grouping logic isolated so stage 06 can refine it without touching the rest of the builder.
