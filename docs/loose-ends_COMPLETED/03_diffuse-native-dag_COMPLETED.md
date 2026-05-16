# Stage 03 — native DAG execution for Diffuse Object Filter

## Goal

Replace `DiffuseObjectFilter.apply(ImagePlus, String)`'s current implementation — which calls `IJ.runMacro(macroContent)`, then adopts `WindowManager.getImage("DoG_result")`, then closes every window that didn't pre-exist — with a DAG path that runs the Difference-of-Gaussians + 3D median sequence directly on pixel data, without `WindowManager`, without `imp.show()`, and without macro-string parsing.

## Why

`DiffuseObjectFilter.java:22` promises: "Native DAG execution (without `WindowManager`) arrives in stage 03; until then this compound handler keeps batch runs safe by routing through a known macro path." No stage 03 of any plan has delivered this. The current macro path:

- Requires the input window to be `show()`n (visible to `WindowManager`).
- Holds `WindowManagerLock.LOCK` for the duration to avoid concurrent collisions.
- Mutates the input image's title (`imp.setTitle(uniqueTitle)`) and restores it in `finally`.
- Scans `WindowManager.getIDList()` before and after to detect intermediate windows and close them.

This stops batch from running variants in parallel, blocks headless use cleanly, and makes the filter the only compound op in the codebase that cannot live inside the DAG without an `IJ.runMacro` round-trip.

## Files touched

- `src/main/java/macro/builder/image/DiffuseObjectFilter.java` — rewrite `apply(...)` to operate directly on `ImagePlus` pixels. Keep `matches(String)` as-is (it's the dispatcher hook); keep `apply(...)`'s signature stable so callers don't change.
- `src/main/java/macro/builder/image/DiffuseObjectFilter.java` — javadoc: drop the "arrives in stage 03" sentence; describe the actual algorithm.
- Possibly `src/main/java/macro/builder/image/WindowManagerLock.java` — if no caller still needs the lock for DoG, the existing callers stay (other filters may use it), but DoG must no longer acquire it.
- `src/test/java/macro/builder/image/DiffuseObjectFilterTest.java` — new or extended; pixel-comparison fixtures.

## Approach

1. **Reproduce the macro inline as Java**. The current macro builds:
   - `DoG_small` = duplicate of input, Gaussian-blurred at σ_small.
   - `DoG_big` = duplicate of input, Gaussian-blurred at σ_big.
   - `DoG_result` = `imageCalculator("Subtract create stack", DoG_small, DoG_big)`.
   - 3D median on `DoG_result`.
   
   In Java, on each slice (or on the stack at once where ImageJ's APIs support it):
   - Two `GaussianBlur` calls on duplicated `ImageProcessor`s.
   - A direct subtract using `ImageProcessor.copyBits(ip2, 0, 0, Blitter.SUBTRACT)` per slice.
   - A 3D median via the same call other filters in this codebase make (likely `Mean3D`/`Median3D` helper, or ImageJ's `Filters3D.filter(stack, Filters3D.MEDIAN, ...)`). Check what existing filters do for 3D median and mirror it.
2. **No `WindowManager` touches**. The new path takes the input `ImagePlus`, builds an output stack, and mutates the input's stack at the end via `imp.setStack(...)` exactly the way the current path does at line 81. No `imp.show()`, no `imp.hide()`, no `setTitle` round-trip.
3. **Parameter extraction**. The current macro hard-codes σ_small, σ_big, and the 3D-median radius inside the macro string. Extract those constants into Java fields on `DiffuseObjectFilter` (with the same numeric values). If the constants live elsewhere (a preset definition file), pull them from there instead.
4. **Keep the `matches(...)` heuristic**. Dispatch unchanged. The change is purely the body of `apply(...)`.
5. **Regression contract**. The bundled DoG preset's pixel output is the contract. Add a fixture test that runs the OLD macro path on a small synthetic stack, captures the pixel array, then runs the NEW path on the same stack and asserts pixel-equality (or, if Gaussian-blur order-of-ops causes ε-level rounding differences, asserts max-abs-diff ≤ 1 LSB for 8/16-bit and ≤ 1e-5 for float).

## Tests

- `DiffuseObjectFilterTest#nativePathMatchesMacroOn8Bit()` — synthetic 64×64×8 8-bit stack; run macro path, run native path, assert pixel equality (or ≤ 1 LSB max diff).
- `DiffuseObjectFilterTest#nativePathMatchesMacroOn16Bit()` — same on 16-bit.
- `DiffuseObjectFilterTest#nativePathMatchesMacroOnFloat()` — same on float; tolerance ≤ 1e-5.
- `DiffuseObjectFilterTest#doesNotShowInputWindow()` — start with `imp.getWindow() == null`, call `apply(...)`, assert `imp.getWindow() == null` afterwards.
- `DiffuseObjectFilterTest#doesNotTouchWindowManager()` — capture `WindowManager.getIDList()` before, run, capture after; assert identical.
- `DiffuseObjectFilterTest#runsInParallelWithoutLock()` — two `apply(...)` calls on independent `ImagePlus` instances in parallel return correct pixels and complete without deadlock (the current path's `WindowManagerLock` would serialise them).

## Exit gate

- `.\mvnw.cmd test "-Denforcer.skip=true"` is green, including the new regression tests.
- Manual smoke check:
  1. Open the bundled "Diffuse Object Filter" preset on a real microscope stack.
  2. Build Macro → run on a single image → confirm output looks visually identical to the previous build's output (eyeball the result against a saved reference TIFF, or use the `Diff` plugin if installed).
  3. Run-as-batch on a folder of ≥ 3 images. Confirm no Fiji windows flicker open mid-run (the old path would briefly show/hide them).
  4. Confirm CPU profile shows no `IJ.runMacro` time in this filter's hot path.
- `DiffuseObjectFilter.java:22` javadoc no longer mentions "stage 03" — that edit lives in stage 05.

## Risks and mitigations

- **Gaussian-blur σ provenance.** If the macro's σ values come from anywhere other than the macro string, the native port must source them from the same place; otherwise it will silently use a different blur.
- **3D median equivalence.** ImageJ's "3D Median" can dispatch through different code paths depending on stack size and bit depth. Pin the native call to the same path the macro hits today; if uncertain, add the OLD path as a fallback gated by a system property for one release.
- **Pixel-equality tolerance.** Reordering arithmetic (subtract before vs after blur) can flip 1-LSB values on integer stacks. Decide upfront whether the contract is "byte-identical" or "≤ 1 LSB max diff" and document the choice in the test.
- **Memory.** Two duplicates plus the result stack triples peak memory vs the macro path (which lets ImageJ reuse buffers). For typical microscope stacks this is fine; for very large stacks, add an estimated-memory check and fall back to slice-by-slice processing if the estimate exceeds the existing 256 MiB cap defined in the test-counts memory budget.
- **`WindowManagerLock` callers.** Audit who else holds the lock. If only the DoG path needed it, the lock class itself may become dead — leave it alone unless an explicit cleanup pass is on the table.
