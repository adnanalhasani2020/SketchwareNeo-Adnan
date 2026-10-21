package mod.pranav.build

import mod.hey.studios.build.BuildSettings
import mod.hey.studios.project.proguard.ProguardHandler
import a.a.a.yq
import android.util.Log
import com.android.tools.r8.CompilationMode
import com.android.tools.r8.OutputMode
import com.android.tools.r8.R8
import com.android.tools.r8.R8Command
import com.android.tools.r8.origin.Origin
import com.android.tools.r8.DiagnosticsHandler
import com.android.tools.r8.Diagnostic
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.Executors

class R8Compiler(
    private val rules: MutableList<String>,
    private val configs: Array<String>,
    val libs: Array<String>,
    private val inputs: Array<String>,
    private val minApi: Int,
    private val multiDexEnabled: Boolean,
    val yq: yq
) {

    companion object {
        private const val TAG = "R8Compiler"
    }

    private fun computeSafeThreadCount(programByteSize: Long): Int {
        val runtime = Runtime.getRuntime()
        val maxHeapMb = runtime.maxMemory() / (1024 * 1024)
        val cpuCount = runtime.availableProcessors()

        val heapCap = when {
            maxHeapMb < 300 -> 1
            maxHeapMb < 500 -> 2
            maxHeapMb < 800 -> 3
            else -> 4
        }

        val programSizeMb = programByteSize / (1024 * 1024)
        val sizeAdjustedCap = when {
            programSizeMb > 20 -> 1
            programSizeMb > 8 -> minOf(heapCap, 2)
            else -> heapCap
        }

        val userCap = BuildSettings(yq.sc_id).parallelThreads
        return minOf(sizeAdjustedCap, cpuCount, userCap).coerceAtLeast(1)
    }

    fun compile() {
        val output = Paths.get(yq.binDirectoryPath, "dex")
        Files.createDirectories(output)
        output.toFile().listFiles()?.forEach { file ->
            if (file.isFile && file.name.matches(Regex("classes(?:[0-9]+)?\\.dex"))) {
                if (!file.delete()) throw java.io.IOException("Unable to remove stale R8 output: ${file.name}")
            }
        }
        inputs.forEach { path ->
            val input = File(path)
            if (!input.isFile || input.length() == 0L) {
                throw java.io.IOException("R8 program input is missing or empty: $path")
            }
        }
        libs.forEach { path ->
            if (!File(path).isFile) throw java.io.IOException("R8 library input is missing: $path")
        }

        val errorLog = StringBuilder()
        val diagnosticsHandler = object : DiagnosticsHandler {
            override fun error(diagnostic: Diagnostic) {
                errorLog.append("ERROR: ").append(diagnostic.diagnosticMessage).append("\n")
            }
            override fun warning(diagnostic: Diagnostic) {
            }
            override fun info(diagnostic: Diagnostic) {}
        }

        val builder = R8Command.builder(diagnosticsHandler)
            .addProgramFiles(inputs.map { Paths.get(it) })
            .addProguardConfiguration(rules, Origin.unknown())
            .addProguardConfigurationFiles(configs.map { Paths.get(it) })
            .setProguardMapOutputPath(Paths.get(yq.proguardMappingPath))
            .setMinApiLevel(minApi)
            .setProguardCompatibility(!ProguardHandler(yq.sc_id).isR8FullModeEnabled())
            .addLibraryFiles(libs.map { Paths.get(it) })
            .setOutput(output, OutputMode.DexIndexed)
            .setMode(CompilationMode.RELEASE)

        if (multiDexEnabled) {

        }

        val programBytes = inputs.sumOf { runCatching { File(it).length() }.getOrDefault(0L) }
        val threadCount = computeSafeThreadCount(programBytes)
        val runtime = Runtime.getRuntime()
        val freeHeapMbBefore =
            (runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())) / (1024 * 1024)
        Log.d(
            TAG,
            "R8 starting: programInputs=${inputs.size} (${programBytes / 1024}KB), " +
                "libraryInputs=${libs.size}, configFiles=${configs.size}, rules=${rules.size}, " +
                "threads=$threadCount, maxHeapMB=${runtime.maxMemory() / (1024 * 1024)}, " +
                "freeHeapMB=$freeHeapMbBefore"
        )
        val startTime = System.currentTimeMillis()

        val executor = Executors.newFixedThreadPool(threadCount)
        try {
            R8.run(builder.build(), executor)
            if (errorLog.isNotEmpty()) {
                throw IllegalStateException("R8 reported compilation errors:\n$errorLog")
            }
            val dexOutputs = output.toFile().listFiles { file ->
                file.isFile && file.name.matches(Regex("classes(?:[0-9]+)?\\.dex")) && file.length() > 0L
            }.orEmpty()
            if (dexOutputs.isEmpty()) {
                throw java.io.IOException("R8 completed without producing a non-empty classes.dex output.")
            }
        } catch (e: Exception) {

            if (errorLog.isNotEmpty() && e.message?.contains(errorLog.toString()) != true) {
                throw RuntimeException("R8 Error:\n$errorLog", e)
            } else {
                throw e
            }
        } finally {
            executor.shutdownNow()
            val duration = System.currentTimeMillis() - startTime
            val freeHeapMbAfter =
                (runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())) / (1024 * 1024)
            Log.d(TAG, "R8 finished in ${duration}ms, freeHeapMB after=$freeHeapMbAfter")
        }
    }
}
