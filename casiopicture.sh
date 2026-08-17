#!/bin/bash
#
# Runner for the local toolchain. Everything it needs lives inside this directory; run ./setup.sh
# once after cloning to populate it.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

case "$(uname -s)" in
    Darwin) JAVA_HOME="${SCRIPT_DIR}/tools/jdk/Contents/Home" ;;
    *)      JAVA_HOME="${SCRIPT_DIR}/tools/jdk" ;;
esac
export JAVA_HOME
export PATH="${SCRIPT_DIR}/tools/maven/bin:${JAVA_HOME}/bin:${PATH}"

JAR="${SCRIPT_DIR}/target/casiopicture.jar"

if [ ! -x "${JAVA_HOME}/bin/java" ] || [ ! -x "${SCRIPT_DIR}/tools/maven/bin/mvn" ]; then
    echo "Toolchain not found in tools/. Run ./setup.sh first." >&2
    exit 1
fi

ensure_jar() {
    if [ ! -f "${JAR}" ]; then
        echo "Building..."
        mvn -q -DskipTests package
    fi
}

COMMAND="${1:-}"
[ $# -gt 0 ] && shift

case "${COMMAND}" in
    build)
        mvn clean package "$@"
        ;;
    run)
        ensure_jar
        java -jar "${JAR}"
        ;;
    cli)
        ensure_jar
        java -jar "${JAR}" "$@"
        ;;
    test)
        mvn test "$@"
        ;;
    refgen)
        if ! command -v node >/dev/null 2>&1; then
            echo "Node.js is required to regenerate the reference vectors." >&2
            exit 1
        fi
        if [ ! -f "${SCRIPT_DIR}/tmp/index.html" ]; then
            echo "tmp/index.html (the img2calc reference) is missing." >&2
            exit 1
        fi
        node "${SCRIPT_DIR}/tools/refgen/refgen.mjs"
        ;;
    package)
        # jpackage wraps the fat jar together with a trimmed runtime, producing a .dmg on macOS,
        # a .msi on Windows and a .deb on Linux.
        mvn -q -DskipTests package
        OUT="${SCRIPT_DIR}/target/installer"
        rm -rf "${OUT}"
        mkdir -p "${OUT}"

        case "$(uname -s)" in
            Darwin) TYPE=dmg ;;
            Linux)  TYPE=deb ;;
            *)      TYPE=msi ;;
        esac

        # A trimmed runtime instead of the whole JDK. The app is non-modular (JavaFX lives in the
        # fat jar on the classpath), so the modules it needs are listed by hand:
        #   java.desktop   AWT/Swing imaging, which the engine and JavaFX both use
        #   java.logging   used by JavaFX internally
        #   java.xml       FXML-adjacent plumbing pulled in by the toolkit
        #   java.prefs     JavaFX preference lookups on some platforms
        #   jdk.unsupported  sun.misc.Unsafe, still referenced by JavaFX
        RUNTIME="${SCRIPT_DIR}/target/runtime"
        rm -rf "${RUNTIME}"
        jlink \
            --add-modules java.base,java.desktop,java.logging,java.xml,java.prefs,jdk.unsupported \
            --strip-debug --no-header-files --no-man-pages --compress=zip-6 \
            --output "${RUNTIME}"

        # Only the fat jar should be packaged, not the rest of target/.
        STAGE="${SCRIPT_DIR}/target/package-input"
        rm -rf "${STAGE}"
        mkdir -p "${STAGE}"
        cp "${JAR}" "${STAGE}/"

        jpackage \
            --name CasioPicture \
            --app-version 1.0.0 \
            --description "Convert images to calculator picture and script formats" \
            --vendor casiopicture \
            --input "${STAGE}" \
            --main-jar "$(basename "${JAR}")" \
            --main-class com.github.casiopicture.Main \
            --runtime-image "${RUNTIME}" \
            --dest "${OUT}" \
            --type "${TYPE}" \
            "$@"
        echo "Installer written to ${OUT}"
        ls -lh "${OUT}"
        ;;
    *)
        cat <<'USAGE'
Usage: ./casiopicture.sh <command> [args]

  run              Open the application
  cli [args]       Run the command line (try: cli --help)
  build            Compile, test and package
  test [args]      Run the test suite
  package          Build a native installer for this platform
  refgen           Regenerate the golden reference vectors from tmp/index.html

Examples:
  ./casiopicture.sh cli convert photo.png -f cp.g3p --name PICT1
  ./casiopicture.sh cli formats --target cg
  ./casiopicture.sh cli inspect PICT1.g3p
USAGE
        ;;
esac
