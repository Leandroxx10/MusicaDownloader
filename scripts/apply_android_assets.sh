#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RES="$ROOT/native-android/app/src/main/res"
WWW="$ROOT/native-android/app/src/main/assets/www"

copy_icon() {
  local size="$1" dir="$2"
  mkdir -p "$RES/$dir"
  cp "$ROOT/app/icons/icon-$size.png" "$RES/$dir/ic_launcher.png"
  cp "$ROOT/app/icons/icon-$size.png" "$RES/$dir/ic_launcher_round.png"
}

copy_icon 48 mipmap-mdpi
copy_icon 72 mipmap-hdpi
copy_icon 96 mipmap-xhdpi
copy_icon 144 mipmap-xxhdpi
copy_icon 192 mipmap-xxxhdpi

rm -rf "$WWW"
mkdir -p "$WWW"
cp -R "$ROOT/app/." "$WWW/"

echo "Ícones e interface Android atualizados."
