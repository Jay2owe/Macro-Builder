package macro.builder.analysis;

import ij.ImagePlus;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.io.RoiEncoder;
import ij.process.ShortProcessor;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GroundTruthLoaderTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void loadsCentroidCsvWithOptionalHeader() throws Exception {
        File file = temporaryFolder.newFile("points.csv");
        Files.write(file.toPath(), "x,y\n1,2\n3.5,4.5\n".getBytes(StandardCharsets.UTF_8));

        GroundTruthReference reference = GroundTruthLoader.load(file);

        assertEquals(GroundTruthReference.SourceFormat.CSV_POINTS, reference.sourceFormat);
        assertEquals(2, reference.size());
        assertEquals(1.0, reference.objects.get(0).x, 0.0001);
        assertEquals(4.5, reference.objects.get(1).y, 0.0001);
    }

    @Test
    public void loadsCellCounterXmlMarkers() throws Exception {
        File file = temporaryFolder.newFile("cell-counter.xml");
        String xml = "<CellCounter_Marker_File><Marker_Data><Marker_Type>"
                + "<Marker><MarkerX>2</MarkerX><MarkerY>3</MarkerY><MarkerZ>1</MarkerZ></Marker>"
                + "<Marker><MarkerX>4</MarkerX><MarkerY>5</MarkerY><MarkerZ>2</MarkerZ></Marker>"
                + "</Marker_Type></Marker_Data></CellCounter_Marker_File>";
        Files.write(file.toPath(), xml.getBytes(StandardCharsets.UTF_8));

        GroundTruthReference reference = GroundTruthLoader.load(file);

        assertEquals(GroundTruthReference.SourceFormat.CELL_COUNTER_XML, reference.sourceFormat);
        assertEquals(2, reference.size());
        assertEquals(0, reference.objects.get(0).z);
        assertEquals(1, reference.objects.get(1).z);
    }

    @Test
    public void loadsRoiSetZip() throws Exception {
        File file = temporaryFolder.newFile("RoiSet.zip");
        ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file));
        try {
            addRoi(zip, "point.roi", new PointRoi(1, 1));
            addRoi(zip, "area.roi", new Roi(3, 3, 2, 2));
        } finally {
            zip.close();
        }

        GroundTruthReference reference = GroundTruthLoader.load(file);

        assertEquals(GroundTruthReference.SourceFormat.ROI_SET, reference.sourceFormat);
        assertEquals(2, reference.size());
    }

    @Test
    public void loadsSixteenBitLabelTiff() throws Exception {
        File file = temporaryFolder.newFile("labels.tif");
        ShortProcessor processor = new ShortProcessor(5, 3);
        processor.set(1, 1, 1);
        processor.set(2, 1, 1);
        processor.set(4, 1, 300);
        ImagePlus image = new ImagePlus("labels", processor);
        assertTrue(new FileSaver(image).saveAsTiff(file.getAbsolutePath()));
        image.flush();

        GroundTruthReference reference = GroundTruthLoader.load(file);

        assertEquals(GroundTruthReference.SourceFormat.LABEL_IMAGE, reference.sourceFormat);
        assertEquals(2, reference.size());
        assertEquals(1, reference.objects.get(0).labelId);
        assertEquals(300, reference.objects.get(1).labelId);
    }

    private static void addRoi(ZipOutputStream zip, String name, Roi roi) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(RoiEncoder.saveAsByteArray(roi));
        zip.closeEntry();
    }
}
