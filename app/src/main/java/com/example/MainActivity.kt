package com.example

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            ChromaFlowApp(context = applicationContext)
        }
    }
}

// --- PROCEDURAL ASMR SOUND SYNTHESIZER (Zero External Assets) ---
class ProceduralSoundEngine(private val context: Context) {
    var soundEnabled by mutableStateOf(true)
    private val scope = CoroutineScope(Dispatchers.Default)

    fun vibrate(ms: Long = 20L) {
        try {
            val dur = ms.coerceIn(5L, 300L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(dur, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createOneShot(dur, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(dur)
                }
            }
        } catch (_: Exception) {}
    }

    private fun playSynthBuffer(durationSec: Float, generator: (t: Float) -> Float) {
        if (!soundEnabled) return
        scope.launch {
            try {
                val sampleRate = 22050
                val numSamples = (durationSec * sampleRate).toInt().coerceAtLeast(100)
                val samples = ShortArray(numSamples)
                for (i in 0 until numSamples) {
                    val t = i.toFloat() / sampleRate
                    val v = generator(t).coerceIn(-1f, 1f)
                    samples[i] = (v * Short.MAX_VALUE * 0.55f).toInt().toShort()
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(samples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(samples, 0, samples.size)
                track.play()
                delay((durationSec * 1000).toLong() + 80L)
                track.release()
            } catch (_: Exception) {}
        }
    }

    fun playTapGlass(pitch: Float = 1.0f) {
        vibrate(15)
        val f1 = 880f * pitch
        val f2 = f1 * 2.75f
        playSynthBuffer(0.15f) { t ->
            val env = exp(-t * 28f)
            val s1 = sin(2f * PI.toFloat() * f1 * t) * 0.65f
            val s2 = asin(sin(2f * PI.toFloat() * f2 * t)) * (2f / PI.toFloat()) * 0.35f
            (s1 + s2) * env
        }
    }

    fun playLiquidPour(fillLevel: Int = 1) {
        vibrate(25)
        val base = 340f + fillLevel * 130f
        playSynthBuffer(0.32f) { t ->
            val env = sin((t / 0.32f) * PI.toFloat()) * exp(-t * 4f)
            val freq = base * (1f + 0.6f * (t / 0.32f))
            val wave = sin(2f * PI.toFloat() * freq * t) * 0.7f
            val bubble = (Random.nextFloat() * 2f - 1f) * 0.25f * exp(-t * 10f)
            (wave + bubble) * env
        }
    }

    fun playPrismMerge() {
        vibrate(45)
        val notes = floatArrayOf(523.25f, 659.25f, 783.99f, 1046.5f)
        playSynthBuffer(0.45f) { t ->
            var sum = 0f
            for (i in notes.indices) {
                val nt = t - i * 0.055f
                if (nt > 0f) {
                    val env = exp(-nt * 10f)
                    sum += sin(2f * PI.toFloat() * notes[i] * nt) * env * 0.35f
                    sum += sin(2f * PI.toFloat() * notes[i] * 2f * nt) * env * 0.15f
                }
            }
            sum
        }
    }

    fun playBlocked() {
        vibrate(35)
        playSynthBuffer(0.18f) { t ->
            val env = exp(-t * 20f)
            val freq = 95f - t * 180f
            asin(sin(2f * PI.toFloat() * freq.coerceAtLeast(45f) * t)) * (2f / PI.toFloat()) * env
        }
    }

    fun playVialComplete() {
        vibrate(60)
        val pitches = floatArrayOf(783.99f, 880f, 1046.5f, 1174.66f, 1318.51f, 1567.98f)
        playSynthBuffer(0.52f) { t ->
            var sum = 0f
            for (i in pitches.indices) {
                val nt = t - i * 0.045f
                if (nt > 0f) {
                    sum += sin(2f * PI.toFloat() * pitches[i] * nt) * exp(-nt * 12f) * 0.32f
                }
            }
            sum
        }
    }

    fun playVictory() {
        vibrate(80)
        val notes = floatArrayOf(523.25f, 659.25f, 783.99f, 1046.5f, 1318.5f, 1567.98f)
        playSynthBuffer(0.65f) { t ->
            var sum = 0f
            for (i in notes.indices) {
                val nt = t - i * 0.06f
                if (nt > 0f) {
                    sum += sin(2f * PI.toFloat() * notes[i] * nt) * exp(-nt * 7f) * 0.3f
                }
            }
            sum
        }
    }
}

// --- COLOR & DIRECTION DEFINITIONS ---
enum class CapsuleColor(
    val label: String,
    val hex: Color,
    val lightHex: Color,
    val darkHex: Color,
    val isPrimary: Boolean
) {
    RED("Ruby", Color(0xFFFF3366), Color(0xFFFF7597), Color(0xFFB3002D), true),
    BLUE("Sapphire", Color(0xFF0088FF), Color(0xFF4DB2FF), Color(0xFF0055B3), true),
    YELLOW("Amber", Color(0xFFFFCC00), Color(0xFFFFE066), Color(0xFFB38F00), true),
    ORANGE("Orange", Color(0xFFFF7700), Color(0xFFFFA347), Color(0xFFB35300), false),
    GREEN("Emerald", Color(0xFF00D664), Color(0xFF52FF9F), Color(0xFF008A40), false),
    PURPLE("Amethyst", Color(0xFFAA33FF), Color(0xFFCC7AFF), Color(0xFF6F00B8), false);

    companion object {
        fun prismMerge(c1: CapsuleColor, c2: CapsuleColor): CapsuleColor? {
            val set = setOf(c1, c2)
            return when {
                set == setOf(RED, YELLOW) -> ORANGE
                set == setOf(BLUE, YELLOW) -> GREEN
                set == setOf(RED, BLUE) -> PURPLE
                else -> null
            }
        }
    }
}

enum class ArrowDir(val dx: Int, val dy: Int, val angleDeg: Float) {
    UP(0, -1, 0f),
    RIGHT(1, 0, 90f),
    DOWN(0, 1, 180f),
    LEFT(-1, 0, 270f);

    fun rotateCW90(): ArrowDir = when (this) {
        UP -> RIGHT
        RIGHT -> DOWN
        DOWN -> LEFT
        LEFT -> UP
    }
}

enum class GlassTheme(
    val id: String,
    val title: String,
    val subtitle: String,
    val bgTop: Color,
    val bgBottom: Color,
    val accent: Color,
    val secondary: Color
) {
    CRYSTAL("crystal", "Crystal Clear", "iOS 26 Optic", Color(0xFF1A2649), Color(0xFF070B14), Color(0xFF00E5FF), Color(0xFFFF2E93)),
    CYBER("cyber", "Cyber Neon", "Synthwave Magenta", Color(0xFF310B4B), Color(0xFF0E0417), Color(0xFFFF007F), Color(0xFF00F0FF)),
    ABYSSAL("abyssal", "Deep Abyssal", "Oceanic Cyan", Color(0xFF063445), Color(0xFF021017), Color(0xFF00FFCC), Color(0xFF0077B6)),
    GOLD("gold", "Royal Gold", "Gilded Amber", Color(0xFF3D2706), Color(0xFF140C02), Color(0xFFFFD700), Color(0xFFFF7700))
}

data class GridItem(
    val id: String,
    val isObstacle: Boolean,
    val r: Int,
    val c: Int,
    val color: CapsuleColor = CapsuleColor.RED,
    val dir: ArrowDir = ArrowDir.UP,
    val ice: Int = 0,
    val isGate: Boolean = false,
    val gateColor: CapsuleColor? = null
)

data class TargetVial(
    val id: String,
    val color: CapsuleColor,
    val target: Int = 3,
    val current: Int = 0,
    val completed: Boolean = false,
    val isBoss: Boolean = false,
    val isGiantPrism: Boolean = false,
    val remainingSecondary: List<CapsuleColor> = emptyList(),
    val filledSegments: List<CapsuleColor> = emptyList()
) {
    fun acceptsColor(candidate: CapsuleColor): Boolean {
        if (completed) return false
        if (isGiantPrism) {
            val isSec = (candidate == CapsuleColor.ORANGE || candidate == CapsuleColor.GREEN || candidate == CapsuleColor.PURPLE)
            if (!isSec) return false
            if (remainingSecondary.contains(candidate)) return true
            return current < target
        }
        return color == candidate
    }
}

data class BufferItem(
    val id: String,
    val color: CapsuleColor,
    val isMerging: Boolean = false
)

data class FlyingCapsule(
    val id: String,
    val color: CapsuleColor,
    val startX: Float,
    val startY: Float,
    val targetX: Float,
    val targetY: Float,
    val progress: Float = 0f,
    val toVialId: String? = null
)

data class VisualParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float,
    val color: Color,
    var life: Float,
    val maxLife: Float,
    val isRing: Boolean = false,
    val isChromatic: Boolean = false,
    val rgbSplit: Float = 0f,
    val splitAngle: Float = 0f,
    val text: String? = null
)

data class CapsuleSloshState(
    val amp: Float,
    val phase: Float
)

data class GoldenArc(
    val idx1: Int,
    val idx2: Int,
    var life: Float = 0.5f
)

data class LevelBuildResult(
    val rows: Int,
    val cols: Int,
    val items: List<GridItem>,
    val vials: List<TargetVial>,
    val conveyorTiles: Set<Pair<Int, Int>>
)

data class DailyQuestState(
    val id: String,
    val title: String,
    val desc: String,
    val progress: Int,
    val target: Int,
    val rewardShards: Int,
    val rewardXP: Int,
    val claimed: Boolean = false
)

data class PrismPassTier(
    val tier: Int,
    val xpRequired: Int,
    val rewardShards: Int,
    val rewardCoins: Int,
    val title: String
)

data class DailyStreakReward(
    val day: Int,
    val mult: Float,
    val coins: Int,
    val shards: Int,
    val title: String
)

val DAILY_STREAK_SCHEDULE = listOf(
    DailyStreakReward(1, 1.2f, 100, 0, "Alchemist Starter"),
    DailyStreakReward(2, 1.4f, 150, 0, "Prism Initiate"),
    DailyStreakReward(3, 1.6f, 200, 0, "Liquid Adept"),
    DailyStreakReward(4, 1.8f, 250, 5, "Aether Weaver"),
    DailyStreakReward(5, 2.0f, 350, 10, "Vial Master"),
    DailyStreakReward(6, 2.5f, 500, 15, "Prism Sovereign"),
    DailyStreakReward(7, 3.0f, 1000, 30, "Grand Grandmaster")
)

fun getDailyStreakMultiplier(day: Int): Float {
    return DAILY_STREAK_SCHEDULE.find { it.day == day }?.mult ?: 1.2f
}

// --- STRICT BACKWARD-CONSTRUCTION SOLVABLE LEVEL GENERATOR ---
object SolvableLevelBuilder {
    private fun rotateCCW90(dir: ArrowDir, steps: Int): ArrowDir {
        var cur = dir
        repeat((steps % 4 + 4) % 4) {
            cur = when (cur) {
                ArrowDir.UP -> ArrowDir.LEFT
                ArrowDir.LEFT -> ArrowDir.DOWN
                ArrowDir.DOWN -> ArrowDir.RIGHT
                ArrowDir.RIGHT -> ArrowDir.UP
            }
        }
        return cur
    }

    fun build(levelNum: Int): LevelBuildResult {
        val rows = if (levelNum >= 3) 8 else 6
        val cols = if (levelNum >= 3) 8 else 6
        val isBoss = (levelNum % 5 == 0)

        val palette = mutableListOf(CapsuleColor.RED, CapsuleColor.BLUE, CapsuleColor.YELLOW)
        if (levelNum >= 2 || isBoss) palette.add(CapsuleColor.ORANGE)
        if (levelNum >= 3 || isBoss) palette.add(CapsuleColor.GREEN)
        if (levelNum >= 5 || isBoss) palette.add(CapsuleColor.PURPLE)

        val numObstacles = if (levelNum < 3) 0 else min(levelNum / 3, 4)
        val numIce = if (levelNum < 2) 0 else min((levelNum - 1) / 2, 5)
        val numConveyors = min(3 + levelNum / 2, 8)

        val totalCells = rows * cols
        val capsuleCount = max(15, ((totalCells - numObstacles) * 7 / 10) / 3 * 3)
        val numVials = if (isBoss) 3 else max(3, min(6, capsuleCount / 3))

        val vials = mutableListOf<TargetVial>()
        // Ordered forward solve sequence: extracting capsules in index order 0, 1, ..., N-1
        // feeds active Target Vials or fuses adjacent primary pairs in the Buffer Dock
        // without ever deadlocking or overflowing the 5-Slot Buffer Dock.
        val orderedExtractColors = mutableListOf<CapsuleColor>()

        if (isBoss) {
            // Alchemist Boss Order: Giant Prism Flask requiring 6 segments of mixed secondary colors
            val bossSecondary = listOf(
                CapsuleColor.ORANGE, CapsuleColor.GREEN, CapsuleColor.PURPLE,
                CapsuleColor.ORANGE, CapsuleColor.GREEN, CapsuleColor.PURPLE
            )
            vials.add(
                TargetVial(
                    id = "vial_boss_$levelNum",
                    color = CapsuleColor.PURPLE,
                    target = 6,
                    isBoss = true,
                    isGiantPrism = true,
                    remainingSecondary = bossSecondary,
                    filledSegments = emptyList()
                )
            )
            // Interleave 3 primary fusion pairs (adjacent in solve order) + 3 direct secondaries
            orderedExtractColors.addAll(listOf(CapsuleColor.RED, CapsuleColor.YELLOW, CapsuleColor.ORANGE))
            orderedExtractColors.addAll(listOf(CapsuleColor.BLUE, CapsuleColor.YELLOW, CapsuleColor.GREEN))
            orderedExtractColors.addAll(listOf(CapsuleColor.RED, CapsuleColor.BLUE, CapsuleColor.PURPLE))
        }

        val startVialIdx = if (isBoss) 1 else 0
        for (i in startVialIdx until numVials) {
            val col = palette.random()
            vials.add(
                TargetVial(
                    id = "vial_${levelNum}_$i",
                    color = col,
                    target = 3,
                    isBoss = false,
                    isGiantPrism = false
                )
            )
            val isSecondary = (col == CapsuleColor.ORANGE || col == CapsuleColor.GREEN || col == CapsuleColor.PURPLE)
            if (isSecondary && Random.nextFloat() < 0.65f && orderedExtractColors.size + 4 <= totalCells - numObstacles - 2) {
                when (col) {
                    CapsuleColor.ORANGE -> orderedExtractColors.addAll(listOf(CapsuleColor.RED, CapsuleColor.YELLOW))
                    CapsuleColor.GREEN -> orderedExtractColors.addAll(listOf(CapsuleColor.BLUE, CapsuleColor.YELLOW))
                    else -> orderedExtractColors.addAll(listOf(CapsuleColor.RED, CapsuleColor.BLUE))
                }
                orderedExtractColors.add(col)
                orderedExtractColors.add(col)
            } else {
                repeat(3) { orderedExtractColors.add(col) }
            }
        }

        // STRICT BACKWARD-CONSTRUCTION ALGORITHM:
        // Start from an empty board (plus interior Obsidian obstacles) and simulate placing
        // capsules in reverse solve order (stepIdx = N - 1 downTo 0), sliding them inwards
        // from the board edges along -exitDir so that the raycast path from (r, c) in direction
        // exitDir to the board edge is 100% clear of all later-solved capsules (stepIdx+1 .. N-1).
        val occupied = Array(rows) { BooleanArray(cols) }
        val items = mutableListOf<GridItem>()

        // Place interior obsidian blocks
        var obsPlaced = 0
        var attempts = 0
        while (obsPlaced < numObstacles && attempts < 40) {
            attempts++
            val r = Random.nextInt(1, rows - 1)
            val c = Random.nextInt(1, cols - 1)
            if (!occupied[r][c]) {
                occupied[r][c] = true
                items.add(GridItem(id = "obs_${r}_$c", isObstacle = true, r = r, c = c))
                obsPlaced++
            }
        }

        // Place Color-Lock Gates: only allow capsules of matching color to pass through
        val gateCount = if (levelNum >= 2) min(3, levelNum / 2) else 0
        var gatesPlaced = 0
        var gateAttempts = 0
        while (gatesPlaced < gateCount && gateAttempts < 45) {
            gateAttempts++
            val r = Random.nextInt(1, rows - 1)
            val c = Random.nextInt(1, cols - 1)
            if (!occupied[r][c]) {
                occupied[r][c] = true
                val gColor = palette.random()
                items.add(
                    GridItem(
                        id = "gate_${r}_$c",
                        isObstacle = false,
                        r = r,
                        c = c,
                        isGate = true,
                        gateColor = gColor
                    )
                )
                gatesPlaced++
            }
        }

        // Place Conveyor Belt Tiles before backward capsule placement so we can pre-compensate
        // each capsule's arrow by (stepIdx % 4) counter-clockwise rotations!
        val conveyorTiles = mutableSetOf<Pair<Int, Int>>()
        var convAttempts = 0
        while (conveyorTiles.size < numConveyors && convAttempts < 45) {
            convAttempts++
            val r = Random.nextInt(0, rows)
            val c = Random.nextInt(0, cols)
            if (!occupied[r][c] && !conveyorTiles.contains(r to c)) {
                conveyorTiles.add(r to c)
            }
        }

        data class BackwardCandidate(val r: Int, val c: Int, val exitDir: ArrowDir, val depth: Int)

        var iceCount = 0
        val totalCapsules = orderedExtractColors.size

        // Place capsules in REVERSE solve order (from last extracted N-1 down to first extracted 0)
        for (stepIdx in totalCapsules - 1 downTo 0) {
            val capColor = orderedExtractColors[stepIdx]
            val candidates = mutableListOf<BackwardCandidate>()

            for (exitDir in ArrowDir.entries) {
                val edgeStarts = mutableListOf<Pair<Int, Int>>()
                when (exitDir) {
                    ArrowDir.UP -> for (c in 0 until cols) edgeStarts.add(0 to c)
                    ArrowDir.DOWN -> for (c in 0 until cols) edgeStarts.add((rows - 1) to c)
                    ArrowDir.LEFT -> for (r in 0 until rows) edgeStarts.add(r to 0)
                    ArrowDir.RIGHT -> for (r in 0 until rows) edgeStarts.add(r to (cols - 1))
                }

                for ((sr, sc) in edgeStarts) {
                    var cr = sr
                    var cc = sc
                    var depth = 0
                    while (cr in 0 until rows && cc in 0 until cols) {
                        val gate = items.find { it.r == cr && it.c == cc && it.isGate }
                        if (gate != null) {
                            if (gate.gateColor == capColor) {
                                // Ray passes through matching Color-Lock Gate!
                                cr -= exitDir.dy
                                cc -= exitDir.dx
                                depth++
                            } else {
                                break
                            }
                        } else if (!occupied[cr][cc]) {
                            candidates.add(BackwardCandidate(cr, cc, exitDir, depth))
                            cr -= exitDir.dy
                            cc -= exitDir.dx
                            depth++
                        } else {
                            break
                        }
                    }
                }
            }

            if (candidates.isNotEmpty()) {
                candidates.sortByDescending { it.depth }
                val poolSize = min(candidates.size, max(4, (candidates.size * 0.35f).toInt()))
                val chosen = candidates[Random.nextInt(poolSize)]
                occupied[chosen.r][chosen.c] = true

                val isConveyor = conveyorTiles.contains(chosen.r to chosen.c)
                val initialDir = if (isConveyor) {
                    rotateCCW90(chosen.exitDir, stepIdx % 4)
                } else {
                    chosen.exitDir
                }

                val isEdge = (chosen.r == 0 || chosen.r == rows - 1 || chosen.c == 0 || chosen.c == cols - 1)
                val ice = if (stepIdx >= 3 && !isEdge && !isConveyor && iceCount < numIce && Random.nextFloat() < 0.3f) {
                    iceCount++
                    2
                } else 0

                items.add(
                    GridItem(
                        id = "cap_${chosen.r}_${chosen.c}_$stepIdx",
                        isObstacle = false,
                        r = chosen.r,
                        c = chosen.c,
                        color = capColor,
                        dir = initialDir,
                        ice = ice
                    )
                )
            }
        }

        return LevelBuildResult(
            rows = rows,
            cols = cols,
            items = items,
            vials = vials,
            conveyorTiles = conveyorTiles
        )
    }
}

@Composable
fun ChromaFlowApp(context: Context) {
    val prefs = remember { context.getSharedPreferences("chromaflow_prefs", Context.MODE_PRIVATE) }
    val soundEngine = remember { ProceduralSoundEngine(context) }
    val scope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()

    var levelNum by remember { mutableIntStateOf(prefs.getInt("levelNum", 1)) }
    var coins by remember { mutableIntStateOf(prefs.getInt("coins", 150)) }
    var shards by remember { mutableIntStateOf(prefs.getInt("shards", 10)) }
    var streak by remember { mutableIntStateOf(0) }

    val todayStr = remember {
        val cal = java.util.Calendar.getInstance()
        String.format(java.util.Locale.US, "%04d-%02d-%02d",
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }

    var dailyStreak by remember { mutableIntStateOf(prefs.getInt("dailyStreak", 1)) }
    var lastDailyLoginDate by remember { mutableStateOf(prefs.getString("lastDailyLoginDate", "") ?: "") }
    var dailyClaimedToday by remember { mutableStateOf(prefs.getBoolean("dailyClaimedToday", false)) }
    var showDailyStreakModal by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (lastDailyLoginDate.isEmpty()) {
            dailyStreak = 1
            lastDailyLoginDate = todayStr
            dailyClaimedToday = false
            prefs.edit().putInt("dailyStreak", 1).putString("lastDailyLoginDate", todayStr).putBoolean("dailyClaimedToday", false).apply()
        } else if (lastDailyLoginDate != todayStr) {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            try {
                val lastD = sdf.parse(lastDailyLoginDate)
                val currD = sdf.parse(todayStr)
                if (lastD != null && currD != null) {
                    val diffDays = ((currD.time - lastD.time) / (1000 * 60 * 60 * 24)).toInt()
                    if (diffDays == 1) {
                        dailyStreak = if (dailyStreak >= 7) 7 else dailyStreak + 1
                        dailyClaimedToday = false
                    } else if (diffDays > 1) {
                        dailyStreak = 1
                        dailyClaimedToday = false
                    }
                }
            } catch (_: Exception) {
                dailyStreak = 1
                dailyClaimedToday = false
            }
            lastDailyLoginDate = todayStr
            prefs.edit().putInt("dailyStreak", dailyStreak).putString("lastDailyLoginDate", todayStr).putBoolean("dailyClaimedToday", dailyClaimedToday).apply()
        }
    }

    val dailyMultiplier = getDailyStreakMultiplier(dailyStreak)
    var currentTheme by remember {
        val saved = prefs.getString("theme", "crystal") ?: "crystal"
        mutableStateOf(GlassTheme.entries.find { it.id == saved } ?: GlassTheme.CRYSTAL)
    }

    var upCoinMult by remember { mutableIntStateOf(prefs.getInt("upCoinMult", 1)) }
    var upStreakBonus by remember { mutableIntStateOf(prefs.getInt("upStreakBonus", 0)) }
    var upBufferCapacity by remember { mutableIntStateOf(prefs.getInt("upBufferCapacity", 0)) }
    var upPrismRadius by remember { mutableIntStateOf(prefs.getInt("upPrismRadius", 1)) }
    var movesRemaining by remember { mutableIntStateOf(30) }
    var isOutOfMoves by remember { mutableStateOf(false) }

    var magnetCount by remember { mutableIntStateOf(prefs.getInt("magnet", 2)) }
    var shuffleCount by remember { mutableIntStateOf(prefs.getInt("shuffle", 2)) }
    var slotBoostCount by remember { mutableIntStateOf(prefs.getInt("slotBoost", 1)) }
    var magnetActive by remember { mutableStateOf(false) }

    var bufferCapacity by remember { mutableIntStateOf(5) }
    var activeTab by remember { mutableStateOf("PLAY") }

    var prismPassXP by remember { mutableIntStateOf(prefs.getInt("prismPassXP", 45)) }
    val claimedPassTiers = remember { mutableStateListOf<Int>() }
    var maxBufferUsedInVial by remember { mutableIntStateOf(0) }
    val dailyQuests = remember {
        mutableStateListOf(
            DailyQuestState(
                id = "quest_prism_5",
                title = "Trigger 5 Prism Merges",
                desc = "Fuse primary capsules in the Buffer Dock into secondary colors",
                progress = prefs.getInt("q_prism_prog", 0),
                target = 5,
                rewardShards = 8,
                rewardXP = 150,
                claimed = prefs.getBoolean("q_prism_claim", false)
            ),
            DailyQuestState(
                id = "quest_clean_vials_10",
                title = "Clear 10 Vials (≤3 Buffer Slots)",
                desc = "Complete potion vials without filling more than 3 Buffer slots",
                progress = prefs.getInt("q_vial_prog", 0),
                target = 10,
                rewardShards = 12,
                rewardXP = 200,
                claimed = prefs.getBoolean("q_vial_claim", false)
            ),
            DailyQuestState(
                id = "quest_extract_20",
                title = "Extract 20 Capsules from Grid",
                desc = "Unblock and launch Liquid Glass capsules along clear paths",
                progress = prefs.getInt("q_ext_prog", 0),
                target = 20,
                rewardShards = 6,
                rewardXP = 100,
                claimed = prefs.getBoolean("q_ext_claim", false)
            )
        )
    }
    val prismPassTiers = remember {
        listOf(
            PrismPassTier(1, 100, 5, 50, "Alchemist Initiate Cache"),
            PrismPassTier(2, 250, 8, 120, "Refractive Vial Bundle"),
            PrismPassTier(3, 450, 12, 180, "Chromatic Prism Core"),
            PrismPassTier(4, 700, 18, 260, "Aether Sovereign Vault"),
            PrismPassTier(5, 1000, 30, 500, "Grand Alchemist Relic")
        )
    }

    var rows by remember { mutableIntStateOf(6) }
    var cols by remember { mutableIntStateOf(6) }
    val gridItems = remember { mutableStateListOf<GridItem>() }
    val conveyorTiles = remember { mutableStateListOf<Pair<Int, Int>>() }
    val vials = remember { mutableStateListOf<TargetVial>() }
    val bufferSlots = remember { mutableStateListOf<BufferItem>() }
    val flyingCapsules = remember { mutableStateListOf<FlyingCapsule>() }
    val particles = remember { mutableStateListOf<VisualParticle>() }
    val shakeMap = remember { mutableStateMapOf<String, Float>() }
    val redRimMap = remember { mutableStateMapOf<String, Float>() }
    val capsuleSloshMap = remember { mutableStateMapOf<String, CapsuleSloshState>() }
    var boardSloshImpulse by remember { mutableFloatStateOf(0f) }
    val goldenArcs = remember { mutableStateListOf<GoldenArc>() }

    var isLevelWon by remember { mutableStateOf(false) }
    var isBufferOverflow by remember { mutableStateOf(false) }
    var isWatchingAd by remember { mutableStateOf(false) }
    var adProgress by remember { mutableFloatStateOf(0f) }

    var boardBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }

    // 60FPS Particle Pool Cap (Max 60 Concurrent Particles)
    val maxParticles = 60
    fun pushParticle(p: VisualParticle) {
        if (particles.size >= maxParticles) {
            val evictIdx = particles.indexOfFirst { it.text == null }
            if (evictIdx != -1) particles.removeAt(evictIdx)
            else particles.removeAt(0)
        }
        particles.add(p)
    }

    fun stepConveyorBelts() {
        for (idx in gridItems.indices) {
            val it = gridItems[idx]
            if (!it.isObstacle && conveyorTiles.contains(it.r to it.c)) {
                gridItems[idx] = it.copy(dir = it.dir.rotateCW90())
                shakeMap[it.id] = 0.14f
            }
        }
    }

    fun incrementQuest(questId: String, amount: Int = 1) {
        val idx = dailyQuests.indexOfFirst { it.id == questId }
        if (idx != -1) {
            val q = dailyQuests[idx]
            if (!q.claimed) {
                dailyQuests[idx] = q.copy(progress = min(q.target, q.progress + amount))
            }
        }
    }

    fun saveState() {
        prefs.edit()
            .putInt("levelNum", levelNum)
            .putInt("coins", coins)
            .putInt("shards", shards)
            .putString("theme", currentTheme.id)
            .putInt("upCoinMult", upCoinMult)
            .putInt("upStreakBonus", upStreakBonus)
            .putInt("upBufferCapacity", upBufferCapacity)
            .putInt("upPrismRadius", upPrismRadius)
            .putInt("magnet", magnetCount)
            .putInt("shuffle", shuffleCount)
            .putInt("slotBoost", slotBoostCount)
            .putInt("prismPassXP", prismPassXP)
            .putInt("q_prism_prog", dailyQuests.getOrNull(0)?.progress ?: 0)
            .putBoolean("q_prism_claim", dailyQuests.getOrNull(0)?.claimed ?: false)
            .putInt("q_vial_prog", dailyQuests.getOrNull(1)?.progress ?: 0)
            .putBoolean("q_vial_claim", dailyQuests.getOrNull(1)?.claimed ?: false)
            .putInt("q_ext_prog", dailyQuests.getOrNull(2)?.progress ?: 0)
            .putBoolean("q_ext_claim", dailyQuests.getOrNull(2)?.claimed ?: false)
            .putInt("dailyStreak", dailyStreak)
            .putString("lastDailyLoginDate", lastDailyLoginDate)
            .putBoolean("dailyClaimedToday", dailyClaimedToday)
            .apply()
    }

    fun loadLevel(num: Int) {
        levelNum = num
        streak = 0
        isLevelWon = false
        isBufferOverflow = false
        isOutOfMoves = false
        magnetActive = false
        bufferCapacity = 5 + upBufferCapacity
        movesRemaining = 30
        bufferSlots.clear()
        flyingCapsules.clear()
        particles.clear()
        shakeMap.clear()
        redRimMap.clear()
        capsuleSloshMap.clear()
        boardSloshImpulse = 0f
        goldenArcs.clear()

        val built = SolvableLevelBuilder.build(num)
        rows = built.rows
        cols = built.cols
        gridItems.clear()
        gridItems.addAll(built.items)
        conveyorTiles.clear()
        conveyorTiles.addAll(built.conveyorTiles)
        vials.clear()
        vials.addAll(built.vials)
        saveState()
    }

    LaunchedEffect(Unit) {
        loadLevel(levelNum)
    }

    // Fixed Delta-Time Game Loop (60Hz / 90Hz / 120Hz independent)
    var sloshPhase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var lastNanos = System.nanoTime()
        while (true) {
            withFrameNanos { now ->
                val rawDt = (now - lastNanos) / 1_000_000_000f
                lastNanos = now
                val dt = rawDt.coerceIn(0.001f, 0.05f)
                sloshPhase += dt * (3.8f + boardSloshImpulse * 4.2f)
                boardSloshImpulse = (boardSloshImpulse - dt * 2.2f).coerceAtLeast(0f)

                // Update per-capsule tap sloshing physics
                val sloshKeys = capsuleSloshMap.keys.toList()
                for (k in sloshKeys) {
                    val cur = capsuleSloshMap[k] ?: continue
                    val nextAmp = cur.amp - dt * 1.65f
                    if (nextAmp <= 0.01f) {
                        capsuleSloshMap.remove(k)
                    } else {
                        capsuleSloshMap[k] = cur.copy(
                            amp = nextAmp,
                            phase = cur.phase + dt * (9.5f + nextAmp * 4.0f)
                        )
                    }
                }

                // Update shake timers
                val shakeKeys = shakeMap.keys.toList()
                for (k in shakeKeys) {
                    val rem = (shakeMap[k] ?: 0f) - dt
                    if (rem <= 0f) shakeMap.remove(k) else shakeMap[k] = rem
                }

                // Update red rim flash timers
                val rimKeys = redRimMap.keys.toList()
                for (k in rimKeys) {
                    val rem = (redRimMap[k] ?: 0f) - dt
                    if (rem <= 0f) redRimMap.remove(k) else redRimMap[k] = rem
                }

                // Update golden arcs
                for (i in goldenArcs.size - 1 downTo 0) {
                    val arc = goldenArcs[i]
                    val nextLife = arc.life - dt
                    if (nextLife <= 0f) goldenArcs.removeAt(i)
                    else goldenArcs[i] = arc.copy(life = nextLife)
                }

                // Update flying capsules
                for (i in flyingCapsules.size - 1 downTo 0) {
                    val fc = flyingCapsules[i]
                    val nextP = fc.progress + dt * 3.6f
                    if (nextP >= 1f) {
                        flyingCapsules.removeAt(i)
                    } else {
                        flyingCapsules[i] = fc.copy(progress = nextP)
                    }
                }

                // Update particles
                for (i in particles.size - 1 downTo 0) {
                    val p = particles[i]
                    val nextLife = p.life - dt
                    if (nextLife <= 0f) {
                        particles.removeAt(i)
                    } else {
                        val nx = if (p.isRing) p.x + dt * 190f else p.x + p.vx * dt
                        val ny = if (p.isRing) p.y else p.y + p.vy * dt
                        val nvy = if (p.isRing || p.text != null) p.vy else p.vy + 140f * dt
                        particles[i] = p.copy(x = nx, y = ny, vy = nvy, life = nextLife)
                    }
                }
            }
        }
    }

