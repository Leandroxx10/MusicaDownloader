from __future__ import annotations

import hashlib
import json
import os
import re
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RELEASE_DIR = ROOT / "release"
GRADLE_FILE = ROOT / "native-android" / "app" / "build.gradle"
BASE_URL = (
    "https://github.com/Leandroxx10/MusicaDownloader/"
    "releases/download/latest"
)


def read_version() -> tuple[int, str]:
    text = GRADLE_FILE.read_text(encoding="utf-8")
    code_match = re.search(r"\bversionCode\s+(\d+)", text)
    name_match = re.search(r"\bversionName\s+['\"]([^'\"]+)['\"]", text)
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


def main() -> None:
    version_code, version_name = read_version()
    manifest = {
        "schemaVersion": 1,
        "versionCode": version_code,
        "versionName": version_name,
        "mandatory": False,
        "publishedAt": datetime.now(timezone.utc).isoformat(),
        "commit": os.environ.get("GITHUB_SHA", ""),
        "notes": (
            "Atualizações dentro do app, instalação mais simples, "
            "preferências lembradas e melhorias de velocidade e confiabilidade."
        ),
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
