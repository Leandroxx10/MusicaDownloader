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
INTERFACE_NAME = "moura-interface.zip"
INTERFACE_MAX_SIZE = 8 * 1024 * 1024
INTERFACE_MAX_EXTRACTED_SIZE = 16 * 1024 * 1024


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
    if manifest.get("schemaVersion") != 2 or int(manifest.get("versionCode", 0)) <= 0:
        fail("O manifesto de atualização tem versão ou formato inválido.")
    native_revision = int(manifest.get("nativeRevision", 0))
    if native_revision <= 0:
        fail("A revisão nativa do manifesto é inválida.")

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

    interface_path = RELEASE_DIR / INTERFACE_NAME
    if not interface_path.is_file() or interface_path.stat().st_size == 0:
        fail(f"Pacote de interface ausente ou vazio: {INTERFACE_NAME}")
    if interface_path.stat().st_size > INTERFACE_MAX_SIZE:
        fail(
            f"{INTERFACE_NAME} ficou grande demais: "
            f"{interface_path.stat().st_size / 1024 / 1024:.1f} MB."
        )
    interface_info = manifest.get("interfaceBundle", {})
    if interface_info.get("sha256") != sha256(interface_path):
        fail(f"SHA-256 incorreto no update.json para {INTERFACE_NAME}.")
    if int(interface_info.get("size", -1)) != interface_path.stat().st_size:
        fail(f"Tamanho incorreto no update.json para {INTERFACE_NAME}.")
    if not str(interface_info.get("url", "")).endswith("/" + INTERFACE_NAME):
        fail(f"Link incorreto no update.json para {INTERFACE_NAME}.")
    if int(interface_info.get("contentVersion", 0)) != int(
        manifest.get("versionCode", 0)
    ):
        fail("A versão do pacote de interface não corresponde ao manifesto.")
    if int(interface_info.get("requiredNativeRevision", 0)) != native_revision:
        fail("A revisão nativa do pacote de interface não corresponde ao manifesto.")

    with zipfile.ZipFile(interface_path) as archive:
        names = archive.namelist()
        required = {"index.html", "app.js", "styles.css", "download.css"}
        if not required.issubset(names):
            fail(
                "O pacote de interface está incompleto: "
                f"{sorted(required - set(names))}."
            )
        if len(names) > 160:
            fail("O pacote de interface contém arquivos demais.")
        if sum(item.file_size for item in archive.infolist()) > INTERFACE_MAX_EXTRACTED_SIZE:
            fail("O pacote de interface extraído excede o limite seguro.")
        invalid = [
            name
            for name in names
            if name.startswith("/")
            or "\\" in name
            or ".." in Path(name).parts
            or name == "assets/logo-source.png"
        ]
        if invalid:
            fail(f"O pacote de interface contém caminhos inválidos: {invalid}.")

    print("Release validado:")
    for filename in APK_RULES:
        path = RELEASE_DIR / filename
        print(f"- {filename}: {path.stat().st_size / 1024 / 1024:.1f} MB")
    print(f"- {AAB_NAME}: {aab.stat().st_size / 1024 / 1024:.1f} MB")
    print(
        f"- {INTERFACE_NAME}: "
        f"{interface_path.stat().st_size / 1024 / 1024:.2f} MB"
    )


if __name__ == "__main__":
    main()
