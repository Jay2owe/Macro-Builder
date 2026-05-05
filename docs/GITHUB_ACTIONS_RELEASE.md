# GitHub Actions Release Automation

Macro Builder has two GitHub Actions workflows:

- `CI`: builds and tests the plugin on pushes, pull requests, tags, and manual runs. It uploads the built jar as a GitHub Actions artifact.
- `Upload To Fiji Update Site`: manually builds the plugin, downloads a clean Fiji install, installs the built jar into `Fiji.app/plugins`, and runs the Fiji updater command-line upload.

The upload workflow is manual only. Normal pushes do not publish to Fiji.

## Required Secret

Add this repository secret before running the upload workflow:

```text
IMAGEJ_UPLOAD_PASSWORD
```

Set it to the ImageJ WebDAV upload password for the `Jay2owe` upload account.

Optional repository secret:

```text
IMAGEJ_UPLOAD_USER
```

If omitted, the workflow uses `Jay2owe`.

## Recommended Environment Gate

Create a GitHub Actions environment named:

```text
fiji-update-site
```

Add a required reviewer for that environment. This makes accidental update-site uploads harder, because the workflow pauses for approval before using the upload secret.

## Running A Dry Run

1. Open GitHub Actions.
2. Select `Upload To Fiji Update Site`.
3. Click `Run workflow`.
4. Leave `dry_run` enabled.
5. Leave `clear_stale_lock` disabled unless the site is currently locked.

The dry run builds the jar and asks Fiji's updater to simulate the upload.

## Running A Real Upload

1. Confirm the local manual smoke test has passed.
2. Open GitHub Actions.
3. Select `Upload To Fiji Update Site`.
4. Click `Run workflow`.
5. Set `dry_run` to `false`.
6. Set `clear_stale_lock` to `true` only if a previous failed upload left `db.xml.gz.lock`.

The workflow uploads only:

```text
plugins/Macro_Builder-*.jar
```

It uses `--forget-missing-dependencies` because Fiji already provides ImageJ core jars.

## Failure Modes

- Missing secret: add `IMAGEJ_UPLOAD_PASSWORD`.
- Remote lock exists: rerun with `clear_stale_lock=true`, or clear it locally with `scripts/clear-update-site-lock.ps1`.
- Upload says dependencies are missing: check that `pom.xml` still marks `net.imagej:ij` as `provided` and that the jar does not contain `META-INF/maven/.../pom.xml`.
