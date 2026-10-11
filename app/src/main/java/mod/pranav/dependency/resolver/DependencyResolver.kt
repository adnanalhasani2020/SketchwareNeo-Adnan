package mod.pranav.dependency.resolver

import android.os.Environment
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import mod.hey.studios.build.BuildSettings
import mod.hey.studios.util.Helper
import org.cosmic.ide.dependency.resolver.api.Artifact
import org.cosmic.ide.dependency.resolver.api.EventReciever
import org.cosmic.ide.dependency.resolver.api.Repository
import org.json.JSONException
import org.cosmic.ide.dependency.resolver.eventReciever
import org.cosmic.ide.dependency.resolver.repositories
import pro.sketchware.utility.FileUtil
import java.io.File
import java.io.IOException
import java.net.URL
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.Collections
import java.util.function.BooleanSupplier
import java.util.regex.Pattern
import java.util.zip.ZipFile
import kotlin.io.path.readText
import kotlin.io.path.writeText

private class ResolverAbort(val failure: ResolverFailure) : RuntimeException(failure.message)

private class Fetched(val folder: String, val cached: Boolean)

private class PlaceholderArtifact(val reason: String) : RuntimeException(reason)

private class Located(val artifact: Artifact, val kind: RepositoryProbe.Kind)

class DependencyResolver(
    private val groupId: String,
    private val artifactId: String,
    private val version: String,
    private val skipDependencies: Boolean,
    private val buildSettings: BuildSettings
) {
    companion object {
        const val IDENTITY_FILE = "maven.txt"

        private val DEFAULT_REPOS = """
          |[
          |    {"url": "https://jitpack.io", "name": "JitPack"},
          |    {"url": "https://maven.google.com", "name": "Google Maven"},
          |    {"url": "https://jcenter.bintray.com", "name": "JCenter"},
          |    {"url": "https://repo.maven.apache.org/maven2", "name": "Apache Maven Central"},
          |    {"url": "https://oss.sonatype.org/content/repositories/releases", "name": "Sonatype"},
          |    {"url": "https://repo.spring.io/plugins-release", "name": "Spring Plugins"},
          |    {"url": "https://repo.spring.io/libs-milestone", "name": "Spring Milestone"},
          |    {"url": "https://maven.atlassian.com/content/repositories/atlassian-public", "name": "Atlassian"},
          |    {"url": "https://repo.hortonworks.com/content/repositories/releases", "name": "HortanWorks"}
          |]
        """.trimMargin()

        private val COORDINATE_PART = Regex("[A-Za-z0-9_.\\-]+")
        private val VERSION_PART = Regex("[A-Za-z0-9_.\\-+]+")
        private val VERSION_FORBIDDEN = Regex("[\\[\\](),\\s]")
        private const val MAX_VARIANT_REDIRECTS = 4
    }

    var providedBy: ((Artifact) -> String?)? = null

    var rootArtifact: Artifact? = null
        private set

    var rootFolder: String? = null
        private set

    @Volatile
    private var cancelled = false

    private val downloadPath: String =
        FileUtil.getExternalStorageDir() + "/.sketchware/libs/local_libs"

    private val repositoriesJson = Paths.get(
        Environment.getExternalStorageDirectory().absolutePath,
        ".sketchware",
        "libs",
        "repositories.json"
    )

    init {
        if (Files.notExists(repositoriesJson)) {
            Files.createDirectories(repositoriesJson.parent)
            repositoriesJson.writeText(DEFAULT_REPOS)
        }

        val reposString = repositoriesJson.readText()
        if (!reposString.contains("jitpack.io") || !reposString.contains("maven.google.com")) {
            repositoriesJson.writeText(DEFAULT_REPOS)
        }

        repositories.clear()
        Gson().fromJson(repositoriesJson.readText(), Helper.TYPE_MAP_LIST).forEach {
            val url: String? = it["url"] as String?
            if (url != null) {
                repositories.add(object : Repository {
                    override fun getName(): String {
                        return (it["name"] as? String) ?: url
                    }

                    override fun getURL(): String {
                        return if (url.endsWith("/")) {
                            url.substringBeforeLast("/")
                        } else {
                            url
                        }
                    }
                })
            }
        }
    }

    open class DependencyResolverCallback : EventReciever() {
        override fun artifactFound(artifact: Artifact) {}
        override fun onArtifactNotFound(artifact: Artifact) {}
        override fun onFetchingLatestVersion(artifact: Artifact) {}
        override fun onFetchedLatestVersion(artifact: Artifact, version: String) {}
        override fun onResolving(artifact: Artifact, dependency: Artifact) {}
        override fun onResolutionComplete(artifact: Artifact) {}
        override fun onSkippingResolution(artifact: Artifact) {}
        override fun onVersionNotFound(artifact: Artifact) {}
        override fun onDependenciesNotFound(artifact: Artifact) {}
        override fun onInvalidScope(artifact: Artifact, scope: String) {}
        override fun onInvalidPOM(artifact: Artifact) {}
        override fun onDownloadStart(artifact: Artifact) {}
        override fun onDownloadEnd(artifact: Artifact) {}
        override fun onDownloadError(artifact: Artifact, error: Throwable) {}
        open fun unzipping(artifact: Artifact) {}
        open fun dexing(artifact: Artifact) {}
        open fun onTaskCompleted(artifacts: List<String>) {}
        open fun dexingFailed(artifact: Artifact, e: Exception) {}
        open fun invalidPackaging(artifact: Artifact) {}

        open fun onCacheHit(artifact: Artifact) {}
        open fun onProvided(artifact: Artifact, provider: String) {}
        open fun onFolderResolved(artifact: Artifact, folder: String) {}
        open fun onDownloadProgress(artifact: Artifact, bytes: Long, total: Long) {}
        open fun onDependencySkipped(artifact: Artifact, reason: String) {}
        open fun onNote(artifact: Artifact, note: String) {}
        open fun onVariantSelected(from: Artifact, to: Artifact, detail: String) {}
        open fun onBomResolved(artifact: Artifact, managed: List<String>) {}
        open fun onFailure(failure: ResolverFailure) {}

        open fun onDirectDownloadStart(url: String) {}
        open fun onDirectDownloadEnd(fileName: String) {}
        open fun onDirectDownloadError(url: String, error: Throwable) {}
    }

    private class RecordingCallback(private val delegate: DependencyResolverCallback) :
        DependencyResolverCallback() {
        val notFound: MutableList<Artifact> = Collections.synchronizedList(ArrayList())

        override fun artifactFound(artifact: Artifact) = delegate.artifactFound(artifact)

        override fun onArtifactNotFound(artifact: Artifact) {
            notFound.add(artifact)
            delegate.onArtifactNotFound(artifact)
        }

        override fun onFetchingLatestVersion(artifact: Artifact) =
            delegate.onFetchingLatestVersion(artifact)

        override fun onFetchedLatestVersion(artifact: Artifact, version: String) =
            delegate.onFetchedLatestVersion(artifact, version)

        override fun onResolving(artifact: Artifact, dependency: Artifact) =
            delegate.onResolving(artifact, dependency)

        override fun onResolutionComplete(artifact: Artifact) =
            delegate.onResolutionComplete(artifact)

        override fun onSkippingResolution(artifact: Artifact) =
            delegate.onSkippingResolution(artifact)

        override fun onVersionNotFound(artifact: Artifact) = delegate.onVersionNotFound(artifact)

        override fun onDependenciesNotFound(artifact: Artifact) =
            delegate.onDependenciesNotFound(artifact)

        override fun onInvalidScope(artifact: Artifact, scope: String) =
            delegate.onInvalidScope(artifact, scope)

        override fun onInvalidPOM(artifact: Artifact) = delegate.onInvalidPOM(artifact)
    }

    fun cancel() {
        cancelled = true
    }

    fun resolveDependency(callback: DependencyResolverCallback) = runBlocking {
        eventReciever = callback
        try {
            resolveInternal(callback)
        } catch (abort: ResolverAbort) {
            callback.onFailure(abort.failure)
        } catch (t: Throwable) {
            callback.onFailure(
                ResolverFailure(
                    ResolverFailure.Stage.UNEXPECTED,
                    label(),
                    FailureFormatter.describe(t),
                    t
                )
            )
        }
    }

    private fun isDirectUrl(): Boolean =
        groupId.startsWith("http://") || groupId.startsWith("https://")

    private fun label(): String = if (isDirectUrl()) groupId else "$groupId:$artifactId:$version"

    private fun abort(
        stage: ResolverFailure.Stage,
        coordinate: String?,
        message: String,
        cause: Throwable? = null
    ): ResolverAbort = ResolverAbort(ResolverFailure(stage, coordinate, message, cause))

    private fun checkCancelled(artifact: Artifact?) {
        if (cancelled) {
            throw abort(
                ResolverFailure.Stage.CANCELLED,
                artifact?.toString() ?: label(),
                "Cancelled by user"
            )
        }
    }

    private val fileNameOverrides = HashMap<String, String>()

    private fun keyOf(artifact: Artifact): String =
        "${artifact.groupId}:${artifact.artifactId}:${artifact.version}"

    private suspend fun resolveInternal(callback: DependencyResolverCallback) {
        if (isDirectUrl()) {
            handleDirectUrlDownload(groupId, callback)
            return
        }

        validateCoordinate()
        val root = locate(groupId, artifactId, version, callback, 0)
        rootArtifact = root

        val extension = root.extension
        if (extension == "pom") {
            resolvePomRoot(root, callback)
            return
        }
        if (extension != "jar" && extension != "aar") {
            callback.invalidPackaging(root)
            throw abort(
                ResolverFailure.Stage.PACKAGING,
                root.toString(),
                "Packaging '$extension' cannot be used as a library (only jar, aar and pom/BOM metadata are supported)."
            )
        }

        val roots = ArrayList<Artifact>()
        roots.add(root)
        resolveAndFinish(roots, root, callback)
    }

    private suspend fun resolveAndFinish(
        roots: List<Artifact>,
        primary: Artifact,
        callback: DependencyResolverCallback
    ) {
        val targets = LinkedHashMap<String, Artifact>()
        for (artifact in roots) targets[keyOf(artifact)] = artifact

        if (!skipDependencies) {
            val recording = RecordingCallback(callback)
            eventReciever = recording
            try {
                for (artifact in roots) {
                    for (dep in resolveTransitive(artifact, recording, callback)) {
                        targets.putIfAbsent(keyOf(dep), dep)
                    }
                }
            } finally {
                eventReciever = callback
            }
        }

        val fetched = LinkedHashMap<String, Fetched>()
        val usable = LinkedHashMap<String, Artifact>()
        for ((key, requested) in targets) {
            checkCancelled(requested)
            val isRequested = roots.any { keyOf(it) == key }
            var current = requested
            var info: Fetched? = null
            try {
                info = fetchWithVariantFallback(current, callback) { replacement -> current = replacement }
            } catch (placeholder: PlaceholderArtifact) {
                if (isRequested) {
                    throw abort(
                        ResolverFailure.Stage.MISSING_CLASSES,
                        requested.toString(),
                        placeholder.reason,
                        placeholder
                    )
                }
                callback.onDependencySkipped(requested, placeholder.reason)
            }
            if (info != null) {
                val actualKey = keyOf(current)
                if (!fetched.containsKey(actualKey)) {
                    fetched[actualKey] = info
                    usable[actualKey] = current
                }
                if (actualKey == keyOf(primary) || current == primary) rootArtifact = current
            }
        }

        if (fetched.isEmpty()) {
            throw abort(
                ResolverFailure.Stage.MISSING_CLASSES,
                primary.toString(),
                "Nothing code-bearing could be downloaded for this request."
            )
        }

        val completed = ArrayList<String>()
        for ((key, info) in fetched) {
            val artifact = usable[key] ?: continue
            checkCancelled(artifact)
            callback.onFolderResolved(artifact, info.folder)
            callback.onResolutionComplete(artifact)
            completed.add(info.folder)
        }

        rootFolder = fetched[keyOf(rootArtifact ?: primary)]?.folder ?: completed.firstOrNull()
        callback.onTaskCompleted(completed)
    }

    private suspend fun resolvePomRoot(root: Artifact, callback: DependencyResolverCallback) {
        val view = viewOf(root)
            ?: throw abort(
                ResolverFailure.Stage.POM,
                root.toString(),
                "The POM could not be read, so its dependencies and managed versions are unknown."
            )
        val resolver = ManagedVersionResolver { g, a, v ->
            checkCancelled(null)
            viewOf(probe(g, a, v, callback).artifact)
        }
        val dependencies = resolver.effectiveDependencies(view)
        val usableDependencies = dependencies.filter { it.scope == null || it.scope == "compile" || it.scope == "runtime" }

        if (usableDependencies.isNotEmpty()) {
            if (skipDependencies) {
                throw abort(
                    ResolverFailure.Stage.PACKAGING,
                    root.toString(),
                    "${root.artifactId} is a metadata-only POM with no code. Its dependencies are: " +
                            usableDependencies.joinToString { it.coordinate() } +
                            ". Turn off \"Skip downloading sub-dependencies\" to download them."
                )
            }
            val roots = ArrayList<Artifact>()
            for (dependency in usableDependencies) {
                if (dependency.version.isEmpty() || dependency.version.contains("\${")) {
                    throw abort(
                        ResolverFailure.Stage.POM,
                        root.toString(),
                        "No version could be determined for ${dependency.group}:${dependency.artifact} (not declared and not managed by a parent POM or imported BOM)." +
                                problemsSuffix(resolver)
                    )
                }
                val located = locate(dependency.group, dependency.artifact, dependency.version, callback, 0)
                if (located.extension == "pom") continue
                if (located.extension != "jar" && located.extension != "aar") {
                    callback.invalidPackaging(located)
                    callback.onDependencySkipped(located, "packaging '${located.extension}' has no classes")
                    continue
                }
                roots.add(located)
            }
            if (roots.isEmpty()) {
                throw abort(
                    ResolverFailure.Stage.PACKAGING,
                    root.toString(),
                    "The POM only lists other metadata artifacts; nothing code-bearing was found."
                )
            }
            resolveAndFinish(roots, roots[0], callback)
            return
        }

        val managed = resolver.managedVersions(view).values.map { it.coordinate() }.sorted()
        if (managed.isEmpty()) {
            throw abort(
                ResolverFailure.Stage.PACKAGING,
                root.toString(),
                "The POM has no dependencies and no managed versions, so there is nothing to download." + problemsSuffix(resolver)
            )
        }
        callback.onBomResolved(root, managed)
    }

    private fun problemsSuffix(resolver: ManagedVersionResolver): String {
        val problems = resolver.problems
        return if (problems.isEmpty()) "" else "\nProblems: " + problems.joinToString("; ")
    }

    private fun viewOf(artifact: Artifact): ManagedVersionResolver.PomView? {
        val pom = artifact.pom ?: artifact.getPOM() ?: return null
        val parent = pom.parent
        val group = pom.groupId ?: parent?.groupId ?: artifact.groupId
        val pomVersion = pom.version ?: parent?.version ?: artifact.version
        val managed = ArrayList<ManagedVersionResolver.Entry>()
        pom.dependencyManagement?.dependencies?.forEach {
            managed.add(ManagedVersionResolver.Entry(it.groupId ?: group, it.artifactId, it.version, it.scope))
        }
        val dependencies = ArrayList<ManagedVersionResolver.Entry>()
        pom.dependencies?.forEach {
            if (!it.optional && it.scope != "test" && it.scope != "provided" && it.scope != "system") {
                dependencies.add(ManagedVersionResolver.Entry(it.groupId ?: group, it.artifactId, it.version, it.scope))
            }
        }
        return ManagedVersionResolver.PomView(
            group,
            pom.artifactId,
            pomVersion,
            parent?.groupId,
            parent?.artifactId,
            parent?.version,
            HashMap(pom.properties ?: emptyMap()),
            managed,
            dependencies
        )
    }

    private fun validateCoordinate() {
        val coordinate = "$groupId:$artifactId:$version"
        if (!COORDINATE_PART.matches(groupId) || !COORDINATE_PART.matches(artifactId)) {
            throw abort(
                ResolverFailure.Stage.INVALID_COORDINATE,
                coordinate,
                "Group and artifact may only contain letters, digits, '.', '_' and '-'."
            )
        }
        if (version.isBlank()) {
            throw abort(
                ResolverFailure.Stage.INVALID_COORDINATE,
                coordinate,
                "A version is required (group:artifact:version)."
            )
        }
        if (version == "+" || VERSION_FORBIDDEN.containsMatchIn(version) || !VERSION_PART.matches(version)) {
            throw abort(
                ResolverFailure.Stage.INVALID_COORDINATE,
                coordinate,
                "The version must be one fixed version, not '$version'."
            )
        }
    }

    private fun probe(
        group: String,
        artifact: String,
        artifactVersion: String,
        callback: DependencyResolverCallback
    ): Located {
        val coordinate = "$group:$artifact:$artifactVersion"
        val repoList = repositories.toList()
        if (repoList.isEmpty()) {
            throw abort(
                ResolverFailure.Stage.REPOSITORY,
                coordinate,
                "No repositories are configured. Add one in the repositories list."
            )
        }
        val repos = repoList.map { RepositoryProbe.Repo(it.getName(), it.getURL()) }
        val result = RepositoryProbe.locate(
            repos,
            group,
            artifact,
            artifactVersion,
            BooleanSupplier { cancelled }
        )
        if (result.cancelled) {
            throw abort(ResolverFailure.Stage.CANCELLED, coordinate, "Cancelled by user")
        }
        val kind = result.foundKind
        if (!result.found() || kind == null) {
            val stage = if (result.allNetworkFailures()) {
                ResolverFailure.Stage.NETWORK
            } else {
                ResolverFailure.Stage.REPOSITORY
            }
            throw abort(stage, coordinate, result.explain(coordinate))
        }

        val located = Artifact(group, artifact, artifactVersion)
        located.repository = repoList[result.foundIndex]

        when (kind) {
            RepositoryProbe.Kind.POM -> {
                val pom = located.getPOM()
                if (pom == null) {
                    throw abort(
                        ResolverFailure.Stage.POM,
                        coordinate,
                        "The POM was found at ${result.foundUrl} but could not be read (invalid XML or unexpected structure)."
                    )
                }
                val packaging = pom.packaging
                located.extension = when {
                    packaging == null || packaging == "bundle" -> "jar"
                    else -> packaging
                }
            }
            RepositoryProbe.Kind.MODULE -> located.extension = "aar"
            RepositoryProbe.Kind.FILE -> {
                located.extension = if (result.foundUrl != null && result.foundUrl!!.endsWith(".aar")) "aar" else "jar"
                callback.onNote(located, "No POM was published; its sub-dependencies cannot be determined")
            }
        }
        return Located(located, kind)
    }

    private fun locate(
        group: String,
        artifact: String,
        artifactVersion: String,
        callback: DependencyResolverCallback,
        depth: Int
    ): Artifact {
        val located = probe(group, artifact, artifactVersion, callback)
        val artifactObject = located.artifact
        callback.artifactFound(artifactObject)
        if (artifactObject.extension == "pom" || located.kind == RepositoryProbe.Kind.FILE) {
            return artifactObject
        }
        return applyModuleMetadata(located, callback, depth)
    }

    private fun applyModuleMetadata(
        located: Located,
        callback: DependencyResolverCallback,
        depth: Int
    ): Artifact {
        val artifact = located.artifact
        val repository = artifact.repository ?: return artifact
        val url = RepositoryProbe.moduleUrl(
            repository.getURL(),
            artifact.groupId,
            artifact.artifactId,
            artifact.version
        )
        val response = HttpFetcher.getWithRetry(url, 4 * 1024 * 1024, 2, BooleanSupplier { cancelled })
        val moduleOnly = located.kind == RepositoryProbe.Kind.MODULE
        if (!response.isSuccess()) {
            if (moduleOnly) {
                throw abort(
                    ResolverFailure.Stage.METADATA,
                    artifact.toString(),
                    "Module metadata at $url could not be loaded: ${response.describe()}"
                )
            }
            return artifact
        }

        val selection = try {
            GradleModuleMetadata.select(response.body)
        } catch (e: JSONException) {
            if (moduleOnly) {
                throw abort(
                    ResolverFailure.Stage.METADATA,
                    artifact.toString(),
                    "Gradle module metadata at $url could not be parsed: ${FailureFormatter.describe(e)}",
                    e
                )
            }
            callback.onNote(artifact, "Gradle module metadata ignored (unreadable); using the POM")
            return artifact
        }
        if (selection == null) return artifact

        if (selection.platformOnly) {
            artifact.extension = "pom"
            return artifact
        }
        if (selection.isRedirect) {
            if (depth >= MAX_VARIANT_REDIRECTS) {
                throw abort(
                    ResolverFailure.Stage.VARIANT,
                    artifact.toString(),
                    "Too many variant redirects while resolving ${artifact} (last variant '${selection.variantName}')."
                )
            }
            val target = locate(
                selection.redirectGroup!!,
                selection.redirectModule!!,
                selection.redirectVersion!!,
                callback,
                depth + 1
            )
            callback.onVariantSelected(
                artifact,
                target,
                "variant '${selection.variantName}' redirects to ${target.groupId}:${target.artifactId}"
            )
            return target
        }
        val fileName = selection.fileName
        if (fileName != null) {
            val lower = fileName.lowercase()
            if (lower.endsWith(".aar")) artifact.extension = "aar"
            else if (lower.endsWith(".jar")) artifact.extension = "jar"
            if (fileName != "${artifact.artifactId}-${artifact.version}.${artifact.extension}") {
                fileNameOverrides[keyOf(artifact)] = fileName
            }
            return artifact
        }
        if (moduleOnly) {
            throw abort(
                ResolverFailure.Stage.VARIANT,
                artifact.toString(),
                "No Android-compatible variant was found. Variants considered: ${selection.considered.joinToString("; ")}."
            )
        }
        callback.onNote(artifact, "No Android variant in the module metadata; using the POM packaging")
        return artifact
    }

    private suspend fun resolveTransitive(
        root: Artifact,
        recording: RecordingCallback,
        callback: DependencyResolverCallback
    ): List<Artifact> {
        checkCancelled(root)
        if (root.pom == null && root.getPOM() == null) {
            callback.onNote(root, "No POM available; sub-dependencies cannot be determined")
            return emptyList()
        }
        val all: Set<Artifact> = try {
            root.resolveDependencyTree()
            root.getAllDependencies()
        } catch (e: ResolverAbort) {
            throw e
        } catch (e: Exception) {
            throw abort(
                ResolverFailure.Stage.POM,
                root.toString(),
                "Resolving the dependency tree failed: ${FailureFormatter.describe(e)}",
                e
            )
        }

        val resolvedKeys = HashSet<String>()
        for (dep in all) resolvedKeys.add("${dep.groupId}:${dep.artifactId}")
        val rootKey = "${root.groupId}:${root.artifactId}"
        val dropped = recording.notFound.toList().filter {
            val key = "${it.groupId}:${it.artifactId}"
            key != rootKey && !resolvedKeys.contains(key)
        }.distinct()
        if (dropped.isNotEmpty()) {
            throw abort(
                ResolverFailure.Stage.REPOSITORY,
                root.toString(),
                "Required dependencies were not found in any repository: ${dropped.joinToString()}. " +
                        "Enable \"Skip downloading sub-dependencies\" to download only $root."
            )
        }

        val result = ArrayList<Artifact>()
        for (dep in all) {
            checkCancelled(dep)
            if (dep == root) continue
            val provider = providedBy?.invoke(dep)
            if (provider != null) {
                callback.onProvided(dep, provider)
                continue
            }
            val usable = repairTransitive(dep, callback) ?: continue
            result.add(usable)
            if (keyOf(usable) != keyOf(dep) && usable.extension != "pom") {
                for (extra in resolveTransitive(usable, recording, callback)) result.add(extra)
            }
        }
        return result
    }

    private fun repairTransitive(dep: Artifact, callback: DependencyResolverCallback): Artifact? {
        if (dep.version.isEmpty() || !VERSION_PART.matches(dep.version)) {
            callback.onVersionNotFound(dep)
            callback.onDependencySkipped(dep, "no usable version")
            return null
        }

        var current = dep
        if (dep.repository == null || dep.pom == null) {
            current = locate(dep.groupId, dep.artifactId, dep.version, callback, 0)
        } else if (dep.extension != "pom") {
            current = applyModuleMetadata(Located(dep, RepositoryProbe.Kind.POM), callback, 0)
        }

        if (current.extension == "pom") {
            callback.onDependencySkipped(current, "metadata-only POM (its dependencies are already part of the tree)")
            return null
        }
        if (current.extension != "jar" && current.extension != "aar") {
            callback.invalidPackaging(current)
            callback.onDependencySkipped(current, "packaging '${current.extension}' has no classes")
            return null
        }
        return current
    }

    private fun identityOf(artifact: Artifact): String =
        "${artifact.groupId}:${artifact.artifactId}:${artifact.version}"

    private fun readIdentity(dir: File): String? {
        val file = File(dir, IDENTITY_FILE)
        if (!file.isFile) return null
        val text = file.readText().trim()
        return if (text.isEmpty()) null else text
    }

    private fun writeIdentity(dir: File, identity: String) {
        File(dir, IDENTITY_FILE).writeText(identity)
    }

    private fun shortHash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
        val builder = StringBuilder()
        for (byte in digest) builder.append(String.format("%02x", byte))
        return builder.toString().substring(0, 8)
    }

    private fun folderFor(artifact: Artifact): String {
        val base = "${artifact.artifactId}-v${artifact.version}"
        val existing = readIdentity(File(downloadPath, base))
        if (existing == null || existing == identityOf(artifact)) return base
        return base + "-" + shortHash(artifact.groupId)
    }

    private fun isFolderComplete(folder: Path, extension: String): Boolean {
        val jar = folder.resolve("classes.jar")
        if (!Files.isRegularFile(jar) || Files.size(jar) == 0L) return false
        if (extension == "aar" && !Files.isRegularFile(folder.resolve("config"))) return false
        return try {
            ZipFile(jar.toFile()).use { true }
        } catch (e: Exception) {
            false
        }
    }

    private fun isCacheValid(folder: Path, extension: String): Boolean {
        if (!buildSettings.isOfflineCacheEnabled) return false
        return isFolderComplete(folder, extension)
    }

    private fun deleteDirectory(folder: Path) {
        if (Files.exists(folder)) FileUtil.deleteFile(folder.toString())
    }

    private fun discardIncomplete(folder: Path) {
        if (!Files.exists(folder)) return
        val extension = if (Files.exists(folder.resolve("config"))) "aar" else "jar"
        if (!isFolderComplete(folder, extension)) deleteDirectory(folder)
    }

    private fun hasResourcePayload(dir: Path): Boolean {
        for (name in listOf("res", "assets", "jni")) {
            val folder = dir.resolve(name)
            if (Files.isDirectory(folder)) {
                val any = try {
                    File(folder.toString()).walk().any { it.isFile }
                } catch (e: Exception) {
                    false
                }
                if (any) return true
            }
        }
        return false
    }

    private fun writePlaceholderJar(jar: Path) {
        java.util.jar.JarOutputStream(Files.newOutputStream(jar), java.util.jar.Manifest().apply {
            mainAttributes[java.util.jar.Attributes.Name.MANIFEST_VERSION] = "1.0"
        }).use { }
    }

    private fun jarHasClasses(jar: File): Boolean {
        return try {
            ZipFile(jar).use { zip ->
                zip.entries().asSequence().any {
                    !it.isDirectory && it.name.endsWith(".class") && !it.name.endsWith("module-info.class")
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun fetchWithVariantFallback(
        artifact: Artifact,
        callback: DependencyResolverCallback,
        replaced: (Artifact) -> Unit
    ): Fetched {
        var current = artifact
        val tried = ArrayList<String>()
        for (attempt in 0..MAX_VARIANT_REDIRECTS) {
            try {
                return fetch(current, callback)
            } catch (placeholder: PlaceholderArtifact) {
                tried.add(current.toString())
                val sibling = siblingVariant(current, tried, callback)
                if (sibling == null) {
                    throw PlaceholderArtifact(
                        placeholder.reason + " No Android/JVM variant could be found (checked Gradle module metadata, " +
                                "POM dependencies and the '-android'/'-jvm' sibling artifacts; tried: ${tried.joinToString()})."
                    )
                }
                callback.onVariantSelected(current, sibling, "placeholder artifact replaced by ${sibling.artifactId}")
                current = sibling
                replaced(current)
            }
        }
        throw PlaceholderArtifact("Too many variant redirects starting from ${artifact}.")
    }

    private fun siblingVariant(
        artifact: Artifact,
        tried: List<String>,
        callback: DependencyResolverCallback
    ): Artifact? {
        val candidates = ArrayList<String>()
        artifact.pom?.dependencies?.forEach {
            val group = it.groupId ?: artifact.groupId
            if (group == artifact.groupId && it.scope != "test" && it.scope != "provided" &&
                it.artifactId.startsWith(artifact.artifactId + "-") && !it.optional
            ) {
                candidates.add(it.artifactId)
            }
        }
        for (suffix in listOf("-android", "-jvm")) {
            val name = artifact.artifactId + suffix
            if (!candidates.contains(name)) candidates.add(name)
        }
        for (candidate in candidates) {
            val key = "${artifact.groupId}:$candidate:${artifact.version}"
            if (tried.contains(key)) continue
            try {
                val located = locate(artifact.groupId, candidate, artifact.version, callback, 0)
                if (located.extension == "jar" || located.extension == "aar") return located
            } catch (e: ResolverAbort) {
                if (e.failure.stage == ResolverFailure.Stage.CANCELLED) throw e
            }
        }
        return null
    }

    private fun fetch(artifact: Artifact, callback: DependencyResolverCallback): Fetched {
        checkCancelled(artifact)
        val folder = folderFor(artifact)
        val dir = Paths.get(downloadPath, folder)
        val extension = artifact.extension

        if (isCacheValid(dir, extension)) {
            callback.onCacheHit(artifact)
            return Fetched(folder, true)
        }

        discardIncomplete(dir)
        Files.createDirectories(dir)

        val repository = artifact.repository
            ?: throw abort(
                ResolverFailure.Stage.REPOSITORY,
                artifact.toString(),
                "No repository was selected for this artifact."
            )
        val fileName = fileNameOverrides[keyOf(artifact)]
        val url = if (fileName != null) {
            RepositoryProbe.fileUrl(repository.getURL(), artifact.groupId, artifact.artifactId, artifact.version, extension)
                .substringBeforeLast('/') + "/" + fileName
        } else {
            RepositoryProbe.fileUrl(repository.getURL(), artifact.groupId, artifact.artifactId, artifact.version, extension)
        }
        val target = dir.resolve("classes.$extension").toFile()

        callback.onDownloadStart(artifact)
        try {
            FileDownloader.download(
                url,
                target,
                FileDownloader.Progress { bytes, total ->
                    callback.onDownloadProgress(artifact, bytes, total)
                },
                BooleanSupplier { cancelled }
            )
        } catch (e: IOException) {
            deleteDirectory(dir)
            if (cancelled) {
                throw abort(ResolverFailure.Stage.CANCELLED, artifact.toString(), "Cancelled by user")
            }
            callback.onDownloadError(artifact, e)
            throw abort(
                ResolverFailure.Stage.DOWNLOAD,
                artifact.toString(),
                FailureFormatter.describe(e),
                e
            )
        }
        callback.onDownloadEnd(artifact)

        if (extension == "aar") {
            callback.unzipping(artifact)
            try {
                unzip(target.toPath())
            } catch (e: Exception) {
                deleteDirectory(dir)
                throw abort(
                    ResolverFailure.Stage.UNZIP,
                    artifact.toString(),
                    "The downloaded AAR could not be extracted: ${FailureFormatter.describe(e)}",
                    e
                )
            }
            target.delete()
            val packageName = findPackageName(dir.toAbsolutePath().toString(), artifact.groupId)
            dir.resolve("config").writeText(packageName)
        }

        validateContent(artifact, dir, callback)
        writeIdentity(dir.toFile(), identityOf(artifact))
        return Fetched(folder, false)
    }

    private fun validateContent(artifact: Artifact, dir: Path, callback: DependencyResolverCallback) {
        val jar = dir.resolve("classes.jar")
        val hasJar = Files.isRegularFile(jar) && Files.size(jar) > 0L
        if (!hasJar) {
            if (artifact.extension == "aar" && hasResourcePayload(dir)) {
                writePlaceholderJar(jar)
                callback.onNote(artifact, "Resource-only library (no code, resources are kept)")
                return
            }
            deleteDirectory(dir)
            throw PlaceholderArtifact(
                "${artifact} contains no classes.jar and no resources; it looks like a metadata or multiplatform placeholder."
            )
        }
        try {
            ZipFile(jar.toFile()).close()
        } catch (e: Exception) {
            deleteDirectory(dir)
            throw abort(
                ResolverFailure.Stage.UNZIP,
                artifact.toString(),
                "classes.jar is not a valid archive: ${FailureFormatter.describe(e)}",
                e
            )
        }
        if (!jarHasClasses(jar.toFile())) {
            if (artifact.extension == "aar" && hasResourcePayload(dir)) {
                callback.onNote(artifact, "Resource-only library (no code, resources are kept)")
                return
            }
            deleteDirectory(dir)
            throw PlaceholderArtifact("${artifact} contains no classes (empty archive).")
        }
    }

    private suspend fun handleDirectUrlDownload(urlStr: String, callback: DependencyResolverCallback) {
        val parsed = try {
            URL(urlStr)
        } catch (e: Exception) {
            throw abort(
                ResolverFailure.Stage.INVALID_COORDINATE,
                urlStr,
                "Not a valid URL: ${FailureFormatter.describe(e)}",
                e
            )
        }
        val rawName = (parsed.path ?: "").substringAfterLast('/')
        val decoded = try {
            URLDecoder.decode(rawName, "UTF-8")
        } catch (e: Exception) {
            rawName
        }
        val lower = decoded.lowercase()
        val ext = when {
            lower.endsWith(".aar") -> "aar"
            lower.endsWith(".jar") -> "jar"
            else -> throw abort(
                ResolverFailure.Stage.INVALID_COORDINATE,
                urlStr,
                "A direct URL must point to a .aar or .jar file (found '${decoded.ifEmpty { "/" }}')."
            )
        }
        val baseName = decoded.substring(0, decoded.length - 4)
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifEmpty { "direct-library" }

        val identity = "direct:$urlStr"
        val existing = readIdentity(File(downloadPath, baseName))
        val folderName = if (existing == null || existing == identity) {
            baseName
        } else {
            baseName + "-" + shortHash(urlStr)
        }

        val directDep = Artifact("direct", baseName).apply {
            version = "1.0"
            extension = ext
        }
        val outFolder = File(downloadPath, folderName)
        val outFile = File(outFolder, "classes.$ext")

        callback.onDirectDownloadStart(urlStr)
        callback.onDownloadStart(directDep)
        outFolder.mkdirs()
        try {
            FileDownloader.download(
                urlStr,
                outFile,
                FileDownloader.Progress { bytes, total ->
                    callback.onDownloadProgress(directDep, bytes, total)
                },
                BooleanSupplier { cancelled }
            )
        } catch (e: IOException) {
            deleteDirectory(outFolder.toPath())
            if (cancelled) {
                throw abort(ResolverFailure.Stage.CANCELLED, urlStr, "Cancelled by user")
            }
            callback.onDirectDownloadError(urlStr, e)
            callback.onDownloadError(directDep, e)
            throw abort(ResolverFailure.Stage.DOWNLOAD, urlStr, FailureFormatter.describe(e), e)
        }
        callback.onDownloadEnd(directDep)
        callback.onDirectDownloadEnd(decoded)

        if (ext == "aar") {
            callback.unzipping(directDep)
            try {
                unzip(outFile.toPath())
            } catch (e: Exception) {
                deleteDirectory(outFolder.toPath())
                throw abort(
                    ResolverFailure.Stage.UNZIP,
                    urlStr,
                    "The downloaded AAR could not be extracted: ${FailureFormatter.describe(e)}",
                    e
                )
            }
            outFile.delete()
            val pkgName = findPackageName(outFolder.absolutePath, "direct.download")
            File(outFolder, "config").writeText(pkgName)
        }

        try {
            validateContent(directDep, outFolder.toPath(), callback)
        } catch (placeholder: PlaceholderArtifact) {
            throw abort(ResolverFailure.Stage.MISSING_CLASSES, urlStr, placeholder.reason, placeholder)
        }
        checkCancelled(directDep)

        writeIdentity(outFolder, identity)
        callback.onResolutionComplete(directDep)
        rootFolder = folderName
        callback.onTaskCompleted(listOf(folderName))
    }

    private fun findPackageName(path: String, defaultValue: String): String {
        val manifest =
            File(path).walk().filter { it.isFile && it.name == "AndroidManifest.xml" }.firstOrNull()
        val content = manifest?.readText() ?: return defaultValue
        val p = Pattern.compile("<manifest.*package=\"(.*?)\"", Pattern.DOTALL)
        val m = p.matcher(content)
        if (m.find()) {
            return m.group(1)!!
        }
        return defaultValue
    }

    private fun unzip(path: Path) {
        val base = path.parent.toAbsolutePath().normalize()
        val zipFile = ZipFile(path.toFile())
        zipFile.use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val entryDestination = base.resolve(entry.name).normalize()
                if (!entryDestination.startsWith(base)) {
                    throw IOException("Blocked unsafe archive entry '${entry.name}'")
                }
                if (entry.isDirectory) {
                    Files.createDirectories(entryDestination)
                } else {
                    Files.createDirectories(entryDestination.parent)
                    zip.getInputStream(entry).use { input ->
                        Files.newOutputStream(entryDestination).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }
}