    fun spawnBurst(cx: Float, cy: Float, color: Color, count: Int = 12) {
        val fx = floor(cx)
        val fy = floor(cy)
        repeat(count) {
            val ang = Random.nextFloat() * 2f * PI.toFloat()
            val spd = Random.nextFloat() * 140f + 50f
            pushParticle(
                VisualParticle(
                    x = fx,
                    y = fy,
                    vx = cos(ang) * spd,
                    vy = sin(ang) * spd,
                    radius = floor(Random.nextFloat() * 3f + 3f),
                    color = color,
                    life = 0.5f,
                    maxLife = 0.5f
                )
            )
        }
    }

    fun triggerBoardSlosh(originR: Float, originC: Float, strength: Float = 1.0f) {
        boardSloshImpulse = (boardSloshImpulse + 1.35f * strength).coerceAtMost(2.6f)
        gridItems.forEach { cell ->
            if (!cell.isObstacle) {
                val dist = hypot(cell.r - originR, cell.c - originC)
                val delayPhase = dist * 0.65f
                val prev = capsuleSloshMap[cell.id]
                val nextAmp = ((prev?.amp ?: 0f) + (1.45f / (1f + dist * 0.22f)) * strength).coerceAtMost(2.5f)
                val nextPhase = (prev?.phase ?: 0f) + delayPhase
                capsuleSloshMap[cell.id] = CapsuleSloshState(amp = nextAmp, phase = nextPhase)
            }
        }
    }

