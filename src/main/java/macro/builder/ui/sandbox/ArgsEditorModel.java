package macro.builder.ui.sandbox;

import macro.builder.image.FilterMacroEditorModel;
import ij.measure.Calibration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
        return hasPixelParameters(tokens, UnitContext.none());
    }

    static boolean hasPixelParameters(List<Token> tokens, UnitContext units) {
        if (tokens == null) return false;
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.isEditable() && "pixels".equals(unitForToken(token, units))) return true;
        }
        return false;
    }

    static boolean hasPhysicalParameters(List<Token> tokens, UnitContext units) {
        if (tokens == null) return false;
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.isEditable() && spacingForToken(token, units) > 0.0) return true;
        }
        return false;
    }

    static String displayLabel(Token token) {
        return displayLabel(token, UnitContext.none());
    }

    static String displayLabel(Token token, UnitContext units) {
        if (token == null) return "";
        String key = token.key();
        String unit = unitForToken(token, units);
        if (unit.length() == 0) return key;
        return key + " (" + unit + ")";
    }

    static String unitHint(Token token) {
        return unitHint(token, UnitContext.none());
    }

    static String unitHint(Token token, UnitContext units) {
        if (token == null) return "";
        String unit = unitForToken(token, units);
        if (isPhysicalUnit(unit)) {
            return "This uses the source image calibration and is saved as pixels, so later metadata loss will not change filtering.";
        }
        if ("pixels".equals(unit)) return "This spatial value is in pixels, not microns.";
        if ("intensity".equals(unit)) return "This value is an image intensity, not a distance.";
        if ("%".equals(unit)) return "This value is a percentage.";
        if ("weight".equals(unit)) return "This value is a dimensionless weighting factor.";
        if ("method setting".equals(unit)) return "This value is specific to the selected method.";
        return "";
    }

    static String displayValue(Token token, UnitContext units) {
        if (token == null) return "";
        double spacing = spacingForToken(token, units);
        if (spacing <= 0.0) return token.value();
        Double pixelValue = parseDouble(token.value());
        if (pixelValue == null) return token.value();
        return formatNumber(pixelValue.doubleValue() * spacing);
    }

    static String storageValue(Token token, String displayedValue, UnitContext units) {
        if (token == null) return displayedValue == null ? "" : displayedValue.trim();
        String value = displayedValue == null ? "" : displayedValue.trim();
        double spacing = spacingForToken(token, units);
        if (spacing <= 0.0) return value;
        Double physicalValue = parseDouble(value);
        if (physicalValue == null) return value;
        return formatNumber(physicalValue.doubleValue() / spacing);
    }

    static String storageArgsForDisplayDefaults(String args, UnitContext units) {
        List<Token> tokens = parse(args);
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.isEditable()) {
                token.setValue(storageValue(token, token.value(), units));
            }
        }
        return render(tokens);
    }

    static String displayArgs(String args, UnitContext units) {
        List<Token> tokens = parse(args);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) sb.append(' ');
            Token token = tokens.get(i);
            if (token.isEditable()) {
                sb.append(token.key()).append('=').append(displayValue(token, units));
                String unit = unitForToken(token, units);
                if (showUnitInSummary(unit)) appendSummaryUnit(sb, unit);
            } else {
                sb.append(token.key());
            }
        }
        return sb.toString();
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

    private static String unitForToken(Token token, UnitContext units) {
        if (token == null) return "";
        double spacing = spacingForToken(token, units);
        if (spacing > 0.0) return units.unitLabel;
        return unitForKey(token.key());
    }

    private static boolean showUnitInSummary(String unit) {
        if (unit == null || unit.length() == 0) return false;
        return !"method setting".equals(unit);
    }

    private static void appendSummaryUnit(StringBuilder sb, String unit) {
        if ("%".equals(unit)) {
            sb.append('%');
        } else {
            sb.append(' ').append(unit);
        }
    }

    private static boolean isPhysicalUnit(String unit) {
        return unit != null && unit.length() > 0
                && !"pixels".equals(unit)
                && !"intensity".equals(unit)
                && !"%".equals(unit)
                && !"weight".equals(unit)
                && !"method setting".equals(unit);
    }

    private static double spacingForToken(Token token, UnitContext units) {
        if (token == null || units == null || !units.isCalibrated()) return 0.0;
        String key = token.key() == null ? "" : token.key().trim().toLowerCase(Locale.ROOT);
        if ("x".equals(key)) return units.pixelWidth;
        if ("y".equals(key)) return units.pixelHeight;
        if ("z".equals(key)) return units.pixelDepth;
        if ("sigma".equals(key) || "radius".equals(key) || "rolling".equals(key)) {
            return units.hasSquarePixels() ? units.pixelWidth : 0.0;
        }
        return 0.0;
    }

    private static String unitForKey(String key) {
        if (key == null) return "";
        String normalized = key.trim().toLowerCase(Locale.ROOT);
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

    private static Double parseDouble(String text) {
        if (text == null) return null;
        try {
            double value = Double.parseDouble(text.trim());
            if (Double.isNaN(value) || Double.isInfinite(value)) return null;
            return Double.valueOf(value);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String formatNumber(double value) {
        BigDecimal decimal = BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros();
        String text = decimal.toPlainString();
        if ("-0".equals(text)) return "0";
        return text;
    }

    static final class UnitContext {
        private final boolean calibrated;
        private final double pixelWidth;
        private final double pixelHeight;
        private final double pixelDepth;
        private final String unitLabel;

        private UnitContext(boolean calibrated, double pixelWidth, double pixelHeight,
                            double pixelDepth, String unitLabel) {
            this.calibrated = calibrated;
            this.pixelWidth = pixelWidth;
            this.pixelHeight = pixelHeight;
            this.pixelDepth = pixelDepth;
            this.unitLabel = unitLabel == null ? "" : unitLabel;
        }

        static UnitContext none() {
            return new UnitContext(false, 0.0, 0.0, 0.0, "");
        }

        static UnitContext fromCalibration(Calibration calibration) {
            if (calibration == null) return none();
            String unit = normalizedUnit(calibration.getUnit());
            if (unit.length() == 0 || isPixelUnit(unit)) return none();
            double pixelWidth = positive(calibration.pixelWidth);
            double pixelHeight = positive(calibration.pixelHeight);
            double pixelDepth = positive(calibration.pixelDepth);
            if (pixelWidth <= 0.0 || pixelHeight <= 0.0) return none();
            if (pixelDepth <= 0.0) pixelDepth = 1.0;
            return new UnitContext(true, pixelWidth, pixelHeight, pixelDepth, unit);
        }

        boolean isCalibrated() {
            return calibrated;
        }

        boolean hasSquarePixels() {
            if (!calibrated || pixelWidth <= 0.0 || pixelHeight <= 0.0) return false;
            double scale = Math.max(Math.abs(pixelWidth), Math.abs(pixelHeight));
            return Math.abs(pixelWidth - pixelHeight) <= Math.max(1.0e-9, scale * 1.0e-6);
        }

        private static double positive(double value) {
            return Double.isNaN(value) || Double.isInfinite(value) || value <= 0.0 ? 0.0 : value;
        }

        private static String normalizedUnit(String raw) {
            String unit = raw == null ? "" : raw.trim();
            if (unit.length() == 0) return "";
            String lower = unit.toLowerCase(Locale.ROOT);
            if ("um".equals(lower) || "micron".equals(lower) || "microns".equals(lower)
                    || "micrometer".equals(lower) || "micrometers".equals(lower)) {
                return "microns";
            }
            return unit;
        }

        private static boolean isPixelUnit(String unit) {
            String lower = unit == null ? "" : unit.trim().toLowerCase(Locale.ROOT);
            return "pixel".equals(lower) || "pixels".equals(lower);
        }
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
