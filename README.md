# Limes

Kotlin- und Jetpack-Compose-Prototyp: *Siedeln. Plündern. Ruhm.* — germanische Siedlung am Limes (Material 3).

**Application-ID:** `com.stoni.androidstone`  
**Anzeigename:** Limes  
**Min SDK:** 26 · **Compile SDK:** 35 · **Target SDK:** 34

## Spielidee (Prototyp)

- **5×5-Raster** mit fester **Thinghalle** in der Mitte
- Gebäude (Stufe 1–2): Langhaus, Holzfäller, Acker, Schmiede
- Ressourcen: Holz, Getreide, Eisen, Ruhm
- Bedürfnisse: Sättigung & Schutz (beeinflussen Arbeitskraft)
- Lokaler PvE-Raubzug gegen den **Römischen Wachturm**
- Rundenbasiert („Runde“-Button)

## Voraussetzungen

- [Android Studio](https://developer.android.com/studio) (aktuell, mit JDK 17)
- Android SDK Platform und Build-Tools
- Optional: Gerät oder Emulator mit API 26+

## Projekt öffnen

1. Android Studio starten
2. **File → Open…** und den Ordner `android_stone` wählen
3. Gradle-Sync abwarten

## Debug-APK bauen

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## App-Struktur

| Bereich | Inhalt |
|--------|--------|
| Startbildschirm | Titel „Limes“, Untertitel, Button „Spielen“ |
| Spielbildschirm | Ressourcenleiste, 5×5-Siedlung, Bau/Ausbau, Raubzug, Runde |
| Spiel-Logik | `com.stoni.androidstone.game` |
| Navigation | Compose Navigation Start ↔ Spiel |

Quellcode: `app/src/main/java/com/stoni/androidstone/`

## Hinweise

- Keine Google Play Services, kein NDK, kein Multiplayer/Shop/Auth
- Keine neuen Gradle-Abhängigkeiten über den Starter hinaus

## Repo

https://github.com/stoni-23/android_stone
