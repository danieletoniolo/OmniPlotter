#!/bin/bash
#
# Runner for the local toolchain. Everything it needs lives inside this directory; run ./setup.sh
# once after cloning to populate it.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Prefer the toolchain setup.sh vendored into tools/; fall back to whatever is on PATH. The fallback
# is what lets CI run these exact commands after actions/setup-java, instead of maintaining a second
# way to build that can drift from this one.
case "$(uname -s)" in
    Darwin) LOCAL_JDK="${SCRIPT_DIR}/tools/jdk/Contents/Home" ;;
    *)      LOCAL_JDK="${SCRIPT_DIR}/tools/jdk" ;;
esac

if [ -x "${LOCAL_JDK}/bin/java" ]; then
    export JAVA_HOME="${LOCAL_JDK}"
    export PATH="${JAVA_HOME}/bin:${PATH}"
elif ! command -v java >/dev/null 2>&1; then
    echo "No JDK found: run ./setup.sh, or install one and put java on PATH." >&2
    exit 1
fi

if [ -x "${SCRIPT_DIR}/tools/maven/bin/mvn" ]; then
    export PATH="${SCRIPT_DIR}/tools/maven/bin:${PATH}"
elif ! command -v mvn >/dev/null 2>&1; then
    echo "No Maven found: run ./setup.sh, or install one and put mvn on PATH." >&2
    exit 1
fi

JAR="${SCRIPT_DIR}/target/omniplotter.jar"

