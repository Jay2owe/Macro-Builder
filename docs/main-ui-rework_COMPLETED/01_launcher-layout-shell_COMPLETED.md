# 01 - Launcher Layout Shell

## Why this stage exists

This stage creates the new main-window structure the rest of the rework depends on. The user should immediately see four compact workflow tiles on the left, the selected image and macro text in the center, and room for macro-specific actions on the right. This stage does not add saved macro history or new macro action behavior yet.

## Prerequisites

None.

## Read first

- `docs/main-ui-rework/00_overview.md`
- `AGENTS.md`
- `src/main/java/macro/builder/Macro_Builder.java:1-90` for imports and `SessionDialog` fields.
- `src/main/java/macro/builder/Macro_Builder.java:97-190` for the current window size and `buildUi`.
- `src/main/java/macro/builder/Macro_Builder.java:395-520` for the existing build, recorder, count, and macro preview actions.
- `src/main/java/macro/builder/Macro_Builder.java:845-858` for current label refresh helpers.

## Scope

- Replace the current top/header, center split pane, and footer action layout with the agreed three-column launcher layout.
- Left column: fixed-width 2 x 2 tile grid with icons above small centered text.
- Center column: selected image label, secondary image controls, macro source label, and the last macro text area.
- Right column: reserve space for macro action buttons; stage 03 owns final wiring.
- Keep the status text and progress bar across the bottom.
- Remove the large active-image preview from the launcher if it creates wasted space.

## Out of scope

- Saved macro combo box and history persistence. Stage 02 owns this.
- Right-side macro action behavior. Stage 03 owns this.
- Builder UI redesign. This plan explicitly leaves `SandboxDialog` for a later plan.
- Deploying to Fiji.

## Files touched

| path | action | reason |
| --- | --- | --- |
| `src/main/java/macro/builder/Macro_Builder.java` | MODIFY | Rework `SessionDialog.buildUi`, add launcher tile helpers, and preserve existing action method calls. |

## Implementation sketch

Target layout:

```text
+-------------------------+--------------------------------------+---------------+
|                         | Selected image                       | Last macro    |
|  [Build]   [Recorder]   | [image name / dimensions]            | actions       |
|                         | [Use current] [Open last]            |               |
|  [Counts]  [Open image] | Macro source: ...                    | reserved      |
|                         | Last macro text area                 | for stage 03  |
+-------------------------+--------------------------------------+---------------+
| status text                                                                    |
| progress bar                                                                   |
+--------------------------------------------------------------------------------+
```

Use explicit sizing:

```java
private static final Dimension TILE_SIZE = new Dimension(96, 104);
private static final int TILE_ICON_SIZE = 44;
private static final int LEFT_COLUMN_WIDTH = 230;
private static final int RIGHT_COLUMN_WIDTH = 200;
```

Suggested helper shape:

```java
private JButton createWorkflowTile(String text, Icon icon, String tooltip) {
    JButton button = new JButton("<html><center>" + text + "</center></html>", icon);
    button.setHorizontalTextPosition(SwingConstants.CENTER);
    button.setVerticalTextPosition(SwingConstants.BOTTOM);
    button.setPreferredSize(TILE_SIZE);
    button.setMinimumSize(TILE_SIZE);
    button.setFocusPainted(false);
    button.setToolTipText(tooltip);
    return button;
}
```

Implement icons as small Swing `Icon` classes or private factory methods:

- Build Macro: crossed hammer and screwdriver.
- Macro Recorder: red record circle.
- Test Counts: several counted dots/cells in a microscope-field circle.
- Open Image/Container: folder with a small image thumbnail.

Existing action mapping:

```java
buildTile.addActionListener(e -> openSandbox());
recordTile.addActionListener(e -> openRecorder());
countTile.addActionListener(e -> openCountTester());
openImageTile.addActionListener(e -> openImageFromDisk());
```

Keep `Use current Fiji image` and `openLastButton` as small secondary controls near `imageLabel`.

## Exit gate

1. `./mvnw.cmd -q test` passes, or any failure is clearly unrelated and documented.
2. `Macro_Builder.java` compiles without unused imports.
3. Manual UI check: main window has a 2 x 2 tile grid on the left, macro text in the center, and no oversized blank preview.
4. Manual behavior check: Build Macro, Macro Recorder, Test Counts, Open Image/Container, Use current Fiji image, and Open last image/container still call their existing flows.

## Known risks

- Custom-painted icons can look cramped. Keep drawings inside a fixed 44 x 44 area.
- Removing `ImagePreviewPanel` from the launcher may leave references in refresh or cleanup code. Remove or adapt those references carefully rather than leaving dead fields.
