package tgo1014.gridlauncher.ui.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tgo1014.gridlauncher.live.ActiveCall
import tgo1014.gridlauncher.live.CallAction
import tgo1014.gridlauncher.live.CallTiles

/**
 * A call on this tile: who, which way it is going, and the controls that can work.
 *
 * [CallTiles] has already decided whether there is anything to show - quiet hours, an excluded app,
 * a locked device and switched-off previews all return no call at all, so this never has to decide
 * whether it may show something. Every control is at least 48dp and carries its own TalkBack
 * description, because a call is exactly the moment a launcher gets used one-handed.
 */
@Composable
fun CallTilePanel(call: ActiveCall, ink: Color, expanded: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    fun act(action: CallAction) = CallTiles.act(context, action)
    val open = CallAction.OpenApp(call.packageName, call.appName)
    val controls = CallTiles.controls(call)
    Column(
        modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(call.state.label, color = ink.copy(alpha = .85f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        // An app that says nothing about who is calling gets no name invented for it, so the
        // fallback is the app's own name, which the tile is already labelled with.
        val subject = call.who.ifBlank { call.appName }
        Text(subject, color = ink, fontSize = if (expanded) 20.sp else 13.sp,
            maxLines = if (expanded) 2 else 1, overflow = TextOverflow.Ellipsis)
        if (expanded && controls.size > 1) Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            controls.forEach { action -> CallControl(action, ink) { act(action) } }
        } else {
            // A tile too small for a row of buttons still gets the one control that always works,
            // so there is never a call tile that cannot be acted on.
            CallControl(open, ink) { act(open) }
        }
    }
}

/** One control, at least 48dp, described for TalkBack in the words the user would use. */
@Composable
private fun CallControl(action: CallAction, ink: Color, onClick: () -> Unit) {
    val label = when (action) {
        is CallAction.OpenApp -> "Open in ${action.appName}"
        is CallAction.Answer -> "Answer call"
        is CallAction.Reject -> "Decline call"
    }
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = label }) {
        Text(label, color = ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
