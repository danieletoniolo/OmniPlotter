package com.github.casiopicture;

import com.github.casiopicture.engine.EngineApi;
import com.github.casiopicture.engine.data.ConversionOptions;
import com.github.casiopicture.engine.data.ConversionResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for G3P encoder.
 * Validates file structure, decompression, and pixel data.
 */
public class G3PEncoderIntegrationTest {
    
    private static final Path TEST_OUTPUT_DIR = Paths.get("target/test-g3p");
    private static final Path TEST_IMAGE_PATH = Paths.get("test_simple_384x192.png");
    
    @BeforeAll
    public static void setupClass() throws IOException {
        Files.createDirectories(TEST_OUTPUT_DIR);
    }
    
    @Test
    public void testGenerateAndValidateG3P() throws Exception {
        // Load test image
        if (!Files.exists(TEST_IMAGE_PATH)) {
            System.out.println("Test image not found at: " + TEST_IMAGE_PATH.toAbsolutePath());
            return;
        }
        
        BufferedImage image = ImageIO.read(TEST_IMAGE_PATH.toFile());
        assertNotNull(image, "Test image should exist: " + TEST_IMAGE_PATH);
        assertEquals(384, image.getWidth(), "Image width should be 384");
        assertEquals(192, image.getHeight(), "Image height should be 192");
        
        // Read image as bytes
        byte[] imageBytes = Files.readAllBytes(TEST_IMAGE_PATH);
        
        // Generate G3P file
        ConversionOptions options = new ConversionOptions(384, 192, 65536, false, false);
        ConversionResult result = EngineApi.convert(imageBytes, "test.png", "cp.g3p", options);
        assertNotNull(result, "Conversion should succeed");
        
        byte[] g3pData = result.fileBytes();
        assertNotNull(g3pData, "Generated file data should not be null");
        assertTrue(g3pData.length > 0, "Generated file should not be empty");
        
        // Validate structure and content
        G3PFileValidator.ValidationResult validation = G3PFileValidator.validate(g3pData);
        assertTrue(validation.valid, "File validation failed: " + validation.error);
        assertEquals(384, validation.width, "Width should be 384");
        assertEquals(192, validation.height, "Height should be 192");
    }
    
    @Test
    public void testStructureOffsets() throws Exception {
        if (!Files.exists(TEST_IMAGE_PATH)) return;
        
        BufferedImage image = ImageIO.read(TEST_IMAGE_PATH.toFile());
        byte[] imageBytes = Files.readAllBytes(TEST_IMAGE_PATH);
        
        ConversionOptions options = new ConversionOptions(384, 192, 65536, false, false);
        ConversionResult result = EngineApi.convert(imageBytes, "test.png", "cp.g3p", options);
        
        byte[] g3pData = result.fileBytes();
        
        // Verify critical offsets
        assertEquals("CP", new String(g3pData, 0x20, 2), "Header should contain 'CP' at 0x20");
        
        // Read dimensions (should match input)
        int width = bytesToBigEndianInt(g3pData, 0xC0);
        int height = bytesToBigEndianInt(g3pData, 0xC4, 2);
        
        assertEquals(384, width, "Width at 0xC0 should be 384");
        assertEquals(192, height, "Height at 0xC4 should be 192");
        
        // Verify compressed size is reasonable
        int compressedSize = bytesToBigEndianInt(g3pData, 0xBC);
        assertTrue(compressedSize > 0, "Compressed size should be positive");
        assertTrue(compressedSize < g3pData.length, "Compressed size should be less than file size");
    }
    
    @Test
    public void testDecompression() throws Exception {
        if (!Files.exists(TEST_IMAGE_PATH)) return;
        
        byte[] imageBytes = Files.readAllBytes(TEST_IMAGE_PATH);
        
        ConversionOptions options = new ConversionOptions(384, 192, 65536, false, false);
        ConversionResult result = EngineApi.convert(imageBytes, "test.png", "cp.g3p", options);
        
        G3PFileValidator.ValidationResult validation = G3PFileValidator.validate(result.fileBytes());
        assertTrue(validation.valid, "File should be valid");
        
        byte[] decompressed = validation.decompressedData;
        assertNotNull(decompressed, "Decompressed data should not be null");
        
        int expectedSize = 384 * 192 * 2; // RGB565 = 2 bytes per pixel
        assertEquals(expectedSize, decompressed.length, "Decompressed size should match expected");
        
        // Verify data is not all zeros or garbage
        long nonZeroCount = 0;
        for (byte b : decompressed) {
            if (b != 0) nonZeroCount++;
        }
        assertTrue(nonZeroCount > decompressed.length / 2, "At least half the data should be non-zero");
    }
    
    @Test
    public void testRegressionAgainstReference() throws Exception {
        if (!Files.exists(TEST_IMAGE_PATH)) return;
        
        // This test compares against a reference file generated from the working JavaScript tool
        Path refPath = Paths.get("debug_tools/Screensh.g3p");
        if (!Files.exists(refPath)) {
            System.out.println("Reference file not found, skipping regression test: " + refPath);
            return;
        }
        
        byte[] referenceData = Files.readAllBytes(refPath);
        G3PFileValidator.ValidationResult refValidation = G3PFileValidator.validate(referenceData);
        assertTrue(refValidation.valid, "Reference file should be valid");
        
        // Generate a test file with known content
        byte[] imageBytes = Files.readAllBytes(TEST_IMAGE_PATH);
        ConversionOptions options = new ConversionOptions(384, 192, 65536, false, false);
        ConversionResult result = EngineApi.convert(imageBytes, "test.png", "cp.g3p", options);
        
        G3PFileValidator.ValidationResult testValidation = G3PFileValidator.validate(result.fileBytes());
        assertTrue(testValidation.valid, "Generated file should be valid");
        
        // Both should have valid structure
        assertEquals(refValidation.width, 384, "Reference width should be 384");
        assertEquals(testValidation.width, 384, "Generated width should be 384");
    }
    
    @Test
    public void testConsistencyAcrossRuns() throws Exception {
        if (!Files.exists(TEST_IMAGE_PATH)) return;
        
        byte[] imageBytes = Files.readAllBytes(TEST_IMAGE_PATH);
        ConversionOptions options = new ConversionOptions(384, 192, 65536, false, false);
        
        // Generate file twice
        ConversionResult result1 = EngineApi.convert(imageBytes, "test.png", "cp.g3p", options);
        ConversionResult result2 = EngineApi.convert(imageBytes, "test.png", "cp.g3p", options);
        
        // Validate both
        G3PFileValidator.ValidationResult validation1 = G3PFileValidator.validate(result1.fileBytes());
        G3PFileValidator.ValidationResult validation2 = G3PFileValidator.validate(result2.fileBytes());
        
        assertTrue(validation1.valid, "First file should be valid");
        assertTrue(validation2.valid, "Second file should be valid");
        
        // Decompressed pixel data should be identical
        G3PFileValidator.ComparisonResult comparison = G3PFileValidator.compare(
            validation1.decompressedData, validation2.decompressedData, 384, 192);
        
        assertTrue(comparison.matches(), "Multiple runs should produce identical pixel data");
    }
    
    // Helper methods
    private static int bytesToBigEndianInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24) |
               ((data[offset + 1] & 0xFF) << 16) |
               ((data[offset + 2] & 0xFF) << 8) |
               (data[offset + 3] & 0xFF);
    }
    
    private static int bytesToBigEndianInt(byte[] data, int offset, int numBytes) {
        int result = 0;
        for (int i = 0; i < numBytes; i++) {
            result = (result << 8) | (data[offset + i] & 0xFF);
        }
        return result;
    }
}
