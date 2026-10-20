package mod.sketchlibx.settings;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ProjectResourceBean;
import com.besome.sketch.beans.ViewBean;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import a.a.a.jC;
import a.a.a.yq;
import pro.sketchware.utility.FileUtil;

public class ProjectAnalyzerEngine {

    public static class UnusedResource {
        public int type;
        public String name;
        public String detail;
        public Object reference;

        public UnusedResource(int type, String name, String detail, Object reference) {
            this.type = type;
            this.name = name;
            this.detail = detail;
            this.reference = reference;
        }
    }

    public static List<UnusedResource> scanForUnusedResources(String sc_id) {
        List<UnusedResource> unusedList = new ArrayList<>();
        
        ArrayList<ProjectFileBean> allFiles = new ArrayList<>();
        if (jC.b(sc_id).b() != null) allFiles.addAll(jC.b(sc_id).b());
        if (jC.b(sc_id).c() != null) allFiles.addAll(jC.b(sc_id).c());
        
        Set<String> usedResources = new HashSet<>();
        Set<String> usedCustomViews = new HashSet<>();

        yq javaCodeGenerator = new yq(pro.sketchware.SketchApplication.getContext(), sc_id);
        StringBuilder deepScanTextBuilder = new StringBuilder();

        for (ProjectFileBean file : allFiles) {
            String targetFileName = file.getXmlName().isEmpty() ? file.getJavaName() : file.getXmlName();
            ArrayList<ViewBean> views = jC.a(sc_id).d(targetFileName);
            if (!file.getJavaName().isEmpty()) {
                 deepScanTextBuilder.append(javaCodeGenerator.getFileSrc(file.getJavaName(), jC.b(sc_id), jC.a(sc_id), jC.c(sc_id))).append("\n");
            }


            if (views != null) {
                for (ViewBean view : views) {
                    if (view.image != null && view.image.resName != null && !view.image.resName.equals("NONE")) {
                        usedResources.add(view.image.resName);
                    }
                    if (view.layout != null && view.layout.backgroundResource != null && !view.layout.backgroundResource.equals("NONE")) {
                        usedResources.add(view.layout.backgroundResource);
                    }
                    if (view.text != null && view.text.textFont != null && !view.text.textFont.equals("default_font")) {
                        usedResources.add(view.text.textFont);
                    }
                    if (view.customView != null && !view.customView.isEmpty()) {
                        usedCustomViews.add(view.customView);
                    }
                }
            }
        }

        String basePath = FileUtil.getExternalStorageDir() + "/.sketchware/";
        File dataDir = new File(basePath + "data/" + sc_id);
        mod.hey.studios.project.ProjectSettings projectSettings = new mod.hey.studios.project.ProjectSettings(sc_id);
        boolean customJavaEnabled = projectSettings.getValue(
                mod.hey.studios.project.ProjectSettings.SETTING_ENABLE_CUSTOM_JAVA, "false").equals("true");
        boolean customManifestEnabled = projectSettings.getValue(
                mod.hey.studios.project.ProjectSettings.SETTING_ENABLE_CUSTOM_MANIFEST, "false").equals("true");

        // Scan only files that participate in the current build. Recursing through the whole
        // data directory also reads backups, history snapshots, and old generated artifacts;
        // any stale reference in those files can incorrectly mark a resource as in use.
        appendDeepScanFiles(new File(dataDir, "files/java"), deepScanTextBuilder);
        appendDeepScanFiles(new File(dataDir, "files/resource"), deepScanTextBuilder);
        if (customJavaEnabled) {
            appendDeepScanFiles(new File(dataDir, "custom_java"), deepScanTextBuilder);
        }
        if (customManifestEnabled) {
            appendDeepScanFile(new File(dataDir, "custom_manifest.xml"), deepScanTextBuilder);
        }

        String fullDeepText = deepScanTextBuilder.toString();

        ArrayList<ProjectResourceBean> images = jC.d(sc_id).b;
        if (images != null) {
            for (ProjectResourceBean img : images) {
                if (img.resName == null || img.resName.equals("app_icon") || img.resName.equals("NONE")) continue;

                if (img.resName != null && !usedResources.contains(img.resName) &&
                        !isReferenced(fullDeepText, img.resName, "@drawable/", "@mipmap/", "R.drawable.", "R.mipmap.", "getIdentifier:drawable", "getIdentifier:mipmap")) {

                    unusedList.add(new UnusedResource(1, img.resName, "Unused Image Drawable", img));
                }
            }
        }

        ArrayList<ProjectResourceBean> sounds = jC.d(sc_id).c;
        if (sounds != null) {
            for (ProjectResourceBean snd : sounds) {
                if (snd.resName != null && !usedResources.contains(snd.resName) &&
                        !isReferenced(fullDeepText, snd.resName, "@raw/", "R.raw.", "getIdentifier:raw")) {

                    unusedList.add(new UnusedResource(2, snd.resName, "Unused Audio File", snd));
                }
            }
        }

        ArrayList<ProjectResourceBean> fonts = jC.d(sc_id).d;
        if (fonts != null) {
            for (ProjectResourceBean fnt : fonts) {
                if (fnt.resName != null && !usedResources.contains(fnt.resName) &&
                        !isReferenced(fullDeepText, fnt.resName, "@font/", "R.font.", "getIdentifier:font")) {

                    unusedList.add(new UnusedResource(3, fnt.resName, "Unused Font File", fnt));
                }
            }
        }

        ArrayList<ProjectFileBean> customViews = jC.b(sc_id).c();
        if (customViews != null) {
            for (ProjectFileBean cv : customViews) {
                String rawName = cv.fileName.replace(".xml", "");

                if (!usedCustomViews.contains(cv.fileName) &&
                        !usedCustomViews.contains(cv.getXmlName()) &&
                        !isReferenced(fullDeepText, rawName, "@layout/", "R.layout.")) {

                    unusedList.add(new UnusedResource(4, cv.getXmlName(), "Unused Custom Layout", cv));
                }
            }
        }

        return unusedList;
    }

