#!/usr/bin/env sh
set -eu

REPO="superquinquin-sur-deule/odoo-cli"
INSTALL_DIR="${HOME}/.local/bin"
BINARY_NAME="odoo-cli"

OS_NAME=$(uname -s)
ARCH_NAME=$(uname -m)

case "${OS_NAME}" in
  Linux)
    case "${ARCH_NAME}" in
      x86_64|amd64) PLATFORM="linux-x86_64" ;;
      *) echo "Error: unsupported Linux architecture: ${ARCH_NAME}" >&2; exit 1 ;;
    esac
    ;;
  Darwin)
    case "${ARCH_NAME}" in
      x86_64) PLATFORM="macos-x86_64" ;;
      arm64|aarch64) PLATFORM="macos-aarch64" ;;
      *) echo "Error: unsupported macOS architecture: ${ARCH_NAME}" >&2; exit 1 ;;
    esac
    ;;
  *)
    echo "Error: unsupported OS: ${OS_NAME}" >&2
    exit 1
    ;;
esac

LATEST_TAG=$(curl -fsSLI -o /dev/null -w '%{url_effective}' "https://github.com/${REPO}/releases/latest" | sed 's|.*/||')

if [ -z "${LATEST_TAG}" ] || [ "${LATEST_TAG}" = "releases" ]; then
  echo "Error: could not resolve latest release for ${REPO}" >&2
  exit 1
fi

VERSION="${LATEST_TAG#v}"
ASSET="odoo-cli-${VERSION}-${PLATFORM}"
URL="https://github.com/${REPO}/releases/download/${LATEST_TAG}/${ASSET}"

mkdir -p "${INSTALL_DIR}"
echo "Downloading ${ASSET}..."
curl -fSL --progress-bar -o "${INSTALL_DIR}/${BINARY_NAME}" "${URL}"
chmod +x "${INSTALL_DIR}/${BINARY_NAME}"

echo "Installed ${BINARY_NAME} ${LATEST_TAG} to ${INSTALL_DIR}/${BINARY_NAME}"

case ":${PATH}:" in
  *":${INSTALL_DIR}:"*) ;;
  *)
    echo ""
    echo "Note: ${INSTALL_DIR} is not in your PATH."
    echo "Add it to your shell profile, e.g.:"
    echo "  export PATH=\"${INSTALL_DIR}:\$PATH\""
    ;;
esac
