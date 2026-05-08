package macro.builder.ui.sandbox.variation;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.MontageMaker;
import ij.process.ImageProcessor;

import java.io.File;
import java.util.List;

public final class MontageExporter {

    private final VariantGridFrame grid;

    public MontageExporter(VariantGridFrame grid) {
        if (grid == null) throw new IllegalArgumentException("grid must not be null");
        this.grid = grid;
    }

    public void exportTo(File path) {
        if (path == null) return;
        List<TilePanel> tiles = grid.visibleTilesInDisplayOrder();
        if (tiles.isEmpty()) {
            IJ.showMessage("Variations", "No visible tiles are available for montage export.");
            return;
        }

        ImageProcessor first = firstProcessor(tiles);
        if (first == null) {
            IJ.showMessage("Variations", "No image tiles are available for montage export.");
            return;
        }
        int width = first.getWidth();
        int height = first.getHeight();
        ImageStack stack = new ImageStack(width, height);
        for (TilePanel tile : tiles) {
            ImageProcessor processor = tile.currentSliceProcessor();
            if (processor == null) continue;
            if (processor.getWidth() != width || processor.getHeight() != height) {
                processor = processor.resize(width, height);
            }
            ImagePlus captioned = new ImagePlus(tile.label(), processor);
            CaptionBaker.bakeAll(captioned, tile.label());
            stack.addSlice(tile.label(), captioned.getProcessor());
        }
        int n = stack.getSize();
        if (n == 0) {
            IJ.showMessage("Variations", "No image tiles are available for montage export.");
            return;
        }
        int[] dims = gridDims(n);
        ImagePlus montage = new MontageMaker().makeMontage2(
                new ImagePlus("variations", stack),
                dims[1], dims[0],
                1.0, 1, n, 1, 4, true);
        IJ.saveAs(montage, "PNG", path.getAbsolutePath());
    }

    static int[] gridDims(int n) {
        int safe = Math.max(1, n);
        int cols = (int) Math.ceil(Math.sqrt(safe));
        int rows = (int) Math.ceil(safe / (double) cols);
        return new int[] { rows, cols };
    }

    private static ImageProcessor firstProcessor(List<TilePanel> tiles) {
        for (TilePanel tile : tiles) {
            ImageProcessor processor = tile.currentSliceProcessor();
            if (processor != null) return processor;
        }
        return null;
    }
}
