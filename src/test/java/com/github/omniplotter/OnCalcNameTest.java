package com.github.omniplotter;

import com.github.omniplotter.engine.EngineApi;
import com.github.omniplotter.engine.data.ConversionOptions;
import com.github.omniplotter.engine.data.Format;
import com.github.omniplotter.engine.data.OnCalcName;
import com.github.omniplotter.engine.data.Target;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * On-calculator addressing: the name written into the file, and the slot it occupies.
 *
 * <p>Both end up in the encoded bytes, so getting them wrong is not cosmetic — a bad TI variable
 * name is refused by the calculator, and a wrong slot index installs the picture somewhere the user
 * did not ask for.
 */
class OnCalcNameTest {

    private static BufferedImage image() {
        BufferedImage img = new BufferedImage(64, 48, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 48; y++) {
            for (int x = 0; x < 64; x++) {
                img.setRGB(x, y, 0xFF000000 | (x * 4 << 16) | (y * 5 << 8) | 0x80);
            }
        }
        return img;
    }

    @Test
    void tiVariableNamesMustStartWithACapital() {
        assertTrue(OnCalcName.validateName(Format.TI_8XV, "Photo1").isEmpty());
        assertTrue(OnCalcName.validateName(Format.TI_8XV, "A").isEmpty());
        assertTrue(OnCalcName.validateName(Format.TI_8XV, "Abcdefgh").isEmpty());

        assertTrue(OnCalcName.validateName(Format.TI_8XV, "lower").isPresent());
        assertTrue(OnCalcName.validateName(Format.TI_8XV, "1Digit").isPresent());
        assertTrue(OnCalcName.validateName(Format.TI_8XV, "Abcdefghi").isPresent());   // 9 chars
        assertTrue(OnCalcName.validateName(Format.TI_8XV, "Has Space").isPresent());
    }

    @Test
    void casioNamesOnlyHaveALengthLimit() {
        assertTrue(OnCalcName.validateName(Format.CP_G3P, "lower").isEmpty());
        assertTrue(OnCalcName.validateName(Format.CP_G3P, "12345678").isEmpty());
        assertTrue(OnCalcName.validateName(Format.CP_G3P, "123456789").isPresent());
    }

    @Test
    void slotFormatsIgnoreTheNameAndScriptsHaveNoRule() {
        assertTrue(OnCalcName.validateName(Format.TI_8CI, "anything at all").isEmpty());
        assertTrue(OnCalcName.validateName(Format.KANDINSKY_PY, "anything at all").isEmpty());
    }

    @Test
    void slotRangesFollowTheModelNotTheFormat() {
        // The same format on three models accepts three different ranges.
        assertTrue(OnCalcName.validateSlot(Format.TI_8XI, Target.TI_73, 2).isEmpty());
        assertTrue(OnCalcName.validateSlot(Format.TI_8XI, Target.TI_73, 0).isPresent());
        assertTrue(OnCalcName.validateSlot(Format.TI_8XI, Target.TI_73, 4).isPresent());

        assertTrue(OnCalcName.validateSlot(Format.TI_82I, Target.TI_82, 6).isEmpty());
        assertTrue(OnCalcName.validateSlot(Format.TI_82I, Target.TI_82, 7).isPresent());

        assertTrue(OnCalcName.validateSlot(Format.TI_8CI, Target.TI_8X_COLOR, 9).isEmpty());
        assertTrue(OnCalcName.validateSlot(Format.TI_8CI, Target.TI_8X_COLOR, 10).isPresent());
    }

    @Test
    void suggestionsFromAFileNameAreAlwaysUsable() {
        // A TI variable cannot hold spaces or start with a digit, so the suggestion has to fix both.
        String suggested = OnCalcName.suggestFrom(Format.TI_8XV, "my photo 2.png");
        assertTrue(OnCalcName.validateName(Format.TI_8XV, suggested).isEmpty(), suggested);

        String fromDigits = OnCalcName.suggestFrom(Format.TI_8XV, "2024.png");
        assertTrue(OnCalcName.validateName(Format.TI_8XV, fromDigits).isEmpty(), fromDigits);

        String longName = OnCalcName.suggestFrom(Format.CP_G3P, "a-very-long-file-name.png");
        assertTrue(OnCalcName.validateName(Format.CP_G3P, longName).isEmpty(), longName);
    }

    @Test
    void theRequestedNameReachesTheOutput() throws Exception {
        ConversionOptions options = ConversionOptions.defaults(Target.CASIO_CG, Format.CP_G3P)
            .withOnCalc("PICT1", 1);
        var result = EngineApi.convert(image(), "something-else.png", Format.CP_G3P, options);
        assertEquals("PICT1.g3p", result.suggestedFileName());
    }

    @Test
    void theRequestedSlotIsEncodedInTheFile() throws Exception {
        // The slot is written into the variable-name field, not just the file name: two slots must
        // produce different bytes, or the picture lands in the wrong place on the device.
        var slot1 = EngineApi.convert(image(), "x.png", Format.TI_8CI,
            ConversionOptions.defaults(Target.TI_8X_COLOR, Format.TI_8CI).withOnCalc("X", 1));
        var slot7 = EngineApi.convert(image(), "x.png", Format.TI_8CI,
            ConversionOptions.defaults(Target.TI_8X_COLOR, Format.TI_8CI).withOnCalc("X", 7));

        assertEquals("Pic1.8ci", slot1.suggestedFileName());
        assertEquals("Pic7.8ci", slot7.suggestedFileName());
        assertNotEquals(
            java.util.Arrays.toString(slot1.fileBytes()),
            java.util.Arrays.toString(slot7.fileBytes()),
            "the slot must change the encoded bytes, not only the file name");
    }

    @Test
    void im8cComesWithAScriptThatDrawsIt() throws Exception {
        var result = EngineApi.convert(image(), "x.png", Format.TI_8XV,
            ConversionOptions.defaults(Target.TI_8X_PYTHON, Format.TI_8XV).withOnCalc("Photo1", 1));

        assertEquals("Photo1.8xv", result.suggestedFileName());
        assertEquals(1, result.extras().size());

        var companion = result.extras().get(0);
        assertEquals("Photo1.py", companion.name());
        String script = new String(companion.bytes(), java.nio.charset.StandardCharsets.US_ASCII);
        // The script has to name the variable it just wrote, or it draws nothing.
        assertTrue(script.contains("drawImage(\"Photo1\", 0, 30)"), script);
        assertEquals(2, result.allFiles().size());
    }

    @Test
    void formatsWithoutACompanionProduceOneFile() throws Exception {
        var result = EngineApi.convert(image(), "x.png", Format.CP_G3P,
            ConversionOptions.defaults(Target.CASIO_CG, Format.CP_G3P));
        assertTrue(result.extras().isEmpty());
        assertEquals(1, result.allFiles().size());
    }

    @Test
    void zpicCarriesItsSlotInTheName() throws Exception {
        var result = EngineApi.convert(image(), "x.png", Format.ZPIC,
            ConversionOptions.defaults(Target.ZERO, Format.ZPIC).withOnCalc("X", 4));
        assertEquals("pic4", result.suggestedFileName());
    }
}
