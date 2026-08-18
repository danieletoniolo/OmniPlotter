package com.github.omniplotter.app;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Making {@code omniplotter} work in a terminal.
 *
 * <p>The installed launcher already is the command line — arguments mean the CLI and their absence
 * means the window — so none of this installs anything. It puts the launcher somewhere the shell
 * looks, under the name a command line expects.
 *
 * <p>Nothing here needs {@code sudo}, and nothing edits a shell's startup file on its own. When the
 * directory is not on {@code PATH} the caller is handed the exact line to add and decides.
 */
public final class CommandSetup {

    private CommandSetup() {}

    public static final String COMMAND = "omniplotter";

    /** What a run of {@link #install} did, so the caller can report it and say what remains. */
    public record Outcome(Path link, boolean created, boolean onPath, String shellLine, Path shellFile) {}

    /**
     * The running executable.
     *
     * <p>Empty when this is a bare {@code java -jar}: there is a launcher to link to only in an
     * installed application, and linking to the JDK would produce a command that ignores its
     * arguments.
     */
    public static Optional<Path> launcher() {
        return ProcessHandle.current().info().command()
            .map(Path::of)
            .filter(path -> {
                String name = path.getFileName().toString().toLowerCase();
                return !name.equals("java") && !name.equals("java.exe");
            });
    }

    /**
     * The launcher a terminal should be pointed at.
     *
     * <p>Only Windows has a choice to make. There the packaged application carries a second,
     * console launcher beside the one the desktop starts, because the desktop one has nowhere to
     * print; a link made from the running window has to be redirected to it. Everywhere else the
     * launcher that is running is the one that works.
     */
    public static Path consoleLauncher(Path running) {
        if (!AppPaths.isWindows()) {
            return running;
        }
        Path sibling = running.resolveSibling(COMMAND + ".exe");
        return Files.isExecutable(sibling) ? sibling : running;
    }

    /** Where the link or shim goes: a per-user directory, never one that would need root. */
    public static Path binDirectory() {
        return AppPaths.isWindows()
            ? AppPaths.config().resolveSibling("bin")
            : Path.of(System.getProperty("user.home"), ".local", "bin");
    }

    public static Path linkPath() {
        return binDirectory().resolve(AppPaths.isWindows() ? COMMAND + ".cmd" : COMMAND);
    }

    public static boolean isInstalled() {
        return Files.exists(linkPath());
    }

    /**
     * Points {@link #linkPath()} at the running launcher.
     *
     * <p>A symbolic link where symbolic links are free, and a one-line batch file on Windows, where
     * creating one needs either developer mode or an elevated prompt.
     */
    public static Outcome install(Path launcher) throws IOException {
        Path link = linkPath();
        Files.createDirectories(link.getParent());
        Files.deleteIfExists(link);

        if (AppPaths.isWindows()) {
            Files.writeString(link, "@echo off\r\n\"" + launcher + "\" %*\r\n");
        } else {
            Files.createSymbolicLink(link, launcher);
        }

        boolean onPath = isOnPath(link.getParent());
        return new Outcome(link, true, onPath,
            onPath ? null : shellLine(link.getParent()),
            onPath ? null : shellFile());
    }

    public static boolean uninstall() throws IOException {
        return Files.deleteIfExists(linkPath());
    }

    /** Whether the shell would find something in {@code directory} without being told about it. */
    public static boolean isOnPath(Path directory) {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String entry : path.split(File.pathSeparator)) {
            if (!entry.isBlank() && Path.of(entry).toAbsolutePath().normalize()
                    .equals(directory.toAbsolutePath().normalize())) {
                return true;
            }
        }
        return false;
    }

    /** Resolves {@code omniplotter} the way the shell would, for reporting what is in the way. */
    public static Optional<Path> resolveOnPath() {
        String path = System.getenv("PATH");
        if (path == null) {
            return Optional.empty();
        }
        List<String> names = AppPaths.isWindows()
            ? List.of(COMMAND + ".cmd", COMMAND + ".exe", COMMAND + ".bat")
            : List.of(COMMAND);
        for (String entry : path.split(File.pathSeparator)) {
            if (entry.isBlank()) {
                continue;
            }
            for (String name : names) {
                Path candidate = Path.of(entry).resolve(name);
                if (Files.isExecutable(candidate) || (AppPaths.isWindows() && Files.exists(candidate))) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    /** The line that would put {@code directory} on PATH, for the user to approve. */
    public static String shellLine(Path directory) {
        if (AppPaths.isWindows()) {
            return "[Environment]::SetEnvironmentVariable('Path', "
                + "[Environment]::GetEnvironmentVariable('Path', 'User') + ';" + directory + "', 'User')";
        }
        return "export PATH=\"" + directory + ":$PATH\"";
    }

    /**
     * The startup file the line belongs in.
     *
     * <p>Read from {@code SHELL} rather than assumed: macOS defaults to zsh and most Linux
     * distributions to bash, and writing to the wrong one looks like the command silently not
     * working.
     */
    public static Path shellFile() {
        if (AppPaths.isWindows()) {
            return null;
        }
        String shell = System.getenv("SHELL");
        String name = shell == null ? "" : Path.of(shell).getFileName().toString();
        Path home = Path.of(System.getProperty("user.home"));
        return switch (name) {
            case "zsh" -> home.resolve(".zshrc");
            case "fish" -> home.resolve(".config").resolve("fish").resolve("config.fish");
            default -> home.resolve(".bashrc");
        };
    }

    /** Appends the line to the startup file, having been told to. */
    public static void addToShellFile(Path file, String line) throws IOException {
        Files.createDirectories(file.getParent());
        List<String> existing = Files.exists(file) ? Files.readAllLines(file) : new ArrayList<>();
        if (existing.stream().anyMatch(l -> l.trim().equals(line.trim()))) {
            return;
        }
        String block = System.lineSeparator()
            + "# Added by omniplotter setup" + System.lineSeparator()
            + line + System.lineSeparator();
        Files.writeString(file, block, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
