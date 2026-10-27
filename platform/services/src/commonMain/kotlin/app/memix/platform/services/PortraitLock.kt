package app.memix.platform.services

import androidx.compose.runtime.Composable

/**
 * Keeps the screen in portrait while this is in the composition, on phones only (smallest width under 600 dp): in
 * landscape a phone leaves about 180 dp for the editor's preview and timeline together (spec P1-04 → Layout). Large
 * screens rotate freely; Android 16+ ignores the lock there anyway. Releases the lock when it leaves.
 */
@Composable
expect fun LockPortraitOnPhones()
