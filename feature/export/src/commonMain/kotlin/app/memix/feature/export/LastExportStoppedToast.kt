package app.memix.feature.export

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.memix.core.designsystem.component.Toast
import app.memix.core.designsystem.component.ToastAction
import app.memix.core.designsystem.component.ToastDismiss
import memix.feature.export.generated.resources.Res
import memix.feature.export.generated.resources.export_dismiss
import memix.feature.export.generated.resources.export_last_stopped
import memix.feature.export.generated.resources.export_open_draft
import org.jetbrains.compose.resources.stringResource

/**
 * The persistent toast on the first screen after Memix was stopped mid-export (spec P1-12 → Next start after Memix was
 * stopped mid-export): nothing was saved; [onOpenDraft] opens the draft, [onDismiss] hides it. Shown once; the caller
 * places it.
 */
@Composable
fun LastExportStoppedToast(onOpenDraft: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Toast(
        stringResource(Res.string.export_last_stopped),
        modifier,
        action = ToastAction(stringResource(Res.string.export_open_draft), onOpenDraft),
        dismiss = ToastDismiss(stringResource(Res.string.export_dismiss), onDismiss),
    )
}
