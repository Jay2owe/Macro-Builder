# Build Macro UI Rework

## End goal

The visual Build Macro screen becomes a clearer, faster workspace. Users can add macro steps from category-specific boxes with local `+` buttons, edit step parameters directly from the built macro sandbox, preview images inside the builder, and merge selected branches without configuring a separate merge box.

## Why we're doing this

The current builder makes users pick a step in one place and add it somewhere else, hides very different macro commands in one flat list, uses separate parameter and merge panels that consume space, and opens previews outside the builder. This rework makes the workflow more direct and leaves room for filter, 3D, binary, plugin, and image-conversion command groups.

## Architecture overview

`Macro_Builder.openSandbox()` launches `SandboxDialog`. `SandboxDialog` owns the builder window layout, `FilterCatalog` owns available steps, `DagCanvasPanel` renders branches, step cards, and merge cards, and `SandboxModel` converts UI state to the saved `DagIR` macro graph. The rework should keep the existing DAG and macro output compatible, but change how users interact with those model objects.

```text
Macro_Builder
  -> SandboxDialog
       -> ImagePreviewPanel        left source/output previews
       -> DagCanvasPanel           center branch sandbox
       -> FilterCatalog            right grouped command boxes
       -> SandboxModel             saved DAG state
```

## Stage map

| NN | name | one-line goal | rough size | depends on |
| --- | --- | --- | --- | --- |
| 01 | catalog-categories | Replace the flat step list with grouped macro categories and row `+` add buttons. | medium | none |
| 02 | sandbox-layout-preview | Rework the builder layout and embed source/output previews on the left. | medium-large | 01 |
| 03 | inline-step-editing | Edit step parameters by double-click/right-click in the sandbox, then remove the separate step settings box. | medium | 02 |
| 04 | inline-merge-editing | Edit merge operation and inputs from merge cards and remove the separate merge editor panel. | medium | 03 |
| 05 | branch-multiselect-merge | Add Ctrl/Shift branch selection and merge selected branches in visual order. | medium | 04 |
| 06 | polish-tests-docs | Add focused tests, update docs, and verify the full Fiji workflow. | medium | 05 |

## House rules

- Follow `AGENTS.md`: be concise, use plain language, and include exact paths or commands when needed.
- Do not overwrite unrelated dirty work. The repository may already contain user edits.
- Keep the saved `DagIR` JSON and emitted macro format backward compatible with existing saved macros.
- When the user says `deploy`, deploy only to local Fiji/plugin folders as described in `AGENTS.md`; do not publish to GitHub Actions or the Fiji update site unless explicitly asked.
- Prefer existing Swing, ImageJ, and project helper patterns over adding a new UI framework.

## Known open questions

- `Plugin` is assumed to mean non-native Fiji commands discovered from Fiji menus, especially `Plugins > ...`. If that should exclude built-in Fiji plugins or include all legacy commands, adjust the stage 01 grouping rule before implementation.
- The repository currently contains `src/main/java/macro/builder/ui/ImagePreviewPanel.java`. Treat it as reusable preview code and preserve user-owned local changes if it differs from the tracked state.

## How to run a stage

Run `/do-step docs/build-macro-ui-rework/` to execute the next incomplete stage.
