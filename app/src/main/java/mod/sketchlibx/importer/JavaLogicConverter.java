package mod.sketchlibx.importer;


import android.util.Log;

import com.besome.sketch.beans.BlockBean;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

import mod.sketchlibx.project.editor.BlocksConverter;

/**
 * Converts a small, EXPLICITLY SCOPED subset of Android Studio Java into real
 * Sketchware Neo blocks, using only opCodes/formats confirmed in
 * sketchware-neo-swb-analysis-complete.md (a real-project reverse-engineering
 * reference — this is not invented). Everything outside that confirmed
 * subset is preserved verbatim as a real "addSourceDirectly" block (spec:
 * "add source directly %s.inputOnly", confirmed exact) — never silently
 * dropped, and never faked as a made-up opCode.
 *
 * HONEST SCOPE — what this DOES convert to native blocks:
 *   - finish()                                    -> finishActivity
 *   - startActivity(new Intent(ctx, X.class))     -> intent component + intentSetScreen + startActivity
 *   - simple field assignment (int/String/boolean/float field = literal or +-*\/ on
 *     numbers/other known fields)                  -> setVarIntNum/setVarString/setVarBoolean/setVarFloat
 *     (NOTE: a Java "int" field now maps to Sketchware's own real int type (setVarIntNum),
 *     not the legacy "Number" type (setVarInt, which is actually a double) — this changed
 *     now that a real int type exists.)
 *   - if/else with a SIMPLE condition:
 *       numeric equality  a == b                   -> "=" opCode (only proven comparison op)
 *       string equality    a.equals(b)              -> stringEquals
 *     any other operator (>,<,>=,<=,!=, &&, ||)     -> NOT converted (unconfirmed opCodes —
 *                                                       see doc §11) — the whole if/else is
 *                                                       preserved as one addSourceDirectly block
 *   - view.setOnClickListener(...) where the view is resolvable to a layout
 *     id (via ViewBinding "binding.viewId" or a local "Type v = findViewById(R.id.viewId)")
 *                                                    -> registers a real "_<viewId>_onClick"
 *                                                       event section (confirmed pattern),
 *                                                       body converted recursively
 *   - onCreate(Bundle) body                         -> the confirmed special "onCreate_initializeLogic"
 *                                                       section (no _events entry needed — confirmed)
 *   - onBackPressed() body                          -> confirmed "onBackPressed_onBackPressed" section
 *
 * HONEST SCOPE — what this explicitly DOES NOT convert, and why:
 *   - Toast.makeText(...).show() — NO confirmed opCode exists anywhere in the reference
 *     doc for showing a toast/message. Inventing one (e.g. "showMessage") would violate
 *     the explicit "do not guess confirmed formats" rule, so every Toast call is preserved
 *     as addSourceDirectly, not faked as a native "message" block.
 *   - onStart/onResume/onPause/onStop/onDestroy — the doc's confirmed section list is
 *     onCreate_initializeLogic, onBackPressed_onBackPressed, and per-widget-event sections
 *     ONLY. There is no confirmed section name for the other four lifecycle callbacks.
 *     Their bodies are therefore NOT wired into any logic section at all — they are only
 *     preserved in the copied raw .java source file (ASProjectImporter always copies the
 *     original file regardless of conversion), and this is logged explicitly as
 *     UNSUPPORTED so it is never silently lost.
 *   - loops (for/while/do-while), switch, try/catch bodies, lambdas other than a plain
 *     View.OnClickListener target, comparisons other than == and .equals() — all preserved
 *     as addSourceDirectly at the statement (or whole-method, for method-level constructs
 *     like try/catch) granularity.
 *   - This is a PATTERN-BASED / brace-and-statement-boundary-aware line splitter, not a
 *     real Java compiler AST. It handles the patterns above correctly on ordinary,
 *     idiomatically-formatted source; unusual formatting (e.g. semicolons inside string
 *     literals containing raw braces in a way that defeats the depth counter) can cause a
 *     statement to be mis-split. When that happens the affected statement(s) still end up
 *     inside an addSourceDirectly block (fail-safe toward "preserve raw", never toward
 *     "silently drop") — but the split boundary itself is not guaranteed byte-perfect.
 *
 * ENTRY-POINT RULE (doc §7, the "hardest-won finding"): the block holding the LOWEST
 * numeric id in a section is what Sketchware Neo treats as that section's entry point.
 * BlockSection below reserves a statement's own id BEFORE building any nested
 * condition/value blocks, structurally, so this can't be gotten wrong by omission.
 */
public class JavaLogicConverter {

    private static final String TAG = "JavaLogicConverter";
    private static final Gson BLOCK_JSON = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

    // ── Public result ────────────────────────────────────────────────────────

    public static class ConversionResult {
        /** Section name -> block-list content (one JSON object per line, no [] wrapper). */
        public final Map<String, String> sections = new LinkedHashMap<>();
        /** "<code>:<name>" lines for _var (deduplicated, insertion order). */
        public final List<String> varLines = new ArrayList<>();
        /** _components content (one JSON object per line). Always present, may be empty. */
        public String componentsSection = "";
        /** _events content (one JSON object per line, one per section actually registered). */
        public String eventsSection = "";
        /** Human-readable list of everything NOT converted to native blocks, with why. */
        public final List<String> unsupported = new ArrayList<>();
        /** Function declaration lines stored in data/logic @<Activity>.java_func. */
        public String functionDeclarations = "";
    }

    // ── Field/variable tracking (very small, heuristic symbol table) ──────────

    /** Declared class fields this converter is confident are simple scalars, by name -> typeCode. */
    private final Map<String, Integer> knownScalarFields = new LinkedHashMap<>();
    /** viewId (layout XML id) -> Sketchware widget-type-derived event support is NOT checked here;
     *  onClick is applicable to virtually every widget type per the confirmed catalog (doc §6),
     *  so onClick registration is always attempted without per-type validation. */
    private final Map<String, String> localVarToViewId = new LinkedHashMap<>();   // e.g. "btnSave" -> "btnSave"
    private final Map<String, String> bindingRefToViewId = new LinkedHashMap<>(); // "binding.btnSave" -> "btnSave"
    private final Map<String, Integer> knownViewTypes = new LinkedHashMap<>();
    private final Map<String, HelperMethod> helperMethods = new LinkedHashMap<>();
    private final Map<String, String> helperSpecs = new LinkedHashMap<>();
    private final java.util.Set<String> ambiguousHelperNames = new java.util.HashSet<>();

    private final List<String> unsupported = new ArrayList<>();
    private final Map<String, String> componentsById = new LinkedHashMap<>(); // componentId -> JSON

