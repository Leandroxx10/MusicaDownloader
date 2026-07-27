from __future__ import annotations

import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
WEB_DIR = ROOT / "app"
ANDROID_WEB_DIR = (
    ROOT / "native-android" / "app" / "src" / "main" / "assets" / "www"
)


def fail(message: str) -> None:
    print(f"ERRO: {message}", file=sys.stderr)
    raise SystemExit(1)


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def relative_files(directory: Path) -> dict[str, Path]:
    return {
        path.relative_to(directory).as_posix(): path
        for path in directory.rglob("*")
        if path.is_file()
    }


def validate_web_mirror() -> None:
    web = relative_files(WEB_DIR)
    android = relative_files(ANDROID_WEB_DIR)
    if web.keys() != android.keys():
        missing_android = sorted(web.keys() - android.keys())
        missing_web = sorted(android.keys() - web.keys())
        fail(
            "O site e os arquivos internos do APK têm estruturas diferentes. "
            f"Ausentes no APK: {missing_android}; ausentes no site: {missing_web}"
        )
    different = [name for name in web if digest(web[name]) != digest(android[name])]
    if different:
        fail(f"O site e o APK estão fora de sincronia: {different}")


def validate_structured_files() -> None:
    for path in [ROOT / "package.json", WEB_DIR / "manifest.webmanifest"]:
        json.loads(path.read_text(encoding="utf-8"))

    xml_files = [
        ROOT / "native-android" / "app" / "src" / "main" / "AndroidManifest.xml",
        *(
            ROOT / "native-android" / "app" / "src" / "main" / "res"
        ).rglob("*.xml"),
    ]
    for path in xml_files:
        ET.parse(path)


def validate_required_features() -> None:
    required = [
        ROOT / ".github" / "workflows" / "build-android.yml",
        ROOT / "netlify.toml",
        WEB_DIR / "sw.js",
        ROOT / "native-android" / "app" / "build.gradle",
        ROOT
        / "native-android"
        / "app"
        / "src"
        / "main"
        / "java"
        / "com"
        / "moura"
        / "downloads"
        / "DownloadService.java",
        ROOT
        / "native-android"
        / "app"
        / "src"
        / "main"
        / "java"
        / "com"
        / "moura"
        / "downloads"
        / "UpdateService.java",
    ]
    missing = [str(path.relative_to(ROOT)) for path in required if not path.is_file()]
    if missing:
        fail(f"Arquivos obrigatórios ausentes: {missing}")

    workflow = required[0].read_text(encoding="utf-8")
    gradle = required[3].read_text(encoding="utf-8")
    index = (WEB_DIR / "index.html").read_text(encoding="utf-8")
    app_js = (WEB_DIR / "app.js").read_text(encoding="utf-8")
    download_service = required[4].read_text(encoding="utf-8")
    update_service = required[5].read_text(encoding="utf-8")

    checks = {
        "build Android release": "assembleRelease" in workflow,
        "assinatura por segredo": "ANDROID_KEYSTORE_BASE64" in workflow,
        "validação do release": "validate-release.py" in workflow,
        "APK arm64": "'arm64-v8a'" in gradle,
        "APK 32 bits": "'armeabi-v7a'" in gradle,
        "aceleração aria2": "youtubedl-android:aria2c:" in gradle,
        "cancelamento de mídia": "cancelLocalDownload" in app_js
        and "ACTION_CANCEL" in download_service,
        "player interno": "playDownload" in app_js,
        "atualização com SHA-256": "sha256(temp)" in update_service,
        "QR rápido": "moura-downloads-arm64.apk" in app_js,
        "site pronto para celular": 'name="viewport"' in index,
    }
    failed = [name for name, valid in checks.items() if not valid]
    if failed:
        fail(f"Recursos obrigatórios não encontrados: {failed}")

    if re.search(r'type=["\']checkbox["\'][^>]*(owner|propriet|rights)', index, re.I):
        fail("A antiga confirmação de propriedade reapareceu na interface.")


def validate_no_sensitive_files() -> None:
    forbidden_suffixes = {".jks", ".keystore", ".p12", ".pfx"}
    forbidden_names = {".env", "google-services.json"}
    found: list[str] = []
    for path in ROOT.rglob("*"):
        if ".git" in path.parts or not path.is_file():
            continue
        if path.suffix.lower() in forbidden_suffixes or path.name.lower() in forbidden_names:
            found.append(path.relative_to(ROOT).as_posix())
    if found:
        fail(f"Arquivos sensíveis não podem entrar no repositório: {found}")


def main() -> None:
    validate_web_mirror()
    validate_structured_files()
    validate_required_features()
    validate_no_sensitive_files()
    print("Projeto validado: site, APK, atualização, segurança e espelhos estão corretos.")


if __name__ == "__main__":
    main()
