package macro.builder.ui.sandbox.variation;

import org.junit.Assume;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowEvent;

import static org.junit.Assert.assertEquals;

public class ProgressDialogTest {

    @Test
    public void cancelHandlerFiresOnClick() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        final int[] calls = new int[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                ProgressDialog dialog = new ProgressDialog(null, 3);
                try {
                    dialog.setCancelHandler(new Runnable() {
                        @Override public void run() {
                            calls[0]++;
                        }
                    });
                    dialog.cancelButtonForTest().doClick();
                } finally {
                    dialog.dispose();
                }
            }
        });
        assertEquals(1, calls[0]);
    }

    @Test
    public void cancelHandlerFiresOnceForRepeatedClicks() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        final int[] calls = new int[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                ProgressDialog dialog = new ProgressDialog(null, 3);
                try {
                    dialog.setCancelHandler(new Runnable() {
                        @Override public void run() {
                            calls[0]++;
                        }
                    });
                    dialog.cancelButtonForTest().doClick();
                    dialog.cancelButtonForTest().doClick();
                } finally {
                    dialog.dispose();
                }
            }
        });
        assertEquals(1, calls[0]);
    }

    @Test
    public void windowClosingRoutesToCancelHandler() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        final int[] calls = new int[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                ProgressDialog dialog = new ProgressDialog(null, 3);
                try {
                    dialog.setCancelHandler(new Runnable() {
                        @Override public void run() {
                            calls[0]++;
                        }
                    });
                    dialog.dispatchEvent(new WindowEvent(dialog, WindowEvent.WINDOW_CLOSING));
                } finally {
                    dialog.dispose();
                }
            }
        });
        assertEquals(1, calls[0]);
    }
}