    fun spawnChromaticAberrationBurst(cx: Float, cy: Float, baseColor: Color, count: Int = 24) {
        val fx = floor(cx)
        val fy = floor(cy)
        // Chromatic Aberration RGB-Split Shockwave Ring
        pushParticle(
            VisualParticle(
                x = 14f,
                y = fy,
                vx = fx,
                vy = fy,
                radius = 14f,
                color = baseColor,
                life = 0.6f,
                maxLife = 0.6f,
                isRing = true,
                isChromatic = true,
                rgbSplit = 12f
            )
        )
        // Chromatic Aberration RGB-Split Shards (capped for 60fps mobile budget)
        for (i in 0 until count) {
            val ang = (2f * PI.toFloat() * i) / count + (Random.nextFloat() - 0.5f) * 0.2f
            val spd = Random.nextFloat() * 250f + 105f
            pushParticle(
                VisualParticle(
                    x = fx,
                    y = fy,
                    vx = cos(ang) * spd,
                    vy = sin(ang) * spd - 40f,
                    radius = floor(Random.nextFloat() * 6f + 5f),
                    color = baseColor,
                    life = 0.72f,
                    maxLife = 0.72f,
                    isChromatic = true,
                    rgbSplit = floor(Random.nextFloat() * 8f + 6f),
                    splitAngle = ang
                )
            )
        }
    }

