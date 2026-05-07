# Update Docs And Validate Hyperstack Workflows

## Why this stage exists

The implementation stages add a user-visible multichannel workflow, so the guide and developer notes need to explain it clearly. This final stage also gathers the end-to-end checks that prove the feature works in Fiji, not just in unit tests. It is intentionally small and should not introduce new behavior beyond documentation or minor wording fixes found during validation.

## Prerequisites

- `01_dag-channel-metadata_COMPLETED.md`
- `02_channel-aware-execution_COMPLETED.md`
- `03_sandbox-channel-ui_COMPLETED.md`
- `04_count-and-batch-channel-settings_COMPLETED.md`

## Read first

- `docs/multichannel-hyperstack-pipeline/00_overview.md`
- `AGENTS.md`
- `docs/DEVELOPER.md`
- `README.md`
- `docs/USER_GUIDE.md`
- `CHANGELOG.md`
- `src/main/java/macro/builder/ui/sandbox/SandboxDialog.java:140-205`
- `src/main/java/macro/builder/ui/sandbox/DagCanvasPanel.java:96-130`
- `src/main/java/macro/builder/analysis/BatchMacroExporter.java:84-120`

## Scope

- Update `README.md` so the top-level description mentions multichannel hyperstack support.
- Update `docs/USER_GUIDE.md` with primary-channel selection and auxiliary branch examples.
- Update `docs/DEVELOPER.md` with the DAG channel metadata and execution rule.
- Update `CHANGELOG.md` with the new capability.
- Add or update final tests only if an implementation stage missed a small documentation-adjacent assertion.
- Run the full automated test suite.
- Perform manual Fiji validation on a synthetic or real multichannel hyperstack.

## Out of scope

- Do not add new UI features. Stage 03 owns UI.
- Do not alter execution semantics. Stage 02 owns execution.
- Do not change batch settings schema beyond documentation. Stage 04 owns settings.
- Do not deploy unless the user explicitly asks for deploy after this stage.
- Do not publish to GitHub, GitHub Actions, or an ImageJ update site.

## Files touched

| path | action | reason |
| --- | --- | --- |
| `README.md` | MODIFY | Mention multichannel hyperstack support in the public overview. |
| `docs/USER_GUIDE.md` | MODIFY | Explain primary-channel selection, auxiliary branches, and subtraction use. |
| `docs/DEVELOPER.md` | MODIFY | Document DAG channel fields and branch execution rules. |
| `CHANGELOG.md` | MODIFY | Record the user-visible feature. |
| `src/test/java/...` | MODIFY | Only if a minor missing validation assertion is discovered. |

## Implementation sketch

Suggested `README.md` wording near the opening description:

```markdown
Macro Builder can use a single image, stack, or multichannel hyperstack. For
multichannel hyperstacks, choose the primary channel to process, then optionally
use parallel branches from other channels to subtract, mask, or combine signals.
```

Suggested `docs/USER_GUIDE.md` section under `Build A Macro`:

```markdown
## Multichannel Hyperstacks

When the selected source image has more than one channel, the visual builder
shows a primary-channel control. Branch 1 starts from the primary channel.
Additional parallel branches can start from any available channel, such as C2.

Example: choose C1 as the primary object channel, add a C2 branch, filter C2,
then merge with `Subtract` so the output is C1 minus the processed C2 signal.
`Subtract` is ordered: the first input is the image being subtracted from.
```

Suggested `docs/DEVELOPER.md` addition:

```markdown
## Visual DAG Channel Metadata

Visual builder DAGs store `primaryChannel` on `DagIR` and `sourceChannel` on
each `DagLine`. Channel numbers are 1-based ImageJ channel indexes. Old DAGs
without these fields must load as channel 1.

Native DAG execution extracts each branch source channel into a one-channel
stack before running branch steps. Combiners operate on those one-channel
branch outputs and produce a single processed output image for preview,
threshold shootout, and batch count workflows.
```

Manual validation checklist:

1. Create or open a two-channel hyperstack with distinct intensities in C1 and C2.
2. Open `Plugins > Macro Builder > Macro Builder`.
3. Select the hyperstack and open `Build Macro`.
4. Choose primary channel `C1`.
5. Add a second branch sourced from `C2`.
6. Add simple steps if useful, then merge with `Subtract`.
7. Preview the full filter and confirm output reflects `C1 - C2`.
8. Save the macro and confirm the embedded DAG JSON contains `primaryChannel` and branch `sourceChannel` fields.
9. Run `Test Counts...` and confirm the source image is unchanged.
10. Save a batch macro and confirm the `.settings.json` contains `primaryChannel`.

Automated checks:

```powershell
.\mvnw.cmd clean test "-Denforcer.skip=true"
```

Optional local Fiji smoke check if relevant:

```powershell
.\scripts\smoke-fiji.ps1
```

## Exit gate

1. `README.md`, `docs/USER_GUIDE.md`, `docs/DEVELOPER.md`, and `CHANGELOG.md` describe the multichannel behavior.
2. `.\mvnw.cmd clean test "-Denforcer.skip=true"` passes.
3. Manual Fiji validation confirms `C1 - C2` preview on a hyperstack.
4. Manual Fiji validation confirms count testing does not mutate the selected source image.
5. Manual Fiji validation confirms exported batch settings include `primaryChannel`.

## Known risks

- Documentation can overpromise named-channel support. Keep wording to numeric channels unless named channels are actually implemented.
- Manual validation depends on a suitable multichannel image. If none is available, create a synthetic two-channel hyperstack in Fiji for the check.
- Do not use this stage to sneak in broader UI polish. If validation exposes a real bug, fix the narrow bug and document it in the final report.

