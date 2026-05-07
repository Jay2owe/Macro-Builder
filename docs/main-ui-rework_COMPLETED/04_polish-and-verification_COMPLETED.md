# 04 - Polish And Verification

## Why this stage exists

The layout is not done until it looks compact and behaves cleanly. This stage tunes tile sizing, icon scale, spacing, labels, and documentation so the reworked launcher feels intentional rather than just rearranged.

## Prerequisites

- `01_launcher-layout-shell.md` must be completed and renamed with `_COMPLETED`.
- `02_saved-macro-history.md` must be completed and renamed with `_COMPLETED`.
- `03_macro-action-column.md` must be completed and renamed with `_COMPLETED`.

## Read first

- `docs/main-ui-rework/00_overview.md`
- `AGENTS.md`
- `src/main/java/macro/builder/Macro_Builder.java:97-190` for final launcher layout and sizing constants.
- `src/main/java/macro/builder/Macro_Builder.java:747-858` for state and label refresh.
- `docs/USER_GUIDE.md:11-35` for old launch, image selection, build, and test labels.
- `docs/USER_GUIDE.md:60-90` for batch, save, and state documentation.
- `pom.xml:65-97` for dependencies and build configuration.

## Scope

- Tune icon tile size, icon drawing bounds, margins, and text size to reduce blank space.
- Make long text fit cleanly, especially `Open Image/Container` and `Create Macro Variations...`.
- Verify the window still packs and opens at a sensible default size.
- Update `docs/USER_GUIDE.md` for the new main UI labels and saved-macro combo box.
- Run tests and perform manual UI checks.

## Out of scope

- Adding new behavior to placeholder buttons.
- Redesigning the visual macro builder.
- Deploying the jar unless the user separately asks for `deploy`.

## Files touched

| path | action | reason |
| --- | --- | --- |
| `src/main/java/macro/builder/Macro_Builder.java` | MODIFY | Final spacing, sizing, icon, and enabled-state polish. |
| `docs/USER_GUIDE.md` | MODIFY | Update user-facing instructions for the new main launcher layout. |

## Implementation sketch

Recommended visual targets:

```java
dialog.setSize(new Dimension(980, 560)); // adjust only if the new layout needs it
private static final Dimension TILE_SIZE = new Dimension(96, 104);
private static final int TILE_ICON_SIZE = 44;
private static final int LEFT_COLUMN_WIDTH = 230;
private static final int RIGHT_COLUMN_WIDTH = 200;
```

Spacing guidance:

- Left column padding around 10 to 12 px.
- Tile grid gap around 8 px.
- Icon should occupy about 40 to 45 percent of tile height.
- Tile text should be 11 to 12 pt, centered, and at most two lines.
- Right action buttons should share one width and not resize based on label length.
- Macro text area should remain monospaced and take most of the center height.

Documentation updates:

- Replace references to `Build step-by-step` with `Build Macro` where describing the main launcher.
- Replace references to `Record in Fiji` with `Macro Recorder` where describing the main launcher.
- Add a short note that saved macros can be selected from the saved-macro dropdown.
- Keep detailed behavior text for Test Counts and batch export intact unless labels changed.

Verification commands:

```powershell
.\mvnw.cmd -q test
```

Manual checks:

- Launch Fiji and open `Plugins > Macro Builder > Macro Builder` if a local Fiji install is available.
- Check the tile grid at the default window size.
- Resize the window narrower and confirm text does not overlap.
- Save a macro, close Macro Builder, reopen it, and load the macro from the combo box.

## Exit gate

1. `.\mvnw.cmd -q test` passes, or any failure is clearly unrelated and documented.
2. User guide accurately describes the new launcher labels and saved-macro dropdown.
3. Main UI has no obvious oversized blank areas at the default window size.
4. Tile text and right-button text do not overlap or truncate badly.
5. Existing workflows still work: open image/container, use current image, build macro, record macro, test counts, save macro, save batch macro.

## Known risks

- Swing look and feel can vary between Windows and Fiji environments. Prefer stable preferred sizes and layout constraints over pixel-perfect assumptions.
- User guide wording can drift from exact button labels. Match labels exactly where commands are named.
