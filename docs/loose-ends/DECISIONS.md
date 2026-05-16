# Loose-ends — Design decisions (post-MVP)

Tracks the four open questions from docs/test-counts-improvements_COMPLETED/00_overview.md (the "Known open questions" section). Each entry below records the current default in code, what evidence would justify changing it, and the resolution.

## 4a. Ground-truth matching rule
- Current default: centroid-in-mask for point ROIs; IoU >= 0.5 for area ROIs (GroundTruthScorer).
- Evidence required: precision/recall/F1 on one real microscope image + RoiSet.zip, compared with centroid-only-for-all and IoU-only-for-all alternatives.
- Status: Pending real-data validation by user.

## 4b. Sidecar schema versioning
- Policy: schema version 1 is the shipped schema. Additive fields bump to 2; renamed or removed fields bump to 3+. Readers must accept versionRead <= versionCurrent. Writers always stamp versionCurrent. Pinned in TestCountsManifest.SCHEMA_VERSION.
- Status: RESOLVED. Written into docs/test-counts-improvements_COMPLETED/00_overview.md as "Sidecar schema policy".

## 4c. 3D live-slider slice policy
- Current default: preview shows the active slice (imp.getSlice()) and a slice scrubber sits next to the threshold slider. Pin captures the full-stack count.
- Evidence required: exercise the slider on a real 3D microscope stack, confirm all three behaviours hold.
- Status: Pending real-data validation by user.

## 4d. Back-solver spread check
- Current default: winning variant must catch >= 90% of clicks AND have a count within +/- 25% of the median variant count (BackSolver).
- Evidence required: click-to-mark runs with 5, 20, 50 marks on one real microscope image. Confirm rule fires correctly in over-fit edge cases.
- Status: Pending real-data validation by user.
