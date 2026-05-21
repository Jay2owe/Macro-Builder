package macro.builder;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Pure parser for ImageJ macro option strings such as
 * {@code input=[C:/data] output=[C:/out] recursive}.
 */
public final class MacroOptionsParser {

    private MacroOptionsParser() {
    }

    public static Options parse(String options) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        Set<String> flags = new LinkedHashSet<String>();
        for (String token : tokens(options)) {
            int equals = token.indexOf('=');
            if (equals < 0) {
                flags.add(token);
                continue;
            }
            String key = token.substring(0, equals).trim();
            if (key.length() == 0) {
                continue;
            }
            String raw = token.substring(equals + 1).trim();
            values.put(key, decodeValue(raw, key));
        }
        return new Options(values, flags);
    }

    public static String bracketedOption(String key, File file) {
        if (file == null) {
            throw new IllegalArgumentException(key + " file must not be null");
        }
        return bracketedOption(key, normalizePath(file));
    }

    public static String bracketedOption(String key, String value) {
        return key + "=[" + requireSafeBracketedValue(value, key) + "]";
    }

    public static String requireSafeBracketedValue(String value, String fieldName) {
        String label = fieldName == null || fieldName.trim().isEmpty()
                ? "Macro option" : fieldName;
        if (value == null) {
            throw new IllegalArgumentException(label + " must not be null");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '[' || c == ']' || c == '"' || Character.isISOControl(c)) {
                throw new IllegalArgumentException(label
                        + " cannot contain brackets, quotes, or line breaks in macro options");
            }
        }
        return value;
    }

    public static String normalizePath(File file) {
        return file.getAbsolutePath().replace(File.separatorChar, '/').replace('\\', '/');
    }

    private static Set<String> tokens(String options) {
        Set<String> tokens = new LinkedHashSet<String>();
        if (options == null || options.trim().isEmpty()) {
            return tokens;
        }
        StringBuilder token = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < options.length(); i++) {
            char c = options.charAt(i);
            if (Character.isWhitespace(c) && depth == 0) {
                addToken(tokens, token);
                continue;
            }
            token.append(c);
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                if (depth == 0) {
                    throw new IllegalArgumentException("Unmatched ']' in macro options");
                }
                depth--;
            }
        }
        if (depth != 0) {
            throw new IllegalArgumentException("Unclosed '[' in macro options");
        }
        addToken(tokens, token);
        return tokens;
    }

    private static void addToken(Set<String> tokens, StringBuilder token) {
        if (token.length() == 0) {
            return;
        }
        tokens.add(token.toString());
        token.setLength(0);
    }

    private static String decodeValue(String raw, String key) {
        if (raw.startsWith("[")) {
            if (!raw.endsWith("]")) {
                throw new IllegalArgumentException("Macro option '" + key
                        + "' has an unclosed bracketed value");
            }
            return raw.substring(1, raw.length() - 1);
        }
        return raw;
    }

    public static final class Options {
        private final Map<String, String> values;
        private final Set<String> flags;

        private Options(Map<String, String> values, Set<String> flags) {
            this.values = Collections.unmodifiableMap(new LinkedHashMap<String, String>(values));
            this.flags = Collections.unmodifiableSet(new LinkedHashSet<String>(flags));
        }

        public String get(String key) {
            return values.get(key);
        }

        public String get(String key, String defaultValue) {
            String value = values.get(key);
            return value == null ? defaultValue : value;
        }

        public boolean hasFlag(String flag) {
            return flags.contains(flag);
        }

        public boolean booleanOption(String key, boolean defaultValue) {
            String value = values.get(key);
            if (value == null) {
                return flags.contains(key) || defaultValue && !flags.contains("no_" + key);
            }
            String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
            if ("true".equals(normalized) || "yes".equals(normalized) || "1".equals(normalized)) {
                return true;
            }
            if ("false".equals(normalized) || "no".equals(normalized) || "0".equals(normalized)) {
                return false;
            }
            throw new IllegalArgumentException("Macro option '" + key
                    + "' must be true or false (" + key + "='" + value + "')");
        }

        public File requiredFile(String key) {
            String value = values.get(key);
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException("Missing required option: " + key);
            }
            return new File(value);
        }

        public int intOption(String key, int defaultValue) {
            String value = values.get(key);
            if (value == null || value.trim().isEmpty()) {
                return defaultValue;
            }
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("Macro option '" + key
                        + "' must be an integer (" + key + "='" + value + "')", nfe);
            }
        }

        public Map<String, String> values() {
            return values;
        }

        public Set<String> flags() {
            return flags;
        }
    }
}
