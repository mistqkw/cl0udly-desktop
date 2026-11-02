#!/usr/bin/env bash
# Ставит cl0udly на этот компьютер: ~/.local/share/cl0udly-app, команда `cl0udly`, ярлык в меню.
set -e
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-$HOME/toolchain/jdk}"
systemd-run --user --scope -q -p MemoryMax=3000M -p CPUQuota=150% nice -n 10 \
  ./gradlew createDistributable --no-daemon -Pkotlin.compiler.execution.strategy=in-process --max-workers=2
APP="$HOME/.local/share/cl0udly-app"
rm -rf "$APP"
cp -r build/compose/binaries/main/app/cl0udly "$APP"
mkdir -p "$HOME/.local/bin" "$HOME/.local/share/applications" "$HOME/.local/share/icons/hicolor/512x512/apps"
ln -sf "$APP/bin/cl0udly" "$HOME/.local/bin/cl0udly"
cp src/main/resources/icon.png "$HOME/.local/share/icons/hicolor/512x512/apps/cl0udly.png"
cp src/main/resources/icon.png "$APP/cl0udly.png"
# a menu that doesn't rescan hicolor still finds the icon by its full path
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
echo "Готово: команда cl0udly и ярлык в меню"
