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

    /**
     * The Windows console launcher, which cannot be called {@code omniplotter}: NTFS is
     * case-insensitive, so that name is already taken by {@code OmniPlotter.exe}.
     */
    public static final String CONSOLE_COMMAND = "omniplotter-cli";

    /**
     * What a run of {@link #install} did, so the caller can report it and say what remains.
     *
     * @param shellLine the line that would put the link on PATH, absent when it already is
     * @param shellFile the startup file that line belongs in — absent on Windows, where there is no
     *                  such file and the line is a command to run once instead
     */
    public record Outcome(Path link, boolean created, boolean onPath,
                          Optional<String> shellLine, Optional<Path> shellFile) {}

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
        Path sibling = running.resolveSibling(CONSOLE_COMMAND + ".exe");
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
     * Where a completion script goes to be picked up without anyone configuring anything.
     *
     * <p>bash-completion reads this directory on its own, on both Linux and macOS. Windows has no
     * equivalent worth writing to, so nothing is generated there.
     */
    public static Path completionFile() {
        String data = System.getenv("XDG_DATA_HOME");
        Path base = data == null || data.isBlank()
            ? Path.of(System.getProperty("user.home"), ".local", "share")
            : Path.of(data);
        return base.resolve("bash-completion").resolve("completions").resolve(COMMAND);
    }

    /** Writes the completion script, or reports that this platform does not take one. */
    public static Optional<Path> installCompletion(String script) throws IOException {
        if (AppPaths.isWindows()) {
            return Optional.empty();
        }
        Path file = completionFile();
        Files.createDirectories(file.getParent());
        Files.writeString(file, script);
        return Optional.of(file);
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
            onPath ? Optional.empty() : Optional.of(shellLine(link.getParent())),
            onPath ? Optional.empty() : shellFile());
    }

    public static boolean uninstall() throws IOException {
        boolean removed = Files.deleteIfExists(linkPath());
        if (!AppPaths.isWindows()) {
            Files.deleteIfExists(completionFile());
        }
        return removed;
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

    /**
     * The line that would put {@code directory} on PATH, for the user to approve.
     *
     * <p>Read from the same place as {@link #shellFile()}, so the two cannot disagree: fish has no
     * {@code export}, and bash syntax written into {@code config.fish} is a line that looks right
     * and silently does nothing.
     */
    public static String shellLine(Path directory) {
        return shellLine(directory, shellName());
    }

    /** The same for a named shell, so each syntax can be checked without that shell installed. */
    public static String shellLine(Path directory, String shell) {
        if (AppPaths.isWindows()) {
            return "[Environment]::SetEnvironmentVariable('Path', "
                + "[Environment]::GetEnvironmentVariable('Path', 'User') + ';" + directory + "', 'User')";
        }
        if ("fish".equals(shell)) {
            return "fish_add_path " + directory;
        }
        return "export PATH=\"" + directory + ":$PATH\"";
    }

    /**
     * The startup file the line belongs in, and nothing on Windows.
     *
     * <p>Read from {@code SHELL} rather than assumed: macOS defaults to zsh and most Linux
     * distributions to bash, and writing to the wrong one looks like the command silently not
     * working. Windows has no such file at all — its line is a command run once — and returning
     * nothing says so where a null used to be printed as the word "null".
     */
    public static Optional<Path> shellFile() {
        return shellFile(shellName());
    }

    /** The same for a named shell. */
    public static Optional<Path> shellFile(String shell) {
        if (AppPaths.isWindows()) {
            return Optional.empty();
        }
        Path home = Path.of(System.getProperty("user.home"));
        return Optional.of(switch (shell) {
            case "zsh" -> home.resolve(".zshrc");
            case "fish" -> home.resolve(".config").resolve("fish").resolve("config.fish");
            default -> home.resolve(".bashrc");
        });
    }

    /** The shell the user is in, as its bare name, or empty when the environment does not say. */
    public static String shellName() {
        String shell = System.getenv("SHELL");
        return shell == null ? "" : Path.of(shell).getFileName().toString();
    }

    /** Appends the line to the startup file, having been told to. */
    public static void addToShellFile(Path file, String line) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
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