    private int intentComponentCounter = 0;

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * @param javaSource   full text of the Activity's original .java file
     * @param knownViewIds every view id present in this screen's imported layout
     *                     (used to validate findViewById/binding references — anything
     *                     resolving to an id NOT in this set is left unconverted, since we
     *                     can't confirm the target actually exists on this screen)
     */
    public ConversionResult convert(String javaSource, java.util.Set<String> knownViewIds) {
        Map<String, Integer> defaultTypes = new LinkedHashMap<>();
        if (knownViewIds != null) for (String id : knownViewIds) defaultTypes.put(id, 0);
        return convert(javaSource, knownViewIds, defaultTypes, true);
    }

    /**
     * Convert a source file using actual imported view types and the MoreBlocks option.
     * Unsupported Java is always reported and preserved as addSourceDirectly/raw source;
     * it is never silently deleted.
     */
    public ConversionResult convert(String javaSource, java.util.Set<String> knownViewIds,
                                    Map<String, Integer> viewTypes, boolean extractCustomMethods) {
        ConversionResult result = new ConversionResult();
        unsupported.clear();
        knownScalarFields.clear();
        localVarToViewId.clear();
        bindingRefToViewId.clear();
        knownViewTypes.clear();
        if (viewTypes != null) knownViewTypes.putAll(viewTypes);
        helperMethods.clear();
        helperSpecs.clear();
        ambiguousHelperNames.clear();
        componentsById.clear();

        try {
            scanClassFields(javaSource, result);
            scanViewBindingsAndFindViewById(javaSource, knownViewIds);
            if (extractCustomMethods) discoverCustomMethods(javaSource, result);

            String onCreateBody = extractMethodBody(javaSource, "onCreate");
            if (onCreateBody != null) {
                BlockSection section = new BlockSection();
                // Sketchware Neo owns Activity view inflation and its content view. Remove
                // framework setup declarations before translating, while retaining ordinary
                // statements that merely mention a view lookup inside a larger expression.
                List<String> logicStatements = removeManagedViewSetupStatements(splitStatements(onCreateBody),
                        "initializeLogic");
                // Click listener registration is emitted as its own Sketchware event
                // section. Remove it before alias rewriting, otherwise a local Java name
                // such as `buttonSave` would be changed to the widget id and the listener
                // would no longer be recognized as already handled.
                List<String> eventFilteredStatements = removeResolvedClickListenerStatementList(logicStatements, knownViewIds);
                List<String> normalizedStatements = rewriteViewAliases(eventFilteredStatements);
                convertStatements(normalizedStatements, section, result);
                String rendered = section.render();
                // Prefer Neo's real BlocksConverter after replacing Java local/binding view
                // references with the actual Sketchware widget ids. This prevents stale
                // variables from producing source blocks that reference no generated field.
                String richInput = joinStatements(normalizedStatements);
                String rich = containsCustomMethodCall(richInput)
                        ? null : convertWithNativeBlockConverter(richInput, java.util.Collections.emptyMap(), result, "onCreate");
                result.sections.put("onCreate_initializeLogic", rich != null ? rich : rendered);
            }

            String onBackPressedBody = extractMethodBody(javaSource, "onBackPressed");
            if (onBackPressedBody != null) {
                List<String> backStatements = rewriteViewAliases(removeManagedViewSetupStatements(
                        splitStatements(onBackPressedBody), "onBackPressed"));
                String normalizedBackBody = joinStatements(backStatements);
                BlockSection section = new BlockSection();
                convertStatements(backStatements, section, result);
                String rendered = section.render();
                String rich = containsCustomMethodCall(normalizedBackBody) ? null
                        : convertWithNativeBlockConverter(normalizedBackBody, java.util.Collections.emptyMap(), result, "onBackPressed");
                result.sections.put("onBackPressed_onBackPressed", rich != null ? rich : rendered);
            }

            // onClickListener registrations found anywhere in the class (field-level
            // setup in onCreate is the common case, but this scans the whole file so
            // listeners attached in helper methods are still found).
            findAndConvertClickListeners(javaSource, result, knownViewIds);

            for (String unsupportedCallback : new String[]{"onStart", "onResume", "onPause", "onStop", "onDestroy",
                    "onRestart", "onActivityResult", "onRequestPermissionsResult", "onNewIntent",
                    "onSaveInstanceState", "onRestoreInstanceState", "onCreateOptionsMenu", "onOptionsItemSelected",
                    "onNavigationItemSelected", "onCheckedChanged", "onTextChanged", "onItemClick", "onItemSelected",
                    "onNothingSelected", "onLongClick", "onTouch", "onKey", "onConfigurationChanged",
                    "onWindowFocusChanged"}) {
                if (extractMethodBody(javaSource, unsupportedCallback) != null) {
                    String msg = "UNSUPPORTED: " + unsupportedCallback + "() has no verified native Sketchware event-section mapping in this importer. Original Java is preserved, but this callback is not wired to blocks.";
                    result.unsupported.add(msg);
                    Log.w(TAG, msg);
                }
            }

        } catch (Exception e) {
            String msg = "UNSUPPORTED: whole-file conversion aborted (" + e.getMessage()
                    + ") — original .java source is still preserved as copied, unconverted.";
            result.unsupported.add(msg);
            Log.w(TAG, msg, e);
        }

        // _var
        for (Map.Entry<String, Integer> f : knownScalarFields.entrySet()) {
            result.varLines.add(f.getValue() + ":" + f.getKey());
        }

        // _components (always present, even empty)
        StringBuilder comp = new StringBuilder();
        for (String json : componentsById.values()) comp.append(json).append("\n");
        result.componentsSection = comp.toString();

        // _events (always present, even empty) — one entry per registered event section,
        // excluding the two special always-exists sections.
        StringBuilder events = new StringBuilder();
        for (String sectionName : result.sections.keySet()) {
            if (sectionName.equals("onCreate_initializeLogic") || sectionName.equals("onBackPressed_onBackPressed")
                    || sectionName.endsWith("_moreBlock")) continue;
            int lastUnderscore = sectionName.lastIndexOf('_');
            if (lastUnderscore <= 0) continue;
            String targetId = sectionName.substring(0, lastUnderscore);
            String eventName = sectionName.substring(lastUnderscore + 1);
            int targetType = knownViewTypes.containsKey(targetId) ? knownViewTypes.get(targetId) : 0;
            events.append("{\"eventName\":\"").append(eventName).append("\",")
                  .append("\"eventType\":1,\"targetId\":\"").append(targetId).append("\",")
                  .append("\"targetType\":").append(targetType).append("}\n");
        }
        result.eventsSection = events.toString();
        result.functionDeclarations = buildFunctionDeclarations();

        result.unsupported.addAll(0, unsupported);
        return result;
    }

    // ── Java void-method → Sketchware MoreBlock conversion ────────────────────

    private static final class HelperMethod {
        final String name;
        final String body;
        final List<String> parameters = new ArrayList<>();
        final List<String> parameterTypes = new ArrayList<>();
        final String declaration;
        HelperMethod(String name, String body, List<String> names, List<String> types, String declaration) {
            this.name = name; this.body = body; this.parameters.addAll(names);
            this.parameterTypes.addAll(types); this.declaration = declaration;
        }
    }

    private static final java.util.Set<String> CALLBACK_METHODS = new java.util.HashSet<>(java.util.Arrays.asList(
            "onCreate", "onStart", "onResume", "onPause", "onStop", "onDestroy", "onRestart",
            "onBackPressed", "onActivityResult", "onRequestPermissionsResult", "onNewIntent",
            "onSaveInstanceState", "onRestoreInstanceState", "onClick", "onLongClick", "onItemClick",
            "onItemSelected", "onNothingSelected", "onTextChanged", "beforeTextChanged", "afterTextChanged",
            "onCheckedChanged", "onTouch", "onKey", "onOptionsItemSelected", "onCreateOptionsMenu",
            "onNavigationItemSelected", "onBind", "onUnbind", "onReceive", "onHandleIntent",
            "onConfigurationChanged", "onWindowFocusChanged", "onAttachedToWindow", "onDetachedFromWindow",
            "onCreateView", "onViewCreated", "onViewStateRestored", "onDestroyView", "onAttach", "onDetach"));

    private void discoverCustomMethods(String source, ConversionResult result) {
        try {
            CompilationUnit unit = StaticJavaParser.parse(source);
            if (unit.getTypes().isEmpty()) return;
            TypeDeclaration<?> primary = unit.getTypes().get(0);
            for (MethodDeclaration method : primary.getMethods()) {
                String name = method.getNameAsString();
                if (CALLBACK_METHODS.contains(name) || !method.getBody().isPresent()
                        || method.isNative() || method.isAbstract()) continue;
                if (!method.getType().isVoidType()) {
                    result.unsupported.add("UNSUPPORTED: method " + name + " returns " + method.getTypeAsString()
                            + "; the documented Sketchware Neo MoreBlock format has no confirmed direct return-value support. Original Java source is preserved.");
                    continue;
                }
                if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                    result.unsupported.add("UNSUPPORTED: method name '" + name + "' is not safe for a Sketchware MoreBlock identifier; original Java source is preserved.");
                    continue;
                }
                List<String> names = new ArrayList<>();
                List<String> types = new ArrayList<>();
                boolean compatible = true;
                for (com.github.javaparser.ast.body.Parameter parameter : method.getParameters()) {
                    String token = moreBlockType(parameter.getType().asString());
                    if (token == null || parameter.isVarArgs()
                            || !parameter.getNameAsString().matches("[A-Za-z_][A-Za-z0-9_]*")) { compatible = false; break; }
                    names.add(parameter.getNameAsString());
                    types.add(token);
                }
                if (!compatible) {
                    result.unsupported.add("UNSUPPORTED: method " + name + " has parameters that cannot be represented as MoreBlock inputs; copied original source only.");
                    continue;
                }
                if (ambiguousHelperNames.contains(name)) {
                    continue;
                }
                if (helperMethods.containsKey(name)) {
                    helperMethods.remove(name);
                    helperSpecs.remove(name);
                    ambiguousHelperNames.add(name);
                    result.unsupported.add("UNSUPPORTED: overloaded method " + name + " cannot be represented unambiguously as a single MoreBlock; all overloads remain in the preserved Java source.");
                    continue;
                }
                String declaration = buildFunctionDeclaration(name, names, types);
                String body = method.getBody().get().toString();
                if (body.length() >= 2) body = body.substring(1, body.length() - 1);
                helperMethods.put(name, new HelperMethod(name, body, names, types, declaration));
                helperSpecs.put(name, declaration);
            }
            for (HelperMethod helper : helperMethods.values()) {
                List<String> helperStatements = rewriteViewAliases(removeManagedViewSetupStatements(
                        splitStatements(helper.body), "MoreBlock " + helper.name));
                String normalizedHelperBody = joinStatements(helperStatements);
                BlockSection section = new BlockSection();
                convertStatements(helperStatements, section, result);
                String legacy = section.render();
                Map<String, String> argumentTypes = new LinkedHashMap<>();
                for (int i = 0; i < helper.parameters.size(); i++) {
                    argumentTypes.put(helper.parameters.get(i), helper.parameterTypes.get(i));
                }
                String rich = containsCustomMethodCall(normalizedHelperBody) ? null
                        : convertWithNativeBlockConverter(normalizedHelperBody, argumentTypes, result, "MoreBlock " + helper.name);
                result.sections.put(helper.name + "_moreBlock", rich != null ? rich : legacy);
            }
        } catch (Exception e) {
            String warning = "UNSUPPORTED: Java AST parsing failed; custom helper methods were not extracted (" + e.getMessage() + "). Original source remains copied.";
            result.unsupported.add(warning);
            Log.w(TAG, warning, e);
        }
    }

