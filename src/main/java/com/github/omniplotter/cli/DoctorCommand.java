package com.github.omniplotter.cli;

import com.github.omniplotter.app.AppPaths;
import com.github.omniplotter.app.CommandSetup;
import com.github.omniplotter.app.UpdateCheck;
import com.github.omniplotter.app.Version;
import picocli.CommandLine.Command;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Prints where everything is.
 *
 * <p>Exists to be pasted into a bug report. Every line is something that has turned out to be wrong
 * on someone's machine: the wrong copy on PATH, settings in a directory nobody looked at, a link
 * pointing at an application that has since been deleted.
 */
@Command(name = "doctor", mixinStandardHelpOptions = true,
    description = "Report where this installation keeps its pieces.")
public class DoctorCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        Version version = UpdateCheck.current();
        line("version", version == null ? "development build" : version.toString());
        line("java", System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        line("platform", System.getProperty("os.name") + " " + System.getProperty("os.version")
            + " " + System.getProperty("os.arch"));
        line("launcher", CommandSetup.launcher().map(Path::toString).orElse("running as a plain jar"));
        line("managed by a package manager", Boolean.toString(UpdateCheck.isManagedInstall()));
        System.out.println();

        line("settings", exists(AppPaths.settingsFile()));
        line("logs", exists(AppPaths.logs()));
        System.out.println();

        Path link = CommandSetup.linkPath();
        line("command link", exists(link));
        line("link directory on PATH", Boolean.toString(CommandSetup.isOnPath(link.getParent())));
        line("omniplotter resolves to",
            CommandSetup.resolveOnPath().map(Path::toString).orElse("nothing on PATH"));
        return 0;
    }

    private static String exists(Path path) {
        return path + (Files.exists(path) ? "" : "  (not created yet)");
    }

    private static void line(String label, String value) {
        System.out.printf("%-30s %s%n", label, value);
    }
}
