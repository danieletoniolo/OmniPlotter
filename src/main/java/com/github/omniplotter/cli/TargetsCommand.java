package com.github.omniplotter.cli;

import com.github.omniplotter.engine.data.Format;
import com.github.omniplotter.engine.data.Mode;
import com.github.omniplotter.engine.data.Target;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(name = "targets", mixinStandardHelpOptions = true,
    description = "List calculator models and the formats they accept.")
public class TargetsCommand implements Callable<Integer> {

    @Option(names = {"-m", "--mode"}, paramLabel = "MODE",
        description = "Only 'var' (binary picture files) or 'script' (Python).")
    private String modeId;

    @Override
    public Integer call() {
        Mode filter;
        try {
            filter = Cli.parseMode(modeId);
        } catch (IllegalArgumentException e) {
            System.err.println("Unknown mode: " + modeId + " (expected var or script)");
            return 2;
        }

        Target.Brand brand = null;
        for (Target target : Target.values()) {
            if (filter != null && !target.supportsMode(filter)) {
                continue;
            }
            if (target.getBrand() != brand) {
                brand = target.getBrand();
                System.out.println();
                System.out.println(brand.label());
            }
            System.out.printf("  %-10s %s%n", target.getId(), target.getDisplayName());
            for (Mode mode : Mode.values()) {
                if ((filter != null && mode != filter) || !target.supportsMode(mode)) {
                    continue;
                }
                Format defaultFormat = target.getDefaultFormat(mode);
                StringBuilder line = new StringBuilder();
                for (Format format : target.getSupportedFormats(mode)) {
                    line.append(line.isEmpty() ? "" : ", ")
                        .append(format.id())
                        .append(format == defaultFormat ? " (default)" : "");
                }
                System.out.printf("    %-7s %s%n", mode, line);
            }
        }
        System.out.println();
        return 0;
    }
}
