# Multichannel Hyperstack Pipeline

## End goal

Macro Builder should accept a multichannel ImageJ hyperstack and let the user choose which channel is the primary signal channel. The visual builder should also let selected parallel branches start from other channels, so a secondary marker can filter, subtract from, mask, or otherwise combine with the primary channel. The finished macro should still produce one processed output image for preview, count testing, and batch count workflows.

## Why we're doing this

The current pipeline treats the selected source image as a single-channel source. That blocks common microscopy workflows where the measured objects are in one channel but another channel is useful for cleanup, exclusion, or subtraction. After this ships, users can build filters like "process C1 as the main object signal, process C2 as a background or exclusion signal, then subtract C2 from C1" without manually splitting channels outside Macro Builder.

## Architecture overview

Macro Builder's visual builder stores a filter graph as a DAG. Each `DagLine` is a parallel branch, each `DagNode` is an ImageJ processing step, and each `Combiner` merges branch outputs. This work adds channel metadata to that graph: the DAG has one primary channel, and each branch has its own source channel. Execution changes from "clone the whole source for every branch" to "extract the branch source channel as a one-channel stack, process that branch, then combine one-channel branch outputs."

```text
source hyperstack C1..Cn
        |
        +-- primary branch: source C(primary) -> steps -> line_A
        +-- auxiliary branch: source C2      -> steps -> line_B
        |
        +-- combiner: line_A SUBTRACT line_B -> single-channel output
```

## Stage map

| NN | name | one-line goal | rough size | depends on |
| --- | --- | --- | --- | --- |
| 01 | dag-channel-metadata | Add primary-channel and per-branch source-channel metadata to the DAG, with old saved DAGs defaulting to channel 1. | medium | none |
| 02 | channel-aware-execution | Make native DAG execution and IJM fallback duplicate the correct channel for each branch, then combine single-channel branch outputs. | medium | 01 |
| 03 | sandbox-channel-ui | Add primary-channel and branch-source controls to the visual builder, with clear branch labels and subtraction ordering. | medium | 02 |
| 04 | count-and-batch-channel-settings | Carry channel selection through preview, run, count shootout, saved macros, and batch count settings. | medium | 03 |
| 05 | docs-and-validation | Update user/developer docs and add final manual validation checks for multichannel hyperstacks. | small | 04 |

## House rules

- Keep Macro Builder standalone. Do not add project-specific importers, channel naming setup, bin analysis setup, or batch import workflows.
- Do not mutate the selected source image. Preview, run-on-duplicate, count shootout, selected-row mask previews, and batch runs must operate on duplicates.
- Fixed numeric thresholds must stay in the processed output's native intensity scale. Do not remap fixed threshold values to 0-255 before thresholding.
- Batch count mode supports ordinary image files first. Bio-Formats containers are still skipped by `BatchShootoutRunner` with a CSV error row.
- Deploy means local Fiji/plugin folder replacement only. Do not publish to GitHub Actions or an ImageJ update site unless explicitly requested.
- Preserve old saved macros and old `.dag.json` sidecars. Missing channel metadata must mean channel 1.
- Keep subtraction ordered and explicit: `SUBTRACT` means first selected input minus later selected inputs.
- No source code changes are expected from this split-plan step. Each numbered stage owns its implementation.

## Known open questions

- The exact UI wording for "primary channel" versus "branch source channel" should be checked in Fiji with a real multichannel image.
- The first implementation should use numeric channel labels such as `C1`, `C2`, and `C3`. Human-readable channel names can be added later if ImageJ exposes reliable names for the source image.
- It is not yet decided whether the preview image panel needs a channel slider. The minimum requirement is that branch source selection is visible and controllable.

## How to run a stage

Run `/do-step docs/multichannel-hyperstack-pipeline/` to execute the first incomplete numbered stage.

