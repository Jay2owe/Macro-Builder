package macro.builder.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MacroBatchCompatibility {

    private static final Pattern SELECT_WINDOW = Pattern.compile(
            "(?i)\\bselectWindow\\s*\\(\\s*\"((?:\\\\.|[^\"])*)\"");
    private static final Pattern WAIT_FOR_USER = Pattern.compile("(?i)\\bwaitForUser\\s*\\(");
    private static final Pattern OPEN_LITERAL = Pattern.compile(
            "(?i)(?:\\bFile\\s*\\.\\s*)?\\bopen\\s*\\(\\s*\"((?:\\\\.|[^\"])*)\"");
    private static final Pattern SAVE_AS_LITERAL = Pattern.compile(
            "(?i)\\bsaveAs\\s*\\(\\s*\"(?:\\\\.|[^\"])*\"\\s*,\\s*\"((?:\\\\.|[^\"])*)\"");
    private static final Pattern CLOSE_ALL = Pattern.compile(
            "(?i)(\\bclose\\s*\\(\\s*\"\\*\"\\s*\\)|\\brun\\s*\\(\\s*\"Close All\"\\s*\\))");

    private MacroBatchCompatibility() {
    }

    public static List<String> warnings(String macro) {
        if (macro == null || macro.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> warnings = new ArrayList<String>();
        String searchable = stripLineComments(macro);
        addSelectWindowWarnings(searchable, warnings);
        if (WAIT_FOR_USER.matcher(searchable).find()) {
            warnings.add("waitForUser pauses for manual input and can stall a batch run.");
        }
        if (OPEN_LITERAL.matcher(searchable).find()) {
            warnings.add("open(\"...\") or File.open(\"...\") uses a fixed input path instead of each batch file.");
        }
        if (SAVE_AS_LITERAL.matcher(searchable).find()) {
            warnings.add("saveAs(\"...\", \"...\") writes to a fixed path and may overwrite files during a batch.");
        }
        if (CLOSE_ALL.matcher(searchable).find()) {
            warnings.add("close(\"*\") or Close All can close batch source or result windows unexpectedly.");
        }
        return Collections.unmodifiableList(warnings);
    }

    private static void addSelectWindowWarnings(String macro, List<String> warnings) {
        Matcher matcher = SELECT_WINDOW.matcher(macro);
        while (matcher.find()) {
            String title = unescape(matcher.group(1));
            if (!looksTemporaryTitle(title)) {
                warnings.add("selectWindow(\"" + title + "\") targets a fixed window title that may not exist in batch.");
            }
        }
    }

    private static boolean looksTemporaryTitle(String title) {
        if (title == null) {
            return true;
        }
        String trimmed = title.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        String lower = trimmed.toLowerCase();
        return lower.startsWith("macro builder ")
                || lower.contains("temporary")
                || lower.contains("temp");
    }

    private static String stripLineComments(String macro) {
        StringBuilder out = new StringBuilder();
        String[] lines = macro.split("\\r?\\n", -1);
        for (String line : lines) {
            boolean inString = false;
            boolean escaping = false;
            int end = line.length();
            for (int i = 0; i < line.length() - 1; i++) {
                char c = line.charAt(i);
                if (escaping) {
                    escaping = false;
                    continue;
                }
                if (c == '\\' && inString) {
                    escaping = true;
                    continue;
                }
                if (c == '"') {
                    inString = !inString;
                    continue;
                }
                if (!inString && c == '/' && line.charAt(i + 1) == '/') {
                    end = i;
                    break;
                }
            }
            out.append(line, 0, end).append('\n');
        }
        return out.toString();
    }

    private static String unescape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        boolean escaping = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!escaping) {
                if (c == '\\') {
                    escaping = true;
                } else {
                    out.append(c);
                }
                continue;
            }
            out.append(c);
            escaping = false;
        }
        if (escaping) {
            out.append('\\');
        }
        return out.toString();
    }
}