    fun spawnText(text: String, cx: Float, cy: Float, color: Color) {
        pushParticle(
            VisualParticle(
                x = floor(cx),
                y = floor(cy),
                vx = 0f,
                vy = -70f,
                radius = 0f,
                color = color,
                life = 0.9f,
                maxLife = 0.9f,
                text = text
            )
        )
    }

    fun checkWinCondition() {
        val remainingCapsules = gridItems.count { !it.isObstacle && !it.isGate }
        val allVialsComplete = vials.isNotEmpty() && vials.all { it.completed }
        val noFlying = flyingCapsules.isEmpty()

        // Win state triggers the moment both the grid is completely empty AND all Target Potion Vials are fully filled/cleared!
        // Also if all capsules are cleared from the grid, auto-complete any remaining vial targets!
        if ((allVialsComplete && remainingCapsules == 0 && noFlying) || (remainingCapsules == 0 && noFlying && bufferSlots.isEmpty()) || (allVialsComplete && remainingCapsules == 0)) {
            vials.forEachIndexed { idx, v ->
                if (!v.completed) {
                    vials[idx] = v.copy(completed = true, current = v.target)
                }
            }
            isLevelWon = true
            soundEngine.playVictory()
            val baseWinCoins = 50 + levelNum * 15 + if (levelNum % 5 == 0) 75 else 0
            val winCoins = (baseWinCoins * upCoinMult * dailyMultiplier).toInt()
            coins += winCoins
            shards += if (levelNum % 5 == 0) 6 else 2
            saveState()
        }
    }

    fun pourIntoVial(matchIdx: Int, pouredColor: CapsuleColor) {
        val v = vials[matchIdx]
        val nextCur = v.current + 1
        val done = nextCur >= v.target
        val nextFilled = if (v.isGiantPrism) v.filledSegments + pouredColor else v.filledSegments
        val nextRemaining = if (v.isGiantPrism) {
            val mut = v.remainingSecondary.toMutableList()
            mut.remove(pouredColor)
            mut
        } else v.remainingSecondary

        vials[matchIdx] = v.copy(
            current = nextCur,
            completed = done,
            filledSegments = nextFilled,
            remainingSecondary = nextRemaining
        )
        soundEngine.playLiquidPour(nextCur)
        if (done) {
            soundEngine.playVialComplete()
            streak++
            if (maxBufferUsedInVial <= 3 && bufferSlots.size <= 3) {
                incrementQuest("quest_clean_vials_10", 1)
            }
            maxBufferUsedInVial = bufferSlots.size
            prismPassXP += 25
            val baseReward = if (v.isGiantPrism) 75 else 25
            val bonus = ((baseReward + streak * 5) * upCoinMult * dailyMultiplier).toInt()
            coins += bonus
            saveState()
            spawnChromaticAberrationBurst(boardBounds.center.x, boardBounds.top + 10f, pouredColor.lightHex, 24)
            spawnText(
                if (v.isGiantPrism) "BOSS FLASK COMPLETE! +$bonus (${"%.1f".format(dailyMultiplier)}x)" else "+$bonus COINS! (${"%.1f".format(dailyMultiplier)}x)",
                boardBounds.center.x,
                boardBounds.top + 40f,
                Color(0xFFFFD700)
            )
        }
    }

    fun checkBufferAutoFillToVials() {
        val activeVials = vials.filter { !it.completed }.take(3)
        for (bIdx in bufferSlots.indices) {
            val bItem = bufferSlots.getOrNull(bIdx) ?: continue
            val matchIdx = vials.indexOfFirst { !it.completed && it in activeVials && it.acceptsColor(bItem.color) }
            if (matchIdx != -1) {
                bufferSlots.removeAt(bIdx)
                pourIntoVial(matchIdx, bItem.color)
                checkWinCondition()
                return
            }
        }
    }

    fun checkPrismMergeInBuffer() {
        for (i in 0 until bufferSlots.size) {
            for (j in i + 1 until bufferSlots.size) {
                val c1 = bufferSlots[i].color
                val c2 = bufferSlots[j].color
                val mergedColor = CapsuleColor.prismMerge(c1, c2)
                if (mergedColor != null) {
                    soundEngine.playPrismMerge()
                    incrementQuest("quest_prism_5", 1)
                    prismPassXP += 20
                    saveState()
                    goldenArcs.add(GoldenArc(i, j, 0.55f))
                    spawnText("PRISM MERGE! -> ${mergedColor.label.uppercase()}", boardBounds.center.x, boardBounds.bottom - 30f, Color(0xFFFFD700))

                    scope.launch {
                        delay(380)
                        if (j < bufferSlots.size && i < bufferSlots.size) {
                            val id1 = bufferSlots[i].id
                            val id2 = bufferSlots[j].id
                            bufferSlots.removeAll { it.id == id1 || it.id == id2 }
                            bufferSlots.add(BufferItem("merged_${System.nanoTime()}", mergedColor))

                            // Thaw frozen ice blocks via shockwave
                            var thawed = 0
                            for (gIdx in gridItems.indices) {
                                val g = gridItems[gIdx]
                                if (!g.isObstacle && g.ice > 0) {
                                    gridItems[gIdx] = g.copy(ice = 0)
                                    thawed++
                                    if (thawed >= upPrismRadius) break
                                }
                            }
                            if (thawed > 0) {
                                spawnText("ICE SHATTERED!", boardBounds.center.x, boardBounds.center.y, Color(0xFF00E5FF))
                            }
                            checkBufferAutoFillToVials()
                        }
                    }
                    return
                }
            }
        }

        if (bufferSlots.size >= bufferCapacity && !isLevelWon) {
            isBufferOverflow = true
            soundEngine.playBlocked()
        }
    }

