package macro.builder.ui;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.table.TableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Locale;

public final class FragilityBarRenderer extends JComponent implements TableCellRenderer {

    private static final int BAR_HEIGHT = 14;
    private static final int PADDING = 4;

    private Value value = Value.empty();
    private boolean selected;

    @Override public Component getTableCellRendererComponent(
            JTable table,
            Object value,
            boolean isSelected,
            boolean hasFocus,
            int row,
            int column) {
        this.value = value instanceof Value ? (Value) value : Value.empty();
        this.selected = isSelected;
        setFont(table.getFont());
        setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
        setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
        setToolTipText(this.value.hasData()
                ? "Fragility " + formatScore(this.value.score)
                : null);
        return this;
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            if (!value.hasData()) {
                return;
            }

            int barX = PADDING;
            int barWidth = Math.max(1, getWidth() - 2 * PADDING);
            int barY = Math.max(0, (getHeight() - BAR_HEIGHT) / 2);
            Rectangle bar = new Rectangle(barX, barY, barWidth, BAR_HEIGHT);
            g.setColor(selected ? selectedTrackColor() : new Color(232, 234, 237));
            g.fillRect(bar.x, bar.y, bar.width, bar.height);

            int span = spanPixels(value, barWidth);
            int centre = bar.x + bar.width / 2;
            int x = Math.max(bar.x, centre - span / 2);
            int right = Math.min(bar.x + bar.width, x + span);
            g.setColor(selected ? selectedBarColor() : new Color(76, 139, 245));
            g.fillRect(x, bar.y, Math.max(1, right - x), bar.height);

            String text = formatScore(value.score);
            FontMetrics metrics = g.getFontMetrics();
            int textX = Math.max(PADDING, getWidth() - PADDING - metrics.stringWidth(text));
            int textY = (getHeight() + metrics.getAscent() - metrics.getDescent()) / 2;
            g.setColor(getForeground());
            g.drawString(text, textX, textY);
        } finally {
            g.dispose();
        }
    }

    public static int spanPixels(Value value, int availableWidth) {
        if (value == null || !value.hasData() || availableWidth <= 0) {
            return 0;
        }
        double score = Math.max(0.0, Math.min(1.0, value.score));
        return Math.max(1, (int) Math.round(score * availableWidth));
    }

    private static Color selectedTrackColor() {
        Color color = UIManager.getColor("Table.selectionBackground");
        return color == null ? new Color(184, 207, 229) : color.brighter();
    }

    private static Color selectedBarColor() {
        Color color = UIManager.getColor("Table.selectionForeground");
        return color == null ? Color.WHITE : color;
    }

    private static String formatScore(double score) {
        if (Double.isNaN(score) || Double.isInfinite(score)) {
            return "";
        }
        return String.format(Locale.ROOT, "%.2f", score);
    }

    public static final class Value {
        public final double score;
        public final int centre;
        public final int min;
        public final int max;
        public final boolean present;

        private Value(double score, int centre, int min, int max, boolean present) {
            this.score = score;
            this.centre = centre;
            this.min = min;
            this.max = max;
            this.present = present;
        }

        public static Value of(double score, int centre, int[] samples) {
            if (Double.isNaN(score) || Double.isInfinite(score) || samples == null) {
                return empty();
            }
            int min = centre;
            int max = centre;
            for (int sample : samples) {
                if (sample < min) {
                    min = sample;
                }
                if (sample > max) {
                    max = sample;
                }
            }
            return new Value(score, centre, min, max, true);
        }

        public static Value empty() {
            return new Value(Double.NaN, 0, 0, 0, false);
        }

        public boolean hasData() {
            return present && !Double.isNaN(score) && !Double.isInfinite(score);
        }
    }
}
