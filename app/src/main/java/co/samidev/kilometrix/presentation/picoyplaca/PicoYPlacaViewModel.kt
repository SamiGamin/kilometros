package co.samidev.kilometrix.presentation.picoyplaca

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.samidev.kilometrix.core.util.Resource
import co.samidev.kilometrix.domain.model.PicoPlacaResponse
import co.samidev.kilometrix.domain.usecase.GetPicoYPlacaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PicoYPlacaViewModel @Inject constructor(
    private val getPicoYPlacaUseCase: GetPicoYPlacaUseCase
) : ViewModel() {

    private val retryTrigger = MutableSharedFlow<Unit>(replay = 1).apply {
        tryEmit(Unit)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val picoPlacaState: StateFlow<Resource<PicoPlacaResponse>> = retryTrigger
        .flatMapLatest {
            getPicoYPlacaUseCase()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Resource.Loading
        )

    fun retry() {
        viewModelScope.launch {
            retryTrigger.emit(Unit)
        }
    }
}
