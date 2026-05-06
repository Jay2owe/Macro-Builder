# Documentation and manual validation

## Why this stage exists

The feature changes how users decide whether a macro is good enough for analysis. This stage updates the docs and records manual Fiji checks so the new count testing, native fixed thresholds, 2D/3D mode, batch CSV, and batch macro export are understandable and releasable.

## Prerequisites

- `03_shootout-dialog-ui.md` completed and renamed with `_COMPLETED`.
- `04_batch-shootout-runner.md` completed and renamed with `_COMPLETED`.
- `05_batch-macro-export.md` completed and renamed with `_COMPLETED`.

## Read first

- `docs/count-shootout-batch/00_overview.md`
- `AGENTS.md`
- `README.md`
- `docs/USER_GUIDE.md:31-42`
- `docs/DEVELOPER.md`
- `docs/UPDATE_SITE_UPLOAD.md`

## Scope

- Update `README.md` feature summary.
- Update `docs/USER_GUIDE.md` with count testing, 2D/3D mode, native fixed thresholds, CSV export, and batch macro export.
- Update `docs/DEVELOPER.md` with new analysis package structure and testing notes.
- Update release/manual smoke checks where appropriate.
- Run automated tests and document manual Fiji checks performed.

## Out of scope

- New analysis behavior or UI controls; earlier stages own implementation.
- Deploying the jar. Only deploy if the user explicitly asks for deploy after this stage.
- Update-site publishing.

## Files touched

| path | action | reason |
|---|---|---|
| `README.md` | MODIFY | Mention count testing and batch validation at a high level. |
| `docs/USER_GUIDE.md` | MODIFY | Explain how users run and interpret count tests. |
| `docs/DEVELOPER.md` | MODIFY | Document new package/classes and verification expectations. |
| `docs/UPDATE_SITE_UPLOAD.md` | MODIFY | Add manual checks for count testing before upload. |
| `CHANGELOG.md` | MODIFY | Record user-facing feature addition if the repo maintains unreleased notes. |

## Implementation sketch

User-facing wording must be explicit about fixed thresholds:

```text
Fixed numeric thresholds use the processed macro output's native intensity scale. On a 16-bit processed image, 2000 means intensity 2000. Macro Builder does not remap that value to 0-255 before thresholding.
```

Manual validation checklist:

```text
1. Launch Fiji and open Macro Builder.
2. Build or record a simple filter macro.
3. Run Test counts... in 2D mode with auto threshold shootout.
4. Run Test counts... in 3D mode on a stack with a fixed numeric threshold.
5. Export CSV and confirm file rows/columns.
6. Run batch count test on at least two ordinary images.
7. Save batch macro and run it on a tiny folder.
8. Confirm the source image is unchanged after all test actions.
```

## Exit gate

1. `.\mvnw.cmd clean test "-Denforcer.skip=true"` passes.
2. Docs mention both `2D particles` and `3D stack objects`.
3. Docs mention native-scale fixed thresholds for 16-bit images.
4. Docs describe CSV export and saved batch macro behavior.
5. Manual Fiji validation notes are present in the final stage summary.

## Known risks

- Docs can overpromise if stage 05 ships a processing-only batch macro fallback. Match the docs to the actual implemented behavior.
- Manual Fiji testing is required because Swing UI and ImageJ window cleanup are not fully covered by unit tests.
