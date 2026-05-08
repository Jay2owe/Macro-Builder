# Create Macro Variations — Tier 1 MVP

## End goal

A new **Create Variations** button in the Sandbox dialog. The user picks a node in the DAG and either swaps it for an alternative filter or sweeps one of its numeric parameters across a range. The plugin runs N variants of the full pipeline in parallel and opens a Swing grid window showing the raw input plus each variant's output. A single shared scrollbar drives the Z slice on every tile in lockstep. The user eliminates losing tiles with an X, drops to a 2-up Compare mode with spacebar flicker when 2 remain, then clicks Promote on the winner — the DAG mutates in place and the chosen variant becomes the new pipeline.

## Why we're doing this

Today, tuning a Macro-Builder pipeline means edit-run-duplicate, edit-run-duplicate, accumulating five misaligned ImageJ windows that the user manually pairs with `Synchronize Windows` and pencil-on-paper notes. Decisions are made from memory. Methods sections are reconstructed after the fact. For the user, a one-parameter sweep that should take 30 seconds takes 5–20 minutes and produces no methods-quality artefact.

The Variations feature replaces that loop with: pick what to vary → click Generate → eliminate losers → click Promote. The whole flow drops to under two minutes, produces an auto-labelled montage as a side effect, and emits a recordable `.ijm` line for the chosen variant.

## Architecture overview

Five new subsystems, plus a button:

```
SandboxDialog [Create Variations button]
       │
       ▼
VariationChooserDialog ──pulls──▶ OpTypeParamRegistry (defaults + ranges)
       │                          MemoryEstimator    (auto-prompt for ROI)
       │
       ▼
VariantPlanner  ──── DagMutations (clone-with-override / clone-with-substitution)
       │             VariantSampler (OFAT default, cartesian behind Advanced)
       ▼
VariantExecutor ──── parallel pool, calls FilterExecutor.runDagThreadSafe per variant
       │
       ▼
VariantGridFrame ──── JPanel grid of ImageCanvas tiles + shared JScrollBar
       │              raw image as tile [0]; baked-in parameter captions
       │              per-tile actions: Promote / Save preset / X
       │              when 2 remain → CompareFrame (2-up + spacebar flicker)
       │
       ▼
Export   ──── MontageMaker (PNG), DagToIjmEmitter (.ijm), preset save
Provenance ── session log entry per Generate; recordable .ijm on Promote
```

All variant DAGs are immutable rebuilds of the source `DagIR` — `DagIR`, `DagLine`, `DagNode` are already immutable value types so cloning with overrides is mechanical.

## Stage map

| NN | Slug | One-line goal | Size | Depends on |
|----|------|---------------|------|-----------|
| 01 | dag-mutations-and-sampler | Pure-logic helpers: clone-with-node-override, clone-with-node-substituted, OFAT and cartesian samplers | M | none |
| 02 | optype-param-registry | Static registry mapping each `OpType` to its parameter specs (name, default, range, log/linear) | M | none |
| 03 | memory-estimator-and-roi-prompt | Memory estimator + ROI-mode auto-prompt mirroring Auto Threshold's >25-slice prompt | S | none |
| 04 | variant-executor | Parallel pool wrapping `FilterExecutor.runDagThreadSafe` for native-tier DAGs; serial fallback for legacy | M | 01 |
| 05 | variation-chooser-dialog | Modal Swing dialog: pick mode, pick node, pick alternatives or numeric range, show live memory estimate | M | 01, 02, 03 |
| 06 | variant-grid-window | Swing JPanel grid of `ImageCanvas` tiles; shared `JScrollBar` driving `setSlice`; baked-in captions; LUT/calibration inheritance | L | 04 |
| 07 | tile-actions-and-compare-mode | Per-tile Promote / Save preset / X buttons; eliminate-from-view; auto-drop to 2-up Compare with spacebar flicker | M | 06 |
| 08 | export-provenance-and-wiring | Add the SandboxDialog button; export montage PNG + `.ijm`; provenance session log; macro-recordability | M | 05, 07 |

Sizes: S ≈ half a day, M ≈ 1 day, L ≈ 1.5–2 days.

## House rules

- **Prefer editing existing files over creating new ones.** The DAG package, FilterExecutor, and SandboxDialog already exist — extend them.
- **Never `Enhance Contrast normalize=true` on measured data** (project-wide).
- **Macro-recordability is non-negotiable.** Every user-visible action — Generate, Eliminate, Promote, Save preset — must round-trip through the macro recorder. A "click to pick winner" gesture that doesn't emit an `.ijm` line is broken.
- **Display-settings inheritance** is non-negotiable on tiles. Variants must inherit source LUT, min/max, calibration, composite mode. Without this, JND-level differences get drowned in display drift.
- **Memory-safety by default.** Estimator runs before any execution; if `N × source_bytes × 1.3 > 0.25 × IJ.maxMemory()`, force ROI mode and warn loudly.
- **Native-tier DAGs only run in parallel.** Legacy-tier (with `commandName != ""`) DAGs serialize through `WindowManagerLock`.
- **No new dependencies.** Pure Swing + AWT + ImageJ core. No JavaFX, no SyncWindows reflection.

## Out of scope (deferred to Tier 2 / Tier 3)

- Intermediate-result cache across variants (big perf win, defer)
- Per-step X-ray view of intermediate node outputs
- Heatmap disagreement view across variants
- Gradient slider on ROI for live numeric parameter scrubbing
- HyperStack-as-channels output mode (alternative to grid)
- Bayesian / genetic / LLM variant generation
- Variant Wardrobe persistence across sessions
- Multi-node simultaneous swaps in one run (only single-node sweep or single-node substitution in v1)

## Known open questions

- **Multichannel input × variant axis.** Recommendation: each tile shows the source's already-merged composite display. Plan addresses in stage 06.
- **Z default.** Recommendation: scrub mode (matches user's instinct), with an explicit MIP toggle. Plan addresses in stage 06.
- **Z-projection vs scrub default for large data ROI mode.** Same answer — scrub default.

## How to run a stage

```
/do-step docs/create-macro-variations/
```

Picks up the lowest-numbered `NN_*.md` without `_COMPLETED`, executes it, commits, renames to `NN_*_COMPLETED.md`.
