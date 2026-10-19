package mod.sketchlibx.terminal;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class TerminalCleanup {

    public static final int CODE_OK = 0;
    public static final int CODE_FAILED = 1;
    public static final int CODE_INVALID = 2;
    public static final int CODE_NOT_CONFIRMED = 3;
    public static final int CODE_NEEDS_CONFIRMATION = 10;

    private static final Pattern PROJECT_ID = Pattern.compile("[0-9A-Za-z_-]{1,64}");
    private static final Pattern GRADLE_BACKUP_NAME = Pattern.compile("backup_gradle_[0-9]{10,16}");
    private static final Set<String> GRADLE_BACKUP_FILES = Set.of("app_build.gradle", "build.gradle", "settings.gradle", "gradle.properties");
    private static final String RESERVED_MYSC_ENTRY = "list";

    private TerminalCleanup() {
    }

    public static final class Result {
        public final int code;
        public final List<String> lines;

        Result(int code, List<String> lines) {
            this.code = code;
            this.lines = lines;
        }
    }

    public static boolean isValidProjectId(String scId) {
        return scId != null && PROJECT_ID.matcher(scId).matches() && !RESERVED_MYSC_ENTRY.equals(scId);
    }

    public static Result clearMyscCache(File storageRoot, String scId, boolean dryRun, boolean confirmed) {
        List<String> out = new ArrayList<>();
        if (!isValidProjectId(scId)) return new Result(CODE_INVALID, List.of("Invalid project id."));
        if (!dryRun && !confirmed) return new Result(CODE_NOT_CONFIRMED, List.of("Confirmation is required before anything is deleted."));
        try {
            File mysc = new File(new File(storageRoot, ".sketchware"), "mysc");
            if (!mysc.isDirectory()) {
                out.add("No build cache directory exists yet. Nothing to clear.");
                return new Result(CODE_OK, out);
            }
            Path base = mysc.toPath().toRealPath();
            Path candidate = base.resolve(scId);
            if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
                out.add("This project has no build cache. Nothing to clear.");
                return new Result(CODE_OK, out);
            }
            if (Files.isSymbolicLink(candidate) || !Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)) {
                out.add("Refusing to touch " + candidate + ": it is not a regular directory.");
                return new Result(CODE_FAILED, out);
            }
            Path target = candidate.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!base.equals(target.getParent()) || !scId.equals(target.getFileName().toString())) {
                out.add("Refusing to touch " + target + ": it is outside the project build cache location.");
                return new Result(CODE_FAILED, out);
            }

            Tally tally = new Tally();
            walk(target, true, tally);
            if (tally.files == 0 && tally.directories == 0) {
                out.add("Build cache " + target + " is already empty.");
                return new Result(CODE_OK, out);
            }
            out.add("Build cache: " + target);
            out.add("  " + tally.files + (tally.files == 1 ? " file, " : " files, ") + formatSize(tally.bytes));
            if (dryRun) return new Result(CODE_NEEDS_CONFIRMATION, out);

            Tally removed = new Tally();
            walk(target, false, removed);
            out.add("Removed " + removed.files + (removed.files == 1 ? " file (" : " files (") + formatSize(removed.bytes) + ").");
            if (removed.failures > 0) {
                out.add(removed.failures + " entries could not be removed (permission denied or in use):");
                for (String name : removed.failureNames) out.add("  " + name);
                return new Result(CODE_FAILED, out);
            }
            out.add("Build cache cleared. The next build recreates it.");
            return new Result(CODE_OK, out);
        } catch (IOException | RuntimeException e) {
            out.add("Could not clear the build cache: " + e.getMessage());
            return new Result(CODE_FAILED, out);
        }
    }

    public static Result deleteGradleBackups(File storageRoot, String scId, boolean dryRun, boolean confirmed) {
        List<String> out = new ArrayList<>();
        if (!isValidProjectId(scId)) return new Result(CODE_INVALID, List.of("Invalid project id."));
        if (!dryRun && !confirmed) return new Result(CODE_NOT_CONFIRMED, List.of("Confirmation is required before anything is deleted."));
        try {
            File dataDir = new File(new File(new File(storageRoot, ".sketchware"), "data"), scId);
            if (!dataDir.isDirectory()) {
                out.add("This project has no data directory. Nothing to delete.");
                return new Result(CODE_OK, out);
            }
            Path data = dataDir.toPath().toRealPath();
            String[] names = dataDir.list();
            if (names == null) {
                out.add("Could not read the project data directory (permission denied).");
                return new Result(CODE_FAILED, out);
            }
            Arrays.sort(names);

            List<Path> verified = new ArrayList<>();
            long totalBytes = 0;
            int skipped = 0;
            for (String name : names) {
                if (!GRADLE_BACKUP_NAME.matcher(name).matches()) continue;
                Path backup = data.resolve(name);
                String problem = verifyGradleBackup(data, backup);
                if (problem != null) {
                    out.add("Skipped " + name + ": " + problem);
                    skipped++;
                    continue;
                }
                long size = 0;
                try (DirectoryStream<Path> children = Files.newDirectoryStream(backup)) {
                    for (Path child : children) size += Files.size(child);
                }
                verified.add(backup);
                totalBytes += size;
                out.add("Gradle backup: " + name + " (" + formatSize(size) + ")");
            }
            if (verified.isEmpty()) {
                out.add(skipped == 0 ? "No Gradle backups found. Nothing to delete." : "No verified Gradle backups to delete.");
                return new Result(CODE_OK, out);
            }
            out.add(verified.size() + (verified.size() == 1 ? " backup, " : " backups, ") + formatSize(totalBytes) + " in total.");
            if (dryRun) return new Result(CODE_NEEDS_CONFIRMATION, out);

            int deleted = 0;
            int failed = 0;
            for (Path backup : verified) {
                try {
                    try (DirectoryStream<Path> children = Files.newDirectoryStream(backup)) {
                        for (Path child : children) Files.delete(child);
                    }
                    Files.delete(backup);
                    deleted++;
                } catch (IOException e) {
                    failed++;
                    out.add("Could not delete " + backup.getFileName() + ": " + e.getMessage());
                }
            }
            out.add("Deleted " + deleted + (deleted == 1 ? " Gradle backup." : " Gradle backups."));
            return new Result(failed == 0 ? CODE_OK : CODE_FAILED, out);
        } catch (IOException | RuntimeException e) {
            out.add("Could not delete Gradle backups: " + e.getMessage());
            return new Result(CODE_FAILED, out);
        }
    }

    private static String verifyGradleBackup(Path dataDir, Path backup) throws IOException {
        if (Files.isSymbolicLink(backup)) return "it is a symbolic link";
        if (!Files.isDirectory(backup, LinkOption.NOFOLLOW_LINKS)) return "it is not a directory";
        Path real = backup.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!dataDir.equals(real.getParent())) return "it is outside the project data directory";
        try (DirectoryStream<Path> children = Files.newDirectoryStream(real)) {
            for (Path child : children) {
                BasicFileAttributes attributes = Files.readAttributes(child, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!attributes.isRegularFile()) return "it contains " + child.getFileName() + ", which is not a plain file";
                if (!GRADLE_BACKUP_FILES.contains(child.getFileName().toString())) {
                    return "it contains " + child.getFileName() + ", which is not a known Gradle file";
                }
            }
        }
        return null;
    }

    private static final class Tally {
        long files;
        long directories;
        long bytes;
        int failures;
        final List<String> failureNames = new ArrayList<>();

        void failed(Path path) {
            failures++;
            if (failureNames.size() < 3) failureNames.add(path.getFileName().toString());
        }
    }

    private static void walk(Path root, boolean countOnly, Tally tally) throws IOException {
        Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (countOnly) {
                    tally.files++;
                    if (attributes.isRegularFile()) tally.bytes += attributes.size();
                    return FileVisitResult.CONTINUE;
                }
                try {
                    long size = attributes.isRegularFile() ? attributes.size() : 0;
                    Files.delete(file);
                    tally.files++;
                    tally.bytes += size;
                } catch (IOException e) {
                    tally.failed(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                tally.failed(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                if (dir.equals(root)) return FileVisitResult.CONTINUE;
                if (countOnly) {
                    tally.directories++;
                    return FileVisitResult.CONTINUE;
                }
                try {
                    Files.delete(dir);
                } catch (IOException e) {
                    tally.failed(dir);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double value = bytes / 1024.0;
        String[] units = {"KB", "MB", "GB"};
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format(Locale.US, "%.1f %s", value, units[unit]);
    }
}
