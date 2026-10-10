package tgo1014.gridlauncher.ui.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
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
import tgo1014.gridlauncher.ui.theme.LocalGlass

/** The alphabet, then the bucket for anything that does not start with a letter. */
private val jumpLetters: List<Char> = ('A'..'Z').toList() + '#'

/** The first letter of a name, uppercased, or `#` for a digit or a symbol. */
fun jumpLetterOf(name: String): Char = name.trim().firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#'

/**
 * The Windows Phone jump list: a rail of letters down the left edge of the app list, which is where
 * Windows Phone kept it. A letter no app starts with is dimmed rather than hidden, so the alphabet
 * keeps its shape as apps come and go; it stays in the layout and does nothing. A long press opens
 * the whole alphabet at once.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JumpRail(
    letters: Set<Char>, current: Char? = null, onJump: (Char) -> Unit = {},
    onShowAlphabet: () -> Unit = {}, modifier: Modifier = Modifier,
) {
    val glass = LocalGlass.current
    BoxWithConstraints(modifier) {
        // The whole alphabet is the point, so the letters share the height they are given rather
        // than taking a fixed slot and spilling off the bottom. Only a pane too short to read a
        // letter falls back to scrolling.
        val slot = (maxHeight / jumpLetters.size).coerceIn(14.dp, 24.dp)
        Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).semantics { contentDescription = "All apps jump list" }) {
            jumpLetters.forEach { letter ->
                val available = letter in letters
                val selected = letter == current
                Box(Modifier.fillMaxWidth().height(slot)
                    .combinedClickable(enabled = available, onClick = { onJump(letter) }, onLongClick = onShowAlphabet)
                    .focusable(enabled = available)
                    .semantics { contentDescription = when {
                        !available -> "$letter, no apps"
                        selected -> "$letter, current letter"
                        else -> "Jump to $letter"
                    } },
                    contentAlignment = Alignment.Center) {
                    Text("$letter", color = when {
                        !available -> glass.ink.copy(alpha = .3f)
                        selected -> glass.accent
                        else -> glass.ink
                    }, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
}
