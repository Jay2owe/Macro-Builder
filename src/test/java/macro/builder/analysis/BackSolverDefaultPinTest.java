package macro.builder.analysis;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class BackSolverDefaultPinTest {

    @Test
    public void spreadCheckRejectsCountMoreThanTwentyFivePercentAboveMedian() {
        ImagePlus image = twelveSpotImage();
        ShootoutContext context = new ShootoutContext(image, new int[256], 1.0, 151.0, false);

        BackSolver.BackSolverResult solved = BackSolver.solve(
                context,
                clicksOnNineHighSpotsAndOneLowSpot(),
                settings(),
                3);

        assertEquals(0.25, BackSolver.SPREAD_FRACTION, 0.0);
        assertEquals(76.0, solved.threshold, 0.0);
        assertEquals(9, solved.caughtClicks);
        assertEquals(10, solved.clickedObjects);
        assertEquals(9.0, solved.medianCount, 0.0);
        assertEquals(9, solved.countSummary.count);
        assertFalse(solved.spreadFallback);
    }

    private static ShootoutSettings settings() {
        return new ShootoutSettings(
                ShootoutSettings.CountingMode.PARTICLES_2D,
                ShootoutSettings.ThresholdMode.AUTO_GRID,
                Collections.<String>emptyList(),
                Collections.<Double>emptyList(),
                ShootoutSettings.DEFAULT_GRID_STEPS,
                0.0,
                Double.POSITIVE_INFINITY,
                true);
    }

    private static ImagePlus twelveSpotImage() {
        ByteProcessor processor = new ByteProcessor(36, 12);
        int[][] starts = spotStarts();
        for (int i = 0; i < starts.length; i++) {
            fillSquare(processor, starts[i][0], starts[i][1], i < 9 ? 151 : 25);
        }
        return new ImagePlus("spots", processor);
    }

    private static List<int[]> clicksOnNineHighSpotsAndOneLowSpot() {
        int[][] starts = spotStarts();
        List<int[]> clicks = new ArrayList<int[]>();
        for (int i = 0; i < 9; i++) {
            clicks.add(new int[]{starts[i][0] + 1, starts[i][1] + 1, 1});
        }
        clicks.add(new int[]{starts[9][0] + 1, starts[9][1] + 1, 1});
        return clicks;
    }

    private static int[][] spotStarts() {
        return new int[][]{
                {1, 1}, {7, 1}, {13, 1}, {19, 1}, {25, 1}, {31, 1},
                {1, 7}, {7, 7}, {13, 7}, {19, 7}, {25, 7}, {31, 7}
        };
    }

    private static void fillSquare(ByteProcessor processor, int startX, int startY, int value) {
        for (int y = startY; y < startY + 2; y++) {
            for (int x = startX; x < startX + 2; x++) {
                processor.set(x, y, value);
            }
        }
    }
}