# One source of truth for the version: the POM. jpackage is told the same thing.
project_version() {
    mvn -q -Dexec.executable=echo -Dexec.args='${project.version}' \
        --non-recursive org.codehaus.mojo:exec-maven-plugin:3.1.0:exec 2>/dev/null | tail -1
}

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
        # Main reads "no arguments" as "open the window", and the `cli` word itself is consumed
        # above, so an argument-less `cli` has to be turned into a usage request explicitly.
        if [ $# -eq 0 ]; then
            java -jar "${JAR}" --help
        else
            java -jar "${JAR}" "$@"
        fi
        ;;
    test)
        mvn test "$@"
        ;;
    refgen)
        if ! command -v node >/dev/null 2>&1; then
            echo "Node.js is required to regenerate the reference vectors." >&2
            exit 1
        fi
        if [ ! -f "${SCRIPT_DIR}/reference/img2calc/index.html" ]; then
            echo "The img2calc reference submodule is not checked out." >&2
            echo "Run: git submodule update --init" >&2
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

        # Per-platform extras:
        #
        #   macOS   a stable bundle identifier, so every version is recognised as the same
        #           application rather than as a new one that happens to share a name.
        #
        #   Windows a fixed upgrade UUID, which is what makes the next .msi replace this one
        #           instead of installing beside it. It has to stay identical for the life of the
        #           product and cannot be corrected after the first release, since it is what
        #           already-installed copies were recorded under. Plus a Start menu entry and a
        #           second, console launcher; see packaging/omniplotter-cli.properties.
        #           macOS and Linux do not get one: their
        #           launcher already prints to a terminal. Note the `-cli` in its name: both NTFS
        #           and the macOS filesystem are case-insensitive, so a launcher called
        #           `omniplotter` is the same file as `OmniPlotter` and jpackage refuses to write
        #           the second one.
        #
        #   Linux   a desktop entry, so the .deb leaves something in the application menu rather
        #           than only a directory under /opt.
        EXTRA=()
        case "$(uname -s)" in
            Darwin) TYPE=dmg; ICON="${SCRIPT_DIR}/src/main/resources/icon/icon.icns"
                    EXTRA=(--mac-package-identifier com.github.omniplotter) ;;
            Linux)  TYPE=deb; ICON="${SCRIPT_DIR}/src/main/resources/icon/icon.png"
                    EXTRA=(--linux-shortcut) ;;
            *)      TYPE=msi; ICON="${SCRIPT_DIR}/src/main/resources/icon/icon.ico"
                    EXTRA=(--win-upgrade-uuid 3B4080D2-5F04-48BC-A0D9-E263FC4948C6
                           --win-menu
                           --add-launcher "omniplotter-cli=${SCRIPT_DIR}/packaging/omniplotter-cli.properties") ;;
        esac

        # A trimmed runtime instead of the whole JDK. The app is non-modular (JavaFX lives in the
        # fat jar on the classpath), so the modules it needs are listed by hand:
        #   java.desktop   AWT/Swing imaging, which the engine and JavaFX both use
        #   java.logging   used by JavaFX internally
        #   java.net.http  the update check
        #   jdk.crypto.ec  the elliptic-curve cipher suites GitHub's TLS negotiates. Without it
        #                  the update check fails with a handshake_failure, and only in the
        #                  packaged application: a full JDK has the provider all along.
        #   java.xml       FXML-adjacent plumbing pulled in by the toolkit
        #   java.prefs     JavaFX preference lookups on some platforms
        #   jdk.unsupported  sun.misc.Unsafe, still referenced by JavaFX
        RUNTIME="${SCRIPT_DIR}/target/runtime"
        rm -rf "${RUNTIME}"
        jlink \
            --add-modules java.base,java.desktop,java.logging,java.net.http,java.xml,java.prefs,jdk.crypto.ec,jdk.unsupported \
            --strip-debug --no-header-files --no-man-pages --compress=zip-6 \
            --output "${RUNTIME}"

        # Only the fat jar should be packaged, not the rest of target/.
        STAGE="${SCRIPT_DIR}/target/package-input"
        rm -rf "${STAGE}"
        mkdir -p "${STAGE}"
        cp "${JAR}" "${STAGE}/"

        # jpackage takes less than a tag can say. It wants one to three integers, so a pre-release
        # suffix has to come off before it sees the version — the full string stays in the jar
        # manifest, which is what --version and the update check read. macOS additionally rejects a
        # leading zero, so a 0.x version cannot be packaged at all; saying so here beats letting the
        # bundler skip itself with a message about invalid components.
        VERSION="${OMNIPLOTTER_VERSION:-$(project_version)}"
        APP_VERSION="${VERSION%%-*}"
        case "${APP_VERSION}" in
            0|0.*)
                echo "Cannot package ${VERSION}: macOS requires the first number of an app" >&2
                echo "version to be 1 or greater. Use a 1.0.0 or later tag." >&2
                exit 1
                ;;
        esac

        if [ "${APP_VERSION}" != "${VERSION}" ]; then
            echo "Packaging version ${VERSION} (installers are stamped ${APP_VERSION})"
        else
            echo "Packaging version ${VERSION}"
        fi

        jpackage \
            --name OmniPlotter \
            --app-version "${APP_VERSION}" \
            --description "Convert images to calculator picture and script formats" \
            --vendor omniplotter \
            --input "${STAGE}" \
            --main-jar "$(basename "${JAR}")" \
            --main-class com.github.omniplotter.Main \
            --icon "${ICON}" \
            --runtime-image "${RUNTIME}" \
            --dest "${OUT}" \
            --type "${TYPE}" \
            ${EXTRA[@]+"${EXTRA[@]}"} \
            "$@"
        echo "Installer written to ${OUT}"
        ls -lh "${OUT}"
        ;;
    *)
        cat <<'USAGE'
Usage: ./omniplotter.sh <command> [args]

  run              Open the application
  cli [args]       Run the command line (try: cli --help)
  build            Compile, test and package
  test [args]      Run the test suite
  package          Build a native installer for this platform
  refgen           Regenerate the golden reference vectors from tmp/index.html

Examples:
  ./omniplotter.sh cli convert photo.png -f cp.g3p --name PICT1
  ./omniplotter.sh cli formats --target cg
  ./omniplotter.sh cli inspect PICT1.g3p
USAGE
        ;;
esac
