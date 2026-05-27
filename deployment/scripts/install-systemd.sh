#!/usr/bin/env bash
set -euo pipefail

APP_USER="textreaderrpi"
APP_GROUP="textreaderrpi"
INSTALL_DIR="/opt/textreaderrpi"
SERVICE_FILE="deployment/systemd/textreaderrpi.service"
JAR_SOURCE="build/libs/TextReaderRpi-all.jar"

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run as root (sudo)." >&2
  exit 1
fi

if [[ ! -f "${JAR_SOURCE}" ]]; then
  echo "Missing ${JAR_SOURCE}. Build first: ./gradlew shadowJar" >&2
  exit 1
fi

if ! id "${APP_USER}" >/dev/null 2>&1; then
  adduser --system --no-create-home --group "${APP_USER}"
fi

mkdir -p "${INSTALL_DIR}/logs"
cp "${JAR_SOURCE}" "${INSTALL_DIR}/TextReaderRpi-all.jar"
cp "${SERVICE_FILE}" /etc/systemd/system/textreaderrpi.service
chown -R "${APP_USER}:${APP_GROUP}" "${INSTALL_DIR}"

systemctl daemon-reload
systemctl enable textreaderrpi
systemctl restart textreaderrpi

echo "textreaderrpi service installed/restarted."
echo "Check status: systemctl status textreaderrpi"

