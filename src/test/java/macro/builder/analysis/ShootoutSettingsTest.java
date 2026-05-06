package macro.builder.analysis;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class ShootoutSettingsTest {

    @Test
    public void parsesCommaSeparatedFixedThresholds() {
        List<Double> thresholds = ShootoutSettings.parseFixedThresholds("2000, 5000");

        assertEquals(2, thresholds.size());
        assertEquals(2000.0, thresholds.get(0).doubleValue(), 0.0001);
        assertEquals(5000.0, thresholds.get(1).doubleValue(), 0.0001);
    }

    @Test
    public void parsesSingleFixedThreshold() {
        List<Double> thresholds = ShootoutSettings.parseFixedThresholds("2000");

        assertEquals(1, thresholds.size());
        assertEquals(2000.0, thresholds.get(0).doubleValue(), 0.0001);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBlankFixedThresholdEntry() {
        ShootoutSettings.parseFixedThresholds("2000,,5000");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsTrailingCommaInFixedThresholds() {
        ShootoutSettings.parseFixedThresholds("2000,");
    }
}
