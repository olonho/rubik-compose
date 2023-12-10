import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import kotlinx.coroutines.*
import kotlin.random.Random


enum class Side(val color: CubeTileColor) {
    Left(CubeTileColor.Blue),
    Front(CubeTileColor.Red),
    Right(CubeTileColor.Green),
    Back(CubeTileColor.Orange),
    Up(CubeTileColor.Yellow),
    Down(CubeTileColor.White)
}

enum class CubeTileColor(val color: Color) {
    Blue(Color.Blue),
    Red(Color.Red),
    Green(Color.Green),
    Orange(Color(0xffFFA500)),
    Yellow(Color.Yellow),
    White(Color.White),
    ;
}

class RotateOp {
    val ops: Array<IntArray>
    constructor(s1: IntArray, s2: IntArray, s3: IntArray,
                s4: IntArray, s5: IntArray) {
        ops = arrayOf(s1, s2, s3, s4, s5)
    }
}

val Rotations = arrayOf(
    RotateOp(
        intArrayOf(1,3,8,6),
        intArrayOf(2,5,7,4),
        intArrayOf(33,9,41,32),
        intArrayOf(36,12,44,29),
        intArrayOf(38,14,46,27)
    ), // left
    RotateOp(
        intArrayOf(9,11,16,14),
        intArrayOf(10,13,15,12),
        intArrayOf(38,17,43,8),
        intArrayOf(39,20,42,5),
        intArrayOf(40,22,41,3)
    ), // front
    RotateOp(
        intArrayOf(17,19,24,22),
        intArrayOf(18,21,23,20),
        intArrayOf(48,16,40,25),
        intArrayOf(45,13,37,28),
        intArrayOf(43,11,35,30)
    ), // right
    RotateOp(
        intArrayOf(25,27,32,30),
        intArrayOf(26,29,31,28),
        intArrayOf(19,33,6,48),
        intArrayOf(21,34,4,47),
        intArrayOf(24,35,1,46)
    ), // bottom
    RotateOp(
        intArrayOf(33,35,40,38),
        intArrayOf(34,37,39,36),
        intArrayOf(25,17,9,1),
        intArrayOf(26,18,10,2),
        intArrayOf(27,19,11,3)
    ), // up
    RotateOp(
        intArrayOf(41,43,48,46),
        intArrayOf(42,45,47,44),
        intArrayOf(6,14,22,30),
        intArrayOf(7,15,23,31),
        intArrayOf(8,16,24,32)
    ), // down
)
class CubeTile {
    private val _color: MutableState<CubeTileColor>
    constructor(color: CubeTileColor) {
        this._color =  mutableStateOf(color)
    }

    public var color: CubeTileColor
        get() = this._color.value
        set(value) { this._color.value = value }
}

enum class SearchStatus {
    None,
    Started,
    Cancelling
}

class AppState {
    val tiles: Array<CubeTile>
    val tilesRaw = IntArray(48)

    val ops = mutableStateOf(0)
    var searchStatus = mutableStateOf(SearchStatus.None)
    var status = mutableStateOf("")

    constructor() {
        tiles = Array(48) { index ->
            CubeTile(colorFor(index))
        }
    }

    fun applyRaw(array: IntArray) {
        array.forEachIndexed {
            index, value ->
            tiles[index].color = CubeTileColor.values()[value]
        }
    }

    fun getRaw(array: IntArray) {
        tiles.forEachIndexed {
                index, value ->
            array[index] = tiles[index].color.ordinal
        }
    }

    fun reset() {
        tiles.forEachIndexed { index, value ->
            value.color = colorFor(index)
        }
        history.clear()
        ops.value = 0
        searchStatus.value = SearchStatus.None
        seen.clear()
        status.value = ""
    }

    private fun colorFor(index: Int): CubeTileColor {
        return CubeTileColor.values()[index / 8]
    }

    fun indexOf(side: Side, row: Int, tile: Int): Int {
        val offset = row * 3 + tile
        if (offset < 4) return side.ordinal * 8 + offset
        if (offset == 4) throw Error("nope")
        return side.ordinal * 8 + offset - 1
    }
    fun colorFor(side: Side, row: Int, tile: Int): CubeTileColor {
        if (tile == 1 && row == 1) return side.color
        return tiles[indexOf(side, row, tile)].color
    }

    fun nameOf(side: Side, row: Int, tile: Int): String {
        if (tile == 1 && row == 1) return side.name
        return (indexOf(side, row, tile) + 1).toString()
    }

    val history = mutableListOf<Side>()
    val seen = mutableSetOf<Long>()
    fun turn(side: Side, save: Boolean) {
        getRaw(tilesRaw)
        turnRaw(side, tilesRaw, false)
        applyRaw(tilesRaw)
        if (save) history.add(side)
    }

    fun turnRaw(side: Side, raw: IntArray, updateSeen: Boolean): Boolean {
        val rotate = Rotations[side.ordinal]
        for (shift in rotate.ops) {
            applyShift(shift, raw)
        }
        if (updateSeen) {
            val newHash = getPositionHash(raw)
            if (seen.contains(newHash)) return false
            seen.add(newHash)
        }
        return true
    }

