package macro.builder.analysis;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BatchMacroScanner {

    public static final String[] DIRECT_IMAGE_EXTENSIONS = {
            "tif", "tiff", "png", "jpg", "jpeg", "gif", "bmp", "ics", "ids"
    };

    public List<BatchMacroInput> scanFolder(
            File rootFolder,
            String filenameRegex,
            boolean recursive) {
        File root = requireDirectory(rootFolder);
        if (filenameRegex == null) {
            throw new IllegalArgumentException("filenameRegex must not be null");
        }

        Pattern pattern = Pattern.compile(filenameRegex);
        List<File> candidates = new ArrayList<File>();
        collectFiles(root, recursive, candidates);
        return scanFiles(root, candidates, pattern);
    }

    List<BatchMacroInput> scanFiles(File rootFolder, List<File> files, Pattern pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException("pattern must not be null");
        }
        if (files == null || files.isEmpty()) {
            return Collections.emptyList();
        }

        List<File> matches = new ArrayList<File>();
        for (File file : files) {
            if (!isDirectImageFile(file)) {
                continue;
            }
            Matcher matcher = pattern.matcher(file.getName());
            if (matcher.matches()) {
                matches.add(file);
            }
        }

        Collections.sort(matches, relativePathComparator(rootFolder));
        Map<String, BatchMacroInput> unique = new LinkedHashMap<String, BatchMacroInput>();
        for (File file : matches) {
            String key = canonicalKey(file);
            if (!unique.containsKey(key)) {
                unique.put(key, BatchMacroInput.file(file, relativePath(rootFolder, file)));
            }
        }
        return new ArrayList<BatchMacroInput>(unique.values());
    }

    public static boolean isDirectImageFile(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        String extension = extension(file);
        for (String directExtension : DIRECT_IMAGE_EXTENSIONS) {
            if (directExtension.equals(extension)) {
                return true;
            }
        }
        return false;
    }

    private static File requireDirectory(File rootFolder) {
        if (rootFolder == null) {
            throw new IllegalArgumentException("rootFolder must not be null");
        }
        if (!rootFolder.isDirectory()) {
            throw new IllegalArgumentException("rootFolder must be a directory");
        }
        return rootFolder;
    }

    private static void collectFiles(File directory, boolean recursive, List<File> files) {
        File[] children = directory.listFiles();
        if (children == null || children.length == 0) {
            return;
        }
        Arrays.sort(children, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        for (File child : children) {
            if (child.isFile()) {
                files.add(child);
            } else if (recursive && child.isDirectory()) {
                collectFiles(child, true, files);
            }
        }
    }

    private static Comparator<File> relativePathComparator(final File rootFolder) {
        return new Comparator<File>() {
            @Override public int compare(File a, File b) {
                String left = relativePath(rootFolder, a);
                String right = relativePath(rootFolder, b);
                int insensitive = left.compareToIgnoreCase(right);
                if (insensitive != 0) {
                    return insensitive;
                }
                return left.compareTo(right);
            }
        };
    }

    private static String relativePath(File rootFolder, File file) {
        if (file == null) {
            return "";
        }
        if (rootFolder == null) {
            return file.getName();
        }
        try {
            Path root = rootFolder.getCanonicalFile().toPath();
            Path child = file.getCanonicalFile().toPath();
            if (child.startsWith(root)) {
                return normalizeSeparators(root.relativize(child).toString());
            }
        } catch (IOException ioe) {
            return file.getName();
        } catch (IllegalArgumentException iae) {
            return file.getName();
        }
        return file.getName();
    }

    private static String canonicalKey(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException ioe) {
            return file.getAbsolutePath();
        }
    }

    private static String normalizeSeparators(String path) {
        return path.replace(File.separatorChar, '/');
    }

    private static String extension(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }
}
