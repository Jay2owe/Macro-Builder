# Sandbox Layout and Embedded Preview

## Why this stage exists

The final builder needs more room for categorized commands and direct editing, while image previews should be visible inside the builder instead of appearing as separate Fiji windows. This stage establishes the new three-zone layout and embeds source/output previews without changing parameter editing yet.

## Prerequisites

- `docs/build-macro-ui-rework/01_catalog-categories_COMPLETED.md`

## Read first

- `docs/build-macro-ui-rework/00_overview.md`
- `AGENTS.md`
- `src/main/java/macro/builder/ui/sandbox/SandboxDialog.java:79-120` for current constructor wiring.
- `src/main/java/macro/builder/ui/sandbox/SandboxDialog.java:166-207` for current `buildMain` split-pane layout.
- `src/main/java/macro/builder/ui/sandbox/SandboxDialog.java:340-388` for current preview execution.
- `src/main/java/macro/builder/Macro_Builder.java:517-526` for sandbox launch.
- `src/main/java/macro/builder/Macro_Builder.java:592-611` for the current sandbox preview handler that opens Fiji image windows.
- `src/main/java/macro/builder/ui/ImagePreviewPanel.java:24-170` for the reusable embedded preview component.

## Scope

- Rework `SandboxDialog.buildMain()` to a left preview column, center sandbox canvas, and right categorized catalog.
- Embed a source preview and processed preview on the left using `ImagePreviewPanel`.
- Update sandbox preview display so `Preview up to selected step` and `Preview full filter` update the embedded processed preview.
- Keep the footer preview buttons and save/cancel behavior.
- Keep the current step settings and merge editor panels temporarily if needed; stage 03 and stage 04 remove them after inline editing exists.
- Avoid changing main launcher preview/run behavior outside the sandbox.

## Out of scope

- Do not change the `FilterCatalog` grouping behavior except for small integration fixes; stage 01 owns it.
- Do not add inline step parameter editing; stage 03 owns it.
- Do not add inline merge editing; stage 04 owns it.
- Do not add branch multi-selection; stage 05 owns it.

## Files touched

| path | action | reason |
| --- | --- | --- |
| `src/main/java/macro/builder/ui/sandbox/SandboxDialog.java` | MODIFY | Rebuild main layout and route previews into embedded panels. |
| `src/main/java/macro/builder/Macro_Builder.java` | MODIFY | Adjust sandbox `PreviewHandler` so it no longer opens separate preview windows for sandbox previews. |
| `src/main/java/macro/builder/ui/ImagePreviewPanel.java` | MODIFY | Add any small APIs needed for source/output preview refresh. |

## Implementation sketch

Extend the preview handler contract so the dialog can show the source image without taking ownership of it:

```java
public interface PreviewHandler {
    ImagePlus createSource() throws Exception;
    ImagePlus getSourceForDisplay();
    ImagePlus showPreview(ImagePlus result, ImagePlus existingPreview) throws Exception;
    void close(ImagePlus imp);
}
```

In `Macro_Builder.createSandboxPreviewHandler()`:

```java
@Override public ImagePlus getSourceForDisplay() {
    return sourceImage;
}

@Override public ImagePlus showPreview(ImagePlus result, ImagePlus existingPreview) {
    closeImageQuietly(existingPreview);
    result.setTitle("Macro Builder Preview");
    sandboxPreview = result;
    return result; // Do not call result.show() here.
}
```

In `SandboxDialog`, keep references:

```java
private final ImagePreviewPanel sourcePreview = new ImagePreviewPanel("Source image");
private final ImagePreviewPanel outputPreview = new ImagePreviewPanel("Preview output");
```

Build the new main layout:

```text
+---------------------+-----------------------------------+----------------------+
| Source preview       | Your filter canvas                | Step categories      |
| Output preview       | Branches, steps, merge row        | Filters / 3D / ...   |
+---------------------+-----------------------------------+----------------------+
| preset/help/status                                      | preview/save buttons |
+--------------------------------------------------------------------------------+
```

Suggested `buildMain()` shape:

```java
JPanel previews = new JPanel(new GridLayout(2, 1, 0, 8));
previews.add(sourcePreview);
previews.add(outputPreview);

JScrollPane canvasScroll = new JScrollPane(canvas);

JPanel catalogPanel = new JPanel(new BorderLayout(0, 8));
catalogPanel.add(catalog, BorderLayout.CENTER);
// Temporarily add existing editors below if inline editing is not done yet.

JSplitPane centerRight = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, canvasScroll, catalogPanel);
centerRight.setResizeWeight(0.70);

JSplitPane all = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, previews, centerRight);
all.setResizeWeight(0.22);
```

Update `preview(DagIR dag)`:

```java
previewImage = previewHandler.showPreview(previewResult, previewImage);
outputPreview.setImage(previewImage);
setBusy(false, "Preview complete.");
```

On dialog open or constructor completion:

```java
if (previewHandler != null) {
    sourcePreview.setImage(previewHandler.getSourceForDisplay());
}
```

## Exit gate

1. `.\mvnw.cmd -q test` passes.
2. `.\mvnw.cmd -q -DskipTests compile` passes with no unused imports.
3. Manual UI check: the builder has previews on the left, sandbox in the center, and grouped steps on the right.
4. Manual UI check: sandbox preview buttons update the embedded output preview instead of opening a separate Fiji image window.
5. Manual behavior check: closing the builder still closes temporary sandbox preview images.

## Known risks

- `ImagePreviewPanel.java` is currently present in this workspace. Preserve any local edits and integrate it instead of replacing it blindly.
- Swing split panes can produce cramped previews. Set sensible preferred and minimum widths for the preview column and catalog column.
- The preview handler still owns temporary duplicate images. Make sure `windowClosed` cleanup closes `previewImage` exactly once.
