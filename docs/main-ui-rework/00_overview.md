# Main UI Rework

## End goal

Macro Builder opens to a cleaner main launcher window. The left side has a compact 2 x 2 grid of icon tiles for the four main workflows: Build Macro, Macro Recorder, Test Counts, and Open Image/Container. The center shows the selected image, a saved-macro dropdown, the macro source, and the last macro text. A narrow right column contains plain text actions for the currently loaded macro.

The visual macro builder UI is not part of this plan. It will be planned separately after the main launcher is done.

## Why we're doing this

The current main window spreads related actions across a header, center split pane, and footer, which makes the main choices less obvious. This rework makes the first screen read as four clear workflow entry points plus one focused area for the current macro. It also makes previously saved macros easier to reload and act on without hunting through the file system.

## Architecture overview

Most work stays inside `src/main/java/macro/builder/Macro_Builder.java`, especially the `SessionDialog` inner class. The existing action methods should be reused where possible: `openSandbox`, `openRecorder`, `openImageFromDisk`, `openCountTester`, `saveBatchMacro`, and the macro execution helpers. A small new UI helper class may be added under `src/main/java/macro/builder/ui/` if keeping icon tile drawing inside `Macro_Builder.java` becomes messy.

```text
------------------+------------------------------+----------------------+
| 2 x 2 icon tiles | selected image + macro picker | current macro actions |
|                  | macro source + macro text    | stacked text buttons  |
+------------------+------------------------------+----------------------+
| status text + progress bar                                             |
+------------------------------------------------------------------------+
```

## Stage map

| NN | name | one-line goal | rough size | depends on |
| --- | --- | --- | --- | --- |
| 01 | launcher-layout-shell | Replace the current header/preview/footer layout with the agreed three-column launcher layout and compact icon tiles. | medium | none |
| 02 | saved-macro-history | Add the saved-macro combo box, local history persistence, macro loading, and save-history registration. | medium | 01 |
| 03 | macro-action-column | Add and wire the right-side text actions for the loaded macro, including placeholders for unfinished features. | small-medium | 01, 02 |
| 04 | polish-and-verification | Tune icon/text sizing to reduce blank space, update user docs, and run compile/tests/manual checks. | small | 01, 02, 03 |

## House rules

- Keep this plan scoped to the main launcher UI. Do not redesign `SandboxDialog` or other builder internals here.
- Use plain language in visible UI labels.
- The left workflow tiles must be compact: large enough to read quickly, but sized to avoid dead blank space.
- Use icon tiles for the four main workflow buttons only. The macro-specific actions on the right should remain plain text buttons.
- Do not remove existing working behavior unless the new layout intentionally relocates it.
- Keep deploy behavior local only when the user says `deploy`: copy the built jar to the local Fiji plugin folders listed in `AGENTS.md`; do not publish to GitHub Actions or the Fiji update site unless explicitly asked.

## Known open questions

- Exact icon drawing style is not fixed yet. The current plan is custom Swing painting or small generated icons: crossed hammer/screwdriver, red record dot, counted microscope-field dots, and folder-with-image thumbnail.
- `Run as batch...` and `Create Macro Variations...` may start as clear placeholders if no existing direct function is available.

## How to run a stage

Run `/do-step docs/main-ui-rework/` to execute the first numbered stage after the stage files have been written.
