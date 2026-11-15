#!/usr/bin/env bash
# Ставит cl0udly для ПК из последнего релиза на GitHub: ~/.local/share/cl0udly-app, команда `cl0udly`, ярлык в меню.
#   curl -fsSL https://raw.githubusercontent.com/mistqkw/cl0udly-desktop/main/get.sh | bash
set -e
REPO=mistqkw/cl0udly-desktop
URL=$(curl -fsSL "https://api.github.com/repos/$REPO/releases/latest" | grep -o '"browser_download_url": *"[^"]*linux-x64\.tar\.gz"' | head -1 | sed 's/.*"\(https[^"]*\)"/\1/')
[ -n "$URL" ] || { echo "Не нашёл сборку в релизах $REPO"; exit 1; }
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
echo "Скачиваю $(basename "$URL")…"
curl -fL# "$URL" -o "$TMP/cl0udly.tar.gz"
tar -xzf "$TMP/cl0udly.tar.gz" -C "$TMP"
APP="$HOME/.local/share/cl0udly-app"
rm -rf "$APP"
mv "$TMP/cl0udly" "$APP"
mkdir -p "$HOME/.local/bin" "$HOME/.local/share/applications" "$HOME/.local/share/icons/hicolor/512x512/apps"
ln -sf "$APP/bin/cl0udly" "$HOME/.local/bin/cl0udly"
cp "$APP/cl0udly.png" "$HOME/.local/share/icons/hicolor/512x512/apps/cl0udly.png"
gtk-update-icon-cache -q -t "$HOME/.local/share/icons/hicolor" 2>/dev/null || true
cat > "$HOME/.local/share/applications/cl0udly.desktop" <<D
[Desktop Entry]
Name=cl0udly
Comment=OpenCode with a little cloud
Exec=$APP/bin/cl0udly
Icon=$APP/cl0udly.png
Terminal=false
Type=Application
Categories=Development;
StartupWMClass=cl0udly
D
update-desktop-database -q "$HOME/.local/share/applications" 2>/dev/null || true
echo "Готово: команда cl0udly и ярлык в меню. Если cl0udly был открыт, закрой его и запусти снова."
