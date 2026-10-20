package app.memix.core.designsystem.component

import androidx.compose.foundation.focusGroup
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.clearAndSetSemantics

/**
 * For the screen content behind an open [Sheet] or [ToolPanel]: while [covered], it has no screen-reader nodes and
 * refuses keyboard focus, so TalkBack and Tab reach only the surface on top, as with a modal dialog (WCAG 2.2 SC 2.4.3
 * Focus Order, 2.4.11 Focus Not Obscured). Put it on the root of what the surface covers, never on the surface itself.
 * A sheet's scrim stays reachable: it's the sheet's own "Close".
 */
fun Modifier.coveredByModal(covered: Boolean): Modifier =
    if (!covered) this else clearAndSetSemantics { }.focusProperties { onEnter = { cancelFocusChange() } }.focusGroup()