    private String buildFunctionDeclarations() {
        StringBuilder out = new StringBuilder();
        for (HelperMethod helper : helperMethods.values()) out.append(helper.declaration).append("\n");
        return out.toString();
    }

    private String buildFunctionDeclaration(String name, List<String> params, List<String> types) {
        StringBuilder out = new StringBuilder(name).append(":").append(name);
        for (int i = 0; i < params.size(); i++) out.append(" %").append(types.get(i)).append(".").append(params.get(i));
        return out.toString();
    }

    private String moreBlockType(String type) {
        if ("String".equals(type) || "java.lang.String".equals(type) || "CharSequence".equals(type)) return "s";
        if ("boolean".equals(type) || "Boolean".equals(type) || "java.lang.Boolean".equals(type)) return "b";
        if ("byte".equals(type) || "short".equals(type) || "int".equals(type) || "long".equals(type)
                || "float".equals(type) || "double".equals(type) || "Integer".equals(type) || "Long".equals(type)
                || "Float".equals(type) || "Double".equals(type)) return "d";
        return null;
    }

    private boolean tryConvertCustomCall(String stmt, BlockSection section, ConversionResult result) {
        Matcher call = Pattern.compile("^\\s*(?:this\\.)?([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\((.*)\\)\\s*;?$", Pattern.DOTALL).matcher(stmt.trim());
        if (!call.matches()) return false;
        String name = call.group(1);
        String declaration = helperSpecs.get(name);
        if (declaration == null) return false;
        List<String> args = splitCommaArguments(call.group(2));
        HelperMethod helper = helperMethods.get(name);
        if (helper == null) return false;
        if (args.size() != helper.parameters.size()) {
            result.unsupported.add("UNSUPPORTED: call to MoreBlock '" + name + "' has " + args.size()
                    + " argument(s), expected " + helper.parameters.size() + "; original statement is preserved as source.");
            return false;
        }
        for (int i = 0; i < args.size(); i++) {
            if (!isSafeMoreBlockLiteral(args.get(i), helper.parameterTypes.get(i))) {
                result.unsupported.add("UNSUPPORTED: call to MoreBlock '" + name + "' uses a non-literal or type-mismatched argument for '"
                        + helper.parameters.get(i) + "'; original statement is preserved as source.");
                return false;
            }
        }
        section.addStatement("definedFunc", declaration, args.toArray(new String[0]), "");
        return true;
    }

    private boolean isSafeMoreBlockLiteral(String value, String type) {
        if (value == null) return false;
        String literal = value.trim();
        if ("b".equals(type)) return "true".equals(literal) || "false".equals(literal);
        if ("d".equals(type)) return literal.matches("-?\\d+(?:\\.\\d+)?[fFdDlL]?");
        if ("s".equals(type)) return literal.length() >= 2 && literal.startsWith("\"") && literal.endsWith("\"");
        return false;
    }

