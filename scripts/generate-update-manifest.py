from __future__ import annotations

import hashlib
import json
import os
import re
import zipfile
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RELEASE_DIR = ROOT / "release"
GRADLE_FILE = ROOT / "native-android" / "app" / "build.gradle"
WEB_DIR = ROOT / "app"
BASE_URL = (
    "https://github.com/Leandroxx10/MusicaDownloader/"
    "releases/download/latest"
)


def read_version() -> tuple[int, str]:
    ci_code = os.environ.get("MOURA_VERSION_CODE")
    ci_name = os.environ.get("MOURA_VERSION_NAME")
    if ci_code and ci_name:
        return int(ci_code), ci_name
    text = GRADLE_FILE.read_text(encoding="utf-8")
    code_match = re.search(r":\s*(\d+)\s*$", next(
        line for line in text.splitlines() if "versionCode ciVersionCode" in line
    ))
    name_match = re.search(r":\s*['\"]([^'\"]+)['\"]", next(
        line for line in text.splitlines() if "versionName ciVersionName" in line
    ))
    if not code_match or not name_match:
        raise RuntimeError("Não foi possível encontrar a versão no build.gradle.")
    return int(code_match.group(1)), name_match.group(1)


def apk_info(filename: str, architecture: str) -> dict[str, object]:
    path = RELEASE_DIR / filename
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    return {
        "architecture": architecture,
        "url": f"{BASE_URL}/{filename}",
        "sha256": digest,
        "size": path.stat().st_size,
    }


def read_native_revision() -> int:
    configured = os.environ.get("MOURA_NATIVE_REVISION")
    if configured:
        return int(configured)
    text = GRADLE_FILE.read_text(encoding="utf-8")
    match = re.search(
        r"nativeRevision\s*=\s*System\.getenv\([^)]*\)\s*\?:\s*['\"](\d+)['\"]",
        text,
    )
    if not match:
        raise RuntimeError("Não foi possível encontrar a revisão nativa.")
    return int(match.group(1))


def create_interface_bundle() -> Path:
    destination = RELEASE_DIR / "moura-interface.zip"
    excluded = {"assets/logo-source.png"}
    with zipfile.ZipFile(
        destination, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9
    ) as archive:
        for path in sorted(WEB_DIR.rglob("*")):
            relative = path.relative_to(WEB_DIR).as_posix()
            if path.is_file() and relative not in excluded:
                archive.write(path, relative)
    return destination


def main() -> None:
    RELEASE_DIR.mkdir(parents=True, exist_ok=True)
    version_code, version_name = read_version()
    native_revision = read_native_revision()
    interface_bundle = create_interface_bundle()
    manifest = {
        "schemaVersion": 2,
        "versionCode": version_code,
        "versionName": version_name,
        "nativeRevision": native_revision,
        "mandatory": False,
        "publishedAt": datetime.now(timezone.utc).isoformat(),
        "commit": os.environ.get("GITHUB_SHA", ""),
        "notes": (
            "Atualizações rápidas, Central YouTube, listas locais, interface "
            "mais clara e melhorias de velocidade, privacidade e acessibilidade."
        ),
        "interfaceBundle": {
            "contentVersion": version_code,
            "requiredNativeRevision": native_revision,
            "url": f"{BASE_URL}/{interface_bundle.name}",
            "sha256": hashlib.sha256(interface_bundle.read_bytes()).hexdigest(),
            "size": interface_bundle.stat().st_size,
        },
        "apks": {
            "arm64": apk_info("moura-downloads-arm64.apk", "arm64-v8a"),
            "armeabi": apk_info("moura-downloads-32bit.apk", "armeabi-v7a"),
            "universal": apk_info("moura-downloads.apk", "universal"),
        },
    }
    destination = RELEASE_DIR / "update.json"
    destination.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Manifesto de atualização criado: {destination}")


if __name__ == "__main__":
    main()
