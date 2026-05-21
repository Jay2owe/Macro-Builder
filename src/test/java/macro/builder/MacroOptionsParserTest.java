package macro.builder;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MacroOptionsParserTest {

    @Test
    public void parsesBracketedValuesAndFlags() {
        MacroOptionsParser.Options options = MacroOptionsParser.parse(
                "macro=[C:\\Data Folder\\filter.ijm] input=[C:/Input Folder] "
                        + "output=[C:/Out] recursive csv=[summary file.csv]");

        assertEquals("C:\\Data Folder\\filter.ijm", options.get("macro"));
        assertEquals("C:/Input Folder", options.get("input"));
        assertEquals("C:/Out", options.get("output"));
        assertEquals("summary file.csv", options.get("csv"));
        assertTrue(options.hasFlag("recursive"));
        assertTrue(options.booleanOption("recursive", false));
    }

    @Test
    public void formatsPathsWithForwardSlashesForMacroOptions() {
        String option = MacroOptionsParser.bracketedOption("input",
                new File("C:\\Data Folder\\image 1.tif"));

        assertTrue(option.startsWith("input=["));
        assertTrue(option.endsWith("]"));
        assertTrue(option.indexOf('\\') < 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnclosedBracketedValue() {
        MacroOptionsParser.parse("input=[C:/Data output=[C:/Out]");
    }
}
