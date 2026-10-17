package mod.sketchlibx.terminal;

import java.util.List;

/** Single source of truth for the helper commands the terminal advertises. */
public final class TerminalCommands {

    public static final String ACTION_COMMAND = "mod.sketchlibx.terminal.ACTION_COMMAND";
    public static final String EXTRA_COMMAND = "command";
    public static final String EXTRA_REQUEST_ID = "request_id";
    public static final String EXTRA_SC_ID = "sc_id";
    public static final String EXTRA_CONFIRMED = "confirmed";
    public static final String EXTRA_DRY_RUN = "dry_run";

    public static final String COMMAND_BUILD = "build";
    public static final String COMMAND_CANCEL_BUILD = "cancel-build";
    public static final String COMMAND_CLEAN_CACHE = "clean-cache";
    public static final String COMMAND_CLEAN_GRADLE_BACKUPS = "clean-gradle-backups";

    public static final int EXIT_NOT_AVAILABLE = 4;
    public static final int EXIT_BUSY = 5;
    public static final int EXIT_CANCELED = 130;

    public static final List<String> HELP_LINES = List.of(
            "Sketchware Neo Terminal commands",
            "--------------------------------",
            "",
            "help, sw_help",
            "    Show this list.",
            "",
            "sw_build",
            "    Build a debug APK of this project with the same pipeline as the Run button.",
            "    Progress and the final result are printed here. Ctrl+C cancels the build",
            "    (press it again to stop waiting). The APK is not installed automatically.",
            "",
            "sw_clean_cache",
            "    Delete the generated build cache of this project (.sketchware/mysc/<project id>)",
            "    after showing its size and asking for confirmation. Projects, sources, manifests",
            "    and settings are not touched; the next build recreates the cache.",
            "",
            "sw_clean_gradle_backups",
            "    Delete the Gradle configuration backups of this project (backup_gradle_*) that",
            "    contain only the known Gradle files, after confirmation. Anything else is skipped.",
            "",
            "sw_project",
            "    Print the project data directory and list its files.",
            "",
            "swdoctor",
            "    Check which common tools are available in this shell and show NDK/CMake status.",
            "",
            "ndk_status",
            "    Show the installed CMake, NDK and Ninja versions.",
            "",
            "ndk_install <url>",
            "    Ask Sketchware Neo to install an NDK/CMake archive from an http(s) URL. The",
            "    install runs in the background and its result is not reported here; run",
            "    ndk_status afterwards to see whether it finished.",
            "",
            "gradlew <tasks>",
            "    Run ./gradlew from the current directory, or system Gradle if installed.",
            "    Explains why it cannot run when neither is available.",
            "",
            "ll",
            "    Alias for ls -la.",
            "",
            "Output menu: use the clear icon to clear the screen and scrollback, or the other",
            "items there for command help, diagnostics and recent commands.",
            "Standard Android shell tools (ls, cd, cat, grep, ...) come from the device and",
            "are not managed by Sketchware Neo.");

    private TerminalCommands() {
    }
}
