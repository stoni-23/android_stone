package com.stoni.androidstone.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.stoni.androidstone.game.RaidResult
import com.stoni.androidstone.game.advanceRound
import com.stoni.androidstone.game.build
import com.stoni.androidstone.game.costForLevel
import com.stoni.androidstone.game.raidWachturm
import com.stoni.androidstone.game.upgrade
import com.stoni.androidstone.ui.PixelAssets
import com.stoni.androidstone.ui.pixelPainterResource
import com.stoni.androidstone.ui.rememberDrawableOrNull
import com.stoni.androidstone.ui.theme.AndroidStoneTheme
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min

private val BoardWood = Color(0xFF3E2C1C)
private val HudPanel = Color(0xFF2A2118)
private val HudPanelEdge = Color(0xFF6B5338)
private val HudGold = Color(0xFFF2E6D0)
private val HudMuted = Color(0xFFC4B59A)
private val BuildModeGlow = Color(0xFFFFD76A)

private sealed class CellDialog {
    data class Build(val index: Int) : CellDialog()
    data class Info(val index: Int) : CellDialog()
}

/** Orthogonal neighbors of the Thinghalle get a path ground tile. */
private fun isAdjacentToThinghalle(index: Int): Boolean {
    val crow = CENTER_INDEX / GRID_SIZE
    val ccol = CENTER_INDEX % GRID_SIZE
    val row = index / GRID_SIZE
    val col = index % GRID_SIZE
    return abs(row - crow) + abs(col - ccol) == 1
}

@Composable
private fun rememberPixelFrame(periodMs: Int = 400): Int {
    val transition = rememberInfiniteTransition(label = "pixelFrame")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = periodMs * 2, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pixelPhase"
    )
    return if (phase < 1f) 0 else 1
}

@Composable
fun PlayScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var state by remember { mutableStateOf(GameState()) }
    var cellDialog by remember { mutableStateOf<CellDialog?>(null) }
    var showRaidPanel by remember { mutableStateOf(false) }
    var buildMode by remember { mutableStateOf(false) }
    var warriorCount by remember { mutableIntStateOf(3) }
    val unitFrame = rememberPixelFrame(400)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF1A140F)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            GameTopChrome(
                round = state.round,
                bevoelkerung = state.bevoelkerung,
                onBackClick = onBackClick
            )
            ResourceHud(state)
            NeedsHud(state)
            Spacer(modifier = Modifier.height(6.dp))

            SettlementBoard(
                state = state,
                unitFrame = unitFrame,
                buildMode = buildMode,
                onCellClick = { index ->
                    val cell = state.cellAt(index)
                    if (cell.isEmpty) {
                        cellDialog = CellDialog.Build(index)
                        buildMode = false
                    } else {
                        cellDialog = CellDialog.Info(index)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            )

            StatusStrip(state.statusMessage)
            ActionBar(
                state = state,
                unitFrame = unitFrame,
                buildMode = buildMode,
                onRound = { state = state.advanceRound() },
                onBuildToggle = { buildMode = !buildMode },
                onRaid = {
                    warriorCount = warriorCount.coerceIn(
                        RAID_MIN_WARRIORS,
                        state.bevoelkerung.coerceAtLeast(RAID_MIN_WARRIORS).coerceAtMost(8)
                    )
                    showRaidPanel = true
                }
            )
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
            onRaid = { count ->
                val (next, result) = state.raidWachturm(count)
                state = next
                result
            }
        )
    }
}

@Composable
private fun GameTopChrome(
    round: Int,
    bevoelkerung: Int,
    onBackClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HudPanel)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Zurück",
                tint = HudGold
            )
        }
        Text(
            text = "Limes",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = HudGold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "Runde $round",
            color = HudMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(end = 10.dp)
        )
        Text(
            text = "Volk $bevoelkerung",
            color = HudGold,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.padding(end = 12.dp)
        )
    }
}

@Composable
private fun ResourceHud(state: GameState) {
    val r = state.resources
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HudPanel.copy(alpha = 0.92f))
            .border(1.dp, HudPanelEdge)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ResourceHudChip("Holz", r.holz, PixelAssets.iconHolz)
        ResourceHudChip("Getreide", r.getreide, PixelAssets.iconGetreide)
        ResourceHudChip("Eisen", r.eisen, PixelAssets.iconEisen)
        ResourceHudChip("Ruhm", r.ruhm, PixelAssets.iconRuhm)
    }
}

