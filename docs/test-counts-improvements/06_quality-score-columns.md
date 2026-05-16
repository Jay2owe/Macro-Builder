# Two plain-language quality columns

## Why this stage exists

Even without ground truth, the histogram itself can tell us whether a threshold separates the bright stuff from the dark stuff cleanly. This stage adds two columns that score every variant on this property. The maths is standard (weighted within-class variance and entropy sum) but the UI does not say either of those words — the columns are labelled "Separation" and "Distinctness".

## Prerequisites

- `01_foundation-perf-refactor` complete.
- `05_ground-truth-scoring` complete, so the result constructor and CSV writer already have the optional `precision`, `recall`, and `f1` fields that these columns append after.
- Depends on stage 01 for `ShootoutContext.histogram`, `rangeMin`, and `rangeMax`.

## Read first

- `src/main/java/macro/builder/analysis/ThresholdShootoutRunner.java` after stage 01 (cached histogram is on `ShootoutContext`)
- `src/main/java/macro/builder/analysis/ShootoutResult.java` after stage 05
- CellProfiler `ApplyThreshold` source for reference on the formulas (do not copy code, BSD-3): <https://github.com/CellProfiler/CellProfiler>

## Scope

- Add two new fields to `ShootoutResult`: `separationScore` and `distinctnessScore`. Both are double in 0..1 where higher is better.
- Add two new columns to the results table: "Separation" and "Distinctness". Default-hidden behind a checkbox "Show quality columns" in the settings panel (off by default to keep the UI calm).
- Compute both scores from the cached histogram and the variant's threshold value. No new image passes.
- Tooltip on the "Separation" header: "How cleanly this threshold splits the bright and dim parts of the image. 0 means total overlap; 1 means perfectly separated."
- Tooltip on the "Distinctness" header: "How different the two groups look as distributions. 0 means identical; 1 means as distinct as possible."

## Out of scope

- Combining the two into a single "quality" score (let the user weigh them).
- Changing how the recommendation is picked; leave the stage 02 plateau or stage 05 reference-winner decision unchanged.

## Files touched

| path | action | reason |
|---|---|---|
| `src/main/java/macro/builder/analysis/HistogramQualityScorer.java` | NEW | Pure functions: `separation(int[] histogram, double threshold, ShootoutContext ctx)` and `distinctness(...)`. |
| `src/main/java/macro/builder/analysis/ShootoutResult.java` | MODIFY | Add `separationScore`, `distinctnessScore`. |
| `src/main/java/macro/builder/analysis/ThresholdShootoutRunner.java` | MODIFY | After each variant, call the scorer with the cached histogram. |
| `src/main/java/macro/builder/ui/ThresholdShootoutDialog.java` | MODIFY | Show-quality-columns checkbox; column hide/show; tooltips. |
| `src/test/java/macro/builder/analysis/HistogramQualityScorerTest.java` | NEW | Canonical histograms: bimodal Gaussian (high separation), uniform (low separation), single Gaussian (low distinctness). |

## Implementation sketch

Separation (Otsu's between-class variance, normalised by total variance):

```java
public static double separation(int[] h, double threshold, ShootoutContext ctx) {
    int bin = binFor(threshold, ctx);
    long n0 = sum(h, 0, bin);
    long n1 = sum(h, bin, h.length);
    if (n0 == 0 || n1 == 0) return 0.0;
    double mu0 = mean(h, 0, bin);
    double mu1 = mean(h, bin, h.length);
    double muAll = mean(h, 0, h.length);
    double between = ((double) n0 * (mu0 - muAll) * (mu0 - muAll)
                    + (double) n1 * (mu1 - muAll) * (mu1 - muAll));
    double total = totalVariance(h, muAll);
    return total <= 0 ? 0.0 : Math.min(1.0, between / total);
}
```

Distinctness (entropy sum, normalised by log of bin count):

```java
public static double distinctness(int[] h, double threshold, ShootoutContext ctx) {
    int bin = binFor(threshold, ctx);
    double e0 = entropy(h, 0, bin);
    double e1 = entropy(h, bin, h.length);
    return Math.min(1.0, (e0 + e1) / (2.0 * Math.log(h.length)));
}
```

CSV export adds two columns after the ground-truth columns from stage 05: `separation`, `distinctness`. See `00_overview.md` "CSV column order (cumulative)".

Threading model:

- `HistogramQualityScorer` runs inside the shootout worker after each row has a threshold value.
- It reads only `ShootoutContext.histogram` and numeric row data; it does not touch Swing or ImageJ windows.
- The show/hide checkbox and table-column updates run on the EDT without re-running the sweep.

## Exit gate

1. All existing and new tests pass with `.\mvnw.cmd test "-Denforcer.skip=true"`.
2. `HistogramQualityScorerTest` exercises the new scoring functions on bimodal, uniform, single-Gaussian, and empty histograms.
3. On a synthetic image with two well-separated Gaussians, the variant whose threshold sits in the valley scores separation > 0.95.
4. On a uniform-distribution synthetic image, no variant scores separation > 0.2.
5. The "Show quality columns" checkbox toggles column visibility within one click and does not trigger another runner invocation.
6. CSV export always includes `separation` and `distinctness` in the order locked in `00_overview.md`, even when the user hides the table columns; numbers use `Locale.ROOT`.

## Known risks

- Normalisation of distinctness is arbitrary. Mitigation: document the formula in the tooltip as `normalised by log of bin count` and keep the score informational only.
- Float-processor histograms have arbitrary bin widths and may have skipped `NaN`/infinite pixels from stage 01. Mitigation: accept the binned histogram from `ShootoutContext`, never recompute pixels here, and test non-finite inputs through a synthetic context.
- Empty or single-bin histograms can divide by zero. Mitigation: return `0.0` for both scores when either side of the threshold is empty or total variance is zero.
- Locale-dependent CSV writing can emit comma decimals. Mitigation: write `separation` and `distinctness` with `Locale.ROOT` and add a comma-locale test.
- The two scores are not independent of each other on typical microscopy images; the user might wonder why they sometimes disagree. Mitigation: add a one-line explainer to the user guide and avoid combining them into a single recommendation.
