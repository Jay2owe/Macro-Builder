# Add DAG Channel Metadata

## Why this stage exists

The visual builder needs a durable way to remember which channel is the primary signal and which channel each parallel branch starts from. Without this model change, the UI and executor can only guess from the selected image. Later stages depend on this metadata being present, serialized, and backward compatible.

## Prerequisites

none

## Read first

- `docs/multichannel-hyperstack-pipeline/00_overview.md`
- `AGENTS.md`
- `docs/DEVELOPER.md`
- `src/main/java/macro/builder/image/dag/DagIR.java:7-54`
- `src/main/java/macro/builder/image/dag/DagLine.java:7-31`
- `src/main/java/macro/builder/image/dag/DagIRSerializer.java:13-170`
- `src/main/java/macro/builder/image/dag/IjmToDagLoader.java:13-55`
- `src/test/java/macro/builder/image/dag/DagIRRoundTripTest.java:15-53`

## Scope

- Add a 1-based primary channel field to `DagIR`.
- Add a 1-based source channel field to `DagLine`.
- Keep existing constructors or add overloads so existing code still compiles.
- Update JSON serialization to write channel fields.
- Update JSON deserialization to treat missing channel fields as `1`.
- Update validation so channel fields must be positive integers.
- Update embedded-DAG loading and IJM-to-DAG seeding so old macros become primary channel 1 with branch source channel 1.
- Add tests for round trip and old JSON compatibility.

## Out of scope

- Do not change execution behavior. Stage 02 owns channel-aware execution.
- Do not change Swing UI. Stage 03 owns primary-channel and branch-source controls.
- Do not change batch settings or count shootout settings. Stage 04 owns those.
- Do not add named microscopy channel workflows or project-specific channel setup.

## Files touched

| path | action | reason |
| --- | --- | --- |
| `src/main/java/macro/builder/image/dag/DagIR.java` | MODIFY | Store primary channel on the visual graph. |
| `src/main/java/macro/builder/image/dag/DagLine.java` | MODIFY | Store the source channel for each parallel branch. |
| `src/main/java/macro/builder/image/dag/DagIRSerializer.java` | MODIFY | Read and write channel metadata while accepting old JSON. |
| `src/main/java/macro/builder/image/dag/IjmToDagLoader.java` | MODIFY | Seed imported macro DAGs with channel 1 defaults. |
| `src/test/java/macro/builder/image/dag/DagIRRoundTripTest.java` | MODIFY | Cover channel metadata round trip and old JSON loading. |

## Implementation sketch

Keep the existing constructor signatures where other code already calls them. Add overloads with channel arguments.

```java
public final class DagIR {
    public final int version;
    public final int primaryChannel;
    public final List<DagLine> lines;
    public final List<Combiner> combiners;
    public final String output;
    public final String executionTier;

    public DagIR(int version, List<DagLine> lines, List<Combiner> combiners,
                 String output, String executionTier) {
        this(version, 1, lines, combiners, output, executionTier);
    }

    public DagIR(int version, int primaryChannel, List<DagLine> lines,
                 List<Combiner> combiners, String output, String executionTier) {
        this.version = version;
        this.primaryChannel = positiveChannel(primaryChannel);
        ...
    }
}
```

```java
public final class DagLine {
    public final String id;
    public final int sourceChannel;
    public final List<DagNode> ops;

    public DagLine(String id, List<DagNode> ops) {
        this(id, ops, 1);
    }

    public DagLine(String id, List<DagNode> ops, int sourceChannel) {
        this.id = id == null ? "" : id;
        this.sourceChannel = sourceChannel < 1 ? 1 : sourceChannel;
        ...
    }
}
```

Serializer changes:

```json
{
  "version": 1,
  "primaryChannel": 1,
  "executionTier": "native",
  "lines": [
    { "id": "line_A", "sourceChannel": 1, "ops": [] },
    { "id": "line_B", "sourceChannel": 2, "ops": [] }
  ],
  "combiners": [],
  "output": "line_A"
}
```

Deserialization must use optional defaults:

```java
int primaryChannel = asInt(optional(obj, "primaryChannel", Long.valueOf(1)), "primaryChannel");
int sourceChannel = asInt(optional(rawLine, "sourceChannel", Long.valueOf(1)), "lines[" + i + "].sourceChannel");
```

Validation should reject `primaryChannel < 1` and any `line.sourceChannel < 1`.

Update `equals` and `hashCode` for both model classes so tests compare channel metadata.

Test cases to add:

```java
@Test
public void serializesChannelMetadata() {
    DagIR dag = new DagIR(1, 2,
            Arrays.asList(
                    new DagLine("line_A", Collections.<DagNode>emptyList(), 2),
                    new DagLine("line_B", Collections.<DagNode>emptyList(), 3)),
            Collections.<Combiner>emptyList(),
            "line_A",
            "native");

    DagIR roundTripped = DagIRSerializer.fromJson(DagIRSerializer.toJson(dag));

    assertEquals(dag, roundTripped);
    assertEquals(2, roundTripped.primaryChannel);
    assertEquals(3, roundTripped.lines.get(1).sourceChannel);
}

@Test
public void oldDagJsonDefaultsToChannelOne() {
    String json = "{\"version\":1,\"executionTier\":\"native\","
            + "\"lines\":[{\"id\":\"line_A\",\"ops\":[]}],"
            + "\"combiners\":[],\"output\":\"line_A\"}";

    DagIR dag = DagIRSerializer.fromJson(json);

    assertEquals(1, dag.primaryChannel);
    assertEquals(1, dag.lines.get(0).sourceChannel);
}
```

## Exit gate

1. `.\mvnw.cmd -Dtest=DagIRRoundTripTest test "-Denforcer.skip=true"` passes.
2. `rg -n "new DagIR\\(" src/main/java src/test/java` shows existing call sites still compile without mass rewrites.
3. Existing old-style JSON without `primaryChannel` or `sourceChannel` loads as channel 1.
4. New serialized DAG JSON contains both `primaryChannel` and `sourceChannel`.

## Known risks

- Breaking old saved `.dag.json` files would be a user-visible regression. Keep channel fields optional in `fromJson`.
- Reordering constructor arguments can silently create wrong graphs. Prefer overloads or static helpers over changing existing argument order.
- `version` does not need to be bumped just to add optional fields. If a bump is added, make sure old version 1 JSON still validates.

