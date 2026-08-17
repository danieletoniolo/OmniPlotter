package com.github.casiopicture.engine.encoder;

import com.github.casiopicture.engine.data.ConversionOptions;
import com.github.casiopicture.engine.data.ConversionResult;
import com.github.casiopicture.engine.data.Format;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Interface for encoding processed images into specific file formats.
 */
public interface FileEncoder {

    /**
     * Encodes a processed image into a final file format.
     *
     * @param processedImage   The intermediate image from the pre-processing phase.
     * @param format           The target format identifier.
     * @param originalFileName The original name of the input file, used for naming suggestions.
     * @param options          The user-defined conversion options.
     * @return A {@link ConversionResult} containing the file bytes and a suggested name.
     * @throws IOException if an error occurs during image processing or byte manipulation.
     */
    ConversionResult encode(BufferedImage processedImage, Format format, String originalFileName, ConversionOptions options) throws IOException;

    /**
     * Returns the appropriate FileEncoder instance for the specified format.
     *
     * @param format the {@link Format} enum value specifying the desired encoder type
     * @return a FileEncoder instance corresponding to the given format:
     *         - {@link TIZ80Encoder} for TI calculator formats (TI_8XV, TI_8CA, TI_8CI, TI_8XI, TI_83I, TI_73I, TI_82I, TI_85I, TI_86I)
     *         - {@link PythonEncoder} for Python-based formats (TI_GRAPHICS_PY, TI_DRAW_CE_PY, KANDINSKY_PY, CASIO_PICTURE_PY, MICROBIT_PY, HPPRIME_PY, and others)
     *         - {@link CasioPictureEncoder} for Casio picture formats (C2P, CP_G3P, CP01_G3P, CP01_G4P, and their indexed variants)
     *         - {@link ZeroEncoder} for ZPIC format
     * @throws IllegalArgumentException if the format is not supported or recognized
     */
    static FileEncoder getEncoder(Format format) {
        return switch (format) {
            case TI_8XV, TI_8CA, TI_8CI, TI_8XI, TI_83I, TI_73I, TI_82I, TI_85I, TI_86I
                -> TIZ80Encoder.getInstance();
            case TI_GRAPHICS_PY, TI_DRAW_CE_PY, TI_DRAW_CX_PY, KANDINSKY_PY, CASIO_PICTURE_PY, CASIOPLOT_CG_PY, GRAPHIC_G3_PY, GINT_G3_PY, GRAPHIC_CG_PY, GINT_CG_PY, NSP_CX_PY, NSP_NS_PY, MICROBIT_PY, MICROBIT_SMALL_PY, TI_HUB_RGBARR_PY, HPPRIME_PY
                -> PythonEncoder.getInstance();
            case C2P, CP_G3P, CP01_G3P, CP01_G4P, I_C2P, CP_I_G3P, CP01_I_G3P, CP01_I_G4P
                -> CasioPictureEncoder.getInstance();
            case ZPIC
                -> ZeroEncoder.getInstance();
        };
    }
}
