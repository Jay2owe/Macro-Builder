package macro.builder.analysis;

import java.util.Arrays;

public final class DetectedObject {
    public final int id;
    public final int area;
    public final int minX;
    public final int minY;
    public final int minZ;
    public final int maxX;
    public final int maxY;
    public final int maxZ;
    public final double centroidX;
    public final double centroidY;
    public final double centroidZ;
    public final int[] pixels;

    DetectedObject(int id, int[] pixels, int width, int height) {
        if (pixels == null) {
            throw new IllegalArgumentException("pixels must not be null");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width and height must be positive");
        }
        this.id = id;
        this.pixels = pixels.clone();
        Arrays.sort(this.pixels);
        this.area = this.pixels.length;

        int planeSize = width * height;
        int localMinX = Integer.MAX_VALUE;
        int localMinY = Integer.MAX_VALUE;
        int localMinZ = Integer.MAX_VALUE;
        int localMaxX = Integer.MIN_VALUE;
        int localMaxY = Integer.MIN_VALUE;
        int localMaxZ = Integer.MIN_VALUE;
        double sumX = 0.0;
        double sumY = 0.0;
        double sumZ = 0.0;

        for (int i = 0; i < this.pixels.length; i++) {
            int pixel = this.pixels[i];
            int z = pixel / planeSize;
            int planeIndex = pixel - z * planeSize;
            int y = planeIndex / width;
            int x = planeIndex - y * width;

            if (x < localMinX) localMinX = x;
            if (y < localMinY) localMinY = y;
            if (z < localMinZ) localMinZ = z;
            if (x > localMaxX) localMaxX = x;
            if (y > localMaxY) localMaxY = y;
            if (z > localMaxZ) localMaxZ = z;
            sumX += x;
            sumY += y;
            sumZ += z;
        }

        if (this.pixels.length == 0) {
            localMinX = localMinY = localMinZ = 0;
            localMaxX = localMaxY = localMaxZ = 0;
        }

        this.minX = localMinX;
        this.minY = localMinY;
        this.minZ = localMinZ;
        this.maxX = localMaxX;
        this.maxY = localMaxY;
        this.maxZ = localMaxZ;
        this.centroidX = this.pixels.length == 0 ? 0.0 : sumX / this.pixels.length;
        this.centroidY = this.pixels.length == 0 ? 0.0 : sumY / this.pixels.length;
        this.centroidZ = this.pixels.length == 0 ? 0.0 : sumZ / this.pixels.length;
    }

    public boolean containsPoint(double x, double y, int z, int width, int height) {
        if (width <= 0 || height <= 0) {
            return false;
        }
        int px = (int) Math.floor(x);
        int py = (int) Math.floor(y);
        if (px < 0 || py < 0 || px >= width || py >= height || z < 0) {
            return false;
        }
        int index = z * width * height + py * width + px;
        return Arrays.binarySearch(pixels, index) >= 0;
    }

    public double iou(int[] referencePixels) {
        if (referencePixels == null || referencePixels.length == 0 || pixels.length == 0) {
            return 0.0;
        }
        int intersection = intersectionSize(referencePixels);
        if (intersection == 0) {
            return 0.0;
        }
        int union = pixels.length + referencePixels.length - intersection;
        return union <= 0 ? 0.0 : (double) intersection / (double) union;
    }

    int intersectionSize(int[] referencePixels) {
        int[] sorted = referencePixels.clone();
        Arrays.sort(sorted);
        int i = 0;
        int j = 0;
        int count = 0;
        while (i < pixels.length && j < sorted.length) {
            int a = pixels[i];
            int b = sorted[j];
            if (a == b) {
                count++;
                i++;
                j++;
            } else if (a < b) {
                i++;
            } else {
                j++;
            }
        }
        return count;
    }
}
