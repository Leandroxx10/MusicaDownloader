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
        ROOT / "native-android" / "app" / "src" / "play" / "AndroidManifest.xml",
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
        ROOT
        / "native-android"
        / "app"
        / "src"
        / "main"
        / "java"
        / "com"
        / "moura"
        / "downloads"
        / "PlaybackService.java",
        ROOT
        / "native-android"
        / "app"
        / "src"
        / "play"
        / "AndroidManifest.xml",
        ROOT / "scripts" / "validate-play-manifest.py",
        ROOT
        / "native-android"
        / "app"
        / "src"
        / "main"
        / "java"
        / "com"
        / "moura"
        / "downloads"
        / "EnergyAudioProcessor.java",
        ROOT
        / "native-android"
        / "app"
        / "src"
        / "main"
        / "java"
        / "com"
        / "moura"
        / "downloads"
        / "EnergyVisualizerView.java",
        ROOT
        / "native-android"
        / "app"
        / "src"
        / "main"
        / "java"
        / "com"
        / "moura"
        / "downloads"
        / "UiUpdateManager.java",
        WEB_DIR / "cloud.js",
        ROOT / "firebase" / "database.rules.json",
        ROOT / "FIREBASE-SETUP.md",
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
    playback_service = required[6].read_text(encoding="utf-8")
    play_manifest = required[7].read_text(encoding="utf-8")
    main_activity = (
        ROOT
        / "native-android"
        / "app"
        / "src"
        / "main"
        / "java"
        / "com"
        / "moura"
        / "downloads"
        / "MainActivity.java"
    ).read_text(encoding="utf-8")
    player_activity = (
        ROOT
        / "native-android"
        / "app"
        / "src"
        / "main"
        / "java"
        / "com"
        / "moura"
        / "downloads"
        / "PlayerActivity.java"
    ).read_text(encoding="utf-8")
    manifest = (
        ROOT / "native-android" / "app" / "src" / "main" / "AndroidManifest.xml"
    ).read_text(encoding="utf-8")
    energy_processor = required[9].read_text(encoding="utf-8")
    energy_view = required[10].read_text(encoding="utf-8")
    ui_update_manager = required[11].read_text(encoding="utf-8")
    update_generator = (
        ROOT / "scripts" / "generate-update-manifest.py"
    ).read_text(encoding="utf-8")

    checks = {
        "build Android release": "assembleSideloadRelease" in workflow,
        "assinatura por segredo": "ANDROID_KEYSTORE_BASE64" in workflow,
        "validação do release": "validate-release.py" in workflow,
        "pacote para Google Play": "bundlePlayRelease" in workflow,
        "variante oficial sem instalador de APK": "PLAY_STORE_BUILD" in gradle
        and 'tools:node="remove"' in play_manifest
        and "validate-play-manifest.py" in workflow,
        "APK arm64": "'arm64-v8a'" in gradle,
        "APK 32 bits": "'armeabi-v7a'" in gradle,
        "atualização do processador no primeiro uso": "updateEngineWhenNeeded(false)"
        in download_service,
        "nova tentativa automática": 'sendEvent("retrying"' in download_service,
        "rota pública alternativa": "android_vr,tv_simply,web_embedded"
        in download_service
        and "verificação anti-robô" in download_service,
        "progresso por etapas": "normalizedDownloadProgress" in download_service
        and 'sendEvent("finalizing"' in download_service,
        "cancelamento de mídia": "cancelLocalDownload" in app_js
        and "ACTION_CANCEL" in download_service,
        "player interno": "playDownload" in app_js,
        "player em segundo plano": "media3-session:" in gradle
        and "MediaSessionService" in playback_service
        and "FOREGROUND_SERVICE_MEDIA_PLAYBACK" in manifest,
        "fila e mixes inteligentes": "playSmartMix" in app_js
        and "rediscover" in app_js
        and 'id="smartLibrary"' in index,
        "timer do player": "ACTION_SET_SLEEP_TIMER" in playback_service,
        "visualizador ligado ao áudio real": "EnergyAudioProcessor"
        in playback_service
        and "AudioEnergyBus.publish" in energy_processor
        and "FFT_SIZE" in energy_processor
        and "EnergyVisualizerView" in player_activity
        and "android.permission.RECORD_AUDIO" not in manifest,
        "player de energia completo": "ENERGIA AO VIVO" in energy_view
        and "seekRelative(-10_000L)" in player_activity
        and "seekRelative(10_000L)" in player_activity
        and "bookmarkKey" in player_activity
        and "visualThemeNames" in player_activity,
        "atualização com SHA-256": "sha256(temp)" in update_service,
        "atualização rápida de interface": "NATIVE_REVISION" in gradle
        and "interfaceBundle" in update_generator
        and "release/*.zip" in workflow
        and "MAX_EXTRACTED_BYTES" in ui_update_manager
        and "startInterfaceUpdate" in main_activity
        and "requiredNativeRevision == BuildConfig.NATIVE_REVISION"
        in main_activity
        and "UiUpdateManager.currentDirectory" in main_activity,
        "central oficial do YouTube": 'id="view-youtube"' in index
        and "youtube-nocookie.com" in app_js
        and "youtube.com/iframe_api" in app_js
        and "youtubeErrorMessage" in app_js
        and "Reprodução oficial e segura" in index
        and "data-view=\"youtube\"" in index,
        "Spotify oficial sem extração protegida": 'id="spotifyPlayer"' in index
        and "open.spotify.com/embed/" in app_js
        and "isSpotifyUrl" in download_service,
        "conta e suporte no Realtime Database": 'id="view-conta"' in index
        and "firebase-database.js" in required[12].read_text(encoding="utf-8")
        and "firebase-firestore" not in required[12].read_text(encoding="utf-8")
        and "firebase-storage" not in required[12].read_text(encoding="utf-8")
        and '"admins"' in required[13].read_text(encoding="utf-8")
        and '"feedback"' in required[13].read_text(encoding="utf-8")
        and '"messages"' in required[13].read_text(encoding="utf-8"),
        "download completo no site": "moura-downloads.apk" in index
        and "moura-downloads-arm64.apk" not in index,
        "somente QR Code no app": 'id="appQrCode"' in index
        and 'id="appShareUrl"' not in index
        and 'id="shareAppBtn"' not in index
        and 'id="copyAppLinkBtn"' not in index,
        "área segura dos botões Android": "WindowInsetsCompat.Type.systemBars()"
        in main_activity
        and "WindowInsetsCompat.Type.systemBars()" in player_activity,
        "Netlify limitado ao instalador": "netlify-mode" in app_js
        and "body.netlify-mode .download-card" in (
            WEB_DIR / "download.css"
        ).read_text(encoding="utf-8"),
        "desenvolvedor identificado": "Leandro Moura" in index,
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
