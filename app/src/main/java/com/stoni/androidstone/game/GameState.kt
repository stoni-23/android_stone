package com.stoni.androidstone.game

import kotlin.random.Random

const val GRID_SIZE = 5
const val CENTER_INDEX = 12 // Reihe 2, Spalte 2
const val MAX_BUILDING_LEVEL = 2
const val RAID_COOLDOWN_ROUNDS = 3
const val RAID_EISEN_COST = 2
const val RAID_MIN_WARRIORS = 2

data class CellBuilding(
    val type: BuildingType,
    val level: Int
)

data class GridCell(
    val building: CellBuilding? = null
) {
    val isEmpty: Boolean get() = building == null
}

data class Resources(
    val holz: Int = 20,
    val getreide: Int = 15,
    val eisen: Int = 10,
    val ruhm: Int = 0
) {
    fun canAfford(cost: BuildCost): Boolean =
        holz >= cost.holz && getreide >= cost.getreide && eisen >= cost.eisen

    fun pay(cost: BuildCost): Resources = copy(
        holz = holz - cost.holz,
        getreide = getreide - cost.getreide,
        eisen = eisen - cost.eisen
    )

    fun apply(delta: ResourceDelta): Resources = copy(
        holz = (holz + delta.holz).coerceAtLeast(0),
        getreide = (getreide + delta.getreide).coerceAtLeast(0),
        eisen = (eisen + delta.eisen).coerceAtLeast(0),
        ruhm = (ruhm + delta.ruhm).coerceAtLeast(0)
    )
}

data class Needs(
    /** 0–100: Wie satt die Siedlung ist (hängt von Getreide ab). */
    val saettigung: Int = 80,
    /** 0–100: Schutz durch Gebäude und Eisen. */
    val schutz: Int = 40
) {
    /** Arbeitskraft-Faktor 0.25–1.0 – sinkt bei schlechten Bedürfnissen. */
    val arbeitkraft: Float
        get() {
            val avg = (saettigung + schutz) / 2f
            return (avg / 100f).coerceIn(0.25f, 1f)
        }
}

data class RaidResult(
    val success: Boolean,
    val warriors: Int,
    val roll: Int,
    val loot: ResourceDelta,
    val message: String
)

data class GameState(
    val resources: Resources = Resources(),
    val bevoelkerung: Int = 5,
    val cells: List<GridCell> = createInitialGrid(),
    val needs: Needs = Needs(),
    val round: Int = 1,
    val raidCooldown: Int = 0,
    val statusMessage: String = "Die Nacht ist günstig. Baue deine Siedlung!"
) {
    fun cellAt(index: Int): GridCell = cells[index]

    fun neighborsOf(index: Int): List<Int> {
        val row = index / GRID_SIZE
        val col = index % GRID_SIZE
        val result = mutableListOf<Int>()
        for (dr in -1..1) {
            for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val r = row + dr
                val c = col + dc
                if (r in 0 until GRID_SIZE && c in 0 until GRID_SIZE) {
                    result += r * GRID_SIZE + c
                }
            }
        }
        return result
    }

    fun hasNeighborType(index: Int, type: BuildingType): Boolean =
        neighborsOf(index).any { cells[it].building?.type == type }

    fun recalculateSchutz(): Needs {
        var schutz = 25 // Basis
        cells.forEach { cell ->
            val b = cell.building ?: return@forEach
            schutz += when (b.type) {
                BuildingType.THINGHALLE -> 15 + if (b.level >= 2) 5 else 0
                BuildingType.SCHMIEDE -> 12 * b.level
                BuildingType.LANGHAUS -> 4 * b.level
                else -> 0
            }
        }
        schutz += (resources.eisen / 2).coerceAtMost(20)
        return needs.copy(schutz = schutz.coerceIn(0, 100))
    }
}

fun createInitialGrid(): List<GridCell> = List(GRID_SIZE * GRID_SIZE) { index ->
    if (index == CENTER_INDEX) {
        GridCell(CellBuilding(BuildingType.THINGHALLE, level = 1))
    } else {
        GridCell()
    }
}

