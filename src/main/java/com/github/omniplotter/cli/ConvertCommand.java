package com.github.omniplotter.cli;

import com.github.omniplotter.engine.EngineApi;
import com.github.omniplotter.engine.data.ConversionOptions;
import com.github.omniplotter.engine.data.ConversionResult;
import com.github.omniplotter.engine.data.Format;
import com.github.omniplotter.engine.data.FormatConfig;
import com.github.omniplotter.engine.data.Mode;
import com.github.omniplotter.engine.data.Target;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "convert", description = "Convert one or more images to a calculator format.")
public class ConvertCommand implements Callable<Integer> {

    @Parameters(index = "0..*", arity = "1..*", paramLabel = "IMAGE",
        description = "Image files to convert.")
    private List<File> inputs = new ArrayList<>();

    @Option(names = {"-f", "--format"}, required = true, paramLabel = "FORMAT",
        description = "Output format, e.g. cp.g3p, 8ci, kandinsky.py. See 'formats'.")
    private String formatId;

    @Option(names = {"-t", "--target"}, paramLabel = "TARGET",
        description = "Calculator model, e.g. cg, cg3, nw110. Defaults to one that supports the "
            + "format. Affects Python imports and the .tns wrapper.")
    private String targetId;

    @Option(names = {"-o", "--output"}, paramLabel = "PATH",
        description = "Output file, or a directory when converting several images. "
            + "Defaults to the current directory.")
    private File output;

    @Option(names = "--width", description = "Canvas width. Defaults to the format's own.")
    private Integer width;

    @Option(names = "--height", description = "Canvas height. Defaults to the format's own.")
    private Integer height;

    @Option(names = "--colors", description = "Colour budget. Defaults to the format's maximum.")
    private Integer colors;

    @Option(names = "--preset", paramLabel = "NAME",
        description = "Named canvas size for the format, e.g. full, menu, graph.")
    private String preset;

    @Option(names = "--enlarge-smaller",
        description = "Scale a source smaller than the canvas up to fill it. Off by default, "
            + "matching the web tool.")
    private boolean enlargeSmaller;

    @Option(names = "--no-keep-ratio", description = "Stretch to the canvas instead of preserving "
        + "the source aspect ratio.")
    private boolean noKeepRatio;

    @Option(names = "--name", paramLabel = "NAME",
        description = "On-calculator variable name, 8 characters max. Defaults to the file name.")
    private String onCalcName;

    @Option(names = "--number", paramLabel = "N",
        description = "On-calculator slot for Pic/Image formats (0-9). Default 1.")
    private int onCalcNumber = 1;

    @Override
    public Integer call() {
        Format format;
        try {
            format = Format.fromString(formatId);
        } catch (IllegalArgumentException e) {
            System.err.println("Unknown format: " + formatId);
            System.err.println("Run 'omniplotter formats' to list them.");
            return 2;
        }

        Mode mode = format.isScript() ? Mode.SCRIPT : Mode.VAR;
        Target target;
        if (targetId != null) {
            try {
                target = Target.fromString(targetId);
            } catch (IllegalArgumentException e) {
                System.err.println("Unknown target: " + targetId);
                return 2;
            }
            if (!target.supports(mode, format)) {
                System.err.println(target.getId() + " does not support " + format.id() + ".");
                List<Format> supported = target.getSupportedFormats(mode);
                if (supported.isEmpty()) {
                    System.err.println(target.getId() + " has no " + mode + " formats at all.");
                } else {
                    System.err.println("It accepts: " + ids(supported));
                }
                System.err.println("Targets for " + format.id() + ": "
                    + String.join(", ", Target.supporting(mode, format).stream().map(Target::getId).toList()));
                return 2;
            }
        } else {
            List<Target> candidates = Target.supporting(mode, format);
            if (candidates.isEmpty()) {
                System.err.println("No target supports " + format.id() + ".");
                return 2;
            }
            target = candidates.get(0);
        }

        FormatConfig config = FormatConfig.of(format);
        ConversionOptions options = ConversionOptions.defaults(target, format)
            .withKeepRatio(!noKeepRatio)
            .withEnlargeSmaller(enlargeSmaller)
            .withOnCalc(onCalcName == null ? "IMAGE" : onCalcName, onCalcNumber);

        if (preset != null) {
            var match = config.presets().stream()
                .filter(p -> p.label().equalsIgnoreCase(preset)).findFirst();
            if (match.isEmpty()) {
                System.err.println("Unknown preset: " + preset);
                System.err.println("Available for " + format.id() + ": "
                    + config.presets().stream().map(FormatConfig.SizePreset::label).toList());
                return 2;
            }
            options = options.withSize(match.get().width(), match.get().height());
        }
        if (width != null || height != null) {
            options = options.withSize(
                width != null ? width : options.width(),
                height != null ? height : options.height());
        }
        if (colors != null) {
            options = options.withColors(colors);
        }

        // Report anything the format will not honour, rather than silently substituting.
        ConversionOptions clamped = options.clampedTo(format);
        if (clamped.width() != options.width() || clamped.height() != options.height()) {
            System.err.printf("note: %s uses a %dx%d canvas; requested %dx%d%n",
                format.id(), clamped.width(), clamped.height(), options.width(), options.height());
        }
        if (clamped.colors() != options.colors()) {
            System.err.printf("note: %s allows at most %d colours; requested %d%n",
                format.id(), clamped.colors(), options.colors());
        }
        options = clamped;

        boolean multiple = inputs.size() > 1;
        if (multiple && output != null && output.exists() && !output.isDirectory()) {
            System.err.println("With several inputs, --output must be a directory.");
            return 2;
        }

        int failures = 0;
        for (File input : inputs) {
            try {
                failures += convertOne(input, format, target, options, multiple) ? 0 : 1;
            } catch (Exception e) {
                System.err.println(input.getName() + ": " + e.getMessage());
                failures++;
            }
        }
        return failures == 0 ? 0 : 1;
    }

    private boolean convertOne(File input, Format format, Target target,
                               ConversionOptions options, boolean multiple) throws Exception {
        if (!input.isFile()) {
            System.err.println(input.getPath() + ": not a file");
            return false;
        }
        byte[] bytes = Files.readAllBytes(input.toPath());

        // The on-calc name defaults to the input file's name when not given explicitly.
        ConversionOptions perFile = onCalcName == null
            ? options.withOnCalc(baseName(input.getName()), options.onCalcNumber())
            : options;

        ConversionResult result = EngineApi.convert(bytes, input.getName(), format, perFile);

        Path destination;
        if (output == null) {
            destination = Path.of(result.suggestedFileName());
        } else if (output.isDirectory() || multiple) {
            Files.createDirectories(output.toPath());
            destination = output.toPath().resolve(result.suggestedFileName());
        } else {
            destination = output.toPath();
        }

        Files.createDirectories(destination.toAbsolutePath().getParent());
        Files.write(destination, result.fileBytes());
        System.out.printf("%s -> %s (%s, %dx%d, %d bytes)%n",
            input.getName(), destination, format.id(),
            perFile.width(), perFile.height(), result.fileBytes().length);
        return true;
    }

    private static String baseName(String fileName) {
        int dot = fileName.indexOf('.');
        String base = dot >= 0 ? fileName.substring(0, dot) : fileName;
        return base.isEmpty() ? "IMAGE" : base;
    }

    private static String ids(List<Format> formats) {
        return String.join(", ", formats.stream().map(Format::id).toList());
    }
}
