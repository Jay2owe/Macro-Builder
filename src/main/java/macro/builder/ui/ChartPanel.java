package macro.builder.ui;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;

final class ChartPanel extends JPanel {
    private static final int MIN_WIDTH = 320;

    private final JLabel histogramLabel = new JLabel(new ImageIcon());
    private final JLabel curveLabel = new JLabel(new ImageIcon());
    private final JLabel legendLabel = new JLabel("vertical lines = tested thresholds; gold line = recommended");

    private BufferedImage histogramImage;
    private BufferedImage curveImage;

    ChartPanel() {
        super(new BorderLayout(0, 3));
        setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        setVisible(false);

        histogramLabel.setHorizontalAlignment(JLabel.CENTER);
        curveLabel.setHorizontalAlignment(JLabel.CENTER);
        Dimension chartSize = new Dimension(ChartRenderer.DEFAULT_WIDTH, ChartRenderer.DEFAULT_HEIGHT);
        histogramLabel.setPreferredSize(chartSize);
        histogramLabel.setMinimumSize(new Dimension(MIN_WIDTH, ChartRenderer.DEFAULT_HEIGHT));
        curveLabel.setPreferredSize(chartSize);
        curveLabel.setMinimumSize(new Dimension(MIN_WIDTH, ChartRenderer.DEFAULT_HEIGHT));

        legendLabel.setFont(legendLabel.getFont().deriveFont(Font.PLAIN, 11f));
        legendLabel.setForeground(new Color(70, 70, 70));
        legendLabel.setHorizontalAlignment(JLabel.LEFT);

        JPanel charts = new JPanel(new BorderLayout(0, 3));
        charts.setOpaque(false);
        charts.add(histogramLabel, BorderLayout.NORTH);
        charts.add(curveLabel, BorderLayout.CENTER);

        add(charts, BorderLayout.CENTER);
        add(legendLabel, BorderLayout.SOUTH);

        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                refreshIcons();
            }
        });
    }

    int chartWidth() {
        int width = getWidth();
        if (width <= 0 && getParent() != null) {
            width = getParent().getWidth();
        }
        if (width <= 0) {
            width = ChartRenderer.DEFAULT_WIDTH;
        }
        return Math.max(MIN_WIDTH, width - 24);
    }

    int chartHeight() {
        return ChartRenderer.DEFAULT_HEIGHT;
    }

    void setImages(final BufferedImage histogram, final BufferedImage curve) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override public void run() {
                    setImages(histogram, curve);
                }
            });
            return;
        }
        if (histogram == null || curve == null) {
            hideForRun();
            return;
        }
        histogramImage = histogram;
        curveImage = curve;
        refreshIcons();
        setVisible(true);
        revalidate();
        repaint();
    }

    void hideForRun() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override public void run() {
                    hideForRun();
                }
            });
            return;
        }
        histogramImage = null;
        curveImage = null;
        histogramLabel.setIcon(null);
        curveLabel.setIcon(null);
        setVisible(false);
        revalidate();
        repaint();
    }

    private void refreshIcons() {
        if (histogramImage == null || curveImage == null) {
            return;
        }
        histogramLabel.setIcon(new ImageIcon(scaled(histogramImage, chartWidth(), chartHeight())));
        curveLabel.setIcon(new ImageIcon(scaled(curveImage, chartWidth(), chartHeight())));
    }

    private static Image scaled(BufferedImage source, int width, int height) {
        if (source.getWidth() == width && source.getHeight() == height) {
            return source;
        }
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return out;
    }
}
