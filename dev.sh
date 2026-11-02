#!/usr/bin/env bash
# Запуск для разработки на отдельных данных: настоящие настройки и сервер (~/.config/cl0udly,
# ~/.local/share/cl0udly) не трогаются. Сборка с потолком памяти: у компьютера нет свопа.
set -e
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-$HOME/toolchain/jdk}"
export XDG_DATA_HOME="${CL0UDLY_DEV_DATA:-/tmp/cl0udly-dev}/data"
export XDG_CONFIG_HOME="${CL0UDLY_DEV_DATA:-/tmp/cl0udly-dev}/config"
mkdir -p "$XDG_DATA_HOME" "$XDG_CONFIG_HOME"
exec systemd-run --user --scope -q -p MemoryMax=3000M -p CPUQuota=150% nice -n 10 \
  ./gradlew run --no-daemon -Pkotlin.compiler.execution.strategy=in-process --max-workers=2 "$@"
