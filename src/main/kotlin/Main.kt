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
        intArrayOf(25,27,32,30),
        intArrayOf(26,29,31,28),
        intArrayOf(19,33,6,48),
        intArrayOf(21,34,4,47),
        intArrayOf(24,35,1,46)
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
    val seen = mutableSetOf<Int>()
    fun turn(side: Side, save: Boolean) {
        val rotate = Rotations[side.ordinal]
        getRaw(tilesRaw)
        for (shift in rotate.ops) {
            applyShift(shift, tilesRaw)
        }
        val newHash = getPositionHash(tilesRaw)
        if (seen.contains(newHash))
            return
        seen.add(newHash)
        applyRaw(tilesRaw)
        if (save) {
            history.add(side)
        }
    }

    fun getPositionHash(raw: IntArray): Int {
        var result = 17
        raw.forEachIndexed { index, value ->
            result = result * 11 + result xor (1701 * (index + 1) * (value + 1))
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
                while (searchStatus.value == SearchStatus.Started) {
                    if (isDone()) break
                    val side = Side.values()[rng.nextInt(1, 6)]
                    turn(side, true)
                    //delay(100)
                    ops.value++
                }
                searchStatus.value = SearchStatus.None
            }
        }
    }

    fun stopSearch() {
        searchStatus.value = SearchStatus.Cancelling
    }
    fun isDone(): Boolean {
        tiles.forEachIndexed { index, value ->
            if (value.color != colorFor(index)) return false
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
        Text(side.toString())
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
        Text(index)
    }
}

@Composable
@Preview
fun Rubik(state: AppState) {
    val coroutineScope = rememberCoroutineScope()

    Column {
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
            Text("${state.ops.value} ops", Modifier.align(Alignment.CenterVertically))

        }
        Row(Modifier.fillMaxSize()) {
            for (side in Side.values()) {
                CubeSide(state, side)
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
        state = WindowState(width = 1100.dp, height = 300.dp),
        resizable = false
    ) {
        App()
    }
}
