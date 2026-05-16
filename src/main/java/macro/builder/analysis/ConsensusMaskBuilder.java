package macro.builder.analysis;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

import java.util.List;

public final class ConsensusMaskBuilder {
    public static final int MIN_SUCCESSFUL_MASKS = 3;
    public static final long RETAINED_MASK_CAP_BYTES = 256L * 1024L * 1024L;
    public static final String CONSENSUS_TITLE = "Test Counts Consensus";

    private ConsensusMaskBuilder() {
    }

    public static ConsensusResult build(List<ImagePlus> masks) {
        if (masks == null || masks.size() < MIN_SUCCESSFUL_MASKS) {
            throw new IllegalArgumentException("at least 3 masks are needed");
        }
        ImagePlus first = masks.get(0);
        validateMask(first, 0, first);
        int width = first.getWidth();
        int height = first.getHeight();
        int slices = first.getStackSize();
        int planeSize = width * height;
        int displayNeedYes = (masks.size() + 1) / 2;
        int otherNeedYes = masks.size() / 2;

        for (int i = 1; i < masks.size(); i++) {
            validateMask(masks.get(i), i, first);
        }

        long[] intersections = new long[masks.size()];
        long[] unions = new long[masks.size()];
        ImageStack consensusStack = new ImageStack(width, height);

        for (int slice = 1; slice <= slices; slice++) {
            int[] votes = new int[planeSize];
            for (ImagePlus mask : masks) {
                ImageProcessor processor = mask.getStack().getProcessor(slice);
                addVotes(votes, processor);
            }

            byte[] consensusPixels = new byte[planeSize];
            for (int i = 0; i < planeSize; i++) {
                if (votes[i] >= displayNeedYes) {
                    consensusPixels[i] = (byte) 255;
                }
            }
            consensusStack.addSlice(first.getStack().getSliceLabel(slice),
                    new ByteProcessor(width, height, consensusPixels, null));

            for (int maskIndex = 0; maskIndex < masks.size(); maskIndex++) {
                ImageProcessor processor = masks.get(maskIndex).getStack().getProcessor(slice);
                addLeaveOneOutIoU(votes, processor, otherNeedYes, intersections, unions, maskIndex);
            }
        }

        double[] scores = new double[masks.size()];
        for (int i = 0; i < scores.length; i++) {
            scores[i] = unions[i] == 0L ? 0.0 : (double) intersections[i] / (double) unions[i];
        }

        ImagePlus consensus = new ImagePlus(CONSENSUS_TITLE, consensusStack);
        copyDimensions(first, consensus);
        return new ConsensusResult(consensus, scores);
    }

    public static long estimateRetainedBytes(List<ImagePlus> masks) {
        if (masks == null || masks.isEmpty() || masks.get(0) == null) {
            return 0L;
        }
        ImagePlus first = masks.get(0);
        long plane = multiplyCapped(first.getWidth(), first.getHeight());
        long pixels = multiplyCapped(plane, Math.max(1, first.getStackSize()));
        long maskBytes = multiplyCapped((long) masks.size() + 1L, pixels);
        long voteBytes = multiplyCapped(4L, plane);
        return addCapped(maskBytes, voteBytes);
    }

    private static void validateMask(ImagePlus mask, int index, ImagePlus first) {
        if (mask == null || mask.getStack() == null) {
            throw new IllegalArgumentException("mask " + index + " is missing");
        }
        if (mask.getWidth() <= 0 || mask.getHeight() <= 0 || mask.getStackSize() <= 0) {
            throw new IllegalArgumentException("mask " + index + " has no pixels");
        }
        if (first != null
                && (mask.getWidth() != first.getWidth()
                || mask.getHeight() != first.getHeight()
                || mask.getStackSize() != first.getStackSize())) {
            throw new IllegalArgumentException("all masks must have the same dimensions");
        }
    }

    private static void addVotes(int[] votes, ImageProcessor processor) {
        Object pixels = processor.getPixels();
        if (pixels instanceof byte[]) {
            byte[] in = (byte[]) pixels;
            for (int i = 0; i < in.length; i++) {
                if (in[i] != 0) {
                    votes[i]++;
                }
            }
            return;
        }
        int width = processor.getWidth();
        int height = processor.getHeight();
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (processor.getPixelValue(x, y) > 0.0f) {
                    votes[index]++;
                }
                index++;
            }
        }
    }

    private static void addLeaveOneOutIoU(
            int[] votes,
            ImageProcessor processor,
            int otherNeedYes,
            long[] intersections,
            long[] unions,
            int maskIndex) {
        Object pixels = processor.getPixels();
        if (pixels instanceof byte[]) {
            byte[] in = (byte[]) pixels;
            for (int i = 0; i < in.length; i++) {
                boolean foreground = in[i] != 0;
                boolean consensus = votes[i] - (foreground ? 1 : 0) >= otherNeedYes;
                if (foreground && consensus) {
                    intersections[maskIndex]++;
                }
                if (foreground || consensus) {
                    unions[maskIndex]++;
                }
            }
            return;
        }

        int width = processor.getWidth();
        int height = processor.getHeight();
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean foreground = processor.getPixelValue(x, y) > 0.0f;
                boolean consensus = votes[index] - (foreground ? 1 : 0) >= otherNeedYes;
                if (foreground && consensus) {
                    intersections[maskIndex]++;
                }
                if (foreground || consensus) {
                    unions[maskIndex]++;
                }
                index++;
            }
        }
    }

    private static void copyDimensions(ImagePlus source, ImagePlus target) {
        int channels = Math.max(1, source.getNChannels());
        int slices = Math.max(1, source.getNSlices());
        int frames = Math.max(1, source.getNFrames());
        if (channels * slices * frames == target.getStackSize()) {
            target.setDimensions(channels, slices, frames);
            if (source.isHyperStack()) {
                target.setOpenAsHyperStack(true);
            }
        }
        if (source.getCalibration() != null) {
            target.setCalibration(source.getCalibration().copy());
        }
    }

    private static long multiplyCapped(long a, long b) {
        if (a <= 0L || b <= 0L) {
            return 0L;
        }
        if (a > Long.MAX_VALUE / b) {
            return Long.MAX_VALUE;
        }
        return a * b;
    }

    private static long addCapped(long a, long b) {
        if (Long.MAX_VALUE - a < b) {
            return Long.MAX_VALUE;
        }
        return a + b;
    }

    public static final class ConsensusResult {
        public final ImagePlus consensusMask;
        public final double[] agreementScores;

        ConsensusResult(ImagePlus consensusMask, double[] agreementScores) {
            this.consensusMask = consensusMask;
            this.agreementScores = agreementScores == null ? new double[0] : agreementScores.clone();
        }
    }
}
