# Docs and smoke checks

## Why this stage exists

Once folder and container batch runs work, the user-facing docs must stop saying `Run as batch...` is unimplemented. This stage also records the manual Fiji checks that automated tests cannot cover, especially real Bio-Formats container behavior.

## Prerequisites

- `03_batch-dialog-folder-mode.md` completed and renamed with `_COMPLETED`.
- `04_bioformats-container-selection.md` completed and renamed with `_COMPLETED`.

## Read first

- `docs/run-as-batch-macro/00_overview.md`
- `AGENTS.md`
- `README.md:1-35` for the current high-level feature description and basic use list.
- `docs/USER_GUIDE.md:11-44` for selected image and loaded macro action docs.
- `docs/USER_GUIDE.md:69-85` for existing batch count and saved batch macro docs.
- `docs/DEVELOPER.md:68-102` for Bio-Formats, count testing, and regression test notes.
- `docs/UPDATE_SITE_UPLOAD.md:53-67` for existing manual release smoke checks.
- `src/main/resources/plugins.config:1-2` to confirm no extra plugin command registration is needed unless a stage added one.

## Scope

- Update README to mention direct `Run as batch...` macro-output runs.
- Update the user guide:
  - explain folder regex mode,
  - explain container series tick-list mode,
  - explain TIFF outputs and CSV summary,
  - keep count batch testing clearly separate.
- Update developer docs:
  - describe the new scanner, runner, dialog, and Bio-Formats provider,
  - document why Bio-Formats remains optional or how any provided dependency is handled,
  - add regression-test expectations.
- Update update-site/manual smoke checklist with folder and container batch checks.
- Record manual test notes in the docs or in this stage file before marking it completed.

## Out of scope

- Any new feature work beyond doc wording or tiny copy fixes found during smoke testing.
- Deploying the jar. Only deploy if the user explicitly asks.
- Publishing to an update site or GitHub release.

## Files touched

| path | action | reason |
|---|---|---|
| `README.md` | MODIFY | Mention direct batch macro output runs at a high level. |
| `docs/USER_GUIDE.md` | MODIFY | Replace the unimplemented-button note and explain both batch input modes. |
| `docs/DEVELOPER.md` | MODIFY | Document the new batch macro architecture and Bio-Formats handling. |
| `docs/UPDATE_SITE_UPLOAD.md` | MODIFY | Add manual smoke checks for folder and container batch macro runs. |
| `docs/run-as-batch-macro/05_docs-and-smoke-checks.md` | MODIFY | Rename to `_COMPLETED` only after recording manual outcomes. |

## Implementation sketch

User guide wording should keep the workflows distinct:

```text
Run as batch...
Runs the loaded macro on selected inputs and saves processed TIFF images plus a CSV summary. Use folder regex mode for ordinary image folders, or container mode for a selected Bio-Formats microscope container where you tick the series/images to process.

Test Counts... > Run batch...
Runs count validation across ordinary image files and writes count rows. This is separate from saving processed image outputs.
```

Folder regex documentation:

```text
The filename regex must match the whole filename. For example, `(?i).*_DAPI\.tif` matches `Sample1_DAPI.tif`, and `(?i).*\.(tif|tiff|png)` matches common image files.
```

Manual smoke checklist to add:

```text
- Build and run `.\mvnw.cmd test "-Denforcer.skip=true"`.
- Launch Fiji with the built jar installed locally.
- Build or load a simple macro.
- Run `Run as batch...` in folder regex mode on two small TIFF or PNG files.
- Confirm TIFF outputs are written and `Macro_Builder_Batch_Run.csv` contains success rows.
- Confirm the selected source image is unchanged.
- Run `Run as batch...` in container mode on a real Bio-Formats container.
- Tick one series and untick another.
- Confirm only ticked series are saved as TIFF outputs and the CSV records series index/name.
- Confirm no leftover temporary batch windows remain open.
```

If a manual container file is not available, record that explicitly:

```text
Manual Bio-Formats container smoke test not run: no local test container available.
```

## Exit gate

1. `.\mvnw.cmd test "-Denforcer.skip=true"` passes.
2. README no longer describes only saved batch count macros as the batch path.
3. `docs/USER_GUIDE.md` no longer says `Run as batch...` is unimplemented.
4. User guide explains folder regex full-filename matching.
5. User guide explains container series tick selection.
6. Developer docs mention the new scanner, runner, dialog, and Bio-Formats provider.
7. Manual Fiji smoke results are recorded, including whether a real container test was available.

## Known risks

- Docs can overpromise if stage 04 had to ship a fallback. Match wording to the actual implemented behavior.
- Do not imply Bio-Formats support works outside Fiji unless the implementation has been tested that way.
- Keep count batch and macro-output batch names distinct so users do not expect count columns in the macro-output CSV.
