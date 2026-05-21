package macro.builder.api;

import ij.ImagePlus;
import macro.builder.image.dag.DagIR;
import macro.builder.image.dag.IjmToDagLoader;
import macro.builder.image.variation.ProgressCallback;
import macro.builder.image.variation.VariantAxis;
import macro.builder.image.variation.VariantPlan;
import macro.builder.image.variation.VariantSampler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MacroBuilderVariationParameters {

    public enum SamplingMode {
        ONE_FACTOR_AT_A_TIME,
        CARTESIAN,
        EXPLICIT_PLANS
    }

    public static final int DEFAULT_MAX_VARIANTS = 9;

    private final ImagePlus sourceImage;
    private final DagIR baseline;
    private final List<VariantAxis> axes;
    private final List<VariantPlan> explicitPlans;
    private final SamplingMode samplingMode;
    private final int maxVariants;
    private final ProgressCallback progress;

    private MacroBuilderVariationParameters(Builder builder) {
        if (builder.sourceImage == null) {
            throw new IllegalArgumentException("sourceImage must not be null");
        }
        this.sourceImage = builder.sourceImage;
        this.baseline = builder.baseline;
        this.axes = immutableAxes(builder.axes);
        this.explicitPlans = immutablePlans(builder.explicitPlans);
        this.samplingMode = builder.samplingMode == null
                ? SamplingMode.ONE_FACTOR_AT_A_TIME
                : builder.samplingMode;
        this.maxVariants = builder.maxVariants;
        this.progress = builder.progress;
        validate();
    }

    public static Builder builder() {
        return new Builder();
    }

    public ImagePlus sourceImage() {
        return sourceImage;
    }

    public DagIR baseline() {
        return baseline;
    }

    public List<VariantAxis> axes() {
        return axes;
    }

    public SamplingMode samplingMode() {
        return samplingMode;
    }

    public int maxVariants() {
        return maxVariants;
    }

    public ProgressCallback progress() {
        return progress;
    }

    public List<VariantPlan> plans() {
        if (samplingMode == SamplingMode.EXPLICIT_PLANS) {
            return explicitPlans;
        }
        if (samplingMode == SamplingMode.CARTESIAN) {
            return VariantSampler.cartesian(baseline, axes, maxVariants);
        }
        return VariantSampler.ofat(baseline, axes, maxVariants);
    }

    private void validate() {
        if (maxVariants < 1) {
            throw new IllegalArgumentException("maxVariants must be >= 1");
        }
        if (samplingMode == SamplingMode.EXPLICIT_PLANS) {
            if (explicitPlans.isEmpty()) {
                throw new IllegalArgumentException("explicit plans must not be empty");
            }
            return;
        }
        if (baseline == null) {
            throw new IllegalArgumentException("baseline DAG must not be null");
        }
    }

    private static List<VariantAxis> immutableAxes(List<VariantAxis> axes) {
        if (axes == null || axes.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<VariantAxis>(axes));
    }

    private static List<VariantPlan> immutablePlans(List<VariantPlan> plans) {
        if (plans == null || plans.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<VariantPlan>(plans));
    }

    public static final class Builder {
        private ImagePlus sourceImage;
        private DagIR baseline;
        private final List<VariantAxis> axes = new ArrayList<VariantAxis>();
        private final List<VariantPlan> explicitPlans = new ArrayList<VariantPlan>();
        private SamplingMode samplingMode = SamplingMode.ONE_FACTOR_AT_A_TIME;
        private int maxVariants = DEFAULT_MAX_VARIANTS;
        private ProgressCallback progress;

        private Builder() {
        }

        public Builder sourceImage(ImagePlus sourceImage) {
            this.sourceImage = sourceImage;
            return this;
        }

        public Builder baseline(DagIR baseline) {
            this.baseline = baseline;
            return this;
        }

        public Builder baselineMacro(String macroContent) {
            this.baseline = IjmToDagLoader.load(macroContent);
            return this;
        }

        public Builder axes(List<VariantAxis> axes) {
            this.axes.clear();
            if (axes != null) {
                for (VariantAxis axis : axes) {
                    if (axis != null) {
                        this.axes.add(axis);
                    }
                }
            }
            return this;
        }

        public Builder addAxis(VariantAxis axis) {
            if (axis != null) {
                this.axes.add(axis);
            }
            return this;
        }

        public Builder explicitPlans(List<VariantPlan> plans) {
            this.explicitPlans.clear();
            if (plans != null) {
                for (VariantPlan plan : plans) {
                    if (plan != null) {
                        this.explicitPlans.add(plan);
                    }
                }
            }
            this.samplingMode = SamplingMode.EXPLICIT_PLANS;
            return this;
        }

        public Builder samplingMode(SamplingMode samplingMode) {
            this.samplingMode = samplingMode;
            return this;
        }

        public Builder maxVariants(int maxVariants) {
            this.maxVariants = maxVariants;
            return this;
        }

        public Builder progress(ProgressCallback progress) {
            this.progress = progress;
            return this;
        }

        public MacroBuilderVariationParameters build() {
            return new MacroBuilderVariationParameters(this);
        }
    }
}
