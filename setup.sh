#!/bin/bash
#
# One-shot setup for a fresh clone.
#
# Downloads a JDK and Maven into tools/, then resolves every dependency into the project itself
# (.mvn/repo and libs/). Nothing is installed system-wide and the user's ~/.m2 is never touched, so
# the checkout is self-contained and safe to delete.
#
# Re-running is cheap: anything already present is left alone.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLS_DIR="${SCRIPT_DIR}/tools"
JDK_DIR="${TOOLS_DIR}/jdk"
MAVEN_DIR="${TOOLS_DIR}/maven"

# JDK 21: jpackage is mature there, and it is what JavaFX 21 is built against.
JDK_FEATURE=21
MAVEN_VERSION=3.9.9

echo "=== CasioPicture setup ==="

# --- 1. Identify the platform -------------------------------------------------------------------

OS="$(uname -s)"
ARCH="$(uname -m)"

case "${OS}" in
    Darwin) JDK_OS=mac;     JDK_HOME_SUFFIX="/Contents/Home" ;;
    Linux)  JDK_OS=linux;   JDK_HOME_SUFFIX="" ;;
    MINGW*|MSYS*|CYGWIN*) JDK_OS=windows; JDK_HOME_SUFFIX="" ;;
    *) echo "Unsupported operating system: ${OS}" >&2; exit 1 ;;
esac

case "${ARCH}" in
    arm64|aarch64)  JDK_ARCH=aarch64 ;;
    x86_64|amd64)   JDK_ARCH=x64 ;;
    *) echo "Unsupported architecture: ${ARCH}" >&2; exit 1 ;;
esac

echo "Platform: ${JDK_OS}/${JDK_ARCH}"

JAVA_HOME="${JDK_DIR}${JDK_HOME_SUFFIX}"
MVN="${MAVEN_DIR}/bin/mvn"

# --- 2. JDK -------------------------------------------------------------------------------------

if [ -x "${JAVA_HOME}/bin/java" ] && "${JAVA_HOME}/bin/java" -version 2>&1 | grep -q "\"${JDK_FEATURE}\."; then
    echo "JDK ${JDK_FEATURE} already present in tools/jdk."
else
    echo "Downloading JDK ${JDK_FEATURE} (${JDK_OS}/${JDK_ARCH})..."
    rm -rf "${JDK_DIR}"
    mkdir -p "${JDK_DIR}"
    URL="https://api.adoptium.net/v3/binary/latest/${JDK_FEATURE}/ga/${JDK_OS}/${JDK_ARCH}/jdk/hotspot/normal/eclipse"
    curl -fL --progress-bar -o "${TOOLS_DIR}/jdk.tar.gz" "${URL}"
    tar -xzf "${TOOLS_DIR}/jdk.tar.gz" -C "${JDK_DIR}" --strip-components=1
    rm -f "${TOOLS_DIR}/jdk.tar.gz"
    echo "JDK ${JDK_FEATURE} installed."
fi

# --- 3. Maven -----------------------------------------------------------------------------------

if [ -x "${MVN}" ]; then
    echo "Maven already present in tools/maven."
else
    echo "Downloading Apache Maven ${MAVEN_VERSION}..."
    rm -rf "${MAVEN_DIR}"
    mkdir -p "${MAVEN_DIR}"
    curl -fL --progress-bar -o "${TOOLS_DIR}/maven.tar.gz" \
        "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
    tar -xzf "${TOOLS_DIR}/maven.tar.gz" -C "${MAVEN_DIR}" --strip-components=1
    rm -f "${TOOLS_DIR}/maven.tar.gz"
    echo "Maven ${MAVEN_VERSION} installed."
fi

chmod +x "${JAVA_HOME}/bin/"* 2>/dev/null || true
chmod +x "${MAVEN_DIR}/bin/"* 2>/dev/null || true
chmod +x "${SCRIPT_DIR}/casiopicture.sh"

# --- 4. Dependencies ----------------------------------------------------------------------------
#
# .mvn/maven.config points the repository at .mvn/repo, so this populates the project rather than
# ~/.m2. `package` also runs maven-dependency-plugin, which mirrors the runtime jars into libs/.

echo "Resolving dependencies into .mvn/repo and libs/ ..."
export JAVA_HOME
cd "${SCRIPT_DIR}"
"${MVN}" -q -DskipTests package

echo ""
echo "=== Ready ==="
echo "  ./casiopicture.sh run              open the app"
echo "  ./casiopicture.sh cli --help       command line usage"
echo "  ./casiopicture.sh test             run the test suite"
echo "  ./casiopicture.sh package          build a native installer"
echo ""
echo "Everything lives in this directory: tools/ (JDK, Maven), .mvn/repo and libs/ (dependencies)."