fun GameState.build(index: Int, type: BuildingType): GameState {
    if (index !in cells.indices) {
        return copy(statusMessage = "Ungültiges Feld.")
    }
    if (index == CENTER_INDEX || !cells[index].isEmpty) {
        return copy(statusMessage = "Dieses Feld ist bereits belegt.")
    }
    if (!type.buildable) {
        return copy(statusMessage = "Die Thinghalle steht bereits im Zentrum.")
    }
    val cost = type.costForLevel(1)
    if (!resources.canAfford(cost)) {
        return copy(statusMessage = "Nicht genug Ressourcen für ${type.displayName}.")
    }
    val newCells = cells.toMutableList()
    newCells[index] = GridCell(CellBuilding(type, level = 1))
    val popGain = type.populationGain(1)
    val updated = copy(
        resources = resources.pay(cost),
        cells = newCells,
        bevoelkerung = bevoelkerung + popGain,
        statusMessage = if (popGain > 0) {
            "${type.displayName} gebaut (+$popGain Bevölkerung)."
        } else {
            "${type.displayName} gebaut."
        }
    )
    return updated.copy(needs = updated.recalculateSchutz())
}

fun GameState.upgrade(index: Int): GameState {
    val building = cells.getOrNull(index)?.building
        ?: return copy(statusMessage = "Kein Gebäude zum Ausbau.")
    if (building.type == BuildingType.THINGHALLE) {
        // Thinghalle darf auf Stufe 2 – kostenlos/günstig als HQ-Ausbau
        if (building.level >= MAX_BUILDING_LEVEL) {
            return copy(statusMessage = "Thinghalle ist bereits maximal ausgebaut.")
        }
        val cost = BuildCost(holz = 10, getreide = 5, eisen = 2)
        if (!resources.canAfford(cost)) {
            return copy(statusMessage = "Nicht genug Ressourcen für den Thinghallen-Ausbau.")
        }
        val newCells = cells.toMutableList()
        newCells[index] = GridCell(building.copy(level = 2))
        val updated = copy(
            resources = resources.pay(cost),
            cells = newCells,
            statusMessage = "Thinghalle auf Stufe 2 ausgebaut."
        )
        return updated.copy(needs = updated.recalculateSchutz())
    }
    if (building.level >= MAX_BUILDING_LEVEL) {
        return copy(statusMessage = "${building.type.displayName} ist bereits Stufe $MAX_BUILDING_LEVEL.")
    }
    val nextLevel = building.level + 1
    val cost = building.type.costForLevel(nextLevel)
    if (!resources.canAfford(cost)) {
        return copy(statusMessage = "Nicht genug Ressourcen für den Ausbau.")
    }
    val newCells = cells.toMutableList()
    newCells[index] = GridCell(building.copy(level = nextLevel))
    val popGain = building.type.populationGain(nextLevel)
    val updated = copy(
        resources = resources.pay(cost),
        cells = newCells,
        bevoelkerung = bevoelkerung + popGain,
        statusMessage = if (popGain > 0) {
            "${building.type.displayName} → Stufe $nextLevel (+$popGain Bevölkerung)."
        } else {
            "${building.type.displayName} → Stufe $nextLevel."
        }
    )
    return updated.copy(needs = updated.recalculateSchutz())
}

