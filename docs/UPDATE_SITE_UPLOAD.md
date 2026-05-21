# Update-Site Upload Checklist

Use this checklist before uploading to:

```text
https://sites.imagej.net/Macro-Builder/
```

The ImageJ update-site docs say to populate a site from a local Fiji installation using the Fiji updater. Do not copy files directly to `sites.imagej.net`, because the updater metadata must stay in sync with the uploaded files.

Official docs:

- https://imagej.net/update-sites/setup
- https://imagej.net/update-sites/faq

## 1. Preflight

From the repository root:

```powershell
git status --short
rg -n "<private-path-or-old-project-name>" . --glob "!target/**" --glob "!.git/**"
.\mvnw.cmd clean test
.\mvnw.cmd clean -DskipTests package
```

Expected upload artifact:

```text
target/Macro_Builder-0.2.2.jar
```

Do not upload:

```text
target/Macro_Builder-0.2.2-sources.jar
target/Macro_Builder-0.2.2-tests.jar
```

## 2. Local Fiji Test

Copy the jar into a test Fiji installation:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke-fiji.ps1 -FijiPluginsDir "C:\path\to\Fiji.app\plugins"
```

Restart Fiji and verify:

- `Plugins > Macro Builder > Macro Builder` appears.
- The dialog opens.
- `Use current Fiji image` works.
- `Open image/container...` works for a 2D image, a TIFF stack, and a Bio-Formats container or folder-style dataset.
- `Build step-by-step` can create and preview a macro.
- `Record in Fiji` can record and preview a macro.
- `Save macro...` writes an `.ijm` file.
- Visual-builder macros also write a `.dag.json` sidecar.
- `Run macro on selected image` runs on a duplicate, not the source image.
- `Run as batch...` folder mode previews at least two ordinary TIFF or PNG files from a filename regex, lets you untick a row, and saves only ticked TIFF outputs plus `Macro_Builder_Batch_Run.csv`.
- `Run as batch...` folder mode reports an invalid filename regex clearly and does not start a run.
- `Run as batch...` cancellation changes the status and stops before starting the next input.
- `Run as batch...` leaves the selected source image unchanged after a folder batch run.
- `Run as batch...` container mode lists series from a real Bio-Formats container, lets you tick one series and untick another, saves only ticked TIFF outputs, and records series index/name in `Macro_Builder_Batch_Run.csv`.
- `Run as batch...` container mode closes temporary Bio-Formats images after the run.
- `Test counts...` opens after a macro is built or recorded.
- `Test counts...` can run `2D particles` with `Auto threshold shootout`.
- `Test counts...` can run `3D stack objects` on a stack with `Fixed numeric threshold`.
- Fixed numeric thresholds use the processed macro output's native intensity scale; on a 16-bit processed image, `2000` means intensity `2000`, not a `0-255` remap.
- `Export CSV...` writes the single-image count table.
- `Test counts...` > `Run batch...` writes a batch count CSV for at least two ordinary image files.
- `Save batch macro...` writes one self-contained wrapper `.ijm`, and the wrapper produces a CSV when run on a tiny folder.
- The source image remains unchanged after preview, run, count testing, mask preview, batch testing, and saved batch macro checks.
- A microscope container format opens through Bio-Formats if you have a test file.

## 3. Configure The Update Site

In Fiji:

1. Open `Help > Update...`.
2. Click `Manage update sites`.
3. Click `Add Unlisted Site`.
4. Set `Name` to `Macro-Builder`.
5. Set `URL` to `https://sites.imagej.net/Macro-Builder/`.
6. Set `Host` to `webdav:<upload-username>`.
7. Apply and close the update-site manager.

If the upload action is missing, resolve pending Fiji downloads first, or mark unrelated pending changes as `Keep as-is`.

## 4. Upload

Use the manual GitHub Actions workflow when possible:

1. Open GitHub Actions in the public repository.
2. Run `Upload To Fiji Update Site`.
3. Keep `dry_run=true` for a simulation, or set `dry_run=false` for a real upload.

See [GitHub Actions release automation](GITHUB_ACTIONS_RELEASE.md).

Manual Fiji upload is still available:

1. Make sure `Macro_Builder-0.2.2.jar` is in the local Fiji `plugins/` folder.
2. Open the updater's `Advanced Mode`.
3. For a first upload, choose `View local-only files`.
4. Select `plugins/Macro_Builder-0.2.2.jar`.
5. Choose `Upload to Macro-Builder`.
6. Click `Apply Changes (upload)`.

If the updater asks about dependencies, only upload dependencies that are not already part of Fiji. This plugin should not need extra dependency jars for the first release.

Do not upload core Fiji/ImageJ jars such as `jars/ij.jar`, Bio-Formats jars, or project plugins from the FLASH workspace. Macro Builder treats Fiji's ImageJ API as provided by the user installation.

## 5. Stale Remote Lock

If upload fails with:

```text
Could not obtain lock for db.xml.gz.lock
```

check whether the remote lock exists:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\clear-update-site-lock.ps1 -CheckOnly
```

If it exists, clear it with the ImageJ WebDAV upload password:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\clear-update-site-lock.ps1
```

Then restart Fiji, reopen the updater, and upload `plugins/Macro_Builder-0.2.2.jar` again.

## 6. Clean Install Verification

Use a separate clean Fiji install:

1. Add the unlisted `Macro-Builder` update site URL.
2. Apply changes and restart Fiji.
3. Confirm the menu entry appears.
4. Open the plugin and repeat the basic image/container smoke test.

## 7. Release Tag

After the uploaded build is verified:

```powershell
git tag v0.2.2
git push origin v0.2.2
```
