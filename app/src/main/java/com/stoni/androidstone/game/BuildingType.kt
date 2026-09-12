package com.stoni.androidstone.game

/**
 * Gebäudearten der Siedlung (Stufen 1–2).
 * Thinghalle ist das feste HQ und wird nicht gebaut.
 */
enum class BuildingType(
    val displayName: String,
    val shortLabel: String,
    val description: String,
    val buildable: Boolean
) {
    THINGHALLE(
        displayName = "Thinghalle",
        shortLabel = "TH",
        description = "Versammlungshaus und festes Zentrum der Siedlung.",
        buildable = false
    ),
    LANGHAUS(
        displayName = "Langhaus",
        shortLabel = "LH",
        description = "Wohnraum – erhöht die Bevölkerung.",
        buildable = true
    ),
    HOLZFAELLER(
        displayName = "Holzfäller",
        shortLabel = "HF",
        description = "Schlägt Holz für den Bau.",
        buildable = true
    ),
    ACKER(
        displayName = "Acker",
        shortLabel = "AK",
        description = "Anbau von Getreide. Bonus neben einem Langhaus.",
        buildable = true
    ),
    SCHMIEDE(
        displayName = "Schmiede",
        shortLabel = "SM",
        description = "Verarbeitet Eisen und stärkt den Schutz.",
        buildable = true
    );

    companion object {
        val buildableTypes: List<BuildingType> = entries.filter { it.buildable }
    }
}

data class BuildCost(
    val holz: Int = 0,
    val getreide: Int = 0,
    val eisen: Int = 0
)

fun BuildingType.costForLevel(level: Int): BuildCost {
    val base = when (this) {
        BuildingType.THINGHALLE -> BuildCost()
        BuildingType.LANGHAUS -> BuildCost(holz = 8, getreide = 4)
        BuildingType.HOLZFAELLER -> BuildCost(holz = 6)
        BuildingType.ACKER -> BuildCost(holz = 5, getreide = 2)
        BuildingType.SCHMIEDE -> BuildCost(holz = 8, eisen = 3)
    }
    if (level <= 1) return base
    // Stufe 2: etwas teurer
    return BuildCost(
        holz = base.holz * 2,
        getreide = base.getreide * 2,
        eisen = base.eisen * 2 + if (this == BuildingType.SCHMIEDE) 2 else 0
    )
}

/** Bevölkerung die beim Bau / Upgrade hinzukommt (Langhaus). */
fun BuildingType.populationGain(level: Int): Int = when (this) {
    BuildingType.LANGHAUS -> if (level == 1) 3 else 2
    BuildingType.THINGHALLE -> 0
    else -> 0
}

/** Basisproduktion pro Runde (vor Arbeitskraft-Faktor und Nachbarbonus). */
fun BuildingType.baseProduction(level: Int): ResourceDelta {
    val mult = if (level >= 2) 2 else 1
    return when (this) {
        BuildingType.THINGHALLE -> ResourceDelta(ruhm = 1 * mult)
        BuildingType.LANGHAUS -> ResourceDelta() // Bevölkerung kommt beim Bau
        BuildingType.HOLZFAELLER -> ResourceDelta(holz = 3 * mult)
        BuildingType.ACKER -> ResourceDelta(getreide = 3 * mult)
        BuildingType.SCHMIEDE -> ResourceDelta(eisen = 2 * mult)
    }
}

data class ResourceDelta(
    val holz: Int = 0,
    val getreide: Int = 0,
    val eisen: Int = 0,
    val ruhm: Int = 0
)