fun GameState.advanceRound(): GameState {
    val factor = needs.arbeitkraft
    var produced = ResourceDelta()

    cells.forEachIndexed { index, cell ->
        val b = cell.building ?: return@forEachIndexed
        var delta = b.type.baseProduction(b.level)
        // Nachbarbonus: Acker neben Langhaus +1 Getreide
        if (b.type == BuildingType.ACKER && hasNeighborType(index, BuildingType.LANGHAUS)) {
            delta = delta.copy(getreide = delta.getreide + 1)
        }
        produced = ResourceDelta(
            holz = produced.holz + delta.holz,
            getreide = produced.getreide + delta.getreide,
            eisen = produced.eisen + delta.eisen,
            ruhm = produced.ruhm + delta.ruhm
        )
    }

    fun scale(value: Int): Int = if (value <= 0) 0 else (value * factor).toInt().coerceAtLeast(if (value > 0) 1 else 0)

    val scaled = ResourceDelta(
        holz = scale(produced.holz),
        getreide = scale(produced.getreide),
        eisen = scale(produced.eisen),
        ruhm = scale(produced.ruhm)
    )

    var newResources = resources.apply(scaled)

    // Getreideverbrauch durch Bevölkerung
    val foodNeed = (bevoelkerung / 2).coerceAtLeast(1)
    val newSaettigung: Int
    if (newResources.getreide >= foodNeed) {
        newResources = newResources.copy(getreide = newResources.getreide - foodNeed)
        newSaettigung = (needs.saettigung + 8).coerceAtMost(100)
    } else {
        // Verbraucht alles, Sättigung sinkt
        newResources = newResources.copy(getreide = 0)
        newSaettigung = (needs.saettigung - 25).coerceAtLeast(0)
    }

    val withNeeds = copy(
        resources = newResources,
        needs = needs.copy(saettigung = newSaettigung),
        round = round + 1,
        raidCooldown = (raidCooldown - 1).coerceAtLeast(0),
        statusMessage = "Runde ${round + 1}: +" +
            "${scaled.holz} Holz, +${scaled.getreide} Getreide, +" +
            "${scaled.eisen} Eisen, +${scaled.ruhm} Ruhm " +
            "(Arbeitskraft ${(factor * 100).toInt()}%)."
    )
    return withNeeds.copy(needs = withNeeds.recalculateSchutz())
}

/**
 * Raid gegen den römischen Wachturm.
 * Krieger = min(gewählte, Bevölkerung), kostet Eisen, einfacher Würfelwurf.
 */
fun GameState.raidWachturm(warriors: Int, random: Random = Random.Default): Pair<GameState, RaidResult> {
    if (raidCooldown > 0) {
        val result = RaidResult(
            success = false,
            warriors = 0,
            roll = 0,
            loot = ResourceDelta(),
            message = "Die Römer sind gewarnt. Wartet."
        )
        return copy(statusMessage = result.message) to result
    }
    if (bevoelkerung < RAID_MIN_WARRIORS) {
        val result = RaidResult(
            success = false,
            warriors = 0,
            roll = 0,
            loot = ResourceDelta(),
            message = "Zu wenig Bevölkerung für einen Raubzug (mind. $RAID_MIN_WARRIORS)."
        )
        return copy(statusMessage = result.message) to result
    }
    if (resources.eisen < RAID_EISEN_COST) {
        val result = RaidResult(
            success = false,
            warriors = 0,
            roll = 0,
            loot = ResourceDelta(),
            message = "Nicht genug Eisen für Waffen ($RAID_EISEN_COST nötig)."
        )
        return copy(statusMessage = result.message) to result
    }
    val used = warriors.coerceIn(RAID_MIN_WARRIORS, bevoelkerung.coerceAtMost(8))

    val roll = random.nextInt(1, 7) // 1–6
    val power = roll + used
    val difficulty = 8
    val success = power >= difficulty

    var newResources = resources.copy(eisen = resources.eisen - RAID_EISEN_COST)
    var newPop = bevoelkerung
    val loot: ResourceDelta
    val message: String

    if (success) {
        loot = ResourceDelta(
            getreide = 4 + used / 2,
            eisen = 3 + roll / 2,
            ruhm = 2 + if (roll == 6) 2 else 0
        )
        newResources = newResources.apply(loot)
        message = "Der Turm brennt. Beute gehört euch. " +
            "(+${loot.getreide} Getreide, +${loot.eisen} Eisen, +${loot.ruhm} Ruhm)"
    } else {
        loot = ResourceDelta()
        // Kleiner Verlust
        if (newPop > 1 && roll <= 2) {
            newPop -= 1
            message = "Die Wache war wach. Zieht euch zurück. Ein Krieger fällt."
        } else {
            message = "Die Wache war wach. Zieht euch zurück."
        }
    }

    val result = RaidResult(
        success = success,
        warriors = used,
        roll = roll,
        loot = loot,
        message = message
    )
    val updated = copy(
        resources = newResources,
        bevoelkerung = newPop,
        raidCooldown = RAID_COOLDOWN_ROUNDS,
        statusMessage = message
    )
    return updated.copy(needs = updated.recalculateSchutz()) to result
}
