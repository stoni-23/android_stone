package com.stoni.androidstone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stoni.androidstone.game.BuildCost
import com.stoni.androidstone.game.BuildingType
import com.stoni.androidstone.game.CENTER_INDEX
import com.stoni.androidstone.game.GRID_SIZE
import com.stoni.androidstone.game.GameState
import com.stoni.androidstone.game.MAX_BUILDING_LEVEL
import com.stoni.androidstone.game.RAID_EISEN_COST
import com.stoni.androidstone.game.RAID_MIN_WARRIORS
import com.stoni.androidstone.game.advanceRound
import com.stoni.androidstone.game.build
import com.stoni.androidstone.game.costForLevel
import com.stoni.androidstone.game.raidWachturm
import com.stoni.androidstone.game.upgrade
import com.stoni.androidstone.ui.theme.AndroidStoneTheme

private sealed class CellDialog {
    data class Build(val index: Int) : CellDialog()
    data class Info(val index: Int) : CellDialog()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var state by remember { mutableStateOf(GameState()) }
    var cellDialog by remember { mutableStateOf<CellDialog?>(null) }
    var showRaidPanel by remember { mutableStateOf(false) }
    var warriorCount by remember { mutableIntStateOf(3) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Limes") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            ResourceBar(state)
            Spacer(modifier = Modifier.height(8.dp))
            NeedsBar(state)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Runde ${state.round} · Bevölkerung ${state.bevoelkerung} · " +
                    "Arbeitskraft ${(state.needs.arbeitkraft * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            SettlementGrid(
                state = state,
                onCellClick = { index ->
                    val cell = state.cellAt(index)
                    cellDialog = if (cell.isEmpty) {
                        CellDialog.Build(index)
                    } else {
                        CellDialog.Info(index)
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = state.statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { state = state.advanceRound() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Runde")
                }
                OutlinedButton(
                    onClick = {
                        warriorCount = warriorCount.coerceIn(
                            RAID_MIN_WARRIORS,
                            state.bevoelkerung.coerceAtLeast(RAID_MIN_WARRIORS).coerceAtMost(8)
                        )
                        showRaidPanel = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (state.raidCooldown > 0) {
                            "Raubzug (${state.raidCooldown})"
                        } else {
                            "Raubzug"
                        }
                    )
                }
            }
        }
    }

    when (val dialog = cellDialog) {
        is CellDialog.Build -> BuildMenuDialog(
            state = state,
            index = dialog.index,
            onDismiss = { cellDialog = null },
            onBuild = { type ->
                state = state.build(dialog.index, type)
                cellDialog = null
            }
        )
        is CellDialog.Info -> BuildingInfoDialog(
            state = state,
            index = dialog.index,
            onDismiss = { cellDialog = null },
            onUpgrade = {
                state = state.upgrade(dialog.index)
                cellDialog = null
            }
        )
        null -> Unit
    }

    if (showRaidPanel) {
        RaidDialog(
            state = state,
            warriors = warriorCount,
            onWarriorsChange = { warriorCount = it },
            onDismiss = { showRaidPanel = false },
            onRaid = {
                val (next, _) = state.raidWachturm(warriorCount)
                state = next
                showRaidPanel = false
            }
        )
    }
}

@Composable
private fun ResourceBar(state: GameState) {
    val r = state.resources
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ResourceChip("Holz", r.holz, "Aus dem Forst")
        ResourceChip("Getreide", r.getreide, "Vom Acker und der Jagd")
        ResourceChip("Eisen", r.eisen, "Aus Erz und Beute")
        ResourceChip("Ruhm", r.ruhm, "Was die Stämme von dir sagen")
    }
}

@Composable
private fun ResourceChip(label: String, value: Int, shortText: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontSize = 11.sp
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = shortText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            fontSize = 8.sp,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
private fun NeedsBar(state: GameState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        NeedRow("Sättigung", state.needs.saettigung)
        Spacer(modifier = Modifier.height(4.dp))
        NeedRow("Schutz", state.needs.schutz)
    }
}

