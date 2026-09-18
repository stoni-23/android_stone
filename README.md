# Stargame

Portrait Retro-Pixel-Shooter (Reichszeitglocke V-3). Kotlin / Jetpack Compose.

## Spielen

Intro antippen → Glocke **um die eigene Achse drehen** wie in klassischen Space-Spielen.

- **Links** virtueller Stick: horizontal drehen, vertikal Schub / Rückschub
- **Rechts halten:** feuern (kein Dauerfeuer ohne Finger)
- Gegner: `BASIC`, `LANG`, `RUND`, `BIG` — immer sichtbar (Sprite oder Fallback-Form)
- Drei Wellen, dann Sieg. Game Over: **Erneut spielen** oder **Hauptmenü**

Build: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/`

GitHub Actions baut die Debug-APK bei Push auf `main`.
