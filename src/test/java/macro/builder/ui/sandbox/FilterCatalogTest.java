package macro.builder.ui.sandbox;

import macro.builder.image.FilterMacroParser.OpType;
import org.junit.Test;

import javax.swing.JButton;
import javax.swing.JLabel;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FilterCatalogTest {

    @Test
    public void includesSupported3dNativeEntriesUnder3dGroup() {
        FilterCatalog catalog = new FilterCatalog(Collections.<FilterCatalog.Entry>emptyList());

        assertCatalogEntry(catalog, "Gaussian Blur 3D",
                OpType.GAUSSIAN_BLUR_3D, "x=2 y=2 z=1", FilterCatalog.CatalogGroup.THREE_D);
        assertCatalogEntry(catalog, "Median 3D",
                OpType.MEDIAN_3D, "x=2 y=2 z=1", FilterCatalog.CatalogGroup.THREE_D);
        assertCatalogEntry(catalog, "Minimum 3D",
                OpType.MINIMUM_3D, "x=2 y=2 z=1", FilterCatalog.CatalogGroup.THREE_D);

        catalog.setSearchTextForTests("3D");

        assertTrue(catalog.visibleGroupsForTests().contains(FilterCatalog.CatalogGroup.THREE_D));
        assertVisible(catalog, "Gaussian Blur 3D");
        assertVisible(catalog, "Median 3D");
        assertVisible(catalog, "Minimum 3D");
    }

    @Test
    public void categorizesNativeAndLegacyEntries() {
        assertEquals(FilterCatalog.CatalogGroup.FILTERS, FilterCatalog.groupFor(
                FilterCatalog.Entry.fast("Smoothing", "Gaussian Blur", OpType.GAUSSIAN_BLUR, "")));
        assertEquals(FilterCatalog.CatalogGroup.BINARY, FilterCatalog.groupFor(
                FilterCatalog.Entry.fast("Morphology", "Dilate", OpType.DILATE, "")));
        assertEquals(FilterCatalog.CatalogGroup.IMAGE_TYPE, FilterCatalog.groupFor(
                FilterCatalog.Entry.fast("Bit depth", "8-bit", OpType.CONVERT_8BIT, "")));
        assertEquals(FilterCatalog.CatalogGroup.PLUGINS, FilterCatalog.groupFor(
                FilterCatalog.Entry.legacy("Tier 2", "Lab Plugin Filter",
                        "Plugins > Filters > Lab Plugin Filter")));
        assertEquals(FilterCatalog.CatalogGroup.FIJI_COMMANDS, FilterCatalog.groupFor(
                FilterCatalog.Entry.legacy("Analyze", "Analyze Particles",
                        "Analyze > Analyze Particles...")));
    }

    @Test
    public void catalogGroupsPluginCommandsFromPluginsMenu() {
        FilterCatalog catalog = new FilterCatalog(Collections.singletonList(
                FilterCatalog.Entry.legacy("Tier 2", "Lab Plugin Filter",
                        "Plugins > Filters > Lab Plugin Filter")));

        catalog.setSearchTextForTests("Lab Plugin");

        assertEquals(Collections.singletonList(FilterCatalog.CatalogGroup.PLUGINS),
                catalog.visibleGroupsForTests());
        assertVisible(catalog, "Lab Plugin Filter");
    }

    @Test
    public void searchMatchesLabelCategoryMenuPathBadgeAndGroupTitle() {
        List<FilterCatalog.Entry> tierTwo = Arrays.asList(
                FilterCatalog.Entry.legacy("Tier 2", "Lab Plugin Filter",
                        "Plugins > Filters > Lab Plugin Filter"),
                FilterCatalog.Entry.legacy("Process", "Watershed",
                        "Process > Binary > Watershed"));
        FilterCatalog catalog = new FilterCatalog(tierTwo);

        catalog.setSearchTextForTests("Enhance Contrast");
        assertVisible(catalog, "Enhance Contrast");
        assertNotVisible(catalog, "Gaussian Blur");

        catalog.setSearchTextForTests("Bit depth");
        assertVisible(catalog, "8-bit");
        assertVisible(catalog, "16-bit");
        assertVisible(catalog, "32-bit");
        assertNotVisible(catalog, "Median");

        catalog.setSearchTextForTests("Process > Binary");
        assertVisible(catalog, "Watershed");
        assertNotVisible(catalog, "Lab Plugin Filter");

        catalog.setSearchTextForTests("legacy");
        assertVisible(catalog, "Lab Plugin Filter");
        assertVisible(catalog, "Watershed");
        assertNotVisible(catalog, "Gaussian Blur");

        catalog.setSearchTextForTests("Image type");
        assertVisible(catalog, "8-bit");
        assertVisible(catalog, "16-bit");
        assertVisible(catalog, "32-bit");
        assertFalse(catalog.visibleGroupsForTests().contains(FilterCatalog.CatalogGroup.FILTERS));
    }

    @Test
    public void rowAddButtonRequestsEntryThroughExistingListener() {
        FilterCatalog catalog = new FilterCatalog(Collections.<FilterCatalog.Entry>emptyList());
        catalog.setSearchTextForTests("Enhance Contrast");

        final List<FilterCatalog.Entry> added = new ArrayList<FilterCatalog.Entry>();
        catalog.setAddRequestListener(new FilterCatalog.AddRequestListener() {
            @Override public void onAddRequested(FilterCatalog.Entry entry) {
                added.add(entry);
            }
        });

        JButton add = findAddButton(catalog, "Enhance Contrast");
        assertNotNull(add);
        add.doClick();

        assertEquals(1, added.size());
        assertEquals("Enhance Contrast", added.get(0).label);
        assertEquals(added.get(0), catalog.getSelectedEntry());
    }

    private static void assertCatalogEntry(FilterCatalog catalog, String label,
                                           OpType type, String args,
                                           FilterCatalog.CatalogGroup group) {
        FilterCatalog.Entry entry = findEntry(catalog, label);
        assertNotNull(entry);
        assertEquals(type, entry.type);
        assertEquals(args, entry.defaultArgs);
        assertEquals(group, FilterCatalog.groupFor(entry));
    }

    private static FilterCatalog.Entry findEntry(FilterCatalog catalog, String label) {
        List<FilterCatalog.Entry> entries = catalog.visibleEntriesForTests();
        for (int i = 0; i < entries.size(); i++) {
            FilterCatalog.Entry entry = entries.get(i);
            if (label.equals(entry.label)) return entry;
        }
        return null;
    }

    private static void assertVisible(FilterCatalog catalog, String label) {
        assertNotNull(findEntry(catalog, label));
    }

    private static void assertNotVisible(FilterCatalog catalog, String label) {
        assertFalse(visibleLabels(catalog).contains(label));
    }

    private static List<String> visibleLabels(FilterCatalog catalog) {
        List<String> labels = new ArrayList<String>();
        List<FilterCatalog.Entry> entries = catalog.visibleEntriesForTests();
        for (int i = 0; i < entries.size(); i++) {
            labels.add(entries.get(i).label);
        }
        return labels;
    }

    private static JButton findAddButton(Container root, String label) {
        if (root == null) return null;
        for (int i = 0; i < root.getComponentCount(); i++) {
            Component component = root.getComponent(i);
            if (component instanceof JButton) {
                JButton button = (JButton) component;
                if ("+".equals(button.getText()) && rowContainsLabel(button.getParent(), label)) {
                    return button;
                }
            }
            if (component instanceof Container) {
                JButton nested = findAddButton((Container) component, label);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static boolean rowContainsLabel(Container row, String label) {
        if (row == null) return false;
        for (int i = 0; i < row.getComponentCount(); i++) {
            Component component = row.getComponent(i);
            if (component instanceof JLabel) {
                String text = ((JLabel) component).getText();
                if (text != null && text.indexOf(label) >= 0) return true;
            }
            if (component instanceof Container && rowContainsLabel((Container) component, label)) {
                return true;
            }
        }
        return false;
    }
}
