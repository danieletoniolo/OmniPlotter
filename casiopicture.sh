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
    refgen)
        if ! command -v node >/dev/null 2>&1; then
            echo "Error: Node.js is required to regenerate the reference vectors."
            exit 1
        fi
        if [ ! -f "${SCRIPT_DIR}/tmp/index.html" ]; then
            echo "Error: tmp/index.html (the img2calc reference) is missing."
            exit 1
        fi
        echo "Regenerating reference vectors from tmp/index.html..."
        node "${SCRIPT_DIR}/tools/refgen/refgen.mjs"
        ;;
    *)
        echo "Usage: $0 {build|run|run-jar|cli|test}"
        echo "  build         - Compile and package the application using local JDK & Maven"
        echo "  run           - Run the GUI application using JavaFX Maven plugin"
        echo "  run-jar       - Run the GUI application from the shaded JAR file"
        echo "  cli [args]    - Run the CLI application with arguments"
        echo "  test [args]   - Run unit/integration tests with arguments"
        echo "  refgen        - Regenerate the golden reference vectors from tmp/index.html"
        echo ""
        echo "Example: Run conversion via CLI:"
        echo "  $0 cli -f cp.g3p test_simple_384x192.png -o output.g3p"
        ;;
esac
