package com.github.omniplotter.cli;

import com.github.omniplotter.engine.inspect.CasioFileInspector;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.Callable;

@Command(name = "inspect", mixinStandardHelpOptions = true,
    description = "Read back a Casio picture file and report its structure.")
public class InspectCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "FILE", description = "A .g3p, .g4p or .c2p file.")
    private File file;

    @Override
    public Integer call() throws Exception {
        if (!file.isFile()) {
            System.err.println(file.getPath() + ": not a file");
            return 2;
        }

        CasioFileInspector.Report report = CasioFileInspector.inspect(Files.readAllBytes(file.toPath()));
        System.out.println(file.getName() + "  [" + report.kind() + "]");
        for (var field : report.fields()) {
            System.out.printf("  %s %-24s %s%n", field.ok() ? "  " : "!!", field.name(), field.value());
        }

        if (report.ok()) {
            System.out.println("\nStructure is consistent.");
            return 0;
        }
        System.out.println();
        for (String problem : report.problems()) {
            System.out.println("problem: " + problem);
        }
        return 1;
    }
}
