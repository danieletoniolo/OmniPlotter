package com.github.casiopicture.cli;

import com.github.casiopicture.engine.EngineApi;
import com.github.casiopicture.engine.data.ConversionOptions;
import com.github.casiopicture.engine.data.ConversionResult;
import com.github.casiopicture.engine.data.FormatConfig;
import org.apache.commons.io.FileUtils;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.util.concurrent.Callable;

@Command(name = "calcpicture", mixinStandardHelpOptions = true, version = "calcpicture 1.0",
        description = "Converts images to calculator formats.")
public class Cli implements Callable<Integer> {

    @Parameters(index = "0", description = "The image file to convert.")
    private File inputFile;

    @Option(names = {"-f", "--format"}, description = "The output format (e.g., 'cp.g3p', 'ti_graphics.py').", required = true)
    private String format;

    @Option(names = {"-o", "--output"}, description = "The output file. If not specified, a name will be generated.")
    private File outputFile;

    @Option(names = {"--width"}, description = "The width of the output image. Defaults to the format's standard width.")
    private Integer width;

    @Option(names = {"--height"}, description = "The height of the output image. Defaults to the format's standard height.")
    private Integer height;

    @Option(names = {"--colors"}, description = "The number of colors in the output image. Defaults to the format's maximum.")
    private Integer colors;

    @Option(names = {"--fit"}, description = "Enlarge smaller images to fit the target dimensions.")
    private boolean fit = false;

    @Option(names = {"--no-keep-ratio"}, description = "Do not preserve the aspect ratio of the original image (stretch to fit).")
    private boolean noKeepRatio = false;

    @Override
    public Integer call() throws Exception {
        // Load format-specific configuration to get defaults
        FormatConfig config = FormatConfig.of(format);
        if (config == null) {
            System.err.println("Error: Unsupported format '" + format + "'.");
            // You could add a command to list available formats.
            return 1;
        }

        // Use provided values or fall back to format defaults
        int finalWidth = (width != null) ? width : config.defaultWidth();
        int finalHeight = (height != null) ? height : config.defaultHeight();
        int finalColors = (colors != null) ? colors : config.maxColors();
        boolean keepRatio = !noKeepRatio;

        byte[] inputBytes = FileUtils.readFileToByteArray(inputFile);

        ConversionOptions options = new ConversionOptions(finalWidth, finalHeight, finalColors, keepRatio, fit);

        System.out.println("Converting " + inputFile.getName() + " to " + format + "...");
        System.out.println("Options: " + finalWidth + "x" + finalHeight + ", " + finalColors + " colors, keepRatio=" + keepRatio + ", fit=" + fit);

        ConversionResult result = EngineApi.convert(inputBytes, inputFile.getName(), format, options);

        File finalOutputFile = outputFile;
        if (finalOutputFile == null) {
            finalOutputFile = new File(result.suggestedFileName());
        }

        FileUtils.writeByteArrayToFile(finalOutputFile, result.fileBytes());
        System.out.println("Success! Converted image saved to " + finalOutputFile.getAbsolutePath());
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Cli()).execute(args);
        System.exit(exitCode);
    }
}
