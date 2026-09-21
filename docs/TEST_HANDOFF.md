# Test handoff

## Status

PASS

## Target and mode

- Target: Macro Builder 0.2.6
- Mode: one focused physical ImageJ plugin test harness run for a legacy `Convolve...` command
- Run ID: `run_0736206f006d`

## Proven

- The 0.2.6 artifact was installed into the disposable harness Fiji and its
  SHA-256 hash was verified: `e27da4d8d4b95b87cf651490e6eb40b1fb79c8a974684956f9ed00df8e2c3cd6`.
- Physical menu dispatch opened Macro Builder and its visual builder.
- The catalog search found `Convolve [legacy]`.
- A physical click opened the legacy command dialog, the command was accepted,
  and the recorded parameter editor was accepted.
- The modified builder was discarded through its real confirmation dialog.
- Physical dispatch, screenshots, performance samples, cleanup, and integrity
  gates all passed.
- Maven: 310 tests passed, 0 failed, 0 errors, 5 skipped.

## Source fix covered

- Menu labels retain the exact ImageJ command suffix for execution while the
  visible label remains compact.
- The command name captured by the recorder is stored in the legacy node before
  macro emission, so menu labels and executable command keys can differ.
- Temporary probe images are closed rather than left as ImageJ's current image.

## Outputs checked

- Harness report: `C:\Users\Owner\.imagej-plugin-test-harness\runs\run_0736206f006d\report\report.html`
- Review: `docs/REVIEW-2026-09-21.md`

## Scope boundary

This run proves the legacy step can be added and stored through the real UI.
The source tests also prove its exact command and options are emitted into the
macro. The harness scenario does not run the full preview after insertion.

## Resume command

```powershell
cd "C:\Users\Owner\UK Dementia Research Institute Dropbox\Brancaccio Lab\Jamie\Experiments\Macro-Builder"
imagej-test-auto --config "C:\Users\Owner\UK Dementia Research Institute Dropbox\Brancaccio Lab\Jamie\Experiments\ImageJ Plugins\ImageJ Plugin Test Harness\harness.yaml" --json run --plugin "." --scenario ".\scenarios\legacy-command\main.yaml" --jar "$env:TEMP\macro-builder-harness-0.2.6\Macro_Builder-0.2.6.jar" --skip-build --keep-run
```
