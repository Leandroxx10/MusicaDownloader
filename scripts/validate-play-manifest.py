from __future__ import annotations

import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
INTERMEDIATES = ROOT / "native-android" / "app" / "build" / "intermediates"
INSTALL_PERMISSION = "android.permission.REQUEST_INSTALL_PACKAGES"


def fail(message: str) -> None:
    print(f"ERRO: {message}", file=sys.stderr)
    raise SystemExit(1)


def manifests_for(variant: str) -> list[Path]:
    normalized = variant.lower()
    matches: list[Path] = []
    for path in INTERMEDIATES.rglob("AndroidManifest.xml"):
        if normalized not in path.as_posix().lower():
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        if "<manifest" in text:
            matches.append(path)
    return matches


def main() -> None:
    play = manifests_for("playRelease")
    sideload = manifests_for("sideloadRelease")
    if not play:
        fail("Manifesto mesclado da variante Google Play não foi encontrado.")
    if not sideload:
        fail("Manifesto mesclado da variante direta não foi encontrado.")
    if any(INSTALL_PERMISSION in path.read_text(encoding="utf-8") for path in play):
        fail("A variante Google Play ainda solicita instalação de APKs.")
    if not any(INSTALL_PERMISSION in path.read_text(encoding="utf-8") for path in sideload):
        fail("A variante direta perdeu a permissão necessária para atualizar o APK.")
    print("Manifestos validados:")
    print("- Google Play: atualizações gerenciadas pela loja")
    print("- Distribuição direta: atualizador assinado preservado")


if __name__ == "__main__":
    main()