    /**
     * Match actual resource references, not arbitrary occurrences of a resource's name.
     * A plain word match creates false positives (for example a Java variable named "logo")
     * and makes it impossible to trust the unused-resource list.
     */
    private static boolean isReferenced(String fullDeepText, String resName, String... prefixes) {
        if (fullDeepText == null || fullDeepText.isEmpty() || resName == null || resName.isEmpty()) return false;
        String escapedName = java.util.regex.Pattern.quote(resName);
        for (String prefix : prefixes) {
            if (prefix.startsWith("getIdentifier:")) {
                String type = prefix.substring("getIdentifier:".length());
                // Literal dynamic lookups: getIdentifier("logo", "drawable", ...)
                java.util.regex.Pattern literalLookup = java.util.regex.Pattern.compile(
                        "getIdentifier\\s*\\(\\s*[\"']" + escapedName
                                + "[\"']\\s*,\\s*[\"']" + java.util.regex.Pattern.quote(type)
                                + "[\"']", java.util.regex.Pattern.DOTALL);
                if (literalLookup.matcher(fullDeepText).find()) return true;

                // A non-literal lookup for a resource type means the scanner cannot prove any
                // individual name is unused, so conservatively retain resources of that type.
                java.util.regex.Pattern dynamicLookup = java.util.regex.Pattern.compile(
                        "getIdentifier\\s*\\([^,]+,\\s*[\"']" + java.util.regex.Pattern.quote(type)
                                + "[\"']", java.util.regex.Pattern.DOTALL);
                if (dynamicLookup.matcher(fullDeepText).find()) return true;
                continue;
            }
            java.util.regex.Pattern reference = java.util.regex.Pattern.compile(
                    java.util.regex.Pattern.quote(prefix) + escapedName + "(?![A-Za-z0-9_])");
            if (reference.matcher(fullDeepText).find()) return true;
        }
        return false;
    }

    private static void appendDeepScanFiles(File dir, StringBuilder builder) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                appendDeepScanFiles(file, builder);
            } else {
                appendDeepScanFile(file, builder);
            }
        }
    }

    private static void appendDeepScanFile(File file, StringBuilder builder) {
        if (file == null || !file.isFile()) return;
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".xml") || name.endsWith(".java") || name.endsWith(".kt") ||
                name.endsWith(".json") || name.endsWith(".gradle") || name.endsWith(".pro")) {
            String contents = FileUtil.readFile(file.getAbsolutePath());
            if (contents != null && !contents.isEmpty()) builder.append(contents).append("\n");
        }
    }

}
