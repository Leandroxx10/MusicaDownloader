from __future__ import annotations

import hashlib
import json
import sys
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RELEASE_DIR = ROOT / "release"
APK_RULES = {
    "moura-downloads-arm64.apk": ({"arm64-v8a"}, 90 * 1024 * 1024),
    "moura-downloads-32bit.apk": ({"armeabi-v7a"}, 90 * 1024 * 1024),
    "moura-downloads.apk": ({"arm64-v8a", "armeabi-v7a"}, 170 * 1024 * 1024),
}
AAB_NAME = "moura-downloads-play-store.aab"
AAB_MAX_SIZE = 170 * 1024 * 1024


def fail(message: str) -> None:
    print(f"ERRO: {message}", file=sys.stderr)
    raise SystemExit(1)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def apk_architectures(path: Path) -> set[str]:
    with zipfile.ZipFile(path) as archive:
        return {
            name.split("/", 2)[1]
            for name in archive.namelist()
            if name.startswith("lib/") and name.count("/") >= 2
        }


def aab_architectures(path: Path) -> set[str]:
    with zipfile.ZipFile(path) as archive:
        return {
            name.split("/", 3)[2]
            for name in archive.namelist()
            if name.startswith("base/lib/") and name.count("/") >= 3
        }


def main() -> None:
    for filename, (expected_architectures, max_size) in APK_RULES.items():
        path = RELEASE_DIR / filename
        if not path.is_file() or path.stat().st_size == 0:
            fail(f"APK ausente ou vazio: {filename}")
        architectures = apk_architectures(path)
        if architectures != expected_architectures:
            fail(
                f"{filename} contém arquiteturas {sorted(architectures)}, "
                f"mas deveria conter {sorted(expected_architectures)}."
            )
        if path.stat().st_size > max_size:
            fail(
                f"{filename} ficou grande demais: "
                f"{path.stat().st_size / 1024 / 1024:.1f} MB."
            )

    aab = RELEASE_DIR / AAB_NAME
    if not aab.is_file() or aab.stat().st_size == 0:
        fail(f"Pacote da Play Store ausente ou vazio: {AAB_NAME}")
    if aab.stat().st_size > AAB_MAX_SIZE:
        fail(
            f"{AAB_NAME} ficou grande demais: "
            f"{aab.stat().st_size / 1024 / 1024:.1f} MB."
        )
    architectures = aab_architectures(aab)
    expected = {"arm64-v8a", "armeabi-v7a"}
    if architectures != expected:
        fail(
            f"{AAB_NAME} contém arquiteturas {sorted(architectures)}, "
            f"mas deveria conter {sorted(expected)}."
        )

    manifest_path = RELEASE_DIR / "update.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("schemaVersion") != 1 or int(manifest.get("versionCode", 0)) <= 0:
        fail("O manifesto de atualização tem versão ou formato inválido.")

    manifest_files = {
        "arm64": "moura-downloads-arm64.apk",
        "armeabi": "moura-downloads-32bit.apk",
        "universal": "moura-downloads.apk",
    }
    for key, filename in manifest_files.items():
        path = RELEASE_DIR / filename
        info = manifest.get("apks", {}).get(key, {})
        if info.get("sha256") != sha256(path):
            fail(f"SHA-256 incorreto no update.json para {filename}.")
        if int(info.get("size", -1)) != path.stat().st_size:
            fail(f"Tamanho incorreto no update.json para {filename}.")
        if not str(info.get("url", "")).endswith("/" + filename):
            fail(f"Link incorreto no update.json para {filename}.")

    print("Release validado:")
    for filename in APK_RULES:
        path = RELEASE_DIR / filename
        print(f"- {filename}: {path.stat().st_size / 1024 / 1024:.1f} MB")
    print(f"- {AAB_NAME}: {aab.stat().st_size / 1024 / 1024:.1f} MB")


if __name__ == "__main__":
    main()