    fun onCapsuleTapped(item: GridItem, cellCenterX: Float, cellCenterY: Float) {
        if (isLevelWon || isBufferOverflow) return
        triggerBoardSlosh(item.r.toFloat(), item.c.toFloat(), 1.15f)

        if (item.isObstacle || item.isGate) {
            soundEngine.playBlocked()
            shakeMap[item.id] = 0.22f
            redRimMap[item.id] = 0.3f
            return
        }

        if (magnetActive) {
            magnetActive = false
            soundEngine.playTapGlass(1.3f)
            gridItems.removeAll { it.id == item.id }
        } else {
            if (item.ice > 0) {
                val idx = gridItems.indexOfFirst { it.id == item.id }
                if (idx != -1) {
                    val nextIce = item.ice - 1
                    gridItems[idx] = item.copy(ice = nextIce)
                    soundEngine.playTapGlass(1.4f)
                    shakeMap[item.id] = 0.2f
                    spawnBurst(cellCenterX, cellCenterY, Color(0xFF00E5FF), 12)
                }
                return
            }

            // Raycast in arrow direction
            var currR = item.r + item.dir.dy
            var currC = item.c + item.dir.dx
            var blocker: GridItem? = null
            while (currR in 0 until rows && currC in 0 until cols) {
                val found = gridItems.find { it.r == currR && it.c == currC }
                if (found != null) {
                    if (found.isGate && found.gateColor == item.color) {
                        // Pass through matching Color-Lock Gate!
                    } else {
                        blocker = found
                        break
                    }
                }
                currR += item.dir.dy
                currC += item.dir.dx
            }

            if (blocker != null) {
                soundEngine.playBlocked()
                shakeMap[item.id] = 0.22f
                shakeMap[blocker.id] = 0.22f
                redRimMap[blocker.id] = 0.35f
                spawnBurst(cellCenterX, cellCenterY, Color(0xFFFF1744), 10)
                return
            }

            // Clear path!
            soundEngine.playTapGlass(1.0f)
            gridItems.removeAll { it.id == item.id }
            movesRemaining = max(0, movesRemaining - 1)
            if (movesRemaining <= 0 && !isLevelWon && gridItems.any { !it.isObstacle && !it.isGate }) {
                scope.launch {
                    delay(400)
                    if (!isLevelWon && gridItems.any { !it.isObstacle && !it.isGate }) {
                        isOutOfMoves = true
                        soundEngine.playBlocked()
                    }
                }
            }
        }

        // Rotate all remaining capsules on Conveyor Belt Tiles by 90° clockwise after every move!
        stepConveyorBelts()
        incrementQuest("quest_extract_20", 1)
        prismPassXP += 8

        spawnBurst(cellCenterX, cellCenterY, item.color.lightHex, 12)

        val activeVials = vials.filter { !it.completed }.take(3)
        val matchIdx = vials.indexOfFirst { !it.completed && it in activeVials && it.acceptsColor(item.color) }

        if (matchIdx != -1) {
            flyingCapsules.add(
                FlyingCapsule(
                    id = item.id,
                    color = item.color,
                    startX = floor(cellCenterX),
                    startY = floor(cellCenterY),
                    targetX = floor(boardBounds.center.x),
                    targetY = floor(boardBounds.top - 40f)
                )
            )
            pourIntoVial(matchIdx, item.color)
            checkBufferAutoFillToVials()
            checkWinCondition()
        } else {
            if (bufferSlots.size < bufferCapacity) {
                flyingCapsules.add(
                    FlyingCapsule(
                        id = item.id,
                        color = item.color,
                        startX = floor(cellCenterX),
                        startY = floor(cellCenterY),
                        targetX = floor(boardBounds.center.x),
                        targetY = floor(boardBounds.bottom + 40f)
                    )
                )
                bufferSlots.add(BufferItem(item.id, item.color))
                if (bufferSlots.size > maxBufferUsedInVial) {
                    maxBufferUsedInVial = bufferSlots.size
                }
                checkPrismMergeInBuffer()
            } else {
                isBufferOverflow = true
                soundEngine.playBlocked()
            }
        }
    }

