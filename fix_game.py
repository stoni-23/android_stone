import os
import re

target_file = "app/src/main/java/com/stoni/androidstone/ui/screens/PlayScreen.kt"

if not os.path.exists(target_file):
    print("FEHLER: Datei PlayScreen.kt nicht gefunden!")
    exit(1)

with open(target_file, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Kugel-Loeschung reparieren (Distanz zum Schiff statt starrer Bildschirmrand)
content = re.sub(
    r"bullets\.removeAll\s*\{[^}]*it\.x\s*<\s*-60[^}]*\}",
    "bullets.removeAll { it.life <= 0 || hypot(it.x - shipPx, it.y - shipPy) > 2200f }",
    content
)

# 2. Gegner-Spawn an Position anpassen
old_spawn = r"val edge = Random\.nextInt\(4\)[\s\S]*?else -> \{ ex = -70f; ey = Random\.nextFloat\(\) \* sh \}\s*\}"
new_spawn = """val spawnAngle = Random.nextFloat() * 2f * PI
                val spawnDist = max(sw, sh) * 0.75f + 120f
                val ex = shipPx + (cos(spawnAngle) * spawnDist).toFloat()
                val ey = shipPy + (sin(spawnAngle) * spawnDist).toFloat()"""

if re.search(old_spawn, content):
    content = re.sub(old_spawn, new_spawn, content)
    print("-> Gegner-Spawn erfolgreich angepasst.")
else:
    print("-> Hinweis: Gegner-Spawn weicht ab oder war schon angepasst.")

with open(target_file, "w", encoding="utf-8") as f:
    f.write(content)

print("ERFOLG: Datei aktualisiert!")
