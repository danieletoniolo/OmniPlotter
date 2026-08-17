#!/bin/bash

# Exit immediately if a command exits with a non-zero status
set -e

echo "=== CasioPicture Standalone Environment Setup ==="

# 1. Detect Architecture
ARCH=$(uname -m)
OS=$(uname -s)

if [ "$OS" != "Darwin" ]; then
    echo "Error: This setup script is configured for macOS only."
    exit 1
fi

echo "Detected OS: macOS ($ARCH)"

# Determine JDK download URL based on architecture
JDK_URL=""
if [ "$ARCH" = "arm64" ]; then
    JDK_URL="https://api.adoptium.net/v3/binary/latest/17/ga/mac/aarch64/jdk/hotspot/normal/eclipse"
elif [ "$ARCH" = "x86_64" ] || [ "$ARCH" = "x64" ]; then
    JDK_URL="https://api.adoptium.net/v3/binary/latest/17/ga/mac/x64/jdk/hotspot/normal/eclipse"
else
    echo "Error: Unsupported architecture $ARCH"
    exit 1
fi

# 2. Create tools directories
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
TOOLS_DIR="${SCRIPT_DIR}/tools"
JDK_DIR="${TOOLS_DIR}/jdk"
MAVEN_DIR="${TOOLS_DIR}/maven"

mkdir -p "${TOOLS_DIR}"

# 3. Download and Install JDK if not already present
if [ -f "${JDK_DIR}/Contents/Home/bin/java" ]; then
    echo "Java JDK 17 is already installed locally in tools/jdk."
else
    echo "Downloading JDK 17..."
    mkdir -p "${JDK_DIR}"
    curl -L -o "${TOOLS_DIR}/jdk.tar.gz" "${JDK_URL}"
    
    echo "Extracting JDK 17..."
    tar -xzf "${TOOLS_DIR}/jdk.tar.gz" -C "${JDK_DIR}" --strip-components=1
    rm "${TOOLS_DIR}/jdk.tar.gz"
    echo "JDK 17 installed successfully."
fi

# 4. Download and Install Maven if not already present
if [ -f "${MAVEN_DIR}/bin/mvn" ]; then
    echo "Apache Maven is already installed locally in tools/maven."
else
    echo "Downloading Apache Maven 3.9.6..."
    mkdir -p "${MAVEN_DIR}"
    curl -L -o "${TOOLS_DIR}/maven.tar.gz" "https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz"
    
    echo "Extracting Apache Maven..."
    tar -xzf "${TOOLS_DIR}/maven.tar.gz" -C "${MAVEN_DIR}" --strip-components=1
    rm "${TOOLS_DIR}/maven.tar.gz"
    echo "Apache Maven 3.9.6 installed successfully."
fi

# 5. Make binaries executable (just in case)
chmod +x "${JDK_DIR}/Contents/Home/bin/"* || true
chmod +x "${MAVEN_DIR}/bin/"* || true

# 6. Generate the workspace wrapper script (casiopicture.sh)
RUNNER_PATH="${SCRIPT_DIR}/casiopicture.sh"
echo "Generating run wrapper script: ${RUNNER_PATH}..."

cat << 'EOF' > "${RUNNER_PATH}"
#!/bin/bash

# Determine workspace root directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Set up local paths to isolated JDK and Maven
export JAVA_HOME="${SCRIPT_DIR}/tools/jdk/Contents/Home"
export PATH="${SCRIPT_DIR}/tools/maven/bin:${JAVA_HOME}/bin:${PATH}"

# Check if environment is initialized
if [ ! -d "${JAVA_HOME}" ] || [ ! -f "${SCRIPT_DIR}/tools/maven/bin/mvn" ]; then
    echo "Error: Standalone environment tools not found in 'tools/'."
    echo "Please run './setup.sh' to download and set up JDK and Maven first."
    exit 1
fi

COMMAND=$1
if [ -n "$COMMAND" ]; then
    shift
fi

case "${COMMAND}" in
    build)
        echo "Building CasioPicture project..."
        mvn clean package
        ;;
    run)
        echo "Running CasioPicture GUI (via JavaFX Maven plugin)..."
        mvn javafx:run
        ;;
    run-jar)
        if [ ! -f "${SCRIPT_DIR}/target/casiopicture-1.0-SNAPSHOT.jar" ]; then
            echo "Error: Shaded JAR not found. Run '$0 build' first."
            exit 1
        fi
        echo "Running CasioPicture GUI from Shaded JAR..."
        java -jar "${SCRIPT_DIR}/target/casiopicture-1.0-SNAPSHOT.jar" "$@"
        ;;
    cli)
        if [ ! -f "${SCRIPT_DIR}/target/casiopicture-1.0-SNAPSHOT.jar" ]; then
            echo "Building project first to ensure target/casiopicture-1.0-SNAPSHOT.jar exists..."
            mvn clean package
        fi
        echo "Running CasioPicture CLI..."
        java -cp "${SCRIPT_DIR}/target/casiopicture-1.0-SNAPSHOT.jar" com.github.casiopicture.cli.Cli "$@"
        ;;
    test)
        echo "Running tests..."
        mvn test "$@"
        ;;
    *)
        echo "Usage: $0 {build|run|run-jar|cli|test}"
        echo "  build         - Compile and package the application using local JDK & Maven"
        echo "  run           - Run the GUI application using JavaFX Maven plugin"
        echo "  run-jar       - Run the GUI application from the shaded JAR file"
        echo "  cli [args]    - Run the CLI application with arguments"
        echo "  test [args]   - Run unit/integration tests with arguments"
        echo ""
        echo "Example: Run conversion via CLI:"
        echo "  $0 cli -f cp.g3p test_simple_384x192.png -o output.g3p"
        ;;
esac
EOF

chmod +x "${RUNNER_PATH}"

echo ""
echo "=== Setup Completed Successfully! ==="
echo "You can now build, test, and run the application using the runner script:"
echo "  Build:      ./casiopicture.sh build"
echo "  Run tests:  ./casiopicture.sh test"
echo "  Run GUI:    ./casiopicture.sh run"
echo "  Run CLI:    ./casiopicture.sh cli [args]"
echo "====================================="
