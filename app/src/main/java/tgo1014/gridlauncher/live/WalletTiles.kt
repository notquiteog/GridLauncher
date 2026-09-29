package tgo1014.gridlauncher.live

import android.content.Context
import android.content.Intent
import android.service.quickaccesswallet.QuickAccessWalletService

/**
 * Hands off to the wallet Android is already running. Android 17 exposes only the wallet *provider*
 * API to third-party apps, not a way to read the active card, so this deliberately does not invent
 * a card name: it reports whether a wallet exists and opens it.
 */
object WalletTiles {
    private fun intent(context: Context) = Intent(QuickAccessWalletService.ACTION_VIEW_WALLET).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun available(context: Context): Boolean = runCatching {
        context.packageManager.queryIntentActivities(intent(context), 0).isNotEmpty()
    }.getOrDefault(false)

    fun openWallet(context: Context) = runCatching {
        context.startActivity(intent(context)); true
    }.getOrDefault(false)
}
