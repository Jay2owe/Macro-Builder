package macro.builder.analysis;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;

public final class ObjectCounter {

    private ObjectCounter() {
    }

    public static CountSummary count(ImagePlus mask, ShootoutSettings settings) {
        if (mask == null) {
            throw new IllegalArgumentException("mask must not be null");
        }
        if (settings == null) {
            throw new IllegalArgumentException("settings must not be null");
        }
        if (mask.getWidth() <= 0 || mask.getHeight() <= 0 || mask.getStackSize() <= 0) {
            return new CountSummary(0, 0.0, 0.0, 0.0);
        }

        if (settings.countingMode == ShootoutSettings.CountingMode.OBJECTS_3D) {
            return count3D(mask, settings);
        }
        return count2D(mask, settings);
    }

    private static CountSummary count2D(ImagePlus mask, ShootoutSettings settings) {
        ImageStack stack = mask.getStack();
        int width = mask.getWidth();
        int height = mask.getHeight();
        int planeSize = width * height;
        int nSlices = mask.getStackSize();

        int acceptedCount = 0;
        double acceptedForeground = 0.0;
        int[] queue = new int[planeSize];

        for (int s = 1; s <= nSlices; s++) {
            ImageProcessor ip = stack.getProcessor(s);
            boolean[] visited = new boolean[planeSize];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int start = y * width + x;
                    if (visited[start] || !isForeground(ip, x, y)) {
                        continue;
                    }

                    int componentSize = flood2D(ip, width, height, x, y, visited, queue);
                    if (accepts(componentSize, settings)) {
                        acceptedCount++;
                        acceptedForeground += componentSize;
                    }
                }
            }
        }

        return summarize(acceptedCount, acceptedForeground, (double) planeSize * nSlices);
    }

    private static int flood2D(
            ImageProcessor ip,
            int width,
            int height,
            int startX,
            int startY,
            boolean[] visited,
            int[] queue) {
        int head = 0;
        int tail = 0;
        int start = startY * width + startX;
        visited[start] = true;
        queue[tail++] = start;
        int size = 0;

        while (head < tail) {
            int index = queue[head++];
            size++;
            int x = index % width;
            int y = index / width;

            for (int dy = -1; dy <= 1; dy++) {
                int ny = y + dy;
                if (ny < 0 || ny >= height) {
                    continue;
                }
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) {
                        continue;
                    }
                    int nx = x + dx;
                    if (nx < 0 || nx >= width) {
                        continue;
                    }
                    int neighbor = ny * width + nx;
                    if (!visited[neighbor] && isForeground(ip, nx, ny)) {
                        visited[neighbor] = true;
                        queue[tail++] = neighbor;
                    }
                }
            }
        }

        return size;
    }

    private static CountSummary count3D(ImagePlus mask, ShootoutSettings settings) {
        ImageStack stack = mask.getStack();
        int width = mask.getWidth();
        int height = mask.getHeight();
        int depth = mask.getStackSize();
        int planeSize = width * height;
        int volumeSize = planeSize * depth;
        boolean[] visited = new boolean[volumeSize];
        int[] queue = new int[volumeSize];

        int acceptedCount = 0;
        double acceptedForeground = 0.0;

        for (int z = 0; z < depth; z++) {
            ImageProcessor ip = stack.getProcessor(z + 1);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int start = z * planeSize + y * width + x;
                    if (visited[start] || !isForeground(ip, x, y)) {
                        continue;
                    }

                    int componentSize = flood3D(stack, width, height, depth, x, y, z, visited, queue);
                    if (accepts(componentSize, settings)) {
                        acceptedCount++;
                        acceptedForeground += componentSize;
                    }
                }
            }
        }

        return summarize(acceptedCount, acceptedForeground, volumeSize);
    }

    private static int flood3D(
            ImageStack stack,
            int width,
            int height,
            int depth,
            int startX,
            int startY,
            int startZ,
            boolean[] visited,
            int[] queue) {
        int planeSize = width * height;
        int head = 0;
        int tail = 0;
        int start = startZ * planeSize + startY * width + startX;
        visited[start] = true;
        queue[tail++] = start;
        int size = 0;

        while (head < tail) {
            int index = queue[head++];
            size++;
            int z = index / planeSize;
            int planeIndex = index - z * planeSize;
            int y = planeIndex / width;
            int x = planeIndex - y * width;

            for (int dz = -1; dz <= 1; dz++) {
                int nz = z + dz;
                if (nz < 0 || nz >= depth) {
                    continue;
                }
                ImageProcessor nip = stack.getProcessor(nz + 1);
                for (int dy = -1; dy <= 1; dy++) {
                    int ny = y + dy;
                    if (ny < 0 || ny >= height) {
                        continue;
                    }
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        int nx = x + dx;
                        if (nx < 0 || nx >= width) {
                            continue;
                        }
                        int neighbor = nz * planeSize + ny * width + nx;
                        if (!visited[neighbor] && isForeground(nip, nx, ny)) {
                            visited[neighbor] = true;
                            queue[tail++] = neighbor;
                        }
                    }
                }
            }
        }

        return size;
    }

    private static boolean isForeground(ImageProcessor ip, int x, int y) {
        return ip.getPixelValue(x, y) > 0.0f;
    }

    private static boolean accepts(int size, ShootoutSettings settings) {
        return size >= settings.minSize && size <= settings.maxSize;
    }

    private static CountSummary summarize(int count, double foreground, double totalPixels) {
        double meanSize = count == 0 ? 0.0 : foreground / count;
        double coverage = totalPixels == 0.0 ? 0.0 : foreground / totalPixels;
        return new CountSummary(count, meanSize, foreground, coverage);
    }

    public static final class CountSummary {
        public final int count;
        public final double meanSize;
        public final double totalForeground;
        public final double coverage;

        public CountSummary(int count, double meanSize, double totalForeground, double coverage) {
            this.count = count;
            this.meanSize = meanSize;
            this.totalForeground = totalForeground;
            this.coverage = coverage;
        }
    }
}
