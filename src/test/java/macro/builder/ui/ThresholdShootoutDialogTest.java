package macro.builder.ui;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import macro.builder.analysis.ObjectCounter;
import macro.builder.analysis.ShootoutResult;
import macro.builder.analysis.ShootoutSettings;
import macro.builder.analysis.TestCountsManifest;
import org.junit.Assume;
import org.junit.Test;

import javax.swing.JDialog;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ThresholdShootoutDialogTest {

    @Test
    public void sidecarApplyUsesDialogSettingsPath() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());

        final Throwable[] failure = new Throwable[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                ThresholdShootoutDialog dialog = null;
                try {
                    final ShootoutSettings[] applied = new ShootoutSettings[1];
                    ImagePlus source = new ImagePlus("sample.tif", new ByteProcessor(2, 2));
                    ShootoutSettings settings = new ShootoutSettings(
                            ShootoutSettings.CountingMode.OBJECTS_3D,
                            ShootoutSettings.ThresholdMode.AUTO_AND_FIXED,
                            Arrays.asList("Otsu", "Triangle"),
                            Arrays.asList(Double.valueOf(42.0), Double.valueOf(84.0)),
                            12,
                            3.0,
                            99.0,
                            false,
                            Collections.singletonList(Integer.valueOf(2)),
                            null,
                            false,
                            Collections.singletonList(new int[]{1, 2, 3}));
                    ShootoutResult chosen = ShootoutResult.success(
                            ShootoutResult.Source.FIXED,
                            ShootoutSettings.CountingMode.OBJECTS_3D,
                            "Fixed 42",
                            Double.valueOf(42.0),
                            0.0,
                            255.0,
                            null,
                            new ObjectCounter.CountSummary(5, 2.0, 10.0, 0.25))
                            .withRecommendation("chosen");
                    TestCountsManifest manifest = TestCountsManifest.builder()
                            .pluginVersion("test")
                            .fijiVersion("headless")
                            .timestamp(Instant.parse("2026-05-16T12:00:00Z"))
                            .imageSource(TestCountsManifest.SourceRef.inMemory("sample.tif"))
                            .settings(settings)
                            .results(Collections.singletonList(chosen))
                            .chosenVariant(chosen)
                            .build();

                    dialog = new ThresholdShootoutDialog(
                            null,
                            source,
                            "run(\"Duplicate...\");",
                            null,
                            null,
                            1,
                            new ThresholdShootoutDialog.SettingsListener() {
                                @Override public void settingsChanged(ShootoutSettings settings) {
                                    applied[0] = settings;
                                }
                            },
                            null);
                    dialog.applySidecarManifest(
                            manifest,
                            null,
                            null,
                            new File("loaded.testcounts.json"),
                            null);

                    assertNotNull(applied[0]);
                    assertEquals(ShootoutSettings.CountingMode.OBJECTS_3D, applied[0].countingMode);
                    assertEquals(ShootoutSettings.ThresholdMode.AUTO_AND_FIXED, applied[0].thresholdMode);
                    assertEquals(Arrays.asList("Otsu", "Triangle"), applied[0].autoMethods);
                    assertEquals(Arrays.asList(Double.valueOf(42.0), Double.valueOf(84.0)),
                            applied[0].fixedThresholds);
                    assertEquals(12, applied[0].gridSteps);
                    assertEquals(3.0, applied[0].minSize, 0.0);
                    assertEquals(99.0, applied[0].maxSize, 0.0);
                    assertEquals(false, applied[0].darkBackground);
                    assertEquals(false, applied[0].runFragilityChecks);
                    assertEquals(1, applied[0].clickPoints.size());
                    assertEquals(1, applied[0].clickPoints.get(0)[0]);
                    assertEquals(2, applied[0].clickPoints.get(0)[1]);
                    assertEquals(3, applied[0].clickPoints.get(0)[2]);

                    JTable table = (JTable) field(dialog, "table");
                    assertEquals(0, table.getSelectedRow());
                    assertEquals("Fixed 42", table.getValueAt(table.getSelectedRow(), 0));
                } catch (Throwable ex) {
                    failure[0] = ex;
                } finally {
                    if (dialog != null) {
                        try {
                            ((JDialog) field(dialog, "dialog")).dispose();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        });

        if (failure[0] != null) {
            if (failure[0] instanceof Exception) {
                throw (Exception) failure[0];
            }
            throw (Error) failure[0];
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