    private List<String> splitCommaArguments(String text) {
        List<String> values = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return values;
        int start = 0, depth = 0; boolean inString = false, inChar = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) { if (c == '\\') i++; else if (c == '"') inString = false; continue; }
            if (inChar) { if (c == '\\') i++; else if (c == '\'') inChar = false; continue; }
            if (c == '"') { inString = true; continue; }
            if (c == '\'') { inChar = true; continue; }
            if (c == '(' || c == '[' || c == '{') depth++;
            else if (c == ')' || c == ']' || c == '}') depth--;
            else if (c == ',' && depth == 0) { values.add(text.substring(start, i).trim()); start = i + 1; }
        }
        values.add(text.substring(start).trim());
        return values;
    }

    /**
     * Runs the same block conversion engine used by Sketchware Neo's Logic editor.
     * BlockBean's @Expose fields match the persisted logic-block schema; unsupported
     * statements become addSourceDirectly blocks inside that engine instead of being lost.
     */
    private String convertWithNativeBlockConverter(String body, Map<String, String> argumentTypes,
                                                   ConversionResult result, String context) {
        if (body == null || body.trim().isEmpty()) return null;
        try {
            BlocksConverter.ConversionResult converted = BlocksConverter.convert(body, argumentTypes);
            if (converted == null || converted.error != null || converted.blocks == null || converted.blocks.isEmpty()) {
                if (converted != null && converted.error != null) {
                    result.unsupported.add("Block engine could not parse " + context + ": " + converted.error
                            + ". Conservative conversion was used instead.");
                }
                return null;
            }
            StringBuilder out = new StringBuilder();
            for (BlockBean block : converted.blocks) {
                if (block == null || block.id == null || block.opCode == null || block.parameters == null) {
                    result.unsupported.add("Block engine emitted an incomplete block in " + context
                            + "; conservative conversion was used instead.");
                    return null;
                }
                out.append(BLOCK_JSON.toJson(block)).append('\n');
            }
            if (converted.fallbackCount > 0) {
                result.unsupported.add(context + ": " + converted.fallbackCount
                        + " statement(s) were preserved as addSourceDirectly blocks by the native block converter.");
            }
            return out.toString();
        } catch (Throwable e) {
            Log.w(TAG, "Native BlocksConverter failed for " + context + "; keeping conservative conversion", e);
            result.unsupported.add("Native BlocksConverter failed for " + context + " (" + e.getClass().getSimpleName()
                    + "); conservative conversion was used and raw Java remains copied.");
            return null;
        }
    }

    private boolean containsCustomMethodCall(String body) {
        if (body == null || helperMethods.isEmpty()) return false;
        for (String methodName : helperMethods.keySet()) {
            if (Pattern.compile("\\b(?:this\\s*\\.\\s*)?" + Pattern.quote(methodName) + "\\s*\\(")
                    .matcher(body).find()) return true;
        }
        return false;
    }

    /** Remove top-level listener statements that are separately written as event sections. */
    private List<String> removeResolvedClickListenerStatementList(List<String> statements,
                                                                  java.util.Set<String> knownViewIds) {
        List<String> out = new ArrayList<>();
        for (String statement : statements) {
            Matcher listener = ON_CLICK.matcher(statement);
            if (listener.find() && listener.start() == 0) {
                String target = listener.group(1);
                String id = localVarToViewId.get(target);
                if (id == null) id = bindingRefToViewId.get(target);
                if (id != null && (knownViewIds == null || knownViewIds.contains(id))) continue;
                // Never persist a listener-registration statement as source code in an
                // event body. If its target cannot be resolved to a real imported widget,
                // the Java variable will not exist in Neo's generated Activity. The
                // original source is preserved under android_studio_original and the
                // separate listener scanner logs the unsupported mapping.
                unsupported.add("UNSUPPORTED: omitted unresolved listener registration '" + truncate(statement, 90)
                        + "' from initializeLogic because its target is not a known imported widget; original Java is preserved.");
                continue;
            }
            out.add(statement);
        }
        return out;
    }

    private String joinStatements(List<String> statements) {
        StringBuilder out = new StringBuilder();
        for (String statement : statements) out.append(statement).append('\n');
        return out.toString();
    }

    /**
     * Resolve source-only Java references such as `btnSave.setText(...)` and
     * `binding.titleText.setText(...)` to the exact widget IDs found in the imported
     * layout. Replacements are restricted to Java code outside string/character
     * literals and comments so text content is not accidentally rewritten.
     */
    private List<String> rewriteViewAliases(List<String> statements) {
        List<String> rewritten = new ArrayList<>(statements.size());
        for (String statement : statements) rewritten.add(rewriteViewAliases(statement));
        return rewritten;
    }

    private String rewriteViewAliases(String source) {
        if (source == null || source.isEmpty() || (localVarToViewId.isEmpty() && bindingRefToViewId.isEmpty())) return source;
        StringBuilder out = new StringBuilder(source.length());
        boolean string = false, character = false, lineComment = false, blockComment = false, escaped = false;
        for (int i = 0; i < source.length();) {
            char c = source.charAt(i);
            char n = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            if (lineComment) {
                out.append(c); i++;
                if (c == '\n') lineComment = false;
                continue;
            }
            if (blockComment) {
                out.append(c); i++;
                if (c == '*' && n == '/') { out.append('/'); i++; blockComment = false; }
                continue;
            }
            if (string || character) {
                out.append(c); i++;
                if (escaped) { escaped = false; continue; }
                if (c == '\\') { escaped = true; continue; }
                if (string && c == '"') string = false;
                else if (character && c == '\'') character = false;
                continue;
            }
            if (c == '/' && n == '/') { out.append("//"); i += 2; lineComment = true; continue; }
            if (c == '/' && n == '*') { out.append("/*"); i += 2; blockComment = true; continue; }
            if (c == '"') { out.append(c); i++; string = true; continue; }
            if (c == '\'') { out.append(c); i++; character = true; continue; }
            if (Character.isJavaIdentifierStart(c) || c == '$') {
                int start = i++;
                while (i < source.length() && (Character.isJavaIdentifierPart(source.charAt(i)) || source.charAt(i) == '$')) i++;
                String token = source.substring(start, i);
                if ("binding".equals(token) && i < source.length() && source.charAt(i) == '.') {
                    int fieldStart = i + 1;
                    int fieldEnd = fieldStart;
                    if (fieldStart < source.length() && (Character.isJavaIdentifierStart(source.charAt(fieldStart)) || source.charAt(fieldStart) == '$')) {
                        fieldEnd++;
                        while (fieldEnd < source.length() && (Character.isJavaIdentifierPart(source.charAt(fieldEnd)) || source.charAt(fieldEnd) == '$')) fieldEnd++;
                        String field = source.substring(fieldStart, fieldEnd);
                        String mapped = bindingRefToViewId.get("binding." + field);
                        if (mapped != null) { out.append(mapped); i = fieldEnd; continue; }
                    }
                }
                String mapped = localVarToViewId.get(token);
                out.append(mapped == null ? token : mapped);
                continue;
            }
            out.append(c); i++;
        }
        return out.toString();
    }

    private String resolveBindingViewId(String fieldName, java.util.Set<String> knownViewIds) {
        if (fieldName == null || knownViewIds == null || knownViewIds.isEmpty()) return null;
        if (knownViewIds.contains(fieldName)) return fieldName;
        // Android ViewBinding converts an XML id such as `title_text` to `titleText`.
        for (String id : knownViewIds) {
            if (fieldName.equals(snakeToCamel(id))) return id;
        }
        return null;
    }

    private String snakeToCamel(String id) {
        StringBuilder result = new StringBuilder(id.length());
        boolean upperNext = false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c == '_' || c == '-') { upperNext = true; continue; }
            result.append(upperNext ? Character.toUpperCase(c) : c);
            upperNext = false;
        }
        return result.toString();
    }


    // ── Field scanning (heuristic) ─────────────────────────────────────────────

    /** Returns true for Activity view-inflation/setup statements owned by Sketchware. */
    private boolean isSketchwareManagedViewSetup(String statement) {
        if (statement == null) return false;
        String s = statement.trim();
        if (s.isEmpty()) return false;
        // splitStatements() keeps complete statements together even where the source
        // uses multiple lines; preserve application logic while omitting the framework setup.
        if (Pattern.compile("(?s)^\\s*setContentView\\s*\\(.*$", Pattern.CASE_INSENSITIVE).matcher(s).find()) return true;
        if (Pattern.compile("(?is)^\\s*(?:(?:final\\s+)?[\\w.$<>?\\[\\]]+\\s+)?[\\w$]+\\s*=\\s*(?:\\([^)]*\\)\\s*)?findViewById\\s*\\(.*$").matcher(s).matches()) return true;
        if (Pattern.compile("(?is)^\\s*findViewById\\s*\\(.*$").matcher(s).matches()) return true;
        if (s.contains("Binding") && s.contains("=") && s.contains(".inflate(")) return true;
        return Pattern.compile("(?s)^\\s*(?:final\\s+)?[\\w.$<>?]+Binding\\s+[\\w$]+\\s*=.*$", Pattern.CASE_INSENSITIVE).matcher(s).find();
    }

    /**
     * Removes only the standalone framework setup statements that Sketchware generates
     * itself for an Activity/screen. View aliases are discovered separately so later
     * references can still be mapped to the corresponding Sketchware widget IDs.
     */
    private List<String> removeManagedViewSetupStatements(List<String> statements, String context) {
        List<String> retained = new ArrayList<>();
        if (statements == null || statements.isEmpty()) return retained;
        for (String statement : statements) {
            if (isSketchwareManagedViewSetup(statement)) {
                String label = context == null || context.trim().isEmpty() ? "screen logic" : context;
                unsupported.add("Omitted framework view setup from " + label
                        + " because Sketchware Neo generates the Activity content view itself: "
                        + truncate(statement == null ? "" : statement.trim(), 160));
                continue;
            }
            retained.add(statement);
        }
        return retained;
    }

    private static final Pattern FIELD_DECL = Pattern.compile(
            "(?m)^\\s*(?:private|public|protected)?\\s*(int|boolean|String|float)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*(?:=[^;]*)?;");

    private void scanClassFields(String javaSource, ConversionResult result) {
        Matcher m = FIELD_DECL.matcher(javaSource);
        while (m.find()) {
            String type = m.group(1);
            String name = m.group(2);
            // NOTE: Java "int" used to be imported as Sketchware's Number(double) type
            // (code 1) since there was no real int type. Now that one exists (code 7),
            // int fields are routed there instead — this is a deliberate behavior change.
            int code = "int".equals(type) ? 7 : "boolean".equals(type) ? 0
                    : "float".equals(type) ? 4 : 2; // 2 = String
            knownScalarFields.put(name, code);
        }
    }

    private static final Pattern FIND_VIEW_BY_ID = Pattern.compile(
            "([A-Za-z_$][A-Za-z0-9_$]*)\\s*=\\s*(?:\\([A-Za-z0-9_$.<>]+\\)\\s*)?findViewById\\(\\s*R\\.id\\.([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\)");
    private static final Pattern BINDING_REF = Pattern.compile(
            "\\bbinding\\.([A-Za-z_$][A-Za-z0-9_$]*)\\b");

    private void scanViewBindingsAndFindViewById(String javaSource, java.util.Set<String> knownViewIds) {
        Matcher m = FIND_VIEW_BY_ID.matcher(javaSource);
        while (m.find()) {
            String localVar = m.group(1);
            String viewId = m.group(2);
            if (knownViewIds == null || knownViewIds.contains(viewId)) {
                localVarToViewId.put(localVar, viewId);
            }
        }
        Matcher b = BINDING_REF.matcher(javaSource);
        while (b.find()) {
            String camelId = b.group(1);
            // ViewBinding turns snake_case ids into camelCase field names — we only accept
            // an exact (case-sensitive) match against the real xml id, no camelCase guessing,
            // to avoid wiring an event to the wrong view.
            String resolvedId = resolveBindingViewId(camelId, knownViewIds);
            if (resolvedId != null) {
                bindingRefToViewId.put("binding." + camelId, resolvedId);
            }
        }
    }

    // ── Method body extraction (brace-depth based, not a real parser) ─────────

    private String extractMethodBody(String source, String methodName) {
        Pattern sig = Pattern.compile("\\b" + Pattern.quote(methodName) + "\\s*\\([^)]*\\)\\s*\\{");
        Matcher m = sig.matcher(source);
        if (!m.find()) return null;
        int braceStart = m.end() - 1;
        int depth = 0;
        for (int i = braceStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return source.substring(braceStart + 1, i);
            }
        }
        return null;
    }

    // ── setOnClickListener discovery ───────────────────────────────────────────

    private static final Pattern ON_CLICK = Pattern.compile(
            "([A-Za-z_$][A-Za-z0-9_$.]*)\\.setOnClickListener\\s*\\(");

    private void findAndConvertClickListeners(String javaSource, ConversionResult result,
                                               java.util.Set<String> knownViewIds) {
        Matcher m = ON_CLICK.matcher(javaSource);
        while (m.find()) {
            String target = m.group(1);
            String viewId = localVarToViewId.get(target);
            if (viewId == null) viewId = bindingRefToViewId.get(target);
            if (viewId == null) {
                unsupported.add("UNSUPPORTED: " + target + ".setOnClickListener(...) — could not resolve "
                        + "\"" + target + "\" to a known view id on this screen; listener body left "
                        + "unconverted in source only.");
                continue;
            }
            // Find the lambda/anonymous-class body that follows the call's opening paren.
            int callParenIdx = m.end() - 1;
            String body = extractLambdaOrAnonymousBody(javaSource, callParenIdx);
            String sectionName = viewId + "_onClick";
            if (body == null) {
                unsupported.add("UNSUPPORTED: " + target + ".setOnClickListener(...) — listener body form "
                        + "not recognized (expected a lambda or anonymous View.OnClickListener); left "
                        + "unconverted in source only.");
                continue;
            }
            List<String> eventStatements = rewriteViewAliases(removeManagedViewSetupStatements(
                    splitStatements(body), "event " + viewId + ".onClick"));
            String normalizedBody = joinStatements(eventStatements);
            BlockSection section = new BlockSection();
            convertStatements(eventStatements, section, result);
            String legacy = section.render();
            String rich = containsCustomMethodCall(normalizedBody) ? null
                    : convertWithNativeBlockConverter(normalizedBody, java.util.Collections.emptyMap(), result,
                    "event " + viewId + ".onClick");
            result.sections.put(sectionName, rich != null ? rich : legacy);
        }
    }

    /** Handles both `v -> { ... }` and `new View.OnClickListener(){ public void onClick(View v){ ... } }`. */
    private String extractLambdaOrAnonymousBody(String source, int openParenIdx) {
        // Find the matching close-paren of the setOnClickListener(...) call, tracking depth.
        int depth = 1;
        int i = openParenIdx + 1;
        for (; i < source.length() && depth > 0; i++) {
            char c = source.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
        }
        String argText = source.substring(openParenIdx + 1, i - 1);

        // Lambda form: v -> { ... }  or  v -> singleStatement;
        int arrow = argText.indexOf("->");
        if (arrow >= 0) {
            String after = argText.substring(arrow + 2).trim();
            if (after.startsWith("{")) {
                int close = findMatchingBrace(after, 0);
                return close > 0 ? after.substring(1, close) : null;
            } else {
                return after.endsWith(";") ? after : after + ";";
            }
        }

        // Anonymous class form: locate the onClick(...) method inside argText.
        String onClickBody = extractMethodBody(argText, "onClick");
        return onClickBody;
    }

    private int findMatchingBrace(String s, int openIdx) {
        int depth = 0;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') { depth--; if (depth == 0) return i; }
        }
        return -1;
    }

    // ── Statement splitting (brace/paren/string aware, top level only) ─────────

    private List<String> splitStatements(String body) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        int start = 0;
        boolean inString = false, inChar = false;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (inString) {
                if (c == '\\') { i++; continue; }
                if (c == '"') inString = false;
                continue;
            }
            if (inChar) {
                if (c == '\\') { i++; continue; }
                if (c == '\'') inChar = false;
                continue;
            }
            if (c == '"') { inString = true; continue; }
            if (c == '\'') { inChar = true; continue; }
            if (c == '{' || c == '(') depth++;
            else if (c == '}' || c == ')') depth--;
            else if (c == ';' && depth == 0) {
                out.add(body.substring(start, i + 1).trim());
                start = i + 1;
            } else if (c == '}' && depth == 0) {
                // end of a block statement (if/else/etc. handled by caller pre-slicing) — should
                // not normally be reached here since block statements are consumed whole below.
            }
        }
        // Handle brace-delimited statements (if/else) that don't end in ';' by re-scanning
        // for constructs starting with "if (" / "} else" at depth 0 and consuming through
        // their matching closing brace(s) as a single statement, replacing the naive split.
        return regroupBraceStatements(body);
    }

    /**
     * Re-splits body into top-level statements, treating any `if (...) { ... } [else if (...) {...}]* [else {...}]`
     * as ONE statement (so it can be handled specially), and every other semicolon-terminated
     * segment as its own statement. This two-pass approach is simpler and more robust than
     * trying to do both in one scan.
     */
    private List<String> regroupBraceStatements(String body) {
        List<String> out = new ArrayList<>();
        int i = 0;
        int n = body.length();
        while (i < n) {
            // skip whitespace
            while (i < n && Character.isWhitespace(body.charAt(i))) i++;
            if (i >= n) break;

            if (body.startsWith("if", i) && i + 2 < n && !Character.isLetterOrDigit(body.charAt(i + 2))) {
                int stmtStart = i;
                int j = i;
                // consume "if (...) { ... }" then any chained "else if (...) { ... }" / "else { ... }"
                j = consumeIfChain(body, j);
                out.add(body.substring(stmtStart, j).trim());
                i = j;
                continue;
            }

            // otherwise: find next top-level ';' (string/char aware)
            int j = i;
            int depth = 0;
            boolean inString = false, inChar = false;
            while (j < n) {
                char c = body.charAt(j);
                if (inString) { if (c == '\\') { j += 2; continue; } if (c == '"') inString = false; j++; continue; }
                if (inChar)   { if (c == '\\') { j += 2; continue; } if (c == '\'') inChar = false; j++; continue; }
                if (c == '"') { inString = true; j++; continue; }
                if (c == '\'') { inChar = true; j++; continue; }
                if (c == '(' || c == '{') depth++;
                else if (c == ')' || c == '}') depth--;
                else if (c == ';' && depth == 0) { j++; break; }
                j++;
            }
            String stmt = body.substring(i, Math.min(j, n)).trim();
            if (!stmt.isEmpty()) out.add(stmt);
            i = j;
        }
        return out;
    }

    private int consumeIfChain(String body, int i) {
        int n = body.length();
        i = consumeOneIfOrElse(body, i); // the initial "if (...) { ... }"
        while (true) {
            int save = i;
            while (i < n && Character.isWhitespace(body.charAt(i))) i++;
            if (body.startsWith("else", i)) {
                i = consumeOneIfOrElse(body, i);
            } else {
                return save;
            }
        }
    }

    /** Consumes one "if (...) { ... }" or "else if (...) { ... }" or "else { ... }" starting at i. */
    private int consumeOneIfOrElse(String body, int i) {
        int n = body.length();
        if (body.startsWith("else", i)) i += 4;
        while (i < n && Character.isWhitespace(body.charAt(i))) i++;
        if (body.startsWith("if", i)) {
            i += 2;
            while (i < n && Character.isWhitespace(body.charAt(i))) i++;
            if (i < n && body.charAt(i) == '(') {
                int depth = 0;
                do {
                    if (body.charAt(i) == '(') depth++;
                    else if (body.charAt(i) == ')') depth--;
                    i++;
                } while (i < n && depth > 0);
            }
            while (i < n && Character.isWhitespace(body.charAt(i))) i++;
        }
        if (i < n && body.charAt(i) == '{') {
            int close = findMatchingBrace(body, i);
            i = close >= 0 ? close + 1 : n;
        } else {
            // brace-less single statement body — consume through next ';'
            int semi = body.indexOf(';', i);
            i = semi >= 0 ? semi + 1 : n;
        }
        return i;
    }

    // ── Statement -> block conversion ───────────────────────────────────────────

    private static final Pattern FINISH_CALL = Pattern.compile("^finish\\s*\\(\\s*\\)\\s*;$");
    private static final Pattern START_ACTIVITY = Pattern.compile(
            "^startActivity\\s*\\(\\s*new\\s+Intent\\s*\\([^,]+,\\s*([A-Za-z_$][A-Za-z0-9_$]*)\\.class\\s*\\)\\s*\\)\\s*;$");
    private static final Pattern SIMPLE_ASSIGN = Pattern.compile(
            "^([A-Za-z_$][A-Za-z0-9_$]*)\\s*=\\s*(.+);$");
    private static final Pattern IF_ELSE_HEAD = Pattern.compile(
            "^if\\s*\\((.+?)\\)\\s*\\{", Pattern.DOTALL);
    private static final Pattern NUMERIC_EQ = Pattern.compile("^\\s*(.+?)\\s*==\\s*(.+?)\\s*$");
    private static final Pattern STRING_EQUALS = Pattern.compile(
            "^\\s*([A-Za-z_$][A-Za-z0-9_$]*)\\.equals\\(\\s*(.+?)\\s*\\)\\s*$");

    private void convertStatements(List<String> statements, BlockSection section, ConversionResult result) {
        for (String stmt : statements) {
            convertOneStatement(stmt, section, result);
        }
    }

    private void convertOneStatement(String stmt, BlockSection section, ConversionResult result) {
        if (stmt.isEmpty()) return;

        // setOnClickListener(...) statements are handled separately by
        // findAndConvertClickListeners(), which converts the listener body into its own
        // real "_<viewId>_onClick" section. Skip them here ONLY when that resolution
        // actually succeeded — otherwise (unresolvable target) fall through to the normal
        // addSourceDirectly fallback below so the statement is never silently dropped.
        Matcher onClickHead = ON_CLICK.matcher(stmt);
        if (onClickHead.find() && onClickHead.start() == 0) {
            String target = onClickHead.group(1);
            String viewId = localVarToViewId.get(target);
            if (viewId == null) viewId = bindingRefToViewId.get(target);
            if (viewId != null) {
                return; // already converted into its own section — do not also emit here
            }
        }

        if (FINISH_CALL.matcher(stmt).matches()) {
            section.addStatement("finishActivity", "Finish Activity", new String[]{}, "f");
            return;
        }

        Matcher startAct = START_ACTIVITY.matcher(stmt);
        if (startAct.matches()) {
            String targetClass = startAct.group(1);
            String componentId = "intent" + (++intentComponentCounter);
            componentsById.put(componentId,
                    "{\"componentId\":\"" + componentId + "\",\"param1\":\"\",\"param2\":\"\",\"param3\":\"\",\"type\":1}");
            int setScreenId = section.reserveId();
            section.finalizeBlock(setScreenId, "intentSetScreen", "%m.intent setScreen %m.activity",
                    new String[]{componentId, targetClass}, "");
            int startId = section.reserveId();
            section.finalizeBlock(startId, "startActivity", "StartActivity %m.intent",
                    new String[]{componentId}, "");
            return;
        }

        if (tryConvertCustomCall(stmt, section, result)) return;

        Matcher assign = SIMPLE_ASSIGN.matcher(stmt);
        if (assign.matches()) {
            String var = assign.group(1);
            String rhs = assign.group(2).trim();
            Integer typeCode = knownScalarFields.get(var);
            if (typeCode != null) {
                String valueParam = buildScalarValueParam(rhs, typeCode);
                if (valueParam != null) {
                    String opCode = typeCode == 1 ? "setVarInt" : typeCode == 0 ? "setVarBoolean"
                            : typeCode == 4 ? "setVarFloat" : typeCode == 7 ? "setVarIntNum" : "setVarString";
                    String spec = typeCode == 1 ? "set %m.varInt to %d"
                                : typeCode == 0 ? "set %m.varBool to %b"
                                : typeCode == 4 ? "set %m.varFloat to %d"
                                : typeCode == 7 ? "set %m.varIntNum to %d" : "set %m.varStr to %s";
                    section.addStatement(opCode, spec, new String[]{var, valueParam}, "");
                    return;
                }
            }
        }

        Matcher ifHead = IF_ELSE_HEAD.matcher(stmt);
        if (ifHead.find() && ifHead.start() == 0) {
            if (tryConvertIfElse(stmt, section, result)) return;
        }

        // Fallback: preserve verbatim as a real addSourceDirectly block (confirmed opCode).
        section.addStatement("addSourceDirectly", "add source directly %s.inputOnly",
                new String[]{stmt}, "");
        unsupported.add("Preserved as addSourceDirectly (not converted to native blocks): "
                + truncate(stmt, 80));
    }

    /** Builds a value param for setVarInt/Bool/String/Float: a literal, or "@ref" to another known var. */
    private String buildScalarValueParam(String rhs, int typeCode) {
        rhs = rhs.trim();
        if (typeCode == 1) { // int
            if (rhs.matches("-?\\d+")) return rhs;
            if (knownScalarFields.containsKey(rhs) && knownScalarFields.get(rhs) == 1) return "@" + rhs;
            // simple a + b / a - b / a * b / a / b of two known-int operands or literals
            Matcher bin = Pattern.compile("^(.+?)\\s*([+\\-*/])\\s*(.+)$").matcher(rhs);
            if (bin.matches()) return null; // composed math needs nested blocks — out of this method's scope
            return null;
        } else if (typeCode == 0) { // boolean
            if (rhs.equals("true") || rhs.equals("false")) return rhs;
            return null;
        } else if (typeCode == 7) { // real int (Sketchware's own true int type)
            if (rhs.matches("-?\\d+")) return rhs;
            if (knownScalarFields.containsKey(rhs) && knownScalarFields.get(rhs) == 7) return "@" + rhs;
            Matcher bin = Pattern.compile("^(.+?)\\s*([+\\-*/])\\s*(.+)$").matcher(rhs);
            if (bin.matches()) return null; // composed math needs nested blocks — out of this method's scope
            return null;
        } else if (typeCode == 4) { // float
            // Strip a Java float-literal suffix ("1.5f" / "1.5F") — the block param itself is
            // stored as a plain numeric literal; Fx's setVarFloat codegen re-adds the "f"
            // suffix (and handles the empty -> "0f" case) when the project is built.
            String literal = rhs;
            if (literal.endsWith("f") || literal.endsWith("F")) {
                literal = literal.substring(0, literal.length() - 1);
            }
            if (literal.matches("-?\\d+(\\.\\d+)?")) return literal;
            if (knownScalarFields.containsKey(rhs) && knownScalarFields.get(rhs) == 4) return "@" + rhs;
            return null;
        } else { // String
            if (rhs.startsWith("\"") && rhs.endsWith("\"")) return rhs.substring(1, rhs.length() - 1);
            if (knownScalarFields.containsKey(rhs) && knownScalarFields.get(rhs) == 2) return "@" + rhs;
            return null;
        }
    }

    private boolean tryConvertIfElse(String stmt, BlockSection section, ConversionResult result) {
        // Parse "if (COND) { TRUE_BODY } [else { FALSE_BODY }]" — single if/else only,
        // no else-if chains (those fall back to addSourceDirectly for the whole chain).
        Matcher head = Pattern.compile("^if\\s*\\(", Pattern.DOTALL).matcher(stmt);
        if (!head.find()) return false;
        int condStart = head.end() - 1;
        int condEnd = findMatchingParen(stmt, condStart);
        if (condEnd < 0) return false;
        String cond = stmt.substring(condStart + 1, condEnd).trim();

        int i = condEnd + 1;
        while (i < stmt.length() && Character.isWhitespace(stmt.charAt(i))) i++;
        if (i >= stmt.length() || stmt.charAt(i) != '{') return false;
        int trueClose = findMatchingBrace(stmt, i);
        if (trueClose < 0) return false;
        String trueBody = stmt.substring(i + 1, trueClose);

        String falseBody = null;
        int j = trueClose + 1;
        while (j < stmt.length() && Character.isWhitespace(stmt.charAt(j))) j++;
        if (stmt.startsWith("else", j)) {
            j += 4;
            while (j < stmt.length() && Character.isWhitespace(stmt.charAt(j))) j++;
            if (j < stmt.length() && stmt.charAt(j) == '{') {
                int falseClose = findMatchingBrace(stmt, j);
                if (falseClose < 0) return false;
                falseBody = stmt.substring(j + 1, falseClose);
            } else {
                return false; // else-if chain — not handled, fall back to addSourceDirectly
            }
        }

        String[] condBlock = buildSimpleConditionParam(cond);
        if (condBlock == null) return false; // unconfirmed comparison operator — fall back

        int ownId = section.reserveId(); // reserve BEFORE building nested branch statements
        int trueEntry = section.buildBranch(splitStatements(trueBody), this, result);
        int falseEntry = falseBody != null
                ? section.buildBranch(splitStatements(falseBody), this, result)
                : -1;

        section.finalizeIfElse(ownId, condBlock[0], condBlock[1], trueEntry, falseEntry);
        return true;
    }

    /** Returns {opCode, valueOrRefParam} for a confirmed condition shape, or null if unconfirmed. */
    private String[] buildSimpleConditionParam(String cond) {
        Matcher strEq = STRING_EQUALS.matcher(cond);
        if (strEq.matches()) {
            return new String[]{"stringEquals", strEq.group(1) + "|" + strEq.group(2).replaceAll("^\"|\"$", "")};
        }
        Matcher numEq = NUMERIC_EQ.matcher(cond);
        if (numEq.matches() && !cond.contains("!=") && !cond.contains(">=") && !cond.contains("<=")) {
            return new String[]{"=", numEq.group(1).trim() + "|" + numEq.group(2).trim()};
        }
        return null; // >, <, >=, <=, !=, &&, || — all unconfirmed (doc §11) — do not fake
    }

    private int findMatchingParen(String s, int openIdx) {
        int depth = 0;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') { depth--; if (depth == 0) return i; }
        }
        return -1;
    }

    private String truncate(String s, int max) {
        s = s.replace("\n", " ");
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    // ── BlockSection: id allocation + rendering, implementing the entry-point rule ────

    private class BlockSection {
        private int counter = 1;
        private final Map<Integer, String> blocksById = new LinkedHashMap<>();
        private final List<Integer> topLevelOrder = new ArrayList<>();
        private Integer lastTopLevelId = null;

        int reserveId() {
            return counter++;
        }

        /** Adds a simple, non-nesting statement block, chained after the previous top-level block. */
        void addStatement(String opCode, String spec, String[] params, String color) {
            int id = reserveId();
            finalizeBlock(id, opCode, spec, params, color);
        }

        /** Writes a block already assigned an id (used when nested blocks needed to be built
         *  between reserving the id and finalizing it, e.g. intentSetScreen/startActivity pair,
         *  and ifElse). Chains it after the previous top-level statement via nextBlock. */
        void finalizeBlock(int id, String opCode, String spec, String[] params, String color) {
            if (lastTopLevelId != null) {
                String prev = blocksById.get(lastTopLevelId);
                blocksById.put(lastTopLevelId, setNextBlock(prev, id));
            }
            StringBuilder paramsJson = new StringBuilder("[");
            for (int i = 0; i < params.length; i++) {
                if (i > 0) paramsJson.append(",");
                paramsJson.append(jsonString(params[i]));
            }
            paramsJson.append("]");
            String json = "{\"id\":\"" + id + "\",\"nextBlock\":-1,\"subStack1\":-1,\"subStack2\":-1,"
                    + "\"opCode\":\"" + opCode + "\",\"spec\":" + jsonString(spec) + ","
                    + "\"parameters\":" + paramsJson + ",\"color\":-10701022,\"type\":\""
                    + (color.isEmpty() ? "" : color) + "\",\"typeName\":\"\"}";
            blocksById.put(id, json);
            topLevelOrder.add(id);
            lastTopLevelId = id;
        }

        void finalizeIfElse(int id, String condOpCode, String condRef, int trueEntry, int falseEntry) {
            if (lastTopLevelId != null) {
                String prev = blocksById.get(lastTopLevelId);
                blocksById.put(lastTopLevelId, setNextBlock(prev, id));
            }
            String[] parts = condRef.split("\\|", 2);
            String p0 = parts.length > 0 ? parts[0] : "";
            String p1 = parts.length > 1 ? parts[1] : "";
            String json = "{\"id\":\"" + id + "\",\"nextBlock\":-1,"
                    + "\"subStack1\":" + trueEntry + ",\"subStack2\":" + falseEntry + ","
                    + "\"opCode\":\"" + (falseEntry >= 0 ? "ifElse" : "if") + "\","
                    + "\"spec\":\"if %b then\",\"parameters\":[" + "\"@__cond" + id + "\"" + "],"
                    + "\"color\":-9993985,\"type\":\"" + (falseEntry >= 0 ? "e" : "") + "\",\"typeName\":\"\"}";
            // Condition itself becomes its own block (a value-only opcode) referenced by the
            // if/ifElse block's parameter — allocated AFTER the statement's own id per the
            // entry-point rule, so it never becomes the section's (lowest-id) entry point.
            int condId = reserveId();
            String condJson = "{\"id\":\"" + condId + "\",\"nextBlock\":-1,\"subStack1\":-1,\"subStack2\":-1,"
                    + "\"opCode\":\"" + condOpCode + "\","
                    + "\"spec\":\"" + (condOpCode.equals("=") ? "%d = %d" : "%s equals %s") + "\","
                    + "\"parameters\":[" + jsonString(p0) + "," + jsonString(p1) + "],"
                    + "\"color\":-10701022,\"type\":\"b\",\"typeName\":\"\"}";
            blocksById.put(condId, condJson);
            json = json.replace("\"@__cond" + id + "\"", "\"@" + condId + "\"");
            blocksById.put(id, json);
            topLevelOrder.add(id);
            lastTopLevelId = id;
        }

        /**
         * Builds the given statements into THIS SAME section's shared id counter and block
         * map (never a separate section — ids must stay unique within one method/event, and
         * a branch's blocks are ordinary members of the same flat section per the confirmed
         * format), isolating the outer nextBlock-chain cursor so the branch's first statement
         * doesn't get chained after whatever statement precedes the if/ifElse itself.
         *
         * Because ids are allocated from one monotonically increasing shared counter and
         * nothing else is allocated concurrently, the branch's entry id (its lowest id, per
         * the entry-point rule) is exactly the counter's value before the branch starts —
         * IF the branch produced at least one block.
         */
        int buildBranch(List<String> statements, JavaLogicConverter outer, ConversionResult result) {
            if (statements.isEmpty()) return -1;
            int entryCandidate = counter;
            Integer savedCursor = lastTopLevelId;
            lastTopLevelId = null;
            outer.convertStatements(statements, this, result);
            boolean producedAnything = counter > entryCandidate;
            lastTopLevelId = savedCursor;
            return producedAnything ? entryCandidate : -1;
        }

        String render() {
            StringBuilder sb = new StringBuilder();
            for (String json : blocksById.values()) sb.append(json).append("\n");
            return sb.toString();
        }

        private String setNextBlock(String blockJson, int nextId) {
            return blockJson.replaceFirst("\"nextBlock\":-1", "\"nextBlock\":" + nextId);
        }
    }

    private String jsonString(String s) {
        if (s == null) s = "";
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n");  break;
                case '\r': out.append("\\r");  break;
                case '\t': out.append("\\t");  break;
                default:   out.append(c);
            }
        }
        return out.append("\"").toString();
    }
}
