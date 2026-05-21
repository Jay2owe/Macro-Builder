package macro.builder.ui;

public final class PreviewDisplaySettings {

    public enum LutMode {
        GREY,
        USER
    }

    private final double displayMin;
    private final double displayMax;
    private final LutMode lutMode;

    private PreviewDisplaySettings(double displayMin, double displayMax, LutMode lutMode) {
        this.displayMin = displayMin;
        this.displayMax = displayMax;
        this.lutMode = lutMode == null ? LutMode.USER : lutMode;
    }

    public static PreviewDisplaySettings of(double displayMin, double displayMax, LutMode lutMode) {
        return new PreviewDisplaySettings(displayMin, displayMax, lutMode);
    }

    public static PreviewDisplaySettings defaultFor(LutMode lutMode) {
        return new PreviewDisplaySettings(Double.NaN, Double.NaN, lutMode);
    }

    public boolean hasDisplayRange() {
        return Double.isFinite(displayMin) && Double.isFinite(displayMax) && displayMax > displayMin;
    }

    public double getDisplayMin() {
        return displayMin;
    }

    public double getDisplayMax() {
        return displayMax;
    }

    public LutMode getLutMode() {
        return lutMode;
    }

    public PreviewDisplaySettings withDisplayRange(double displayMin, double displayMax) {
        return new PreviewDisplaySettings(displayMin, displayMax, lutMode);
    }

    public PreviewDisplaySettings withLutMode(LutMode lutMode) {
        return new PreviewDisplaySettings(displayMin, displayMax, lutMode);
    }
}
