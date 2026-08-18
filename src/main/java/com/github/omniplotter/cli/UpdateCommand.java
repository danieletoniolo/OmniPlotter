package com.github.omniplotter.cli;

import com.github.omniplotter.app.Settings;
import com.github.omniplotter.app.UpdateCheck;
import com.github.omniplotter.app.Version;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Reports whether a newer release exists, and nothing more.
 *
 * <p>Unlike the window's check, this one runs whatever the daily interval and the skipped version
 * say: someone typing the command is asking now.
 */
@Command(name = "update", description = "Check whether a newer release is available.")
public class UpdateCommand implements Callable<Integer> {

    @Option(names = "--auto", paramLabel = "true|false",
        description = "Turn the window's daily check on or off, and exit.")
    private Boolean auto;

    @Override
    public Integer call() {
        if (auto != null) {
            Settings.setBoolean(Settings.UPDATE_CHECK, auto);
            Settings.save();
            System.out.println("The daily check is now " + (auto ? "on." : "off."));
            return 0;
        }

        Version current = UpdateCheck.current();
        System.out.println("Installed: " + (current == null ? "development build" : current));

        Optional<UpdateCheck.Result> latest;
        try {
            latest = UpdateCheck.fetchLatest();
        } catch (IOException e) {
            System.err.println("Could not reach GitHub: " + e.getMessage());
            return 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Interrupted.");
            return 1;
        }

        if (latest.isEmpty()) {
            System.out.println("No releases have been published yet.");
            return 0;
        }

        UpdateCheck.Result result = latest.get();
        System.out.println("Latest:    " + result.latest());
        System.out.println();
        if (current == null) {
            System.out.println("A development build carries no version to compare against.");
            System.out.println(result.url());
        } else if (result.latest().isNewerThan(current)) {
            System.out.println("An update is available: " + result.url());
            System.out.println("Installers there are unsigned; the release notes say what the");
            System.out.println("first launch needs on macOS and Windows.");
        } else {
            System.out.println("Up to date.");
        }
        return 0;
    }
}
