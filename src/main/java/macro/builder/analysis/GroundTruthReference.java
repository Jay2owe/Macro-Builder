package macro.builder.analysis;

import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.process.FloatPolygon;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class GroundTruthReference {
    public enum SourceFormat {
        ROI_SET,
        ROI_MANAGER,
        CELL_COUNTER_XML,
        CSV_POINTS,
        LABEL_IMAGE
    }

    public final SourceFormat sourceFormat;
    public final String sourceName;
    public final List<ReferenceObject> objects;

    public GroundTruthReference(SourceFormat sourceFormat, String sourceName, List<ReferenceObject> objects) {
        if (sourceFormat == null) {
            throw new IllegalArgumentException("sourceFormat must not be null");
        }
        this.sourceFormat = sourceFormat;
        this.sourceName = sourceName == null ? "" : sourceName;
        this.objects = immutableObjects(objects);
    }

    public int size() {
        return objects.size();
    }

    public boolean isEmpty() {
        return objects.isEmpty();
    }

    public static GroundTruthReference fromRois(SourceFormat format, String sourceName, Roi[] rois) {
        List<ReferenceObject> objects = new ArrayList<ReferenceObject>();
        if (rois != null) {
            for (int i = 0; i < rois.length; i++) {
                Roi roi = rois[i];
                if (roi == null) {
                    continue;
                }
                if (roi.getType() == Roi.POINT) {
                    addPointObjects(objects, roi);
                } else if (roi.isArea()) {
                    objects.add(ReferenceObject.area(roi, i + 1));
                }
            }
        }
        return new GroundTruthReference(format, sourceName, objects);
    }

    private static void addPointObjects(List<ReferenceObject> objects, Roi roi) {
        int defaultZ = zPosition(roi);
        if (roi instanceof PointRoi) {
            PointRoi points = (PointRoi) roi;
            FloatPolygon polygon = points.getContainedFloatPoints();
            for (int i = 0; polygon != null && i < polygon.npoints; i++) {
                int z = defaultZ;
                int position = points.getPointPosition(i);
                if (position > 0) {
                    z = position - 1;
                }
                objects.add(ReferenceObject.point(polygon.xpoints[i], polygon.ypoints[i], z, i + 1));
            }
            return;
        }

        Rectangle bounds = roi.getBounds();
        objects.add(ReferenceObject.point(
                bounds.getCenterX(),
                bounds.getCenterY(),
                defaultZ,
                objects.size() + 1));
    }

    static int zPosition(Roi roi) {
        if (roi == null) {
            return 0;
        }
        int z = roi.getZPosition();
        if (z <= 0) {
            z = roi.getPosition();
        }
        return Math.max(0, z - 1);
    }

    private static List<ReferenceObject> immutableObjects(List<ReferenceObject> objects) {
        if (objects == null || objects.isEmpty()) {
            return Collections.emptyList();
        }
        List<ReferenceObject> copy = new ArrayList<ReferenceObject>(objects.size());
        for (ReferenceObject object : objects) {
            if (object != null) {
                copy.add(object);
            }
        }
        return Collections.unmodifiableList(copy);
    }

    public static final class ReferenceObject {
        public enum Kind {
            POINT,
            AREA,
            LABEL
        }

        public final Kind kind;
        public final double x;
        public final double y;
        public final int z;
        public final int labelId;
        public final Roi roi;
        private final int[] pixels;

        private ReferenceObject(Kind kind, double x, double y, int z, int labelId, Roi roi, int[] pixels) {
            if (kind == null) {
                throw new IllegalArgumentException("kind must not be null");
            }
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.z = Math.max(0, z);
            this.labelId = labelId;
            this.roi = cloneRoi(roi);
            if (pixels == null || pixels.length == 0) {
                this.pixels = new int[0];
            } else {
                this.pixels = pixels.clone();
                Arrays.sort(this.pixels);
            }
        }

        public static ReferenceObject point(double x, double y, int z, int labelId) {
            return new ReferenceObject(Kind.POINT, x, y, z, labelId, null, null);
        }

        public static ReferenceObject area(Roi roi, int labelId) {
            if (roi == null) {
                throw new IllegalArgumentException("roi must not be null");
            }
            Rectangle bounds = roi.getBounds();
            return new ReferenceObject(
                    Kind.AREA,
                    bounds.getCenterX(),
                    bounds.getCenterY(),
                    zPosition(roi),
                    labelId,
                    roi,
                    null);
        }

        public static ReferenceObject label(int labelId, int[] pixels, int width, int height) {
            return new ReferenceObject(Kind.LABEL, centroidX(pixels, width, height),
                    centroidY(pixels, width, height), centroidZ(pixels, width, height),
                    labelId, null, pixels);
        }

        public int[] pixels(int width, int height, int depth) {
            if (kind == Kind.LABEL) {
                return pixels.clone();
            }
            if (kind == Kind.POINT) {
                return pointPixels(width, height, depth);
            }
            return roiPixels(width, height, depth);
        }

        private int[] pointPixels(int width, int height, int depth) {
            int px = (int) Math.floor(x);
            int py = (int) Math.floor(y);
            int pz = Math.min(Math.max(0, z), Math.max(0, depth - 1));
            if (px < 0 || py < 0 || px >= width || py >= height || depth <= 0) {
                return new int[0];
            }
            return new int[]{pz * width * height + py * width + px};
        }

        private int[] roiPixels(int width, int height, int depth) {
            if (roi == null || width <= 0 || height <= 0 || depth <= 0) {
                return new int[0];
            }
            Rectangle bounds = roi.getBounds();
            int minX = Math.max(0, bounds.x);
            int minY = Math.max(0, bounds.y);
            int maxX = Math.min(width - 1, bounds.x + bounds.width - 1);
            int maxY = Math.min(height - 1, bounds.y + bounds.height - 1);
            int slice = Math.min(Math.max(0, zPosition(roi)), depth - 1);
            int planeOffset = slice * width * height;
            IntList out = new IntList();
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    if (roi.contains(x, y)) {
                        out.add(planeOffset + y * width + x);
                    }
                }
            }
            return out.toArray();
        }

        private static Roi cloneRoi(Roi roi) {
            if (roi == null) {
                return null;
            }
            return (Roi) roi.clone();
        }

        private static double centroidX(int[] pixels, int width, int height) {
            return centroid(pixels, width, height, 0);
        }

        private static double centroidY(int[] pixels, int width, int height) {
            return centroid(pixels, width, height, 1);
        }

        private static int centroidZ(int[] pixels, int width, int height) {
            return (int) Math.round(centroid(pixels, width, height, 2));
        }

        private static double centroid(int[] pixels, int width, int height, int axis) {
            if (pixels == null || pixels.length == 0 || width <= 0 || height <= 0) {
                return 0.0;
            }
            int planeSize = width * height;
            double sum = 0.0;
            for (int i = 0; i < pixels.length; i++) {
                int z = pixels[i] / planeSize;
                int planeIndex = pixels[i] - z * planeSize;
                int y = planeIndex / width;
                int x = planeIndex - y * width;
                if (axis == 0) {
                    sum += x;
                } else if (axis == 1) {
                    sum += y;
                } else {
                    sum += z;
                }
            }
            return sum / pixels.length;
        }
    }

    static final class IntList {
        private int[] values = new int[16];
        private int size;

        void add(int value) {
            if (size == values.length) {
                int[] grown = new int[values.length * 2];
                System.arraycopy(values, 0, grown, 0, values.length);
                values = grown;
            }
            values[size++] = value;
        }

        int[] toArray() {
            int[] out = new int[size];
            System.arraycopy(values, 0, out, 0, size);
            Arrays.sort(out);
            return out;
        }
    }
}