@Composable
private fun ResourceHudChip(
    label: String,
    value: Int,
    @DrawableRes iconId: Int
) {
    val icon = rememberDrawableOrNull(iconId)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) {
            Image(
                painter = pixelPainterResource(icon),
                contentDescription = label,
                modifier = Modifier.size(28.dp),
                contentScale = ContentScale.Fit
            )
        }
        Column {
            Text(
                text = value.toString(),
                color = HudGold,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                text = label,
                color = HudMuted,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun NeedsHud(state: GameState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NeedChip(
            label = "Sättigung",
            value = state.needs.saettigung,
            modifier = Modifier.weight(1f)
        )
        NeedChip(
            label = "Schutz",
            value = state.needs.schutz,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "AK ${(state.needs.arbeitkraft * 100).toInt()}%",
            color = HudMuted,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun NeedChip(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = HudMuted, fontSize = 10.sp)
            Text("$value%", color = HudGold, fontSize = 10.sp)
        }
        LinearProgressIndicator(
            progress = { value / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = Color(0xFF8FBF5A),
            trackColor = Color(0xFF3A3024)
        )
    }
}

@Composable
private fun SettlementBoard(
    state: GameState,
    unitFrame: Int,
    buildMode: Boolean,
    onCellClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val boardSize = min(maxWidth.value, maxHeight.value).dp
        Column(
            modifier = Modifier
                .size(boardSize)
                .clip(RoundedCornerShape(10.dp))
                .border(3.dp, BoardWood, RoundedCornerShape(10.dp))
                .background(BoardWood)
                .padding(3.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            for (row in 0 until GRID_SIZE) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    for (col in 0 until GRID_SIZE) {
                        val index = row * GRID_SIZE + col
                        GridCellView(
                            state = state,
                            index = index,
                            unitFrame = unitFrame,
                            buildMode = buildMode,
                            onClick = { onCellClick(index) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GridCellView(
    state: GameState,
    index: Int,
    unitFrame: Int,
    buildMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cell = state.cellAt(index)
    val building = cell.building
    val usePath = isAdjacentToThinghalle(index)
    // Empty buildable cells always use Looki build-slot (never a plus).
    // Occupied cells sit on grass, or path when next to the Thinghalle.
    val groundRes = when {
        building == null -> PixelAssets.buildSlot
        usePath -> PixelAssets.path
        else -> PixelAssets.grass
    }
    val groundPrimary = rememberDrawableOrNull(groundRes)
    val grassFallback = rememberDrawableOrNull(PixelAssets.grass)
    val resolvedGround = groundPrimary ?: grassFallback

    val mappedSprite = building?.let { PixelAssets.building(it.type, it.level) } ?: 0
    val spriteId = rememberDrawableOrNull(mappedSprite)
    val workerId = rememberDrawableOrNull(
        if (unitFrame == 0) PixelAssets.workerA else PixelAssets.workerB
    )

    Box(
        modifier = modifier
            .then(
                if (buildMode && building == null) Modifier.border(2.dp, BuildModeGlow)
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (resolvedGround != null) {
            Image(
                painter = pixelPainterResource(resolvedGround),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        }
        if (building != null) {
            if (spriteId != null) {
                Image(
                    painter = pixelPainterResource(spriteId),
                    contentDescription = building.type.displayName,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp),
                    contentScale = ContentScale.Fit
                )
                if (PixelAssets.isProductive(building.type) && workerId != null) {
                    Image(
                        painter = pixelPainterResource(workerId),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 2.dp, bottom = 14.dp)
                            .fillMaxWidth(0.38f)
                            .aspectRatio(1f),
                        contentScale = ContentScale.Fit
                    )
                }
                Text(
                    text = "S${building.level}",
                    color = HudGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 2.dp)
                        .background(Color(0xAA1A140F), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            } else {
                Text(
                    text = building.type.shortLabel,
                    color = HudGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
        // No plus signs — empty cells are tile_build_slot / path only.
    }
}

@Composable
private fun StatusStrip(message: String) {
    Text(
        text = message,
        color = HudGold,
        fontSize = 12.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun ActionBar(
    state: GameState,
    unitFrame: Int,
    buildMode: Boolean,
    onRound: () -> Unit,
    onBuildToggle: () -> Unit,
    onRaid: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HudPanel)
            .border(1.dp, HudPanelEdge)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onRound,
            modifier = Modifier.weight(1.1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF5D6B3A),
                contentColor = Color.White
            )
        ) {
            Text("Runde", fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = onBuildToggle,
            modifier = Modifier.weight(1.1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (buildMode) Color(0xFFB8892E) else Color(0xFF8B6B4A),
                contentColor = Color.White
            )
        ) {
            Text(if (buildMode) "Bauen…" else "Bauen", fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            onClick = onRaid,
            modifier = Modifier.weight(1f)
        ) {
            val warriorId = rememberDrawableOrNull(
                if (unitFrame == 0) PixelAssets.warriorA else PixelAssets.warriorB
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (warriorId != null) {
                    Image(
                        painter = pixelPainterResource(warriorId),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = if (state.raidCooldown > 0) {
                        "Raid (${state.raidCooldown})"
                    } else {
                        "Raid"
                    },
                    color = HudGold,
                    fontSize = 13.sp
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
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Feld ${index + 1} – wähle ein Gebäude:",
                    style = MaterialTheme.typography.bodyMedium
                )
                BuildingType.buildableTypes.forEach { type ->
                    val cost = type.costForLevel(1)
                    val affordable = state.resources.canAfford(cost)
                    val sprite = rememberDrawableOrNull(PixelAssets.building(type, 1))
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (sprite != null) {
                                Image(
                                    painter = pixelPainterResource(sprite),
                                    contentDescription = type.displayName,
                                    modifier = Modifier.size(48.dp),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
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
    val sprite = rememberDrawableOrNull(PixelAssets.building(building.type, building.level))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(building.type.displayName) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (sprite != null) {
                    Image(
                        painter = pixelPainterResource(sprite),
                        contentDescription = building.type.displayName,
                        modifier = Modifier.size(96.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
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
    onRaid: (Int) -> RaidResult
) {
    val maxWarriors = state.bevoelkerung.coerceAtMost(8).coerceAtLeast(RAID_MIN_WARRIORS)
    val canRaid = state.raidCooldown == 0 &&
        state.bevoelkerung >= RAID_MIN_WARRIORS &&
        state.resources.eisen >= RAID_EISEN_COST

    var lastResult by remember { mutableStateOf<RaidResult?>(null) }
    var showFx by remember { mutableStateOf(false) }
    val smokeFrame = rememberPixelFrame(500)
    val fireFrame = rememberPixelFrame(280)
    val wachturmId = rememberDrawableOrNull(PixelAssets.wachturm)
    val smokeId = rememberDrawableOrNull(
        if (smokeFrame == 0) PixelAssets.fxSmoke1 else PixelAssets.fxSmoke2
    )
    val fireId = rememberDrawableOrNull(
        if (fireFrame == 0) PixelAssets.fxFire1 else PixelAssets.fxFire2
    )
    val hitId = rememberDrawableOrNull(PixelAssets.fxHit)

    LaunchedEffect(showFx, lastResult) {
        if (showFx && lastResult != null) {
            delay(900)
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!showFx) onDismiss()
        },
        title = { Text("Römischer Wachturm") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(140.dp)
                ) {
                    if (wachturmId != null) {
                        Image(
                            painter = pixelPainterResource(wachturmId),
                            contentDescription = "Römischer Wachturm",
                            modifier = Modifier.size(120.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    if (!showFx) {
                        if (smokeId != null) {
                            Image(
                                painter = pixelPainterResource(smokeId),
                                contentDescription = null,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(48.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    } else if (lastResult?.success == true) {
                        val fxId = fireId ?: hitId
                        if (fxId != null) {
                            Image(
                                painter = pixelPainterResource(fxId),
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    } else if (lastResult != null) {
                        if (hitId != null) {
                            Image(
                                painter = pixelPainterResource(hitId),
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (showFx && lastResult != null) {
                    Text(
                        text = lastResult!!.message,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                } else {
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
            }
        },
        confirmButton = {
            if (!showFx) {
                Button(
                    onClick = {
                        lastResult = onRaid(warriors)
                        showFx = true
                    },
                    enabled = canRaid
                ) {
                    Text("Angreifen")
                }
            }
        },
        dismissButton = {
            if (!showFx) {
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
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
