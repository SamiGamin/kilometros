package co.samidev.kilometrix.domain.usecase

import co.samidev.kilometrix.core.util.Resource
import co.samidev.kilometrix.domain.model.PicoPlacaResponse
import co.samidev.kilometrix.domain.repository.PicoYPlacaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetPicoYPlacaUseCase @Inject constructor(
    private val repository: PicoYPlacaRepository
) {
    operator fun invoke(): Flow<Resource<PicoPlacaResponse>> {
        return repository.getPicoYPlacaData()
    }
}
