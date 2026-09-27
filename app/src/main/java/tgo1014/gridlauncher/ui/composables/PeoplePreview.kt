package tgo1014.gridlauncher.ui.composables

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tgo1014.gridlauncher.live.PinnedContact
import tgo1014.gridlauncher.ui.theme.AsyncImage
import tgo1014.gridlauncher.ui.theme.LocalGlass

@Composable
fun PeopleMosaic(people: List<PinnedContact>, page: Int, modifier: Modifier = Modifier, ink: androidx.compose.ui.graphics.Color = LocalGlass.current.ink) {
    val glass = LocalGlass.current
    val shown = if (people.isEmpty()) emptyList() else (0 until minOf(4, people.size)).map { people[(page + it) % people.size] }
    Column(modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        shown.chunked(2).forEach { row -> Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            row.forEach { person -> Box(Modifier.weight(1f).fillMaxHeight().clip(CircleShape).background(glass.accent.copy(alpha = .25f)), contentAlignment = Alignment.Center) {
                if (person.photo != null) AsyncImage(person.photo, Modifier.fillMaxSize())
                else Text(person.name.split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1) }, color = ink, fontSize = 20.sp)
            } }
        } }
    }
}

@Composable
fun PeoplePreview(people: List<PinnedContact>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(people.singleOrNull()) }
    fun launch(intent: Intent) { runCatching { context.startActivity(intent) }.onFailure { android.widget.Toast.makeText(context, "No app can open this action", android.widget.Toast.LENGTH_SHORT).show() } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(selected?.name ?: "People") }, text = {
        Column {
            if (selected == null) people.forEach { contact -> TextButton(onClick = { selected = contact }) { Text(contact.name) } }
            else {
                selected!!.phones.forEach { number ->
                    Text(number)
                    Row { TextButton(onClick = { launch(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))) }) { Text("Call") }
                        TextButton(onClick = { launch(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))) }) { Text("Message") } }
                }
                selected!!.emails.forEach { email -> TextButton(onClick = { launch(Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", email, null))) }) { Text(email) } }
                if (selected!!.phones.isEmpty() && selected!!.emails.isEmpty()) TextButton(onClick = { launch(Intent(Intent.ACTION_VIEW, android.provider.ContactsContract.Contacts.CONTENT_LOOKUP_URI.buildUpon().appendPath(selected!!.key).build())) }) { Text("Open contact") }
                if (people.size > 1) TextButton(onClick = { selected = null }) { Text("All people") }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}
