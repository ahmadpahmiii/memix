package app.memix.platform.services

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberFreeUpSpaceLauncher(): FreeUpSpaceLauncher {
    // Started for a result, as StorageManager asks, so the system shows which app wants the space. The result
    // itself isn't needed: the import checks the space again when the app is back in the foreground.
    // The contract is remembered: a new instance on each recomposition would register the launcher again.
    val contract = remember { StartActivityForResult() }
    val activityLauncher = rememberLauncherForActivityResult(contract) { }
    return remember(activityLauncher) {
        object : FreeUpSpaceLauncher {
            override fun launch(requestedBytes: Long): Boolean {
                val manageStorage = Intent(StorageManager.ACTION_MANAGE_STORAGE)
                    .putExtra(StorageManager.EXTRA_UUID, StorageManager.UUID_DEFAULT)
                    .putExtra(StorageManager.EXTRA_REQUESTED_BYTES, requestedBytes)
                return tryLaunch { activityLauncher.launch(manageStorage) } ||
                    tryLaunch { activityLauncher.launch(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }
            }
        }
    }
}

private inline fun tryLaunch(launch: () -> Unit): Boolean = try {
    launch()
    true
} catch (e: ActivityNotFoundException) {
    false
}