    // --- UI & CANVAS RENDER ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(currentTheme.bgTop, currentTheme.bgBottom)
                )
            )
            .systemBarsPadding()
    ) {
        // Ambient Bioluminescent Floating Orbs Background Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val o1x = w * (0.25f + 0.15f * sin(sloshPhase * 0.3f))
            val o1y = h * (0.25f + 0.1f * cos(sloshPhase * 0.25f))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(currentTheme.accent.copy(alpha = 0.28f), Color.Transparent),
                    center = Offset(o1x, o1y),
                    radius = w * 0.45f
                ),
                radius = w * 0.45f,
                center = Offset(o1x, o1y)
            )

            val o2x = w * (0.75f + 0.15f * cos(sloshPhase * 0.22f))
            val o2y = h * (0.65f + 0.12f * sin(sloshPhase * 0.28f))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(currentTheme.secondary.copy(alpha = 0.26f), Color.Transparent),
                    center = Offset(o2x, o2y),
                    radius = w * 0.5f
                ),
                radius = w * 0.5f,
                center = Offset(o2x, o2y)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. TOP LIQUID GLASS HUD BAR
            LiquidGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(currentTheme.accent.copy(alpha = 0.25f))
                                .border(1.dp, currentTheme.accent.copy(alpha = 0.6f), CircleShape)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                if (levelNum % 5 == 0) "👑 BOSS $levelNum" else "LVL $levelNum",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFFFFD700).copy(alpha = 0.22f))
                                .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.7f), CircleShape)
                                .clickable {
                                    val nextBoss = if (levelNum % 5 == 0) levelNum else ((levelNum / 5) + 1) * 5
                                    loadLevel(nextBoss)
                                    soundEngine.playTapGlass(1.25f)
                                }
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                        ) {
                            Text("👑 Boss", color = Color(0xFFFFD700), fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                        }

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFFFF7700).copy(alpha = 0.28f))
                                .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.6f), CircleShape)
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                        ) {
                            Text("🔥 x${streak + 1 + upStreakBonus}", color = Color(0xFFFFD700), fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                        }

                        // Daily Streak Bonus UI Element in Top HUD
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (!dailyClaimedToday) Brush.horizontalGradient(
                                        listOf(Color(0xFFFFD700).copy(alpha = 0.35f), Color(0xFFFF7700).copy(alpha = 0.35f))
                                    ) else SolidColor(Color(0xFFFFD700).copy(alpha = 0.22f))
                                )
                                .border(
                                    1.dp,
                                    if (!dailyClaimedToday) Color(0xFFFFD700) else Color(0xFFFFD700).copy(alpha = 0.65f),
                                    CircleShape
                                )
                                .clickable {
                                    showDailyStreakModal = true
                                    soundEngine.playTapGlass(1.15f)
                                }
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(if (!dailyClaimedToday) "✨" else "📅", fontSize = 11.sp)
                                Text(
                                    "Day $dailyStreak (${"%.1f".format(dailyMultiplier)}x)",
                                    color = Color(0xFFFFE66D),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 10.sp
                                )
                                if (!dailyClaimedToday) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF00FF88))
                                    )
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f))
                                .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.45f), CircleShape)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("🎯 $movesRemaining", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f))
                                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("🪙 $coins", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f))
                                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("💎 $shards", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.18f))
                                .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                .clickable { soundEngine.soundEnabled = !soundEngine.soundEnabled },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (soundEngine.soundEnabled) "🔊" else "🔇", fontSize = 14.sp)
                        }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.18f))
                                .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                .clickable { activeTab = if (activeTab == "THEMES") "PLAY" else "THEMES" },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🎨", fontSize = 14.sp)
                        }
                    }
                }
            }

            // 2. TARGET POTION VIALS TRAY (3 Active Sloshing Vials)
            LiquidGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(82.dp)
                    .padding(vertical = 3.dp)
            ) {
                val activeVials = vials.filter { !it.completed }.take(3)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    activeVials.forEach { vial ->
                        Box(
                            modifier = Modifier
                                .weight(if (vial.isGiantPrism) 1.55f else 1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Black.copy(alpha = 0.38f))
                                .border(
                                    width = if (vial.isGiantPrism) 2.dp else 1.5.dp,
                                    color = if (vial.isGiantPrism) Color(0xFFFFD700) else vial.color.lightHex.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                        ) {
                            val fillRatio = (vial.current.toFloat() / vial.target.toFloat()).coerceIn(0f, 1f)
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                if (vial.isGiantPrism) {
                                    // Draw 6 stacked mixed secondary color segments
                                    val segH = size.height / 6f
                                    vial.filledSegments.forEachIndexed { idx, segColor ->
                                        val topY = size.height - (idx + 1) * segH
                                        drawRect(
                                            brush = Brush.horizontalGradient(
                                                listOf(segColor.lightHex, segColor.hex, segColor.darkHex)
                                            ),
                                            topLeft = Offset(0f, floor(topY)),
                                            size = Size(size.width, ceil(segH))
                                        )
                                    }
                                    for (seg in 1..5) {
                                        val sy = floor(size.height * (1f - seg / 6f))
                                        drawLine(
                                            color = Color.White.copy(alpha = 0.45f),
                                            start = Offset(size.width - 10.dp.toPx(), sy),
                                            end = Offset(size.width - 2.dp.toPx(), sy),
                                            strokeWidth = 1.5.dp.toPx()
                                        )
                                    }
                                } else {
                                    val fh = size.height * fillRatio
                                    if (fh > 0f) {
                                        val topY = floor(size.height - fh)
                                        val path = Path().apply {
                                            moveTo(0f, size.height)
                                            lineTo(0f, topY)
                                            val wave = floor(sin(sloshPhase) * 4.dp.toPx())
                                            cubicTo(
                                                floor(size.width * 0.33f), topY - wave,
                                                floor(size.width * 0.66f), topY + wave,
                                                size.width, topY
                                            )
                                            lineTo(size.width, size.height)
                                            close()
                                        }
                                        drawPath(
                                            path = path,
                                            brush = Brush.verticalGradient(
                                                listOf(vial.color.lightHex, vial.color.hex, vial.color.darkHex),
                                                startY = topY,
                                                endY = size.height
                                            )
                                        )
                                    }
                                    // 3 Segment Graduation Ticks
                                    for (seg in 1..2) {
                                        val sy = floor(size.height * (1f - seg / 3f))
                                        drawLine(
                                            color = Color.White.copy(alpha = 0.4f),
                                            start = Offset(size.width - 10.dp.toPx(), sy),
                                            end = Offset(size.width - 2.dp.toPx(), sy),
                                            strokeWidth = 2.dp.toPx()
                                        )
                                    }
                                }
                            }
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (vial.isGiantPrism) "👑 GIANT PRISM FLASK" else vial.color.label,
                                    color = if (vial.isGiantPrism) Color(0xFFFFD700) else Color.White,
                                    fontSize = if (vial.isGiantPrism) 9.sp else 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "${vial.current}/${vial.target}",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black
                                )
                                if (vial.isGiantPrism && vial.remainingSecondary.isNotEmpty()) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        vial.remainingSecondary.forEach { reqCol ->
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(reqCol.hex)
                                                    .border(0.8.dp, Color.White, CircleShape)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. INTERACTIVE HIGH-DPI GAME BOARD CANVAS
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(rows, cols, magnetActive, isLevelWon, isBufferOverflow) {
                            detectTapGestures { offset ->
                                val cellW = boardBounds.width / cols
                                val cellH = boardBounds.height / rows
                                if (cellW > 0f && boardBounds.contains(offset)) {
                                    val c = ((offset.x - boardBounds.left) / cellW).toInt().coerceIn(0, cols - 1)
                                    val r = ((offset.y - boardBounds.top) / cellH).toInt().coerceIn(0, rows - 1)
                                    val tapped = gridItems.find { it.r == r && it.c == c }
                                    if (tapped != null) {
                                        val cx = boardBounds.left + c * cellW + cellW / 2f
                                        val cy = boardBounds.top + r * cellH + cellH / 2f
                                        onCapsuleTapped(tapped, cx, cy)
                                    } else {
                                        triggerBoardSlosh(r.toFloat(), c.toFloat(), 0.9f)
                                    }
                                } else {
                                    triggerBoardSlosh(rows / 2f, cols / 2f, 0.7f)
                                }
                            }
                        }
                ) {
                    val pad = floor(12.dp.toPx())
                    val availW = size.width - pad * 2f
                    val availH = size.height - pad * 2f
                    val cellSize = floor(min(availW / cols, availH / rows))
                    val bW = cellSize * cols
                    val bH = cellSize * rows
                    val startX = floor((size.width - bW) / 2f)
                    val startY = floor((size.height - bH) / 2f)
                    boardBounds = androidx.compose.ui.geometry.Rect(startX, startY, startX + bW, startY + bH)

                    // Outer Refractive Glass Board Frame
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.07f),
                        topLeft = Offset(startX - 8.dp.toPx(), startY - 8.dp.toPx()),
                        size = Size(bW + 16.dp.toPx(), bH + 16.dp.toPx()),
                        cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx())
                    )
                    drawRoundRect(
                        color = if (levelNum % 5 == 0) Color(0xFFFFD700).copy(alpha = 0.65f) else Color.White.copy(alpha = 0.35f),
                        topLeft = Offset(startX - 8.dp.toPx(), startY - 8.dp.toPx()),
                        size = Size(bW + 16.dp.toPx(), bH + 16.dp.toPx()),
                        cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Grid Slots & Conveyor Belt Tiles (Rotate Arrows 90° CW After Every Move)
                    for (r in 0 until rows) {
                        for (c in 0 until cols) {
                            val gx = floor(startX + c * cellSize)
                            val gy = floor(startY + r * cellSize)
                            val isConveyor = conveyorTiles.contains(r to c)
                            if (isConveyor) {
                                drawRoundRect(
                                    color = Color(0xFF00E5FF).copy(alpha = 0.11f),
                                    topLeft = Offset(gx + 2.dp.toPx(), gy + 2.dp.toPx()),
                                    size = Size(cellSize - 4.dp.toPx(), cellSize - 4.dp.toPx()),
                                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                                )
                                drawRoundRect(
                                    color = Color(0xFF00E5FF).copy(alpha = 0.65f),
                                    topLeft = Offset(gx + 2.dp.toPx(), gy + 2.dp.toPx()),
                                    size = Size(cellSize - 4.dp.toPx(), cellSize - 4.dp.toPx()),
                                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                                    style = Stroke(
                                        width = 1.6.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f), 0f)
                                    )
                                )
                            } else {
                                drawRoundRect(
                                    color = Color.White.copy(alpha = 0.06f),
                                    topLeft = Offset(gx + 3.dp.toPx(), gy + 3.dp.toPx()),
                                    size = Size(cellSize - 6.dp.toPx(), cellSize - 6.dp.toPx()),
                                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                                    style = Stroke(width = 1.dp.toPx())
                                )
                            }
                        }
                    }

                    // Draw Capsules and Obsidian Blocks (with floor() coordinate snapping)
                    gridItems.forEach { item ->
                        val shakeTimer = shakeMap[item.id] ?: 0f
                        val shakeOffset = if (shakeTimer > 0f) floor(sin(shakeTimer * 75f) * 7.dp.toPx()) else 0f
                        val padCell = floor(3.5.dp.toPx())
                        val cx = floor(startX + item.c * cellSize + padCell + shakeOffset)
                        val cy = floor(startY + item.r * cellSize + padCell)
                        val cSize = floor(cellSize - padCell * 2f)
                        val rad = floor(cSize * 0.28f)
                        val onConveyor = conveyorTiles.contains(item.r to item.c)

                        if (item.isObstacle) {
                            drawRoundRect(
                                brush = Brush.linearGradient(
                                    listOf(Color(0xFF3B475E), Color(0xFF1A202C)),
                                    start = Offset(cx, cy),
                                    end = Offset(cx + cSize, cy + cSize)
                                ),
                                topLeft = Offset(cx, cy),
                                size = Size(cSize, cSize),
                                cornerRadius = CornerRadius(rad, rad)
                            )
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.35f),
                                topLeft = Offset(cx, cy),
                                size = Size(cSize, cSize),
                                cornerRadius = CornerRadius(rad, rad),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        } else if (item.isGate) {
                            val gateCol = item.gateColor?.hex ?: Color(0xFF00E5FF)
                            drawRoundRect(
                                color = Color(0xFF080E1A).copy(alpha = 0.85f),
                                topLeft = Offset(cx, cy),
                                size = Size(cSize, cSize),
                                cornerRadius = CornerRadius(rad, rad)
                            )
                            drawRoundRect(
                                color = gateCol,
                                topLeft = Offset(cx, cy),
                                size = Size(cSize, cSize),
                                cornerRadius = CornerRadius(rad, rad),
                                style = Stroke(width = 2.5.dp.toPx())
                            )
                            drawCircle(
                                color = item.gateColor?.lightHex ?: Color.White,
                                radius = cSize * 0.28f,
                                center = Offset(cx + cSize / 2f, cy + cSize / 2f),
                                style = Stroke(width = 1.8.dp.toPx())
                            )
                        } else {
                            // Capsule Glass Shell
                            val clipPath = Path().apply {
                                addRoundRect(
                                    androidx.compose.ui.geometry.RoundRect(
                                        left = cx,
                                        top = cy,
                                        right = cx + cSize,
                                        bottom = cy + cSize,
                                        cornerRadius = CornerRadius(rad, rad)
                                    )
                                )
                            }
                            clipPath(clipPath) {
                                drawRect(
                                    color = item.color.darkHex.copy(alpha = 0.42f),
                                    topLeft = Offset(cx, cy),
                                    size = Size(cSize, cSize)
                                )
                                val capSlosh = capsuleSloshMap[item.id]
                                val extraAmp = capSlosh?.amp ?: 0f
                                val localPhase = (capSlosh?.phase ?: 0f) + sloshPhase + (item.r * 0.9f + item.c * 1.1f)
                                val waveAmp = (2.8f + extraAmp * 5.5f + boardSloshImpulse * 2.4f).dp.toPx()
                                val wave = sin(localPhase) * waveAmp
                                val secondaryWave = cos(localPhase * 1.6f) * (waveAmp * 0.65f)
                                val topLiquid = cy + cSize * (0.22f - min(0.08f, extraAmp * 0.03f))

                                // Secondary caustic back-wave crest
                                val backPath = Path().apply {
                                    moveTo(cx, cy + cSize)
                                    lineTo(cx, topLiquid - wave * 0.8f)
                                    cubicTo(
                                        cx + cSize * 0.33f, topLiquid + secondaryWave,
                                        cx + cSize * 0.66f, topLiquid - secondaryWave,
                                        cx + cSize, topLiquid + wave * 0.8f
                                    )
                                    lineTo(cx + cSize, cy + cSize)
                                    close()
                                }
                                drawPath(
                                    path = backPath,
                                    color = item.color.lightHex.copy(alpha = 0.48f)
                                )

                                // Primary sloshing liquid body
                                val lPath = Path().apply {
                                    moveTo(cx, cy + cSize)
                                    lineTo(cx, topLiquid + wave)
                                    cubicTo(
                                        cx + cSize * 0.35f, topLiquid - wave - secondaryWave * 0.5f,
                                        cx + cSize * 0.65f, topLiquid + wave + secondaryWave * 0.5f,
                                        cx + cSize, topLiquid - wave
                                    )
                                    lineTo(cx + cSize, cy + cSize)
                                    close()
                                }
                                drawPath(
                                    path = lPath,
                                    brush = Brush.verticalGradient(
                                        listOf(item.color.lightHex, item.color.hex, item.color.darkHex),
                                        startY = cy,
                                        endY = cy + cSize
                                    )
                                )

                                // Glowing white meniscus crest
                                val crestPath = Path().apply {
                                    moveTo(cx, topLiquid + wave)
                                    cubicTo(
                                        cx + cSize * 0.35f, topLiquid - wave - secondaryWave * 0.5f,
                                        cx + cSize * 0.65f, topLiquid + wave + secondaryWave * 0.5f,
                                        cx + cSize, topLiquid - wave
                                    )
                                }
                                drawPath(
                                    path = crestPath,
                                    color = Color.White.copy(alpha = 0.88f),
                                    style = Stroke(width = (1.5f + extraAmp * 0.6f).dp.toPx())
                                )

                                // Effervescent internal micro-bubbles triggered during tap sloshing
                                val bubbleCount = if (extraAmp > 0.2f) 3 else 1
                                for (b in 0 until bubbleCount) {
                                    val bx = cx + cSize * (0.25f + 0.25f * b) + sin(localPhase + b * 2.1f) * 3.5.dp.toPx()
                                    val by = cy + cSize * 0.82f - ((localPhase * 10f + b * 14f) % (cSize * 0.52f))
                                    val br = (1.4f + (b % 2) * 0.9f + if (extraAmp > 0.4f) 0.7f else 0f).dp.toPx()
                                    drawCircle(
                                        color = Color.White.copy(alpha = 0.6f),
                                        radius = br,
                                        center = Offset(bx, by)
                                    )
                                }
                            }

                            // Specular White / Conveyor Cyan Glass Rim
                            drawRoundRect(
                                color = if (onConveyor) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.65f),
                                topLeft = Offset(cx, cy),
                                size = Size(cSize, cSize),
                                cornerRadius = CornerRadius(rad, rad),
                                style = Stroke(width = if (onConveyor) 2.2.dp.toPx() else 1.8.dp.toPx())
                            )

                            if (onConveyor) {
                                // Corner golden rotation indicator dot on Conveyor capsules
                                drawCircle(
                                    color = Color(0xFFFFD700),
                                    radius = 3.5.dp.toPx(),
                                    center = Offset(cx + cSize - 6.dp.toPx(), cy + 6.dp.toPx())
                                )
                            }

                            // Glowing White Specular Chevron Arrow
                            val center = Offset(cx + cSize / 2f, cy + cSize / 2f)
                            rotate(degrees = item.dir.angleDeg, pivot = center) {
                                val aw = cSize * 0.26f
                                val ah = cSize * 0.18f
                                val chev = Path().apply {
                                    moveTo(center.x - aw, center.y + ah * 0.5f)
                                    lineTo(center.x, center.y - ah * 0.8f)
                                    lineTo(center.x + aw, center.y + ah * 0.5f)
                                }
                                drawPath(
                                    path = chev,
                                    color = Color.White,
                                    style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }

                            // Frozen Ice Overlay
                            if (item.ice > 0) {
                                drawRoundRect(
                                    color = Color(0xFF00E5FF).copy(alpha = 0.42f),
                                    topLeft = Offset(cx, cy),
                                    size = Size(cSize, cSize),
                                    cornerRadius = CornerRadius(rad, rad)
                                )
                                drawRoundRect(
                                    color = Color.White,
                                    topLeft = Offset(cx, cy),
                                    size = Size(cSize, cSize),
                                    cornerRadius = CornerRadius(rad, rad),
                                    style = Stroke(width = 2.2.dp.toPx())
                                )
                            }
                        }

                        // Flashing Red Specular Rim along Blocking Obstacle
                        if ((redRimMap[item.id] ?: 0f) > 0f) {
                            drawRoundRect(
                                color = Color(0xFFFF1744),
                                topLeft = Offset(cx - 2.dp.toPx(), cy - 2.dp.toPx()),
                                size = Size(cSize + 4.dp.toPx(), cSize + 4.dp.toPx()),
                                cornerRadius = CornerRadius(rad + 2.dp.toPx(), rad + 2.dp.toPx()),
                                style = Stroke(width = 3.5.dp.toPx())
                            )
                        }
                    }

                    // Flying Animating Capsules
                    flyingCapsules.forEach { fc ->
                        val t = fc.progress.coerceIn(0f, 1f)
                        val curX = fc.startX + (fc.targetX - fc.startX) * t
                        val curY = fc.startY + (fc.targetY - fc.startY) * t - sin(t * PI.toFloat()) * 60.dp.toPx()
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(fc.color.lightHex, fc.color.hex, fc.color.darkHex),
                                center = Offset(curX, curY),
                                radius = cellSize * 0.38f
                            ),
                            radius = cellSize * 0.38f,
                            center = Offset(curX, curY)
                        )
                    }

                    // Particles & Floating Combat Text
                    particles.forEach { p ->
                        val alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
                        if (p.text != null) {
                            val layout = textMeasurer.measure(
                                text = p.text,
                                style = TextStyle(
                                    color = p.color.copy(alpha = alpha),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black
                                )
                            )
                            drawText(
                                textLayoutResult = layout,
                                topLeft = Offset(p.x - layout.size.width / 2f, p.y)
                            )
                        } else if (p.isRing && p.isChromatic) {
                            val split = p.rgbSplit
                            val rCenter = Offset(p.vx, p.vy)
                            // Red Chromatic Ring
                            drawCircle(
                                color = Color(0xFFFF1744).copy(alpha = alpha * 0.9f),
                                radius = p.x,
                                center = Offset(rCenter.x - split, rCenter.y - split * 0.4f),
                                style = Stroke(width = 3.2.dp.toPx()),
                                blendMode = BlendMode.Plus
                            )
                            // Green Chromatic Ring
                            drawCircle(
                                color = Color(0xFF00E676).copy(alpha = alpha * 0.9f),
                                radius = p.x,
                                center = Offset(rCenter.x, rCenter.y + split * 0.6f),
                                style = Stroke(width = 3.2.dp.toPx()),
                                blendMode = BlendMode.Plus
                            )
                            // Blue Chromatic Ring
                            drawCircle(
                                color = Color(0xFF00E5FF).copy(alpha = alpha * 0.95f),
                                radius = p.x,
                                center = Offset(rCenter.x + split, rCenter.y - split * 0.4f),
                                style = Stroke(width = 3.2.dp.toPx()),
                                blendMode = BlendMode.Plus
                            )
                        } else if (p.isRing) {
                            drawCircle(
                                color = p.color.copy(alpha = alpha),
                                radius = p.x,
                                center = boardBounds.center,
                                style = Stroke(width = 3.dp.toPx())
                            )
                        } else if (p.isChromatic) {
                            val split = p.rgbSplit * (0.6f + 0.6f * alpha)
                            val ox = cos(p.splitAngle) * split
                            val oy = sin(p.splitAngle) * split
                            // Red Chromatic Lobe
                            drawCircle(
                                color = Color(0xFFFF1744).copy(alpha = alpha * 0.92f),
                                radius = p.radius,
                                center = Offset(p.x - ox, p.y - oy),
                                blendMode = BlendMode.Plus
                            )
                            // Green Chromatic Lobe
                            drawCircle(
                                color = Color(0xFF00E676).copy(alpha = alpha * 0.92f),
                                radius = p.radius * 0.95f,
                                center = Offset(p.x + oy * 0.6f, p.y - ox * 0.6f),
                                blendMode = BlendMode.Plus
                            )
                            // Blue/Cyan Chromatic Lobe
                            drawCircle(
                                color = Color(0xFF00E5FF).copy(alpha = alpha * 0.95f),
                                radius = p.radius,
                                center = Offset(p.x + ox, p.y + oy),
                                blendMode = BlendMode.Plus
                            )
                            // White Hot Specular Core
                            drawCircle(
                                color = Color.White.copy(alpha = alpha * 0.9f),
                                radius = p.radius * 0.45f,
                                center = Offset(p.x, p.y),
                                blendMode = BlendMode.Plus
                            )
                        } else {
                            drawCircle(
                                color = p.color.copy(alpha = alpha),
                                radius = p.radius,
                                center = Offset(p.x, p.y)
                            )
                        }
                    }
                }
            }

            // 4. 5-SLOT LIQUID GLASS BUFFER DOCK (Prism Merge Ready + 4/5 Red Displacement Pulse scale='45')
            val isDanger = bufferSlots.size >= 4 || bufferSlots.size >= bufferCapacity - 1
            val dangerPulseAlpha = if (isDanger) (0.55f + 0.45f * sin(sloshPhase * 2.2f)).coerceIn(0.25f, 1f) else 0f
            LiquidGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                borderColor = if (isDanger) Color(0xFFFF1744).copy(alpha = 0.7f + 0.3f * dangerPulseAlpha) else Color.White.copy(alpha = 0.45f)
            ) {
                if (isDanger) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        drawRoundRect(
                            brush = Brush.linearGradient(
                                listOf(
                                    Color(0xFFFF1744).copy(alpha = 0.34f * dangerPulseAlpha),
                                    Color(0xFF4A071C).copy(alpha = 0.65f * dangerPulseAlpha)
                                )
                            ),
                            cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx())
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            if (isDanger) "⚠️ BUFFER CRITICAL (REFRACT SCALE=45)" else "BUFFER DOCK (PRISM MERGE READY)",
                            color = if (isDanger) Color(0xFFFF5252) else Color.White.copy(alpha = 0.85f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            if (isDanger) "${bufferSlots.size}/$bufferCapacity [SCALE 45]" else "${bufferSlots.size}/$bufferCapacity",
                            color = if (isDanger) Color(0xFFFF5252) else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0 until bufferCapacity) {
                            val item = bufferSlots.getOrNull(i)
                            val isArcHighlight = goldenArcs.any { it.idx1 == i || it.idx2 == i }
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (item != null) item.color.hex.copy(alpha = 0.28f)
                                        else Color.Black.copy(alpha = 0.4f)
                                    )
                                    .border(
                                        width = if (isArcHighlight) 2.5.dp else 1.5.dp,
                                        color = when {
                                            isArcHighlight -> Color(0xFFFFD700)
                                            item != null -> item.color.lightHex
                                            else -> Color.White.copy(alpha = 0.3f)
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (item != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.radialGradient(
                                                    listOf(item.color.lightHex, item.color.hex, item.color.darkHex)
                                                )
                                            )
                                            .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            item.color.label.first().toString(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. TACTILE CAPSULE BOOSTER BAR
            LiquidGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp, horizontal = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    BoosterPillButton(
                        icon = "🧲",
                        label = "Magnet",
                        count = magnetCount,
                        active = magnetActive
                    ) {
                        if (magnetCount > 0 && !magnetActive) {
                            magnetCount--
                            magnetActive = true
                            soundEngine.playTapGlass(1.2f)
                            saveState()
                        }
                    }

                    BoosterPillButton(
                        icon = "🔀",
                        label = "Shuffle",
                        count = shuffleCount,
                        active = false
                    ) {
                        if (shuffleCount > 0) {
                            shuffleCount--
                            soundEngine.playTapGlass(1.3f)
                            for (i in gridItems.indices) {
                                val g = gridItems[i]
                                if (!g.isObstacle) {
                                    gridItems[i] = g.copy(dir = ArrowDir.entries.random())
                                    shakeMap[g.id] = 0.2f
                                }
                            }
                            saveState()
                        }
                    }

                    BoosterPillButton(
                        icon = "➕",
                        label = "+1 Slot",
                        count = slotBoostCount,
                        active = bufferCapacity >= 6
                    ) {
                        if (slotBoostCount > 0 && bufferCapacity < 6) {
                            slotBoostCount--
                            bufferCapacity = 6
                            soundEngine.playTapGlass(1.5f)
                            saveState()
                        }
                    }
                }
            }

            // 6. BOTTOM NAVIGATION SWITCHER
            LiquidGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NavTabButton("🧪", "Play", activeTab == "PLAY") { activeTab = "PLAY" }
                    NavTabButton("⚗️", "Lab", activeTab == "LAB") { activeTab = "LAB" }
                    NavTabButton("✨", "Themes", activeTab == "THEMES") { activeTab = "THEMES" }
                    NavTabButton("🏆", "Prism Pass", activeTab == "PASS") { activeTab = "PASS" }
                }
            }
        }

        // --- OVERLAY MODALS ---
        if (isLevelWon) {
            ModalGlassDialog {
                Text("ALCHEMIST VICTORY!", color = Color(0xFF00E5FF), fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text("★ ★ ★", color = Color(0xFFFFD700), fontSize = 30.sp, fontWeight = FontWeight.Black)
                val baseWinCoins = 50 + levelNum * 15 + if (levelNum % 5 == 0) 75 else 0
                val winCoins = (baseWinCoins * upCoinMult * dailyMultiplier).toInt()
                Text(
                    "+$winCoins Coins (${"%.1f".format(dailyMultiplier)}x Daily Bonus!)   |   +${if (levelNum % 5 == 0) 6 else 2} Shards",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Button(
                    onClick = { loadLevel(levelNum + 1) },
                    colors = ButtonDefaults.buttonColors(containerColor = currentTheme.accent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("NEXT EXPERIMENT ➔", color = Color.Black, fontWeight = FontWeight.Black)
                }
            }
        }

        if (isOutOfMoves && !isLevelWon) {
            ModalGlassDialog {
                Text("OUT OF MOVES!", color = Color(0xFFFF2E93), fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text(
                    "You have exhausted all moves for this puzzle.",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = {
                        isWatchingAd = true
                        adProgress = 0f
                        scope.launch {
                            for (step in 1..30) {
                                delay(100)
                                adProgress = step / 30f
                            }
                            isWatchingAd = false
                            isOutOfMoves = false
                            movesRemaining += 5
                            soundEngine.playVictory()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🎬 Watch Ad: +5 Moves", color = Color.Black, fontWeight = FontWeight.Black)
                }
                OutlinedButton(
                    onClick = { loadLevel(levelNum) },
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔄 Restart Level", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (isBufferOverflow && !isWatchingAd) {
            ModalGlassDialog {
                Text("BUFFER OVERFLOW!", color = Color(0xFFFF2E93), fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text(
                    "All buffer slots are full and no Prism Merges are available.",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = {
                        isWatchingAd = true
                        adProgress = 0f
                        scope.launch {
                            for (step in 1..30) {
                                delay(100)
                                adProgress = step / 30f
                            }
                            isWatchingAd = false
                            isBufferOverflow = false
                            bufferCapacity = 6
                            if (bufferSlots.size >= 2) {
                                bufferSlots.removeAt(0)
                                bufferSlots.removeAt(0)
                            } else {
                                bufferSlots.clear()
                            }
                            soundEngine.playVictory()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🎬 Watch Ad: +1 Slot & Clear 2", color = Color.Black, fontWeight = FontWeight.Black)
                }
                OutlinedButton(
                    onClick = { loadLevel(levelNum) },
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔄 Restart Level", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (isWatchingAd) {
            ModalGlassDialog {
                Text("Simulated Rewarded Video", color = Color(0xFF00E5FF), fontSize = 18.sp, fontWeight = FontWeight.Black)
                LinearProgressIndicator(
                    progress = { adProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape),
                    color = Color(0xFFFF2E93),
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
                Text("Recharging Alchemical Buffer...", color = Color.White, fontSize = 13.sp)
            }
        }

        if (activeTab == "LAB") {
            ModalGlassDialog {
                Text("ALCHEMIST LAB", color = Color(0xFF00E5FF), fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text("🪙 Available: $coins Coins", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold)

                LabUpgradeRow("Aether Transmuter", "+20% Coin Value Multiplier", upCoinMult, 100 * upCoinMult) {
                    val cost = 100 * upCoinMult
                    if (coins >= cost) {
                        coins -= cost
                        upCoinMult++
                        soundEngine.playTapGlass(1.3f)
                        saveState()
                    }
                }
                LabUpgradeRow("Streak Catalyst", "+1 Starting Streak Multiplier", upStreakBonus, 120 * (upStreakBonus + 1)) {
                    val cost = 120 * (upStreakBonus + 1)
                    if (coins >= cost) {
                        coins -= cost
                        upStreakBonus++
                        soundEngine.playTapGlass(1.3f)
                        saveState()
                    }
                }
                LabUpgradeRow("Buffer Capacity", "+1 Permanent Buffer Dock Slot", upBufferCapacity, 200 * (upBufferCapacity + 1)) {
                    val cost = 200 * (upBufferCapacity + 1)
                    if (coins >= cost) {
                        coins -= cost
                        upBufferCapacity++
                        bufferCapacity = 5 + upBufferCapacity
                        soundEngine.playTapGlass(1.3f)
                        saveState()
                    }
                }

                Button(
                    onClick = { activeTab = "PLAY" },
                    colors = ButtonDefaults.buttonColors(containerColor = currentTheme.accent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Back to Puzzle", color = Color.Black, fontWeight = FontWeight.Black)
                }
            }
        }

        if (activeTab == "THEMES") {
            ModalGlassDialog {
                Text("LIQUID GLASS THEMES", color = Color(0xFF00E5FF), fontSize = 20.sp, fontWeight = FontWeight.Black)
                GlassTheme.entries.forEach { theme ->
                    val selected = theme == currentTheme
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) theme.accent.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.4f))
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) theme.accent else Color.White.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                currentTheme = theme
                                soundEngine.playTapGlass(1.2f)
                                saveState()
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(theme.title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                            Text(theme.subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(theme.accent, theme.secondary)))
                                .border(1.dp, Color.White, CircleShape)
                        )
                    }
                }
                Button(
                    onClick = { activeTab = "PLAY" },
                    colors = ButtonDefaults.buttonColors(containerColor = currentTheme.accent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Apply & Return", color = Color.Black, fontWeight = FontWeight.Black)
                }
            }
        }

        if (activeTab == "PASS") {
            ModalGlassDialog {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🏆 PRISM PASS & QUESTS", color = Color(0xFF00E5FF), fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Text("💎 $shards Shards", color = Color(0xFFFFD700), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                }

                val curTier = prismPassTiers.lastOrNull { prismPassXP >= it.xpRequired }?.tier ?: 1
                val nextXP = prismPassTiers.firstOrNull { prismPassXP < it.xpRequired }?.xpRequired ?: 1000
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF00E5FF).copy(alpha = 0.12f))
                        .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("SEASON 1 • TIER $curTier", color = Color(0xFF00E5FF), fontWeight = FontWeight.Black, fontSize = 11.sp)
                        Text("$prismPassXP / $nextXP XP", color = Color(0xFFFFD700), fontWeight = FontWeight.Black, fontSize = 11.sp)
                    }
                    LinearProgressIndicator(
                        progress = { (prismPassXP.toFloat() / nextXP.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(CircleShape),
                        color = Color(0xFFFF2E93),
                        trackColor = Color.Black.copy(alpha = 0.5f)
                    )
                }

                Text(
                    "⚡ DAILY ALCHEMIST QUESTS",
                    color = Color(0xFF00E5FF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.fillMaxWidth()
                )

                dailyQuests.forEachIndexed { qIdx, q ->
                    val canClaim = q.progress >= q.target && !q.claimed
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(q.title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                            Text(
                                "${q.progress}/${q.target} • +${q.rewardShards} 💎 & +${q.rewardXP} XP",
                                color = Color(0xFFFFD700),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Button(
                            onClick = {
                                if (canClaim) {
                                    dailyQuests[qIdx] = q.copy(claimed = true)
                                    shards += q.rewardShards
                                    prismPassXP += q.rewardXP
                                    soundEngine.playVictory()
                                    saveState()
                                }
                            },
                            enabled = canClaim,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (canClaim) Color(0xFF00E676) else Color.White.copy(alpha = 0.2f)
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                if (q.claimed) "✓ Done" else if (canClaim) "Claim +${q.rewardShards}💎" else "${q.progress}/${q.target}",
                                color = if (canClaim) Color.Black else Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Text(
                    "🌈 SEASONAL PRISM PASS TRACK",
                    color = Color(0xFFFFD700),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.fillMaxWidth()
                )

                prismPassTiers.take(3).forEach { t ->
                    val unlocked = prismPassXP >= t.xpRequired
                    val claimed = claimedPassTiers.contains(t.tier)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.4f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Tier ${t.tier}: ${t.title}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                            Text("Requires ${t.xpRequired} XP • +${t.rewardShards} 💎 & +${t.rewardCoins} 🪙", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                        }
                        Button(
                            onClick = {
                                if (unlocked && !claimed) {
                                    claimedPassTiers.add(t.tier)
                                    shards += t.rewardShards
                                    coins += t.rewardCoins
                                    soundEngine.playVictory()
                                    saveState()
                                }
                            },
                            enabled = unlocked && !claimed,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (unlocked && !claimed) Color(0xFFFFD700) else Color.White.copy(alpha = 0.2f)
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                if (claimed) "✓ Claimed" else if (unlocked) "Claim +${t.rewardShards}💎" else "🔒 ${t.xpRequired}",
                                color = if (unlocked && !claimed) Color.Black else Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Button(
                    onClick = { activeTab = "PLAY" },
                    colors = ButtonDefaults.buttonColors(containerColor = currentTheme.accent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Back to Puzzle", color = Color.Black, fontWeight = FontWeight.Black)
                }
            }
        }

        if (showDailyStreakModal) {
            ModalGlassDialog {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📅 DAILY STREAK BONUS", color = Color(0xFFFFD700), fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("🪙 $coins", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }

                Text(
                    "Login daily to amplify all level coin rewards up to 3.0x!",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFFFD700).copy(alpha = 0.22f), Color(0xFFFF7700).copy(alpha = 0.22f))
                            )
                        )
                        .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("ACTIVE MULTIPLIER STATUS", color = Color(0xFFFFD700), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                        Text("${"%.1f".format(dailyMultiplier)}x COIN MULTIPLIER", color = Color(0xFFFFE66D), fontSize = 20.sp, fontWeight = FontWeight.Black)
                        Text("Consecutive Login: Day $dailyStreak / 7", color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DAILY_STREAK_SCHEDULE.forEach { r ->
                        val isPast = r.day < dailyStreak || (r.day == dailyStreak && dailyClaimedToday)
                        val isToday = r.day == dailyStreak
                        val isLocked = r.day > dailyStreak

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isToday && !dailyClaimedToday) Color(0xFFFFD700).copy(alpha = 0.25f)
                                    else if (isPast) Color(0xFF00FF88).copy(alpha = 0.12f)
                                    else Color.White.copy(alpha = 0.06f)
                                )
                                .border(
                                    1.dp,
                                    if (isToday && !dailyClaimedToday) Color(0xFFFFD700)
                                    else if (isPast) Color(0xFF00FF88).copy(alpha = 0.4f)
                                    else Color.White.copy(alpha = 0.15f),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Day ${r.day}: ${r.title}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "+${r.coins} 🪙" + if (r.shards > 0) "  +${r.shards} 💎" else "",
                                    color = Color(0xFFFFD700),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFFFD700).copy(alpha = 0.2f))
                                        .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("${"%.1f".format(r.mult)}x", color = Color(0xFFFFE66D), fontWeight = FontWeight.Black, fontSize = 11.sp)
                                }
                                Text(
                                    if (isPast) "✓ Claimed" else if (isToday) (if (dailyClaimedToday) "✓ Active" else "★ TODAY") else "🔒 Day ${r.day}",
                                    color = if (isPast) Color(0xFF00FF88) else if (isToday) Color(0xFFFFD700) else Color.White.copy(alpha = 0.4f),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                val todayReward = DAILY_STREAK_SCHEDULE.find { it.day == dailyStreak } ?: DAILY_STREAK_SCHEDULE[0]
                Button(
                    onClick = {
                        if (!dailyClaimedToday) {
                            coins += todayReward.coins
                            shards += todayReward.shards
                            dailyClaimedToday = true
                            soundEngine.playVictory()
                            spawnChromaticAberrationBurst(boardBounds.center.x, boardBounds.center.y, Color(0xFFFFD700), 24)
                            spawnText("+${todayReward.coins} COINS & ${"%.1f".format(todayReward.mult)}x BONUS!", boardBounds.center.x, boardBounds.center.y, Color(0xFFFFD700))
                            saveState()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !dailyClaimedToday,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFD700),
                        disabledContainerColor = Color.White.copy(alpha = 0.15f)
                    )
                ) {
                    Text(
                        if (!dailyClaimedToday) "Claim Day $dailyStreak Bonus (+${todayReward.coins} 🪙${if (todayReward.shards > 0) ", +${todayReward.shards} 💎" else ""})!"
                        else "✓ Today's Bonus Claimed (${"%.1f".format(dailyMultiplier)}x Active)",
                        color = if (!dailyClaimedToday) Color.Black else Color.White.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp
                    )
                }

                Button(
                    onClick = { showDailyStreakModal = false },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.18f))
                ) {
                    Text("Back to Puzzle", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    borderColor: Color = Color.White.copy(alpha = 0.42f),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.20f),
                        Color(0xFF162038).copy(alpha = 0.65f)
                    )
                )
            )
            .border(1.5.dp, borderColor, RoundedCornerShape(20.dp)),
        content = content
    )
}

@Composable
fun BoosterPillButton(
    icon: String,
    label: String,
    count: Int,
    active: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (active) Color(0xFF00E5FF).copy(alpha = 0.35f)
                else Color.White.copy(alpha = 0.16f)
            )
            .border(
                width = if (active) 2.dp else 1.dp,
                color = if (active) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.38f),
                shape = CircleShape
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(icon, fontSize = 15.sp)
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFFFF2E93))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text("$count", color = Color.White, fontWeight = FontWeight.Black, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun NavTabButton(icon: String, title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) Color.White.copy(alpha = 0.22f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(icon, fontSize = 15.sp)
        Text(
            title,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.65f),
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

@Composable
fun LabUpgradeRow(title: String, desc: String, level: Int, cost: Int, onBuy: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("$title (Lv. $level)", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            Text(desc, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
        }
        Button(
            onClick = onBuy,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text("🪙 $cost", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
    }
}

@Composable
fun ModalGlassDialog(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(enabled = true) {},
        contentAlignment = Alignment.Center
    ) {
        LiquidGlassSurface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content
            )
        }
    }
}
