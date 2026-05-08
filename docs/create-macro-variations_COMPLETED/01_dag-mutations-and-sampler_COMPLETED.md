# Stage 01 — DAG mutations and variant sampler

## Why this stage exists

Every variant is a small mutation of the user's current `DagIR`: one node's `args` overridden with new values, or one node's whole filter type substituted. Variants are then enumerated by a sampler — OFAT (one-factor-at-a-time) by default, cartesian behind an Advanced toggle. This stage builds the pure-logic core that every later UI stage drives. No Swing, no I/O — just functions over immutable DAGs.

## Prerequisites

None.

## Read first

- `docs/create-macro-variations/00_overview.md`
- `src/main/java/macro/builder/image/dag/DagIR.java` (entire file — immutable value type)
- `src/main/java/macro/builder/image/dag/DagLine.java` (entire file)
- `src/main/java/macro/builder/image/dag/DagNode.java` (entire file)
- `src/main/java/macro/builder/image/dag/Combiner.java` (skim — combiners pass through unchanged)
- `src/main/java/macro/builder/image/FilterMacroParser.java` lines 37–95 (the `OpType` enum)

## Scope

- New `DagMutations` utility class with two static methods:
  - `withNodeArgs(DagIR src, String nodeId, String newArgs)` — rebuild the DAG with one node's `args` replaced, all other fields equal.
  - `withNodeSubstituted(DagIR src, String nodeId, OpType newType, String newArgs)` — rebuild the DAG replacing one node's `type` and `args`. Keeps the same node `id` so downstream references stay valid.
  - Both methods preserve `version`, `primaryChannel`, `combiners`, `output`, and per-line `id`/`name`/`sourceChannel`.
  - Both throw `IllegalArgumentException` if `nodeId` not found.
- New `VariantPlan` value type representing a single planned variant: `String label`, `DagIR dag`, `Map<String, String> paramDelta`. Immutable.
- New `VariantSampler` class with two static methods:
  - `ofat(DagIR baseline, List<VariantAxis> axes, int maxVariants)` — generates baseline + per-axis perturbations, capped at `maxVariants`.
  - `cartesian(DagIR baseline, List<VariantAxis> axes, int maxVariants)` — full cross-product, hard-capped at 16, throws if requested > 16.
- New `VariantAxis` value type: `String nodeId`, `AxisKind kind` (PARAM_SWEEP or FILTER_SWAP), `List<AlternativeValue> alternatives`. Each `AlternativeValue` carries the args string to apply and a short label for the caption.

## Out of scope

- The OpType parameter registry that *generates* axes from filter knowledge — that's stage 02.
- Memory estimation — stage 03.
- Anything that runs a DAG — stage 04.
- Any UI — stages 05, 06.

## Files touched

| Path | NEW / MODIFY | Reason |
|------|--------------|--------|
| `src/main/java/macro/builder/image/dag/DagMutations.java` | NEW | Static helpers for cloning a DAG with overrides |
| `src/main/java/macro/builder/image/variation/VariantAxis.java` | NEW | Value type — one axis of variation |
| `src/main/java/macro/builder/image/variation/VariantPlan.java` | NEW | Value type — one planned variant |
| `src/main/java/macro/builder/image/variation/VariantSampler.java` | NEW | OFAT + cartesian sampling |
| `src/test/java/macro/builder/image/dag/DagMutationsTest.java` | NEW | Round-trip tests for both mutation methods |
| `src/test/java/macro/builder/image/variation/VariantSamplerTest.java` | NEW | OFAT count, cartesian count, hard-cap behaviour |

## Implementation sketch

