package macro.builder.analysis;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PlateauFinderTest {

    @Test
    public void cleanPlateauReturnsMidpoint() {
        assertEquals(3, PlateauFinder.findPlateauIndex(
                thresholds(7),
                new int[]{120, 80, 50, 50, 50, 50, 20}));
    }

    @Test
    public void monotonicDecayReturnsNoPlateau() {
        assertEquals(-1, PlateauFinder.findPlateauIndex(
                thresholds(8),
                new int[]{100, 90, 80, 70, 60, 50, 40, 30}));
    }

    @Test
    public void twoPlateausReturnsLongerPlateauMidpoint() {
        assertEquals(6, PlateauFinder.findPlateauIndex(
                thresholds(10),
                new int[]{100, 70, 70, 70, 50, 30, 30, 30, 30, 10}));
    }

    @Test
    public void noisyPlateauStillReturnsMidpoint() {
        assertEquals(4, PlateauFinder.findPlateauIndex(
                thresholds(10),
                new int[]{100, 78, 59, 60, 58, 59, 57, 56, 30, 10}));
    }

    @Test
    public void stableRegionShorterThanThreePointsReturnsNoPlateau() {
        assertEquals(-1, PlateauFinder.findPlateauIndex(
                thresholds(4),
                new int[]{100, 50, 50, 10}));
    }

    private static double[] thresholds(int count) {
        double[] values = new double[count];
        for (int i = 0; i < count; i++) {
            values[i] = i;
        }
        return values;
    }
}
