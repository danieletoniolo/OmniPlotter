package com.github.casiopicture.cli;

import com.github.casiopicture.engine.data.Format;
import com.github.casiopicture.engine.data.FormatConfig;
import com.github.casiopicture.engine.data.Mode;
import com.github.casiopicture.engine.data.Target;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "formats", description = "List output formats, with their canvas and colour limits.")
public class FormatsCommand implements Callable<Integer> {

    @Option(names = {"-t", "--target"}, paramLabel = "TARGET",
        description = "Only formats this calculator accepts.")
    private String targetId;

    @Option(names = {"-m", "--mode"}, paramLabel = "MODE",
        description = "Only 'var' (binary picture files) or 'script' (Python).")
    private String modeId;

    @Override
    public Integer call() {
        Target target = null;
        if (targetId != null) {
            try {
                target = Target.fromString(targetId);
            } catch (IllegalArgumentException e) {
                System.err.println("Unknown target: " + targetId);
                return 2;
            }
        }
        Mode mode;
        try {
            mode = Cli.parseMode(modeId);
        } catch (IllegalArgumentException e) {
            System.err.println("Unknown mode: " + modeId + " (expected var or script)");
            return 2;
        }

        List<Format> formats = new ArrayList<>();
        for (Format format : Format.values()) {
            Mode formatMode = format.isScript() ? Mode.SCRIPT : Mode.VAR;
            if (mode != null && formatMode != mode) {
                continue;
            }
            if (target != null && !target.supports(formatMode, format)) {
                continue;
            }
            formats.add(format);
        }
        if (formats.isEmpty()) {
            System.out.println("No formats match.");
            return 0;
        }

        System.out.printf("%-18s %-7s %-10s %-9s %s%n", "FORMAT", "MODE", "CANVAS", "COLOURS", "PRESETS");
        for (Format format : formats) {
            FormatConfig config = FormatConfig.of(format);
            String canvas = config.defaultWidth() + "x" + config.defaultHeight()
                + (config.editableSize() ? "" : " fixed");
            String presets = String.join(", ",
                config.presets().stream().map(FormatConfig.SizePreset::label).toList());
            System.out.printf("%-18s %-7s %-10s %-9d %s%n",
                format.id(), format.isScript() ? "script" : "var", canvas, config.maxColors(), presets);
        }
        return 0;
    }
}