    private fun getPositionHash(raw: IntArray): Long {
        var result = 17L
        raw.forEachIndexed { index, value ->
            result = result * 11 + result xor ((1701L + index) * value)
        }
        return result
    }
    fun undo() {
        if (history.size > 0) {
            val side = history.removeLast()
            turn(side, false)
            turn(side, false)
            turn(side, false)
        }
    }

    fun startSearch(scope: CoroutineScope) {
        if (searchStatus.value == SearchStatus.Started) return
        searchStatus.value = SearchStatus.Started
        scope.launch {
            withContext(Dispatchers.IO) {
                val rng = Random(System.currentTimeMillis())
                val tiles = IntArray(48)
                val tiles2 = IntArray(48)
                getRaw(tiles)
                val log = mutableListOf<Side>()
                while (searchStatus.value == SearchStatus.Started) {
                    if (isDone(tiles)) {
                        status.value = "Found in ${log.size}"
                        println(log)
                        break
                    }
                    val side = Side.values()[rng.nextInt(0, 6)]
                    tiles.copyInto(tiles2)
                    if (turnRaw(side, tiles2, true)) {
                        log.add(side)
                        tiles2.copyInto(tiles)
                        if (true) {
                            status.value = side.toString()
                            applyRaw(tiles)
                            delay(500)
                        }
                        ops.value++
                    }
                }

                searchStatus.value = SearchStatus.None
            }
        }
    }

    fun stopSearch() {
        searchStatus.value = SearchStatus.Cancelling
    }
    fun isDone(tiles: IntArray): Boolean {
        tiles.forEachIndexed { index, value ->
            if (value != index / 8) return false
        }
        return true
    }

    fun applyShift(shift: IntArray, raw: IntArray) {
        val v0 = raw[shift[0] - 1]
        val v1 = raw[shift[1] - 1]
        val v2 = raw[shift[2] - 1]
        val v3 = raw[shift[3] - 1]

        raw[shift[1] - 1] = v0
        raw[shift[2] - 1] = v1
        raw[shift[3] - 1] = v2
        raw[shift[0] - 1] = v3
    }
}
@Composable
@Preview
fun CubeSide(state: AppState, side: Side) {
    Column(Modifier.clickable {
        state.turn(side, true)
    }) {
        // Text(side.toString())
        for (row in 0 .. 2) {
            CubeRow(state, side, row)
        }
    }
}

@Composable
@Preview
fun CubeRow(state: AppState, side: Side, row: Int) {
    Row(Modifier.width(180.dp).padding(all = 3.dp)) {
        //Text(row.toString(), Modifier.width(20.dp) )
        for (tile in 0 .. 2) {
            val color = state.colorFor(side, row, tile)
            val name = state.nameOf(side, row, tile)
            CubeTile(color, name)
        }
    }
}

@Composable
@Preview
fun CubeTile(tile: CubeTileColor, index: String) {
    Box(Modifier
        .width(50.dp)
        .height(50.dp)
        .background(tile.color)
        .border(BorderStroke(1.dp, Color.Black))
    ) {
        Text(index, Modifier.align(Alignment.Center))
    }
}

@Composable
@Preview
fun Rubik(state: AppState) {
    val coroutineScope = rememberCoroutineScope()

    Column {
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(40.dp)) {
            Spacer(Modifier.width(10.dp))
            Button({
                state.reset()
            }) {
                Text("Reset")
            }
            Spacer(Modifier.width(10.dp))
            Button({
                state.undo()
            }) {
                Text("Undo")
            }
            Spacer(Modifier.width(10.dp))
            Button({
                if (state.searchStatus.value == SearchStatus.Started)
                    state.stopSearch()
                else
                    state.startSearch(coroutineScope)
            }) {
                Text(if (state.searchStatus.value == SearchStatus.Started) "Stop"  else "Search")
            }
            Spacer(Modifier.width(10.dp))
            Text("${state.ops.value} ops, ${state.seen.size} seen", Modifier.align(Alignment.CenterVertically))
            Spacer(Modifier.width(10.dp))
            Text(state.status.value, color =  Color.Red, modifier = Modifier.align(Alignment.CenterVertically))
        }
        Row(Modifier.fillMaxSize()) {
            Column {
                Row {
                    Spacer(Modifier.width(180.dp))
                    CubeSide(state, Side.Up)
                }
                Row {
                    for (side in arrayOf(Side.Left, Side.Front, Side.Right, Side.Back)) {
                        CubeSide(state, side)
                    }
                }
                Row {
                    Spacer(Modifier.width(180.dp))
                    CubeSide(state, Side.Down)
                }
            }
        }
    }
}

@Composable
@Preview
fun App() {
    MaterialTheme {
        val state = remember { AppState() }
        Column( modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Rubik(state)
        }
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication,
        state = WindowState(width = 760.dp, height = 600.dp),
        resizable = false
    ) {
        App()
    }
}
