package macro.builder.ui;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageCanvas;
import ij.gui.Toolbar;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.KeyEventDispatcher;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public final class ClickCapture {
    private static final long CLICK_DEBOUNCE_MS = 200L;

    private final ImagePlus source;
    private final ImageCanvas canvas;
    private final Consumer<List<int[]>> onDone;
    private final Runnable onCancel;
    private final List<int[]> points = new ArrayList<int[]>();
    private final JDialog helper;
    private final JLabel countLabel = new JLabel("0 points");

    private MouseAdapter mouseListener;
    private KeyEventDispatcher keyDispatcher;
    private boolean active;
    private long lastClickTime = Long.MIN_VALUE;

    private ClickCapture(
            Window owner,
            ImagePlus source,
            ImageCanvas canvas,
            Consumer<List<int[]>> onDone,
            Runnable onCancel) {
        this.source = source;
        this.canvas = canvas;
        this.onDone = onDone;
        this.onCancel = onCancel;
        this.helper = new JDialog(owner, "Click-fit capture");
        this.helper.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        buildHelper();
    }

    public static ClickCapture start(
            Window owner,
            ImagePlus source,
            Consumer<List<int[]>> onDone,
            Runnable onCancel) {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("Click capture needs the Fiji desktop UI.");
        }
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (source.getWindow() == null) {
            source.show();
        }
        if (source.getWindow() != null) {
            source.getWindow().toFront();
            WindowManager.setCurrentWindow(source.getWindow());
        }
        ImageCanvas canvas = source.getCanvas();
        if (canvas == null) {
            throw new IllegalStateException("The source image has no canvas to capture clicks from.");
        }
        try {
            IJ.setTool("multi-point");
        } catch (Throwable ignored) {
            Toolbar toolbar = Toolbar.getInstance();
            if (toolbar != null) {
                toolbar.setTool(Toolbar.POINT);
            }
        }
        ClickCapture capture = new ClickCapture(owner, source, canvas, onDone, onCancel);
        capture.attach();
        return capture;
    }

    public boolean isActive() {
        return active;
    }

    public void toFront() {
        helper.toFront();
        if (source.getWindow() != null) {
            source.getWindow().toFront();
        }
    }

    public void cancel() {
        if (!active) {
            return;
        }
        detach();
        if (onCancel != null) {
            onCancel.run();
        }
    }

    private void buildHelper() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        String title = source.getTitle() == null ? "source image" : source.getTitle();
        panel.add(new JLabel("<html>Click 5-10 real objects in " + escapeHtml(title)
                + ".<br>Press Done when finished. Press Esc to cancel.</html>"), BorderLayout.CENTER);
        panel.add(countLabel, BorderLayout.NORTH);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton cancel = new JButton("Cancel");
        JButton done = new JButton("Done");
        cancel.addActionListener(e -> cancel());
        done.addActionListener(e -> finish());
        buttons.add(cancel);
        buttons.add(done);
        panel.add(buttons, BorderLayout.SOUTH);

        helper.setContentPane(panel);
        helper.pack();
        helper.setLocationRelativeTo(helper.getOwner());
        helper.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                cancel();
            }
        });
    }

    private void attach() {
        active = true;
        mouseListener = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                captureClick(e);
            }
        };
        keyDispatcher = new KeyEventDispatcher() {
            @Override public boolean dispatchKeyEvent(KeyEvent e) {
                if (active && e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    cancel();
                    return true;
                }
                return false;
            }
        };
        canvas.addMouseListener(mouseListener);
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyDispatcher);
        helper.setVisible(true);
    }

    private void captureClick(MouseEvent e) {
        if (!active || !SwingUtilities.isLeftMouseButton(e)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (lastClickTime != Long.MIN_VALUE && now - lastClickTime < CLICK_DEBOUNCE_MS) {
            return;
        }
        lastClickTime = now;
        int x = canvas.offScreenX(e.getX());
        int y = canvas.offScreenY(e.getY());
        int z = Math.max(1, source.getZ());
        points.add(new int[]{x, y, z});
        countLabel.setText(points.size() == 1 ? "1 point" : points.size() + " points");
    }

    private void finish() {
        if (!active) {
            return;
        }
        List<int[]> captured = pointCopy(points);
        detach();
        if (onDone != null) {
            onDone.accept(captured);
        }
    }

    private void detach() {
        active = false;
        if (mouseListener != null) {
            canvas.removeMouseListener(mouseListener);
            mouseListener = null;
        }
        if (keyDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyDispatcher);
            keyDispatcher = null;
        }
        helper.setVisible(false);
        helper.dispose();
    }

    private static List<int[]> pointCopy(List<int[]> points) {
        if (points == null || points.isEmpty()) {
            return Collections.emptyList();
        }
        List<int[]> copy = new ArrayList<int[]>(points.size());
        for (int i = 0; i < points.size(); i++) {
            int[] point = points.get(i);
            copy.add(new int[]{point[0], point[1], point[2]});
        }
        return Collections.unmodifiableList(copy);
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
