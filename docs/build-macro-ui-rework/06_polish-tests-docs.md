# Polish, Tests, and Documentation

## Why this stage exists

The earlier stages change several pieces of UI behavior that users will notice immediately. This final stage tightens rough edges, expands focused tests, updates user-facing docs, and verifies the full Fiji workflow end to end.

## Prerequisites

- `docs/build-macro-ui-rework/01_catalog-categories_COMPLETED.md`
- `docs/build-macro-ui-rework/02_sandbox-layout-preview_COMPLETED.md`
- `docs/build-macro-ui-rework/03_inline-step-editing_COMPLETED.md`
- `docs/build-macro-ui-rework/04_inline-merge-editing_COMPLETED.md`
- `docs/build-macro-ui-rework/05_branch-multiselect-merge_COMPLETED.md`

## Read first

- `docs/build-macro-ui-rework/00_overview.md`
- `AGENTS.md`
- `README.md:28-34` for current high-level workflow wording.
- `docs/USER_GUIDE.md:29-50` for Build Macro and preview/count documentation.
- `docs/DEVELOPER.md:17-30` for source layout notes.
- `src/test/java/macro/builder/image/dag/DagIRRoundTripTest.java:13-49` for existing DAG tests.
- `src/main/java/macro/builder/ui/sandbox` after stages 01-05 are complete.

## Scope

- Add or refine focused tests introduced by stages 01-05.
- Update `README.md` and `docs/USER_GUIDE.md` so users know the builder has grouped steps, row `+` buttons, inline parameter editing, embedded previews, and branch multi-select merge.
- Update `docs/DEVELOPER.md` if the sandbox UI class responsibilities changed.
- Do a final pass for cramped Swing layouts, unclear button labels, stale help text, and unused classes/imports.
- Run the full automated test suite.
- Perform a manual Fiji smoke check if local Fiji is available.

## Out of scope

- Do not add new builder features beyond polishing the completed stages.
- Do not publish to GitHub, GitHub Actions, or a Fiji update site.
- Do not deploy unless the user explicitly asks for `deploy`.
- Do not redesign the main launcher outside text/docs needed to reflect the builder changes.

## Files touched

| path | action | reason |
| --- | --- | --- |
| `src/test/java/macro/builder/ui/sandbox/FilterCatalogTest.java` | MODIFY | Ensure grouped catalog behavior is covered. |
| `src/test/java/macro/builder/ui/sandbox/SandboxModelTest.java` | MODIFY | Ensure multi-selection and merge-selected behavior is covered. |
| `src/test/java/macro/builder/image/dag/DagIRRoundTripTest.java` | MODIFY | Ensure multi-input combiners round-trip and emit correctly. |
| `README.md` | MODIFY | Update Build Macro workflow summary. |
| `docs/USER_GUIDE.md` | MODIFY | Document grouped add buttons, inline editing, embedded previews, and branch merge selection. |
| `docs/DEVELOPER.md` | MODIFY | Update sandbox UI class responsibility notes if needed. |
| `src/main/java/macro/builder/ui/sandbox/*.java` | MODIFY | Final UI polish, stale help text cleanup, and unused class removal if needed. |

## Implementation sketch

Suggested test coverage:

```java
@Test public void catalogGroups3dCommands() { ... }
@Test public void catalogGroupsPluginCommandsFromPluginsMenu() { ... }
@Test public void ctrlClickTogglesSelectedBranches() { ... }
@Test public void shiftClickSelectsBranchRange() { ... }
@Test public void mergeSelectedBranchesUsesVisualOrder() { ... }
@Test public void dagRoundTripsCombinerWithThreeInputs() { ... }
```

Update the sandbox help text in `SandboxDialog.showSandboxHelp()` so it describes the new workflow:

```text
Use the + buttons in the grouped step boxes to add commands to the selected branch.
Double-click or right-click a step to edit parameters.
Ctrl-click or Shift-click branches, then use Merge selected branches.
Use preview buttons to update the embedded preview.
```

Run:

```powershell
.\mvnw.cmd -q test
.\mvnw.cmd -q -DskipTests compile
```

Manual Fiji smoke check:

1. Launch Fiji and open `Plugins > Macro Builder > Macro Builder`.
2. Select or open an image.
3. Open `Build Macro`.
4. Add one filter, one 3D step, one binary step, and one image type conversion with row `+` buttons.
5. Double-click a step and change a parameter.
6. Preview up to a selected step and preview the full filter; both should update embedded output preview.
7. Add at least three branches, Ctrl/Shift select two or more, and merge selected branches.
8. Save the macro, reopen it with `Edit Macro...`, and confirm the DAG reloads.

## Exit gate

1. `.\mvnw.cmd -q test` passes.
2. `.\mvnw.cmd -q -DskipTests compile` passes.
3. README and user guide describe the new builder workflow in plain language.
4. Developer docs reflect the current sandbox class split if it changed.
5. Manual Fiji smoke check passes, or the reason it could not be run is documented in the final report.
6. No unrelated dirty files were reverted or overwritten.

## Known risks

- Swing UI behavior is hard to fully unit test. Keep model and parsing behavior covered by automated tests, then document manual checks clearly.
- If a dialog class cannot be tested headlessly, move validation logic into a package-private helper and test that helper instead.
- Existing saved macros must keep loading. Include at least one manual or automated check using an emitted embedded DAG macro.
