#!/usr/bin/env sh
set -eu

REPO="superquinquin-sur-deule/odoo-cli"
INSTALL_DIR="${HOME}/.local/bin"
BINARY_NAME="odoo-cli"

LATEST_TAG=$(curl -fsSLI -o /dev/null -w '%{url_effective}' "https://github.com/${REPO}/releases/latest" | sed 's|.*/||')

if [ -z "${LATEST_TAG}" ] || [ "${LATEST_TAG}" = "releases" ]; then
  echo "Error: could not resolve latest release for ${REPO}" >&2
  exit 1
fi

VERSION="${LATEST_TAG#v}"
ASSET="odoo-cli-${VERSION}-linux-x86_64"
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
