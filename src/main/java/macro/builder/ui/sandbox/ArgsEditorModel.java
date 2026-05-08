package macro.builder.ui.sandbox;

import macro.builder.image.FilterMacroEditorModel;

import java.util.ArrayList;
import java.util.List;

final class ArgsEditorModel {

    private ArgsEditorModel() {}

    static List<Token> parse(String args) {
        List<Token> tokens = new ArrayList<Token>();
        if (args == null || args.trim().isEmpty()) return tokens;
        List<String> parts = RecorderParameterProbe.tokenizeOptions(args);
        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i);
            int equals = part.indexOf('=');
            if (equals > 0 && equals < part.length() - 1) {
                String key = part.substring(0, equals);
                String value = part.substring(equals + 1);
                tokens.add(new Token(key,
                        new FilterMacroEditorModel.Parameter(key, value, value, "", "")));
            } else {
                tokens.add(new Token(part));
            }
        }
        return tokens;
    }

    static boolean hasEditableParameters(List<Token> tokens) {
        if (tokens == null) return false;
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).isEditable()) return true;
        }
        return false;
    }

    static boolean hasPixelParameters(List<Token> tokens) {
        if (tokens == null) return false;
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.isEditable() && "pixels".equals(unitForKey(token.key()))) return true;
        }
        return false;
    }

    static String displayLabel(Token token) {
        if (token == null) return "";
        String key = token.key();
        String unit = unitForKey(key);
        if (unit.length() == 0) return key;
        return key + " (" + unit + ")";
    }

    static String unitHint(Token token) {
        if (token == null) return "";
        String unit = unitForKey(token.key());
        if ("pixels".equals(unit)) return "This spatial value is in pixels, not microns.";
        if ("intensity".equals(unit)) return "This value is an image intensity, not a distance.";
        if ("%".equals(unit)) return "This value is a percentage.";
        if ("weight".equals(unit)) return "This value is a dimensionless weighting factor.";
        if ("method setting".equals(unit)) return "This value is specific to the selected method.";
        return "";
    }

    static String render(List<Token> tokens) {
        StringBuilder sb = new StringBuilder();
        if (tokens == null) return "";
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) sb.append(' ');
            Token token = tokens.get(i);
            if (token.isEditable()) {
                sb.append(token.key()).append('=').append(token.value());
            } else {
                sb.append(token.key());
            }
        }
        return sb.toString();
    }

    private static String unitForKey(String key) {
        if (key == null) return "";
        String normalized = key.trim().toLowerCase(java.util.Locale.ROOT);
        if ("sigma".equals(normalized)
                || "radius".equals(normalized)
                || "rolling".equals(normalized)
                || "x".equals(normalized)
                || "y".equals(normalized)
                || "z".equals(normalized)) {
            return "pixels";
        }
        if ("value".equals(normalized)) return "intensity";
        if ("saturated".equals(normalized)) return "%";
        if ("mask".equals(normalized)) return "weight";
        if ("parameter_1".equals(normalized) || "parameter_2".equals(normalized)) {
            return "method setting";
        }
        return "";
    }

    static final class Token {
        private final String key;
        private final boolean editable;
        private final FilterMacroEditorModel.Parameter parameter;

        Token(String key) {
            this.key = key == null ? "" : key;
            this.editable = false;
            this.parameter = null;
        }

        Token(String key, FilterMacroEditorModel.Parameter parameter) {
            this.key = key == null ? "" : key;
            this.editable = true;
            this.parameter = parameter;
        }

        String key() {
            return key;
        }

        boolean isEditable() {
            return editable;
        }

        String value() {
            return parameter == null ? "" : parameter.getValue();
        }

        void setValue(String value) {
            if (parameter != null) parameter.setValue(value == null ? "" : value);
        }
    }
}
