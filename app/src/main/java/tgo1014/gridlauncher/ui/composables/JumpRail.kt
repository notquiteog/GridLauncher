package tgo1014.gridlauncher.ui.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.theme.LocalGlass

/** The alphabet, then the bucket for anything that does not start with a letter. */
private val jumpLetters: List<Char> = ('A'..'Z').toList() + '#'

/** The first letter of a tile's name, uppercased, or `#` for a digit or a symbol. */
fun jumpLetterOf(name: String): Char = name.trim().firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#'

/**
 * The Windows Phone jump list: a rail of letters down the left edge of Start. A letter with no
 * tile is dimmed rather than hidden, so the alphabet keeps its shape as the grid changes; it still
 * takes a tap and simply does nothing. A long press opens the whole alphabet at once, the way a
 * press and hold on the Start screen did.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JumpRail(anchors: Map<Char, GridItem>, onJump: suspend (GridItem) -> Unit, modifier: Modifier = Modifier) {
    val glass = LocalGlass.current
    val scope = rememberCoroutineScope()
    var alphabet by remember { mutableStateOf(false) }
    Column(modifier.verticalScroll(rememberScrollState()).semantics { contentDescription = "Start jump list" }) {
        jumpLetters.forEach { letter ->
            val target = anchors[letter]
            Box(Modifier.fillMaxWidth().height(22.dp)
                .combinedClickable(onClick = { target?.let { tile -> scope.launch { onJump(tile) } } }, onLongClick = { alphabet = true })
                .semantics { contentDescription = if (target == null) "$letter, nothing on Start" else "Jump to $letter" },
                contentAlignment = Alignment.Center) {
                Text("$letter", color = if (target == null) glass.ink.copy(alpha = .3f) else glass.ink,
                    fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
    if (alphabet) AlertDialog(onDismissRequest = { alphabet = false }, title = { Text("Jump to letter") }, text = {
        Column { jumpLetters.chunked(6).forEach { row -> Row {
            row.forEach { letter ->
                val target = anchors[letter]
                TextButton(onClick = {
                    alphabet = false
                    if (target != null) scope.launch { onJump(target) }
                }, modifier = Modifier.weight(1f)) {
                    Text("$letter", fontSize = 18.sp, color = if (target == null) glass.ink.copy(alpha = .3f) else glass.accent)
                }
            }
            repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
        } } }
    }, confirmButton = { TextButton(onClick = { alphabet = false }) { Text("Close") } })
}