@Composable
private fun NeedRow(label: String, value: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            modifier = Modifier.width(88.dp),
            style = MaterialTheme.typography.bodyMedium
        )
        LinearProgressIndicator(
            progress = { value / 100f },
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        )
        Text(
            text = "$value%",
            modifier = Modifier
                .width(44.dp)
                .padding(start = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun SettlementGrid(
    state: GameState,
    onCellClick: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (row in 0 until GRID_SIZE) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (col in 0 until GRID_SIZE) {
                    val index = row * GRID_SIZE + col
                    GridCellView(
                        state = state,
                        index = index,
                        onClick = { onCellClick(index) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun GridCellView(
    state: GameState,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cell = state.cellAt(index)
    val building = cell.building
    val bg = when {
        building?.type == BuildingType.THINGHALLE -> MaterialTheme.colorScheme.primary
        building != null -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val fg = when {
        building?.type == BuildingType.THINGHALLE -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        if (building == null) {
            Text(
                text = "+",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 18.sp
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = building.type.shortLabel,
                    color = fg,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "S${building.level}",
                    color = fg.copy(alpha = 0.85f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun BuildMenuDialog(
    state: GameState,
    index: Int,
    onDismiss: () -> Unit,
    onBuild: (BuildingType) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gebäude errichten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Feld ${index + 1} – wähle ein Gebäude (Stufe 1):",
                    style = MaterialTheme.typography.bodyMedium
                )
                BuildingType.buildableTypes.forEach { type ->
                    val cost = type.costForLevel(1)
                    val affordable = state.resources.canAfford(cost)
                    val costText = buildString {
                        if (cost.holz > 0) append("${cost.holz} Holz ")
                        if (cost.getreide > 0) append("${cost.getreide} Getreide ")
                        if (cost.eisen > 0) append("${cost.eisen} Eisen")
                    }.trim()
                    OutlinedButton(
                        onClick = { onBuild(type) },
                        enabled = affordable,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(type.displayName, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "$costText · ${type.description}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun BuildingInfoDialog(
    state: GameState,
    index: Int,
    onDismiss: () -> Unit,
    onUpgrade: () -> Unit
) {
    val building = state.cellAt(index).building ?: return
    val canUpgrade = building.level < MAX_BUILDING_LEVEL
    val upgradeCost = if (building.type == BuildingType.THINGHALLE) {
        BuildCost(holz = 10, getreide = 5, eisen = 2)
    } else {
        building.type.costForLevel(building.level + 1)
    }
    val affordable = state.resources.canAfford(upgradeCost)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(building.type.displayName) },
        text = {
            Column {
                Text("Stufe ${building.level} / $MAX_BUILDING_LEVEL")
                Spacer(modifier = Modifier.height(4.dp))
                Text(building.type.description)
                if (index == CENTER_INDEX) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Festes HQ – kann nicht abgerissen werden.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canUpgrade) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val costText = buildString {
                        if (upgradeCost.holz > 0) append("${upgradeCost.holz} Holz ")
                        if (upgradeCost.getreide > 0) append("${upgradeCost.getreide} Getreide ")
                        if (upgradeCost.eisen > 0) append("${upgradeCost.eisen} Eisen")
                    }.trim()
                    Text("Ausbau auf Stufe ${building.level + 1}: $costText")
                }
            }
        },
        confirmButton = {
            if (canUpgrade) {
                Button(onClick = onUpgrade, enabled = affordable) {
                    Text("Ausbauen")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Schließen") }
        }
    )
}

@Composable
private fun RaidDialog(
    state: GameState,
    warriors: Int,
    onWarriorsChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    onRaid: () -> Unit
) {
    val maxWarriors = state.bevoelkerung.coerceAtMost(8).coerceAtLeast(RAID_MIN_WARRIORS)
    val canRaid = state.raidCooldown == 0 &&
        state.bevoelkerung >= RAID_MIN_WARRIORS &&
        state.resources.eisen >= RAID_EISEN_COST

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Römischer Wachturm") },
        text = {
            Column {
                Text(
                    if (state.raidCooldown > 0) {
                        "Die Römer sind gewarnt. Wartet."
                    } else {
                        "Die Nacht ist günstig. Kosten: $RAID_EISEN_COST Eisen."
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (state.raidCooldown > 0) {
                    Text("Noch ${state.raidCooldown} Runde(n).")
                } else {
                    Text("Krieger: $warriors")
                    Slider(
                        value = warriors.toFloat().coerceIn(
                            RAID_MIN_WARRIORS.toFloat(),
                            maxWarriors.toFloat()
                        ),
                        onValueChange = { onWarriorsChange(it.toInt()) },
                        valueRange = RAID_MIN_WARRIORS.toFloat()..maxWarriors.toFloat(),
                        steps = (maxWarriors - RAID_MIN_WARRIORS - 1).coerceAtLeast(0)
                    )
                    Text(
                        text = "Siegchance steigt mit mehr Kriegern (Würfel + Krieger ≥ 8).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onRaid, enabled = canRaid) {
                Text("Angreifen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun PlayScreenPreview() {
    AndroidStoneTheme {
        PlayScreen(onBackClick = {})
    }
}
