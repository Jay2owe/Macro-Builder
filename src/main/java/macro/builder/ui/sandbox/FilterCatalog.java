package macro.builder.ui.sandbox;

import macro.builder.image.FilterMacroParser.OpType;
import ij.Menus;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FilterCatalog extends JPanel {

    public interface AddRequestListener {
        void onAddRequested(Entry entry);
    }

    enum CatalogGroup {
        FILTERS("Filters"),
        THREE_D("3D"),
        BINARY("Binary"),
        IMAGE_TYPE("Image type"),
        PLUGINS("Plugins"),
        FIJI_COMMANDS("Fiji commands");

        final String title;

        CatalogGroup(String title) {
            this.title = title;
        }
    }

    private static volatile List<Entry> cachedTierTwoEntries;

    private final JTextField search = new JTextField();
    private final JPanel groupsPanel = new JPanel(new GridBagLayout());
    private final List<Entry> entries = new ArrayList<Entry>();
    private final List<Entry> visibleEntries = new ArrayList<Entry>();
    private final List<CatalogGroup> visibleGroups = new ArrayList<CatalogGroup>();
    private Entry selectedEntry;
    private AddRequestListener addListener;

    public FilterCatalog() {
        this(null);
    }

    FilterCatalog(List<Entry> tierTwoEntries) {
        super(new BorderLayout(6, 6));
        setBorder(BorderFactory.createTitledBorder("Available steps"));

        add(search, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(groupsPanel);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);

        seedTierOne();
        addTierTwoEntries(tierTwoEntries);
        refresh();

        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { refresh(); }
            @Override public void removeUpdate(DocumentEvent e) { refresh(); }
            @Override public void changedUpdate(DocumentEvent e) { refresh(); }
        });
    }

    public Entry getSelectedEntry() {
        return selectedEntry;
    }

    public void setAddRequestListener(AddRequestListener listener) {
        this.addListener = listener;
    }

    public void focusSearch() {
        search.requestFocusInWindow();
    }

    private void refresh() {
        String q = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        visibleEntries.clear();
        visibleGroups.clear();
        groupsPanel.removeAll();

        Map<CatalogGroup, List<Entry>> grouped = new EnumMap<CatalogGroup, List<Entry>>(CatalogGroup.class);
        CatalogGroup[] groups = CatalogGroup.values();
        for (int i = 0; i < groups.length; i++) {
            grouped.put(groups[i], new ArrayList<Entry>());
        }

        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (matches(entry, q)) {
                grouped.get(groupFor(entry)).add(entry);
                visibleEntries.add(entry);
            }
        }

        if (!visibleEntries.contains(selectedEntry)) {
            selectedEntry = visibleEntries.isEmpty() ? null : visibleEntries.get(0);
        }

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.insets = new Insets(0, 0, 6, 0);
        for (int i = 0; i < groups.length; i++) {
            CatalogGroup group = groups[i];
            List<Entry> groupEntries = grouped.get(group);
            if (groupEntries.isEmpty()) continue;
            visibleGroups.add(group);
            groupsPanel.add(buildGroupPanel(group, groupEntries), gbc);
            gbc.gridy++;
        }

        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, 0, 0, 0);
        groupsPanel.add(new JPanel(), gbc);
        groupsPanel.revalidate();
        groupsPanel.repaint();
    }

    private void seedTierOne() {
        add("Smoothing", "Gaussian Blur", OpType.GAUSSIAN_BLUR, "sigma=2 stack");
        add("Smoothing", "Median", OpType.MEDIAN, "radius=2 stack");
        add("Smoothing", "Mean", OpType.MEAN, "radius=2 stack");
        add("Smoothing", "Minimum", OpType.MINIMUM, "radius=2 stack");
        add("Smoothing", "Maximum", OpType.MAXIMUM, "radius=2 stack");
        add("Smoothing", "Variance", OpType.VARIANCE, "radius=2 stack");

        add("3D", "Gaussian Blur 3D", OpType.GAUSSIAN_BLUR_3D, "x=2 y=2 z=1");
        add("3D", "Median 3D", OpType.MEDIAN_3D, "x=2 y=2 z=1");
        add("3D", "Minimum 3D", OpType.MINIMUM_3D, "x=2 y=2 z=1");

        add("Background", "Subtract Background", OpType.SUBTRACT_BACKGROUND, "rolling=50 stack");

        add("Morphology", "Dilate", OpType.DILATE, "");
        add("Morphology", "Erode", OpType.ERODE, "");
        add("Morphology", "Open", OpType.OPEN, "");
        add("Morphology", "Close-", OpType.CLOSE_, "");
        add("Morphology", "Fill Holes", OpType.FILL_HOLES, "");
        add("Morphology", "Skeletonize", OpType.SKELETONIZE, "");
        add("Morphology", "Unsharp Mask", OpType.UNSHARP_MASK, "radius=10 mask=0.60 stack");

        add("Thresholding", "Auto Local Threshold", OpType.AUTO_LOCAL_THRESHOLD,
                "method=Bernsen radius=15 parameter_1=0 parameter_2=0 white");

        add("Math", "Invert", OpType.INVERT, "");
        add("Math", "Add", OpType.ADD, "value=1 stack");
        add("Math", "Subtract", OpType.SUBTRACT, "value=1 stack");
        add("Math", "Multiply", OpType.MULTIPLY, "value=1 stack");
        add("Math", "Divide", OpType.DIVIDE, "value=1 stack");

        add("Bit depth", "8-bit", OpType.CONVERT_8BIT, "");
        add("Bit depth", "16-bit", OpType.CONVERT_16BIT, "");
        add("Bit depth", "32-bit", OpType.CONVERT_32BIT, "");

        add("Enhance", "Enhance Contrast", OpType.ENHANCE_CONTRAST, "saturated=0.35 normalize stack");
    }

    private void addTierTwoEntries(List<Entry> tierTwoEntries) {
        entries.addAll(tierTwoEntries == null ? getCachedTierTwoEntries() : tierTwoEntries);
    }

    private void add(String category, String label, OpType type, String args) {
        entries.add(Entry.fast(category, label, type, args));
    }

    static CatalogGroup groupFor(Entry entry) {
        if (entry == null) return CatalogGroup.FILTERS;
        if (entry.legacy && entry.menuPath.startsWith("Plugins")) return CatalogGroup.PLUGINS;
        if (entry.legacy) return CatalogGroup.FIJI_COMMANDS;
        switch (entry.type) {
            case GAUSSIAN_BLUR_3D:
            case MEDIAN_3D:
            case MINIMUM_3D:
                return CatalogGroup.THREE_D;
            case DILATE:
            case ERODE:
            case OPEN:
            case CLOSE_:
            case FILL_HOLES:
            case SKELETONIZE:
            case AUTO_LOCAL_THRESHOLD:
                return CatalogGroup.BINARY;
            case CONVERT_8BIT:
            case CONVERT_16BIT:
            case CONVERT_32BIT:
                return CatalogGroup.IMAGE_TYPE;
            default:
                return CatalogGroup.FILTERS;
        }
    }

    private boolean matches(Entry entry, String q) {
        if (q.length() == 0) return true;
        CatalogGroup group = groupFor(entry);
        return contains(entry.label, q)
                || contains(entry.category, q)
                || contains(entry.menuPath, q)
                || contains(entry.commandName, q)
                || contains(entry.badge(), q)
                || contains(group.title, q);
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }

    private JPanel buildGroupPanel(CatalogGroup group, List<Entry> groupEntries) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(group.title));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 4, 2, 4);
        for (int i = 0; i < groupEntries.size(); i++) {
            panel.add(buildEntryRow(groupEntries.get(i)), gbc);
            gbc.gridy++;
        }
        return panel;
    }

    private JPanel buildEntryRow(final Entry entry) {
        final JPanel row = new JPanel(new BorderLayout(6, 0));
        boolean selected = entry == selectedEntry;
        row.setOpaque(true);
        row.setBackground(selected ? new Color(226, 238, 252) : Color.WHITE);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(selected ? new Color(70, 120, 200) : new Color(220, 220, 220)),
                BorderFactory.createEmptyBorder(3, 5, 3, 3)));

        String path = entry.menuPath.length() > 0 ? entry.menuPath : entry.category;
        if (entry.stub) path = path + " - placeholder";
        JLabel label = new JLabel("<html><b>" + html(entry.label) + " " + html(entry.badge())
                + "</b><br><span style='font-size:9px;'>" + html(path) + "</span></html>");
        label.setOpaque(false);
        label.setEnabled(!entry.stub);
        JButton add = new JButton("+");
        add.setMargin(new Insets(1, 7, 1, 7));
        add.setEnabled(!entry.stub);
        add.setToolTipText("Add " + entry.label + " to the selected branch");
        add.addActionListener(e -> {
            selectEntry(entry);
            if (addListener != null && !entry.stub) addListener.onAddRequested(entry);
        });
        row.add(add, BorderLayout.WEST);
        row.add(label, BorderLayout.CENTER);

        MouseAdapter selector = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || entry.stub) return;
                selectEntry(entry);
                if (e.getClickCount() == 2 && addListener != null) {
                    addListener.onAddRequested(entry);
                }
            }
        };
        row.addMouseListener(selector);
        label.addMouseListener(selector);
        return row;
    }

    private void selectEntry(Entry entry) {
        if (entry == null || entry.stub) return;
        selectedEntry = entry;
        refresh();
    }

    private static String html(String text) {
        if (text == null) return "";
        String escaped = text.replace("&", "&amp;");
        escaped = escaped.replace("<", "&lt;");
        escaped = escaped.replace(">", "&gt;");
        escaped = escaped.replace("\"", "&quot;");
        return escaped;
    }

    static List<Entry> getCachedTierTwoEntries() {
        List<Entry> cached = cachedTierTwoEntries;
        if (cached != null) return cached;
        synchronized (FilterCatalog.class) {
            if (cachedTierTwoEntries == null) {
                cachedTierTwoEntries = Collections.unmodifiableList(loadTierTwoEntries());
            }
            return cachedTierTwoEntries;
        }
    }

    static void clearTierTwoCacheForTests() {
        cachedTierTwoEntries = null;
    }

    private static List<Entry> loadTierTwoEntries() {
        if (GraphicsEnvironment.isHeadless()) {
            return Collections.emptyList();
        }
        try {
            return collectTierTwoFromMenuBar(Menus.getMenuBar());
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    static List<Entry> collectTierTwoFromMenuBar(MenuBar menuBar) {
        List<Entry> out = new ArrayList<Entry>();
        if (menuBar == null) return out;
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            Menu menu = menuBar.getMenu(i);
            if (menu == null) continue;
            String label = normalizeLabel(menu.getLabel());
            if (includeWholeTopMenu(label)) {
                walkMenu(menu, label, label, out);
            } else if ("Plugins".equals(label)) {
                walkPluginsMenu(menu, label, out);
            }
        }
        return out;
    }

    private static void walkPluginsMenu(Menu menu, String path, List<Entry> out) {
        for (int i = 0; i < menu.getItemCount(); i++) {
            MenuItem item = menu.getItem(i);
            if (!(item instanceof Menu)) continue;
            String label = normalizeLabel(item.getLabel());
            if ("Filters".equals(label) || "Process".equals(label)) {
                walkMenu((Menu) item, "Tier 2", path + " > " + label, out);
            }
        }
    }

    private static void walkMenu(Menu menu, String category, String path, List<Entry> out) {
        for (int i = 0; i < menu.getItemCount(); i++) {
            MenuItem item = menu.getItem(i);
            if (item == null) continue;
            String rawLabel = item.getLabel();
            String label = normalizeLabel(rawLabel);
            if (label.length() == 0 || "-".equals(label) || shouldSkipCommand(label)) continue;
            String itemPath = path + " > " + normalizePathLabel(rawLabel);
            if (item instanceof Menu) {
                walkMenu((Menu) item, category, itemPath, out);
            } else {
                out.add(Entry.legacy(category, label, itemPath));
            }
        }
    }

    private static boolean includeWholeTopMenu(String label) {
        return "Process".equals(label) || "Image".equals(label) || "Analyze".equals(label);
    }

    private static boolean shouldSkipCommand(String label) {
        String lower = label.toLowerCase(Locale.ROOT);
        return lower.startsWith("about ")
                || lower.equals("help")
                || lower.equals("update")
                || lower.equals("refresh menus")
                || lower.equals("compile and run")
                || lower.equals("edit");
    }

    static String normalizeLabel(String label) {
        String normalized = label == null ? "" : label.trim();
        normalized = normalized.replace("&", "");
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private static String normalizePathLabel(String label) {
        String normalized = label == null ? "" : label.trim();
        normalized = normalized.replace("&", "");
        return normalized;
    }

    List<Entry> visibleEntriesForTests() {
        return new ArrayList<Entry>(visibleEntries);
    }

    List<CatalogGroup> visibleGroupsForTests() {
        return new ArrayList<CatalogGroup>(visibleGroups);
    }

    void setSearchTextForTests(String text) {
        search.setText(text == null ? "" : text);
    }

    public static final class Entry {
        public final String category;
        public final String label;
        public final OpType type;
        public final String defaultArgs;
        public final boolean stub;
        public final boolean legacy;
        public final String commandName;
        public final String menuPath;

        private Entry(String category, String label, OpType type, String defaultArgs,
                      boolean stub, boolean legacy, String commandName, String menuPath) {
            this.category = category;
            this.label = label;
            this.type = type;
            this.defaultArgs = defaultArgs == null ? "" : defaultArgs;
            this.stub = stub;
            this.legacy = legacy;
            this.commandName = commandName == null ? "" : commandName;
            this.menuPath = menuPath == null ? "" : menuPath;
        }

        static Entry fast(String category, String label, OpType type, String args) {
            return new Entry(category, label, type, args, false, false, label, "");
        }

        static Entry legacy(String category, String commandName, String menuPath) {
            return new Entry(category, commandName, OpType.UNKNOWN, "", false, true, commandName, menuPath);
        }

        public String badge() {
            return legacy ? "[legacy]" : "[fast]";
        }

        @Override public String toString() {
            return category + " - " + label;
        }
    }
}
