package mod.sketchlibx.importer;

/** User choices for an Android Studio project import. */
public final class ImportOptions {
    public boolean convertJavaToBlocks = true;
    public boolean convertXmlLayoutsToVisualViews = true;
    public boolean extractCustomMethodsToMoreBlocks = true;
    public boolean importGradleDependencies = true;

    public ImportOptions copy() {
        ImportOptions copy = new ImportOptions();
        copy.convertJavaToBlocks = convertJavaToBlocks;
        copy.convertXmlLayoutsToVisualViews = convertXmlLayoutsToVisualViews;
        copy.extractCustomMethodsToMoreBlocks = extractCustomMethodsToMoreBlocks;
        copy.importGradleDependencies = importGradleDependencies;
        return copy;
    }
}
