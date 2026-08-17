# G3P Encoder Test Pipeline

## Overview

This test suite provides integration and regression testing for the G3P encoder to ensure changes don't break functionality.

## Test Components

### 1. **G3PFileValidator** (`src/test/java/com/github/casiopicture/G3PFileValidator.java`)

Utility class that validates and inspects .g3p file structure:

- **`validate(byte[] g3pData)`** - Validates entire file structure
  - Checks dimensions at proper offsets
  - Verifies file structure (length prefix, zlib header, Adler32)
  - De-obfuscates and decompresses data
  - Returns ValidationResult with decompressed pixel data

- **`compare(byte[] data1, byte[] data2, int width, int height)`** - Compares two pixel datasets
  - Returns match percentage and byte-level differences

### 2. **G3PEncoderIntegrationTest** (`src/test/java/com/github/casiopicture/G3PEncoderIntegrationTest.java`)

Main test class with 5 integration tests:

#### Test 1: `testGenerateAndValidateG3P()`
- Loads test image
- Converts to G3P format
- Validates file structure and dimensions
- **Ensures**: Basic conversion pipeline works

#### Test 2: `testStructureOffsets()`
- Verifies critical file offsets are correct:
  - 0x20: Format header ("CP")
  - 0xBC: Compressed data size
  - 0xC0: Image width
  - 0xC4: Image height
- **Ensures**: File structure remains consistent

#### Test 3: `testDecompression()`
- Validates decompression produces expected size
- Checks pixel data isn't all zeros
- **Ensures**: Compression and decompression integrity

#### Test 4: `testRegressionAgainstReference()`
- Compares generated file against reference (if available)
- **Ensures**: Generated files match known good format

#### Test 5: `testConsistencyAcrossRuns()`
- Generates file twice from same input
- Compares decompressed pixel data
- **Ensures**: Deterministic generation (no random elements)

## Running Tests

### Run all tests:
```bash
./casiopicture.sh test
```

### Run only G3P tests:
```bash
./casiopicture.sh test -Dtest=G3PEncoderIntegrationTest
```

### Run specific test:
```bash
./casiopicture.sh test -Dtest=G3PEncoderIntegrationTest#testDecompression
```

### With detailed output:
```bash
./casiopicture.sh test -Dtest=G3PEncoderIntegrationTest -e
```

## Test Image

A 384×192 gradient test image is automatically created:
- Location: `test_simple_384x192.png` and `src/test/resources/test_simple_384x192.png`
- Format: Red→Yellow gradient from left to right
- Used by all integration tests

## Expected Behavior

All 5 tests should **PASS** with no failures:

```
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
```

If any test fails after code changes:
1. Check the error message for which offset/size is wrong
2. Verify the encoder changes didn't modify critical sections
3. Use `G3PFileValidator` to inspect the generated file

## File Structure Validated

The test pipeline ensures:

```
[4-byte length prefix]
[zlib header: 78 9C]
[compressed pixel data]
[4-byte Adler32 checksum]
├── All obfuscated with:
├── 1. Bit inversion
└── 2. Bit swap(5,3)
```

## Adding New Tests

To add a new test:

1. Add new test method in `G3PEncoderIntegrationTest`
2. Use `G3PFileValidator.validate()` to check structure
3. Compare pixel data if needed
4. Run with: `./casiopicture.sh test`

## CI/CD Integration

These tests can be integrated into CI/CD:

```bash
./casiopicture.sh test
```

Fails the build if any test fails, ensuring regressions are caught immediately.

## Troubleshooting

**Test skipped due to missing image?**
- Run: `java CreateTestImage` in root directory
- Or: `./mvnw test` will auto-create if missing

**Regression test skipped?**
- Place your `Screensh.g3p` reference file in `debug_tools/` folder
- Test will automatically validate against it

**Unexpected structure errors?**
- Check encoder offsets haven't changed
- Verify Deflater uses `false` (zlib format)
- Ensure length prefix is added before obfuscation
