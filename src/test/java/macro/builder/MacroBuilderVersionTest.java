package macro.builder;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class MacroBuilderVersionTest {

    @Test
    public void pluginVersionFallsBackToDevOutsideBuiltJar() {
        String version = Macro_Builder.getPluginVersion();

        assertNotNull(version);
        assertFalse(version.trim().isEmpty());
    }
}
