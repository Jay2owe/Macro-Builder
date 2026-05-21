package macro.builder.analysis;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BackSolverTest {

    @Test
    public void choosesGridThresholdThatCatchesClicksWithPlausibleCount() {
        ImagePlus image = twelveSpotImage(0);
        ShootoutContext context = new ShootoutContext(image, new int[256], 0.0, 200.0, false);
        ShootoutSettings settings = settings();
        List<int[]> clicks = clicksAtFirstSixSpots();

        BackSolver.BackSolverResult solved = BackSolver.solve(context, clicks, settings);
        ShootoutResult row = BackSolver.toShootoutResult(
                solved,
                context,
                settings.withClickPoints(clicks));

        assertEquals(6, solved.caughtClicks);
        assertEquals(6, solved.clickedObjects);
        assertTrue(solved.countSummary.count >= 9);
        assertTrue(solved.countSummary.count <= 15);
        assertFalse(solved.spreadFallback);
        assertFalse(solved.reason.contains("manual verification recommended"));
        assertEquals(ShootoutResult.Source.CLICK_FIT, row.source);
        assertEquals("Click-fit", row.variant);
        assertTrue(row.recommended);
        assertEquals(solved.reason, row.recommendationReason);
    }

    @Test
    public void warnsWhenSpreadCheckRejectsEveryGridPoint() {
        ImagePlus image = twelveSpotImage(100);
        ShootoutContext context = new ShootoutContext(image, new int[256], 0.0, 200.0, false);

        BackSolver.BackSolverResult solved = BackSolver.solve(
                context,
                clicksAtFirstSixSpots(),
                settings());

        assertEquals(6, solved.caughtClicks);
        assertTrue(solved.spreadFallback);
        assertTrue(solved.reason.contains("count varies a lot; manual verification recommended"));
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

    private static ImagePlus twelveSpotImage(int background) {
        ByteProcessor processor = new ByteProcessor(80, 60);
        for (int y = 0; y < processor.getHeight(); y++) {
            for (int x = 0; x < processor.getWidth(); x++) {
                processor.set(x, y, background);
            }
        }
        if (background > 0) {
            processor.set(0, 0, 0);
        }
        int[][] starts = spotStarts();
        for (int i = 0; i < starts.length; i++) {
            fillSquare(processor, starts[i][0], starts[i][1], 200);
        }
        return new ImagePlus("spots", processor);
    }

    private static List<int[]> clicksAtFirstSixSpots() {
        int[][] starts = spotStarts();
        List<int[]> clicks = new ArrayList<int[]>();
        for (int i = 0; i < 6; i++) {
            clicks.add(new int[]{starts[i][0] + 1, starts[i][1] + 1, 1});
        }
        return clicks;
    }

    private static int[][] spotStarts() {
        return new int[][]{
                {5, 5}, {20, 5}, {35, 5}, {50, 5},
                {5, 25}, {20, 25}, {35, 25}, {50, 25},
                {5, 45}, {20, 45}, {35, 45}, {50, 45}
        };
    }

    private static void fillSquare(ByteProcessor processor, int startX, int startY, int value) {
        for (int y = startY; y < startY + 3; y++) {
            for (int x = startX; x < startX + 3; x++) {
                processor.set(x, y, value);
            }
        }
    }
}
