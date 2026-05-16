# Stage 02 — variant-generation cancel button

## Goal

Add a Cancel button to `ProgressDialog` that interrupts the variant-generation `SwingWorker` cleanly, disposes the dialog, and leaves the underlying state (`VariationChooserDialog`, the source image, the open Variant Grid window if any) consistent — no half-built tiles, no leaked windows, no zombie thread.

## Why

`ProgressDialog.java:18-20` documents: "the `SwingWorker` backing the run cannot be cleanly cancelled at this stage — stage 07 / 08 may add a cancel button." Stages 07 and 08 of `create-macro-variations` shipped without it. Today a 60-variant run on a large stack is uninterruptible, and the title-bar close is explicitly disabled (`setDefaultCloseOperation(DO_NOTHING_ON_CLOSE)`).

## Files touched

- `src/main/java/macro/builder/ui/sandbox/variation/ProgressDialog.java` — add `JButton cancel` to the south panel, expose an `addCancelListener(ActionListener)` (or a `setCancelHandler(Runnable)`) hook.
- `src/main/java/macro/builder/ui/sandbox/variation/VariationChooserDialog.java` — own the `SwingWorker` reference at `~line 334`; on cancel-click call `worker.cancel(true)` and dispose the progress dialog. Also remove the `setDefaultCloseOperation(DO_NOTHING_ON_CLOSE)` once cancel is wired, or leave it as a no-op and route the close-button through the same handler.
- `src/main/java/macro/builder/image/variation/VariantExecutor.java` — check `Thread.currentThread().isInterrupted()` between variants (and ideally before each `FilterExecutor.run...` call inside a variant). Abort cleanly, returning the partial list collected so far so the caller can decide what to do.
- `src/main/java/macro/builder/image/variation/ProgressCallback.java` — add an `onCancelled()` notification (or document that the existing `onError(...)` carries cancellation). Whichever choice, document it in the javadoc.
- Tests under `src/test/java/macro/builder/image/variation/VariantExecutorTest.java`.

## Approach

1. **Executor-side**: at the top of `VariantExecutor.runAll`'s per-variant loop, check `Thread.currentThread().isInterrupted()`. If interrupted, return the partial list and call `progress.onCancelled()` (or whatever signal is agreed). Inside per-variant work, if a `FilterExecutor` call is the long bit, see whether its existing thread-interrupt handling propagates — if not, document that mid-variant cancellation may take one full variant's worth of time.
2. **Dialog-side**: `ProgressDialog` grows a Cancel button. Hold the cancel handler as a `Runnable`. When the parent (`VariationChooserDialog`) builds the dialog it sets `progress.setCancelHandler(() -> { worker.cancel(true); progress.markCancelling(); })`. `markCancelling()` swaps the status label to "Cancelling..." and disables the Cancel button so a second click is a no-op.
3. **Parent-side**: `VariationChooserDialog` already constructs the worker (`SwingWorker<List<VariantResult>, Integer>` at `~line 334`). The worker's `done()` already runs on EDT; add an `isCancelled()` branch that disposes the dialog, restores `generateButton.setEnabled(true)`, and posts the status `"Variant generation cancelled. Partial results discarded."` to whatever the dialog uses as its status label. Do NOT open the Variant Grid window on a cancelled run.
4. **Window-bar close**: once cancel is wired, switch `ProgressDialog`'s default close operation from `DO_NOTHING_ON_CLOSE` to one that routes through the same cancel handler. A `WindowListener` on `windowClosing` is the simplest path.

## Tests

- `VariantExecutorTest#runAllStopsOnInterrupt()` — spawn `runAll(...)` on a background thread, interrupt that thread after the first variant completes, assert the returned list size is ≤ first-variant-count and that `onCancelled()` was called.
- `VariantExecutorTest#runAllReturnsPartialList()` — same setup; assert the partial list contains complete `VariantResult`s only (no half-baked entries).
- `ProgressDialogTest#cancelHandlerFires()` — construct dialog, register a counting handler, click the Cancel button programmatically, assert handler invoked once.
- `ProgressDialogTest#cancelHandlerFiresOnceForRepeatedClicks()` — repeated clicks after the first do not re-invoke the handler.
- `ProgressDialogTest#windowClosingRoutesToCancel()` — dispatching a `WindowEvent.WINDOW_CLOSING` runs the cancel handler.

UI tests use `assumeFalse(GraphicsEnvironment.isHeadless())` when they construct a real `JDialog`.

## Exit gate

- `.\mvnw.cmd test "-Denforcer.skip=true"` is green.
- Manual smoke check:
  1. Open Build Macro → Create variations → start a run with ≥ 20 variants on a stack > 500 MB.
  2. Click Cancel mid-run. Confirm the dialog goes to "Cancelling..." then closes within one variant's runtime. Confirm no Variant Grid window opens.
  3. Confirm the Generate button is re-enabled and the chooser dialog is responsive.
  4. Memory check: `Heap Status` in Fiji shows no orphaned variant `ImagePlus` retained after cancellation.
- The "stage 07 / 08 may add a cancel button" javadoc in `ProgressDialog.java:18-20` is replaced with current behaviour (or removed entirely if the new code is self-explanatory) — that edit belongs in stage 05.

## Risks and mitigations

- **Mid-variant cancellation is hard.** If `FilterExecutor` doesn't honour interrupts, cancellation will block until the current variant finishes. Acceptable for the first version; document it in the status banner ("Finishing current variant before stopping...").
- **Worker `done()` path needs an `isCancelled()` branch.** Forgetting this means the existing `done()` will still try to open a Variant Grid with `null`/partial results.
- **Window leaks under cancel.** `VariantExecutor.runAll` may have intermediate `ImagePlus` instances it expects to close on success. Ensure cancellation runs the same cleanup path — either by wrapping the per-variant loop in `try { ... } finally { cleanup(); }` or by routing through `onCancelled()` to the cleanup hook.
- **EDT discipline.** All dialog mutations from the worker's `done()`/`onCancelled()` callbacks must marshal onto the EDT explicitly if the callbacks fire from a background thread.