```java
// DagMutations.java
public final class DagMutations {
    private DagMutations() {}

    public static DagIR withNodeArgs(DagIR src, String nodeId, String newArgs) {
        return rebuildWith(src, nodeId, node ->
            new DagNode(node.id, node.type, newArgs, node.commandName, node.menuPath));
    }

    public static DagIR withNodeSubstituted(DagIR src, String nodeId, OpType newType, String newArgs) {
        return rebuildWith(src, nodeId, node ->
            new DagNode(node.id, newType, newArgs, "", ""));
    }

    private static DagIR rebuildWith(DagIR src, String nodeId, Function<DagNode, DagNode> fn) {
        boolean found = false;
        List<DagLine> newLines = new ArrayList<>(src.lines.size());
        for (DagLine line : src.lines) {
            List<DagNode> newOps = new ArrayList<>(line.ops.size());
            for (DagNode n : line.ops) {
                if (n.id.equals(nodeId)) { newOps.add(fn.apply(n)); found = true; }
                else newOps.add(n);
            }
            newLines.add(new DagLine(line.id, line.name, newOps, line.sourceChannel));
        }
        if (!found) throw new IllegalArgumentException("nodeId not found: " + nodeId);
        return new DagIR(src.version, src.primaryChannel, newLines,
                         src.combiners, src.output, src.executionTier);
    }
}
```

```java
// VariantAxis.java
public final class VariantAxis {
    public enum Kind { PARAM_SWEEP, FILTER_SWAP }
    public final String nodeId;
    public final Kind kind;
    public final List<AlternativeValue> alternatives;
    // ctor copies list defensively, makes unmodifiable

    public static final class AlternativeValue {
        public final String label;       // baked into tile caption, e.g. "σ=2.0" or "Median"
        public final OpType type;        // null for PARAM_SWEEP (keeps existing type)
        public final String args;        // the args string to apply
        // ctor
    }
}
```

```java
// VariantSampler.java
public final class VariantSampler {
    public static List<VariantPlan> ofat(DagIR baseline, List<VariantAxis> axes, int maxVariants) {
        List<VariantPlan> out = new ArrayList<>();
        out.add(new VariantPlan("baseline", baseline, Collections.emptyMap()));
        for (VariantAxis axis : axes) {
            for (AlternativeValue alt : axis.alternatives) {
                if (out.size() >= maxVariants) return out;
                DagIR mutated = (axis.kind == Kind.PARAM_SWEEP)
                    ? DagMutations.withNodeArgs(baseline, axis.nodeId, alt.args)
                    : DagMutations.withNodeSubstituted(baseline, axis.nodeId, alt.type, alt.args);
                out.add(new VariantPlan(alt.label, mutated, Map.of(axis.nodeId, alt.label)));
            }
        }
        return out;
    }

    public static List<VariantPlan> cartesian(DagIR baseline, List<VariantAxis> axes, int maxVariants) {
        if (maxVariants > 16) throw new IllegalArgumentException("hard cap is 16");
        // standard cartesian iteration; same mutation rules as ofat;
        // throw if count exceeds maxVariants (don't silently truncate)
    }
}
```

## Exit gate

1. `mvn test -Dtest=DagMutationsTest,VariantSamplerTest` passes.
2. `DagMutationsTest` covers: (a) `withNodeArgs` returns a new DAG equal to source except for the target node's args; (b) `withNodeSubstituted` swaps both type and args on the right node; (c) both throw on unknown `nodeId`; (d) source DAG is unchanged after mutation (immutability check via `equals`).
3. `VariantSamplerTest` covers: (a) OFAT with 4 axes × 2 alternatives produces 1 baseline + 8 variants when `maxVariants=9`; (b) OFAT respects `maxVariants` cap and stops mid-axis; (c) cartesian with 2×3 axes produces 6 variants; (d) cartesian throws when total > `maxVariants` and when `maxVariants > 16`.
4. `mvn compile` produces no new warnings.
5. No new public method shows up in `FilterExecutor` or `DagToIjmEmitter` — this stage is purely additive in the `dag` and `variation` packages.

## Known risks

- `DagIR` constructor recomputes `executionTier` from the lines. After substitution, a node might gain or lose a non-empty `commandName` and flip the tier. Tests should assert the post-mutation tier matches expectations: substituting *into* a tier-2 op flips to legacy, substituting *out* preserves whatever the rest of the DAG implies.
- `Combiner` references nodes by id. Substitution preserves the id, so combiners stay valid; tests should include a DAG with a combiner to confirm.
