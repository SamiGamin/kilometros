package co.samidev.kilometrix.domain.repository

import co.samidev.kilometrix.core.util.Resource
import co.samidev.kilometrix.domain.model.PicoPlacaResponse
import kotlinx.coroutines.flow.Flow

interface PicoYPlacaRepository {
    fun getPicoYPlacaData(): Flow<Resource<PicoPlacaResponse>>
}
