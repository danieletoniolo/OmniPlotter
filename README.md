# CasioPicture - Standalone Portable Run Environment

This project contains the Java implementation of **web image convert** (converting images to calculator formats like Casio `.g3p` and TI-Python format). It has been migrated from an IntelliJ-dependent setup to a completely standalone, portable execution environment.

---

## Quick Start

### 1. Initialize the Environment
Run the setup script. It will detect your Mac's CPU architecture (Intel or Apple Silicon), download and unpack JDK 17 and Apache Maven 3.9.6 locally into the `tools/` folder, and generate a workspace runner script (`casiopicture.sh`).

```bash
chmod +x setup.sh
./setup.sh
```

### 2. Build the Application
Use the local runner script to build the project. This compiles the code and bundles all the required dependencies (such as MaterialFX and imgscalr) into a single shaded fat JAR.

```bash
./casiopicture.sh build
```

---

## Running the Application

The runner script (`./casiopicture.sh`) handles setting up `JAVA_HOME` and paths on the fly. It supports the following commands:

### Command-Line Interface (CLI)
To run the CLI tool to convert an image:
```bash
./casiopicture.sh cli -f <format> <input-image> -o <output-file>
```
*Example:*
```bash
./casiopicture.sh cli -f cp.g3p test_simple_384x192.png -o test_output.g3p
```

To see CLI help:
```bash
./casiopicture.sh cli --help
```

### Graphical User Interface (GUI)
You can launch the JavaFX/MaterialFX GUI in two ways:

1. **Direct Dev Mode (via JavaFX Maven plugin):**
   ```bash
   ./casiopicture.sh run
   ```
2. **Packaged JAR Mode (runs the shaded fat JAR):**
   ```bash
   ./casiopicture.sh run-jar
   ```

### Running Tests
To run unit and integration tests using the local environment:
```bash
./casiopicture.sh test
```

---

## Technical Details

- **Local Tools Directory:** All tools (JDK 17 and Apache Maven) are stored inside the project workspace directory under `tools/` to keep the user's global system environment clean and unmodified.
- **JAR Launcher:** Non-modular JavaFX applications packaged in a shaded fat JAR will throw an initialization error if the main class extends `javafx.application.Application` without modules on the module path. To prevent this, we added [Launcher.java](file:///Users/toniolo/Documents/Misc-Workspace/casiopicture/src/main/java/com/github/casiopicture/gui/Launcher.java) as a plain main entry point which boots the JavaFX application safely from the classpath.
