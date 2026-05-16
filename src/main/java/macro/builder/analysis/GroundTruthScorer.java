package macro.builder.analysis;

import ij.ImagePlus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class GroundTruthScorer {
    public static final int DETECTION_TP = 1;
    public static final int DETECTION_FP = 2;
    public static final int REFERENCE_TP = 1;
    public static final int REFERENCE_FN = 3;
    public static final double IOU_THRESHOLD = 0.5;

    private GroundTruthScorer() {
    }

    public static ScoreSummary score(ImagePlus mask, GroundTruthReference reference, ShootoutSettings settings) {
        if (mask == null) {
            throw new IllegalArgumentException("mask must not be null");
        }
        if (reference == null) {
            throw new IllegalArgumentException("reference must not be null");
        }
        if (settings == null) {
            throw new IllegalArgumentException("settings must not be null");
        }

        List<DetectedObject> detected = ObjectCounter.detect(mask, settings);
        List<ReferenceGeometry> references = referenceGeometries(
                reference,
                mask.getWidth(),
                mask.getHeight(),
                mask.getStackSize());
        List<Candidate> candidates = candidates(detected, references, mask.getWidth(), mask.getHeight());
        Collections.sort(candidates, new Comparator<Candidate>() {
            @Override public int compare(Candidate a, Candidate b) {
                int byScore = Double.compare(b.score, a.score);
                if (byScore != 0) return byScore;
                int byReference = a.referenceIndex - b.referenceIndex;
                if (byReference != 0) return byReference;
                return a.detectedIndex - b.detectedIndex;
            }
        });

        boolean[] usedDetections = new boolean[detected.size()];
        boolean[] usedReferences = new boolean[references.size()];
        int[] perObjectStatus = new int[detected.size()];
        int[] referenceStatus = new int[references.size()];
        for (int i = 0; i < perObjectStatus.length; i++) {
            perObjectStatus[i] = DETECTION_FP;
        }
        for (int i = 0; i < referenceStatus.length; i++) {
            referenceStatus[i] = REFERENCE_FN;
        }

        int tp = 0;
        for (Candidate candidate : candidates) {
            if (usedDetections[candidate.detectedIndex] || usedReferences[candidate.referenceIndex]) {
                continue;
            }
            usedDetections[candidate.detectedIndex] = true;
            usedReferences[candidate.referenceIndex] = true;
            perObjectStatus[candidate.detectedIndex] = DETECTION_TP;
            referenceStatus[candidate.referenceIndex] = REFERENCE_TP;
            tp++;
        }

        int fp = detected.size() - tp;
        int fn = references.size() - tp;
        double precision = tp + fp == 0 ? 0.0 : (double) tp / (double) (tp + fp);
        double recall = tp + fn == 0 ? 0.0 : (double) tp / (double) (tp + fn);
        double f1 = precision + recall == 0.0 ? 0.0 : 2.0 * precision * recall / (precision + recall);
        return new ScoreSummary(tp, fp, fn, precision, recall, f1,
                perObjectStatus, referenceStatus, detected, references);
    }

    static List<ReferenceGeometry> referenceGeometries(
            GroundTruthReference reference,
            int width,
            int height,
            int depth) {
        if (reference == null || reference.objects.isEmpty()) {
            return Collections.emptyList();
        }
        List<ReferenceGeometry> geometries = new ArrayList<ReferenceGeometry>(reference.objects.size());
        for (int i = 0; i < reference.objects.size(); i++) {
            GroundTruthReference.ReferenceObject object = reference.objects.get(i);
            geometries.add(new ReferenceGeometry(i, object, object.pixels(width, height, depth)));
        }
        return geometries;
    }

    private static List<Candidate> candidates(
            List<DetectedObject> detected,
            List<ReferenceGeometry> references,
            int width,
            int height) {
        if (detected.isEmpty() || references.isEmpty()) {
            return Collections.emptyList();
        }
        List<Candidate> out = new ArrayList<Candidate>();
        for (int r = 0; r < references.size(); r++) {
            ReferenceGeometry reference = references.get(r);
            for (int d = 0; d < detected.size(); d++) {
                DetectedObject object = detected.get(d);
                double score = matchScore(object, reference, width, height);
                if (score >= IOU_THRESHOLD || isPointMatch(reference, score)) {
                    out.add(new Candidate(d, r, score));
                }
            }
        }
        return out;
    }

    private static double matchScore(
            DetectedObject detected,
            ReferenceGeometry reference,
            int width,
            int height) {
        if (reference.object.kind == GroundTruthReference.ReferenceObject.Kind.POINT) {
            return detected.containsPoint(
                    reference.object.x,
                    reference.object.y,
                    reference.object.z,
                    width,
                    height) ? 1.0 : 0.0;
        }
        return detected.iou(reference.pixels);
    }

    private static boolean isPointMatch(ReferenceGeometry reference, double score) {
        return reference.object.kind == GroundTruthReference.ReferenceObject.Kind.POINT && score > 0.0;
    }

    public static final class ScoreSummary {
        public final int tp;
        public final int fp;
        public final int fn;
        public final double precision;
        public final double recall;
        public final double f1;
        public final int[] perObjectStatus;
        public final int[] referenceStatus;
        public final List<DetectedObject> detectedObjects;
        public final List<ReferenceGeometry> references;

        private ScoreSummary(
                int tp,
                int fp,
                int fn,
                double precision,
                double recall,
                double f1,
                int[] perObjectStatus,
                int[] referenceStatus,
                List<DetectedObject> detectedObjects,
                List<ReferenceGeometry> references) {
            this.tp = tp;
            this.fp = fp;
            this.fn = fn;
            this.precision = precision;
            this.recall = recall;
            this.f1 = f1;
            this.perObjectStatus = perObjectStatus.clone();
            this.referenceStatus = referenceStatus.clone();
            this.detectedObjects = Collections.unmodifiableList(new ArrayList<DetectedObject>(detectedObjects));
            this.references = Collections.unmodifiableList(new ArrayList<ReferenceGeometry>(references));
        }
    }

    public static final class ReferenceGeometry {
        public final int index;
        public final GroundTruthReference.ReferenceObject object;
        public final int[] pixels;

        private ReferenceGeometry(int index, GroundTruthReference.ReferenceObject object, int[] pixels) {
            this.index = index;
            this.object = object;
            this.pixels = pixels == null ? new int[0] : pixels.clone();
        }
    }

    private static final class Candidate {
        final int detectedIndex;
        final int referenceIndex;
        final double score;

        Candidate(int detectedIndex, int referenceIndex, double score) {
            this.detectedIndex = detectedIndex;
            this.referenceIndex = referenceIndex;
            this.score = score;
        }
    }
}
