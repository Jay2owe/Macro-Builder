package macro.builder.ui.sandbox;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RecorderParameterProbeTest {

    @Test
    public void keepsRecordedExecutableCommandAndOptions() {
        RecorderParameterProbe.ProbeResult result =
                RecorderParameterProbe.parseLastRunLine(
                        "run(\"Convolver...\", \"text1=[1 0 -1] normalize\");",
                        "Convolve...");

        assertFalse(result.userCancelled);
        assertEquals("Convolver...", result.commandName);
        assertEquals("text1=[1 0 -1] normalize", result.optionsString);
    }

    @Test
    public void reportsCancellationWhenRecorderProducedNoRunLine() {
        RecorderParameterProbe.ProbeResult result =
                RecorderParameterProbe.parseLastRunLine("// no command", "Convolve...");

        assertTrue(result.userCancelled);
        assertEquals("Convolve...", result.commandName);
    }
}
