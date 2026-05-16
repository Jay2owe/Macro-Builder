package macro.builder.analysis;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.io.RoiDecoder;
import ij.process.ImageProcessor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class GroundTruthLoader {

    private GroundTruthLoader() {
    }

    public static GroundTruthReference load(File file) {
        if (file == null) {
            throw new IllegalArgumentException("reference file must not be null");
        }
        if (!file.isFile()) {
            throw new IllegalArgumentException("reference file does not exist");
        }

        String extension = extension(file);
        if ("zip".equals(extension)) {
            return loadRoiSet(file);
        }
        if ("xml".equals(extension)) {
            return loadCellCounterXml(file);
        }
        if ("csv".equals(extension) || "txt".equals(extension)) {
            return loadCentroidCsv(file);
        }
        if ("tif".equals(extension) || "tiff".equals(extension)) {
            return loadLabelImage(file);
        }
        return loadBySniffing(file);
    }

    public static GroundTruthReference fromRois(String sourceName, Roi[] rois) {
        return GroundTruthReference.fromRois(
                GroundTruthReference.SourceFormat.ROI_MANAGER,
                sourceName,
                rois);
    }

    static GroundTruthReference loadRoiSet(File file) {
        List<Roi> rois = new ArrayList<Roi>();
        ZipInputStream zip = null;
        try {
            zip = new ZipInputStream(new FileInputStream(file));
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().toLowerCase(Locale.ROOT).endsWith(".roi")) {
                    continue;
                }
                byte[] data = readEntry(zip);
                Roi roi = new RoiDecoder(data, entry.getName()).getRoi();
                if (roi != null) {
                    rois.add(roi);
                }
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException("Could not read RoiSet.zip: " + cleanMessage(ex), ex);
        } finally {
            closeQuietly(zip);
        }
        return GroundTruthReference.fromRois(
                GroundTruthReference.SourceFormat.ROI_SET,
                file.getName(),
                rois.toArray(new Roi[rois.size()]));
    }

    static GroundTruthReference loadCentroidCsv(File file) {
        List<GroundTruthReference.ReferenceObject> objects =
                new ArrayList<GroundTruthReference.ReferenceObject>();
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            boolean headerSkipped = false;
            boolean sawData = false;
            for (String line : lines) {
                String trimmed = line == null ? "" : line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String[] parts = trimmed.split(",", -1);
                if (parts.length < 2) {
                    throw new IllegalArgumentException("CSV reference rows need x,y columns.");
                }
                try {
                    double x = Double.parseDouble(parts[0].trim());
                    double y = Double.parseDouble(parts[1].trim());
                    objects.add(GroundTruthReference.ReferenceObject.point(x, y, 0, objects.size() + 1));
                    sawData = true;
                } catch (NumberFormatException nfe) {
                    if (!sawData && !headerSkipped) {
                        headerSkipped = true;
                    } else {
                        throw new IllegalArgumentException(
                                "CSV reference coordinates must use dot-decimal x,y numbers.");
                    }
                }
            }
        } catch (Exception ex) {
            if (ex instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) ex;
            }
            throw new IllegalArgumentException("Could not read centroid CSV: " + cleanMessage(ex), ex);
        }
        return new GroundTruthReference(
                GroundTruthReference.SourceFormat.CSV_POINTS,
                file.getName(),
                objects);
    }

    static GroundTruthReference loadCellCounterXml(File file) {
        List<GroundTruthReference.ReferenceObject> objects =
                new ArrayList<GroundTruthReference.ReferenceObject>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            disableExternalXml(factory);
            Document document = factory.newDocumentBuilder().parse(file);
            NodeList markers = document.getElementsByTagName("Marker");
            for (int i = 0; i < markers.getLength(); i++) {
                if (!(markers.item(i) instanceof Element)) {
                    continue;
                }
                Element marker = (Element) markers.item(i);
                double x = parseChildDouble(marker, "MarkerX", "X", "x");
                double y = parseChildDouble(marker, "MarkerY", "Y", "y");
                int z = Math.max(0, (int) Math.round(parseChildDouble(marker, "MarkerZ", "Z", "z")) - 1);
                objects.add(GroundTruthReference.ReferenceObject.point(x, y, z, objects.size() + 1));
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException("Could not read Cell Counter XML: " + cleanMessage(ex), ex);
        }
        return new GroundTruthReference(
                GroundTruthReference.SourceFormat.CELL_COUNTER_XML,
                file.getName(),
                objects);
    }

    static GroundTruthReference loadLabelImage(File file) {
        ImagePlus image = IJ.openImage(file.getAbsolutePath());
        if (image == null || image.getStack() == null) {
            throw new IllegalArgumentException("Could not open label image TIFF.");
        }
        try {
            int width = image.getWidth();
            int height = image.getHeight();
            ImageStack stack = image.getStack();
            Map<Integer, GroundTruthReference.IntList> labels =
                    new TreeMap<Integer, GroundTruthReference.IntList>();
            for (int z = 0; z < stack.getSize(); z++) {
                ImageProcessor processor = stack.getProcessor(z + 1);
                Object pixels = processor.getPixels();
                int planeOffset = z * width * height;
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int label = labelAt(processor, pixels, x, y, y * width + x);
                        if (label == 0) {
                            continue;
                        }
                        GroundTruthReference.IntList list = labels.get(Integer.valueOf(label));
                        if (list == null) {
                            list = new GroundTruthReference.IntList();
                            labels.put(Integer.valueOf(label), list);
                        }
                        list.add(planeOffset + y * width + x);
                    }
                }
            }

            List<GroundTruthReference.ReferenceObject> objects =
                    new ArrayList<GroundTruthReference.ReferenceObject>(labels.size());
            for (Map.Entry<Integer, GroundTruthReference.IntList> entry : labels.entrySet()) {
                objects.add(GroundTruthReference.ReferenceObject.label(
                        entry.getKey().intValue(),
                        entry.getValue().toArray(),
                        width,
                        height));
            }
            return new GroundTruthReference(
                    GroundTruthReference.SourceFormat.LABEL_IMAGE,
                    file.getName(),
                    objects);
        } finally {
            image.flush();
        }
    }

    private static GroundTruthReference loadBySniffing(File file) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file.toPath());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Could not read reference file: " + cleanMessage(ex), ex);
        }
        if (bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K') {
            return loadRoiSet(file);
        }
        String prefix = new String(bytes, 0, Math.min(bytes.length, 512), StandardCharsets.UTF_8).trim();
        if (prefix.startsWith("<")) {
            return loadCellCounterXml(file);
        }
        if (prefix.indexOf(',') >= 0) {
            return loadCentroidCsv(file);
        }
        return loadLabelImage(file);
    }

    private static byte[] readEntry(ZipInputStream zip) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = zip.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static double parseChildDouble(Element parent, String firstName, String secondName, String thirdName) {
        String value = childText(parent, firstName);
        if (value == null) value = childText(parent, secondName);
        if (value == null) value = childText(parent, thirdName);
        if (value == null) {
            throw new IllegalArgumentException("Cell Counter marker is missing " + firstName + ".");
        }
        return Double.parseDouble(value.trim());
    }

    private static String childText(Element parent, String name) {
        NodeList nodes = parent.getElementsByTagName(name);
        if (nodes == null || nodes.getLength() == 0 || nodes.item(0) == null) {
            return null;
        }
        return nodes.item(0).getTextContent();
    }

    private static int labelAt(ImageProcessor processor, Object pixels, int x, int y, int index) {
        int label;
        if (pixels instanceof byte[]) {
            label = ((byte[]) pixels)[index] & 0xff;
        } else if (pixels instanceof short[]) {
            label = ((short[]) pixels)[index] & 0xffff;
        } else if (pixels instanceof int[]) {
            label = ((int[]) pixels)[index];
        } else if (pixels instanceof float[]) {
            float value = ((float[]) pixels)[index];
            label = Float.isNaN(value) || Float.isInfinite(value) ? 0 : (int) Math.round(value);
        } else {
            float value = processor.getPixelValue(x, y);
            label = Float.isNaN(value) || Float.isInfinite(value) ? 0 : (int) Math.round(value);
        }
        return label < 0 ? 0 : label;
    }

    private static String extension(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }

    private static void disableExternalXml(DocumentBuilderFactory factory) {
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (Exception ignored) {
        }
        try {
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        } catch (Exception ignored) {
        }
        try {
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Exception ignored) {
        }
        factory.setExpandEntityReferences(false);
    }

    private static void closeQuietly(ZipInputStream zip) {
        if (zip == null) {
            return;
        }
        try {
            zip.close();
        } catch (Exception ignored) {
        }
    }

    private static String cleanMessage(Throwable ex) {
        if (ex == null) {
            return "Unknown error";
        }
        String message = ex.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return ex.getClass().getSimpleName();
        }
        return message.trim().replace('\n', ' ').replace('\r', ' ');
    }
}
