package macro.builder.api;

import ij.ImagePlus;
import macro.builder.image.variation.VariantPlan;
import macro.builder.image.variation.VariantResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MacroBuilderVariationResult {

    private final List<VariantPlan> plans;
    private final List<VariantResult> results;

    MacroBuilderVariationResult(List<VariantPlan> plans, List<VariantResult> results) {
        this.plans = plans == null
                ? Collections.<VariantPlan>emptyList()
                : Collections.unmodifiableList(new ArrayList<VariantPlan>(plans));
        this.results = results == null
                ? Collections.<VariantResult>emptyList()
                : Collections.unmodifiableList(new ArrayList<VariantResult>(results));
    }

    public List<VariantPlan> plans() {
        return plans;
    }

    public List<VariantResult> results() {
        return results;
    }

    public int successCount() {
        int count = 0;
        for (VariantResult result : results) {
            if (result != null && result.isSuccess()) {
                count++;
            }
        }
        return count;
    }

    public int failureCount() {
        return results.size() - successCount();
    }

    public boolean successful() {
        return failureCount() == 0;
    }

    public List<ImagePlus> successfulOutputs() {
        List<ImagePlus> outputs = new ArrayList<ImagePlus>();
        for (VariantResult result : results) {
            if (result != null && result.isSuccess()) {
                outputs.add(result.output);
            }
        }
        return Collections.unmodifiableList(outputs);
    }

    /**
     * Release successful variant output images. Call this when the caller does
     * not intend to show, save, or otherwise retain the returned ImagePlus data.
     */
    public void closeOutputs() {
        for (ImagePlus output : successfulOutputs()) {
            if (output != null) {
                output.changes = false;
                if (output.getWindow() != null) {
                    output.close();
                } else {
                    output.flush();
                }
            }
        }
    }
}
