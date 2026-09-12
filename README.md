# Android Stone

Kotlin- und Jetpack-Compose-Starterprojekt für ein Android-Spiel (Material 3).

**Application-ID:** `com.stoni.androidstone`  
**Anzeigename:** Android Stone  
**Min SDK:** 26 · **Compile SDK:** 37 · **Target SDK:** 34

> Endgültiger Spieltitel noch offen – Platzhalter: **Android Stone**.

## Voraussetzungen

- [Android Studio](https://developer.android.com/studio) (aktuell, mit JDK 17)
- Android SDK Platform 37 und Build-Tools
- Optional: Gerät oder Emulator mit API 26+

## Projekt öffnen

1. Android Studio starten
2. **File → Open…** und den Ordner `android_stone` wählen
3. Gradle-Sync abwarten (Wrapper lädt Gradle bei Bedarf herunter)
4. Falls der Gradle-Wrapper fehlt oder Sync scheitert: **File → Settings → Build Tools → Gradle** prüfen bzw. Sync erneut auslösen

## Debug-APK bauen

Im Projektroot:

```bash
./gradlew assembleDebug
```

Unter Windows:

```bat
gradlew.bat assembleDebug
```

Die fertige APK liegt hier:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Installation

**Über Android Studio:** Gerät/Emulator wählen → grüner Run-Button (▶).

**Über ADB:**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

USB-Debugging am Gerät aktivieren bzw. Emulator starten.

## App-Struktur (Kurzüberblick)

| Bereich | Inhalt |
|--------|--------|
| Startbildschirm | Titel „Android Stone“, Untertitel „Spiel-Starter“, Button „Spielen“ |
| Spielbildschirm | Tip-Zähler / Tip-Ziel als spielbereite Platzhalter-Mechanik |
| Navigation | Compose Navigation zwischen Start und Spiel |

Quellcode: `app/src/main/java/com/stoni/androidstone/`

## Hinweise

- Keine Google Play Services, kein NDK
- AGP 9.4.0, Compose BOM `2026.08.00`, Kotlin 2.2.10
- Wenn `gradlew` / `gradle-wrapper.jar` fehlt: Projekt in Android Studio öffnen (legt Wrapper oft an) oder lokal `gradle wrapper` ausführen

## Lizenz / Repo

Ziel-Repository: https://github.com/stoni-23/android_stone  
Dieses Starterpaket ist lokal erzeugt und noch nicht gepusht.
