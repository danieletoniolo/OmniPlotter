package com.github.casiopicture;

import java.util.zip.Inflater;

/**
 * Utility class to validate and inspect .g3p file structure and content.
 * Used for integration and regression testing.
 */
public class G3PFileValidator {
    
    public static class ValidationResult {
        public boolean valid;
        public int width;
        public int height;
        public int compressedSize;
        public byte[] decompressedData;
        public String error;
        
        public ValidationResult(boolean valid, String error) {
            this.valid = valid;
            this.error = error;
        }
        
        public ValidationResult(boolean valid, int width, int height, int compressedSize, byte[] decompressed) {
            this.valid = valid;
            this.width = width;
            this.height = height;
            this.compressedSize = compressedSize;
            this.decompressedData = decompressed;
        }
    }
    
    /**
     * Validate a .g3p file structure and content
     */
    public static ValidationResult validate(byte[] g3pData) {
        if (g3pData == null || g3pData.length < 0xCC + 1) {
            return new ValidationResult(false, "File too small");
        }
        
        // Read dimensions from file offsets
        int width = bytesToBigEndianInt(g3pData, 0xC0);
        int height = bytesToBigEndianInt(g3pData, 0xC4, 2);
        
        if (width <= 0 || height <= 0 || width > 512 || height > 512) {
            return new ValidationResult(false, "Invalid dimensions: " + width + "x" + height);
        }
        
        // Read compressed size
        int compressedSize = bytesToBigEndianInt(g3pData, 0xBC);
        if (compressedSize <= 4 || g3pData.length < 0xCC + compressedSize) {
            return new ValidationResult(false, "Invalid compressed size: " + compressedSize);
        }
        
        // Read the 4-byte length prefix (which is not obfuscated!)
        int lengthPrefix = bytesToBigEndianInt(g3pData, 0xCC);
        if (lengthPrefix != compressedSize - 4) {
            return new ValidationResult(false, 
                "Length prefix mismatch: expected " + (compressedSize - 4) + ", got " + lengthPrefix);
        }
        
        // Extract the obfuscated body (excluding the 4-byte prefix)
        byte[] body = new byte[lengthPrefix];
        System.arraycopy(g3pData, 0xCC + 4, body, 0, lengthPrefix);
        
        // De-obfuscate body
        byte[] deobfuscated = deobfuscate(body);
        
        // Check zlib header of the deobfuscated body
        int byte0 = deobfuscated[0] & 0xFF;
        int byte1 = deobfuscated[1] & 0xFF;
        if (byte0 != 0x78 || byte1 != 0x9C) {
            return new ValidationResult(false, 
                "Invalid zlib header: 0x" + String.format("%02X%02X", byte0, byte1) + " (expected 0x789C)");
        }
        
        // Decompress
        try {
            byte[] decompressed = decompress(deobfuscated, 0, deobfuscated.length);
            int expectedSize = width * height * 2; // RGB565 = 2 bytes per pixel
            
            if (decompressed.length != expectedSize) {
                return new ValidationResult(false, 
                    "Decompressed size mismatch: expected " + expectedSize + ", got " + decompressed.length);
            }
            
            return new ValidationResult(true, width, height, compressedSize, decompressed);
        } catch (Exception e) {
            return new ValidationResult(false, "Decompression failed: " + e.getMessage());
        }
    }
    
    /**
     * De-obfuscate compressed data (inverse of obfuscation)
     */
    private static byte[] deobfuscate(byte[] data) {
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            int b = data[i] & 0xFF;
            // Un-swap bits (INVERSE of swap(5,3))
            int mask1_shifted = 0xF8;   // where lower 5 bits ended up
            int mask2_shifted = 0x07;   // where upper 3 bits ended up
            b = ((b & mask1_shifted) >> 3) | ((b & mask2_shifted) << 5);
            // Un-invert
            b = (~b) & 0xFF;
            result[i] = (byte) b;
        }
        return result;
    }
    
    /**
     * Decompress zlib data (WITH header)
     */
    private static byte[] decompress(byte[] data, int offset, int length) throws Exception {
        Inflater inflater = new Inflater(false); // false = zlib WITH header
        inflater.setInput(data, offset, length);
        
        byte[] output = new byte[147456 * 2]; // Max size for decompression
        int decompressedLen = inflater.inflate(output);
        inflater.end();
        
        byte[] result = new byte[decompressedLen];
        System.arraycopy(output, 0, result, 0, decompressedLen);
        return result;
    }
    
    /**
     * Read big-endian integer (4 bytes)
     */
    private static int bytesToBigEndianInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24) |
               ((data[offset + 1] & 0xFF) << 16) |
               ((data[offset + 2] & 0xFF) << 8) |
               (data[offset + 3] & 0xFF);
    }
    
    /**
     * Read big-endian integer (variable bytes)
     */
    private static int bytesToBigEndianInt(byte[] data, int offset, int numBytes) {
        int result = 0;
        for (int i = 0; i < numBytes; i++) {
            result = (result << 8) | (data[offset + i] & 0xFF);
        }
        return result;
    }
    
    /**
     * Sample a pixel from decompressed RGB565 data
     */
    public static int getPixel(byte[] decompressed, int x, int y, int width) {
        int offset = (y * width + x) * 2;
        if (offset < 0 || offset + 1 >= decompressed.length) {
            return 0;
        }
        return ((decompressed[offset] & 0xFF) << 8) | (decompressed[offset + 1] & 0xFF);
    }
    
    /**
     * Compare two decompressed pixel data arrays
     */
    public static ComparisonResult compare(byte[] data1, byte[] data2, int width, int height) {
        ComparisonResult result = new ComparisonResult();
        result.totalPixels = width * height;
        
        if (data1.length != data2.length) {
            result.error = "Size mismatch: " + data1.length + " vs " + data2.length;
            return result;
        }
        
        int differences = 0;
        for (int i = 0; i < data1.length; i++) {
            if (data1[i] != data2[i]) {
                differences++;
            }
        }
        
        result.matchingBytes = data1.length - differences;
        result.diffBytes = differences;
        result.matchPercentage = (result.matchingBytes * 100.0) / data1.length;
        
        return result;
    }
    
    public static class ComparisonResult {
        public int totalPixels;
        public int matchingBytes;
        public int diffBytes;
        public double matchPercentage;
        public String error;
        
        public boolean matches() {
            return diffBytes == 0;
        }
    }
}
