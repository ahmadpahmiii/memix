package app.memix.core.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * One immutable [State] out, [Intent]s in. Screens render [state] and send every user action
 * through [onIntent]; nothing else on the ViewModel is public.
 */
abstract class MemixViewModel<State : Any, Intent : Any>(initialState: State) : ViewModel() {
    private val mutableState = MutableStateFlow(initialState)
    val state: StateFlow<State> = mutableState.asStateFlow()

    abstract fun onIntent(intent: Intent)

    protected fun updateState(transform: (State) -> State) = mutableState.update(transform)
}
