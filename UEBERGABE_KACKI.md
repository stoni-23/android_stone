# Übergabe an Kacki (Grok-Bot) — nur verdrahten, nicht neu schreiben

Repo: `stoni-23/android_stone`  Branch: `main`  HEAD: `e75b906`
APK: Kotlin / Jetpack Compose. Datei: `app/src/main/java/com/stoni/androidstone/ui/screens/PlayScreen.kt`
Loader: `loadStargameAsset(context, "dateiname.png")` sucht `stargame/` dann Asset-Root.
**Kein Web-Spiel. Kein PlayScreen-Rewrite. Keine Steuerung anfassen.**

## Ist-Zustand (nicht kaputtmachen)

Große Karte `WORLD_W=8000` `WORLD_H=12000`, Kamera folgt der Glocke (`camX`/`camY`).
Touch: Finger halten = fliegen + schießen (`isTouching`). Loslassen = Stopp.
Typen jetzt: `SWARMER` (Raute, kein Sprite), `SCOUT` (`enemy_stoerer_64`/`_b`), `TANK`/`BOSS` (`enemy_stoerer_big_128`).
Sterne: `bg_stars_far/mid/near` via `drawMirroredTiled`. Modulo immer positiv: `((o % s)+s)%s`.

## Auftrag

1. Neue PNGs laden (`remember { loadStargameAsset(...) }`). Fallback: wenn `null` → bestehende Farbe/`drawOval`, nie unsichtbar.
2. `EnemyType` erweitern, Spawn + Draw + AI. Alte Typen bleiben.
3. Parallax: `bg_nebula` zwischen far/mid, `bg_debris` vor near. Nur kacheln, nicht rotieren.
4. `*_licht`-Frames: Blink wie SCOUT, `(tick/12+i)%2`.
5. Nach Push: GitHub Actions baut die APK (Release-Tag `latest`).

## Neue Assets (`app/src/main/assets/stargame/`)

| Datei | Rolle | Größe |
|---|---|---|
| `bg_nebula.png` | Nebel-Parallax | 360×640 |
| `bg_debris.png` | Geröll-Parallax | 360×640 |
| `enemy_komet_64.png` / `enemy_komet_licht_64.png` | Komet | 64 |
| `enemy_komet_128.png` / `enemy_komet_licht_128.png` | Komet groß | 128 |
| `enemy_komet_big_128.png` | Komet-Boss | 128 |
| `enemy_asteroid_64.png` / `enemy_asteroid_b_64.png` | Asteroid tumble | 64 |
| `enemy_asteroid_80.png` | Asteroid mid | 80 |
| `enemy_asteroid_big_128.png` | Fels-Tank | 128 |
| `enemy_jaeger_64.png` / `enemy_jaeger_licht_64.png` | Jäger-Schiff | 64 |
| `enemy_jaeger_80.png` | Jäger mid | 80 |
| `enemy_mine_64.png` / `enemy_mine_licht_64.png` | Treibmine | 64 |
| `enemy_mine_80.png` | Mine mid | 80 |

## Typen (so einbauen)

```
KOMET     — driftet, eigene Achse (angle += 1.2f), HP 2, kein Schuss, rammt
KOMET_BIG — langsam, angle += 0.6f, HP 18, Boss-Ersatz ab Welle 6, Sprite komet_big_128
ASTEROID  — trudelt (angle += 2.5f), HP 3, kein Schuss, Sprite 64/_b
FELS      — trudelt langsam, HP 12, Sprite asteroid_big_128, ersetzt Teil der TANKs
JAEGER    — fliegt auf Spieler, schießt 1 Schuss, Sprite jaeger_64/_licht
MINE      — treibt, explodiert bei Nähe < 70px, Sprite mine_64/_licht
```

Spawn-Mix (Welle `wave`):
- immer ~25% ASTEROID, ~15% KOMET
- ab Welle 2: JAEGER statt mancher SCOUT
- ab Welle 3: MINE + FELS statt mancher TANK
- Boss weiter `BOSS`; ab Welle 6 darf Boss `KOMET_BIG` sein (50/50)

Draw:
- Komet/Asteroid/Fels/Mine: `rotate(e.angle)` um Sprite-Mitte, **nicht** zur Glocke drehen.
- Jäger: wie SCOUT zur Glocke zielen (`atan2` + 90).
- SWARMER-Raute darf bleiben oder durch `enemy_komet_64` ersetzt werden.

## Verboten

- PlayScreen nicht von Null neu schreiben
- Kamera/Touch/`isTouching`-Feuer nicht „verbessern“
- Schüsse nicht um die Welt wrappen (kein Friendly-Fire von hinten)
- Dateinamen nicht umbenennen
- `loadStargameAsset("stargame/foo.png")` falsch — nur `"foo.png"`

## Fertig wenn

Kometen, Asteroiden, Jäger, Minen sichtbar im Flug. Nebel+Geröll im Hintergrund. Build grün. Glocke fliegt/schießt wie jetzt.
