package co.samidev.kilometrix.data.repository

import co.samidev.kilometrix.core.util.Resource
import co.samidev.kilometrix.domain.model.CityData
import co.samidev.kilometrix.domain.model.PicoPlacaResponse
import co.samidev.kilometrix.domain.model.Restriction
import co.samidev.kilometrix.domain.repository.PicoYPlacaRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PicoYPlacaRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : PicoYPlacaRepository {

    private val defaultFallbackResponse = PicoPlacaResponse(
        schemaVersion = "1.0",
        lastUpdated = "2026-07-25",
        holidays = listOf(
            "2026-01-01", "2026-01-12", "2026-03-23", "2026-04-02", "2026-04-03",
            "2026-05-01", "2026-05-18", "2026-06-08", "2026-06-15", "2026-06-29",
            "2026-07-20", "2026-08-07", "2026-08-17", "2026-10-12", "2026-11-02",
            "2026-11-16", "2026-12-08", "2026-12-25"
        ),
        cities = listOf(
            CityData(
                id = "bogota",
                name = "Bogotá",
                state = "Cundinamarca",
                sourceUrl = "https://www.movilidadbogota.gov.co",
                restrictions = listOf(
                    Restriction(
                        vehicleType = "PARTICULAR",
                        algorithm = "BOGOTA_PARITY",
                        schedule = "6:00 - 21:00",
                        description = "Días impares circulan 1-2-3-4-5 (restringidos 6-7-8-9-0). Días pares circulan 6-7-8-9-0 (restringidos 1-2-3-4-5).",
                        days = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes")
                    )
                )
            ),
            CityData(
                id = "medellin",
                name = "Medellín",
                state = "Antioquia",
                sourceUrl = "https://www.medellin.gov.co/movilidad",
                restrictions = listOf(
                    Restriction(
                        vehicleType = "PARTICULAR",
                        algorithm = "WEEKDAY_MAP",
                        schedule = "5:00 - 20:00",
                        description = "Restricción de 5:00 am a 8:00 pm según día de la semana.",
                        days = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes"),
                        weekdayRules = mapOf(
                            "1" to listOf(5, 8),
                            "2" to listOf(1, 4),
                            "3" to listOf(2, 0),
                            "4" to listOf(3, 6),
                            "5" to listOf(7, 9)
                        )
                    )
                )
            ),
            CityData(
                id = "cali",
                name = "Cali",
                state = "Valle del Cauca",
                sourceUrl = "https://www.cali.gov.co/movilidad",
                restrictions = listOf(
                    Restriction(
                        vehicleType = "PARTICULAR",
                        algorithm = "WEEKDAY_MAP",
                        schedule = "6:00 - 19:00",
                        description = "Restricción de 6:00 am a 7:00 pm.",
                        days = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes"),
                        weekdayRules = mapOf(
                            "1" to listOf(1, 2),
                            "2" to listOf(3, 4),
                            "3" to listOf(5, 6),
                            "4" to listOf(7, 8),
                            "5" to listOf(9, 0)
                        )
                    )
                )
            )
        )
    )

    override fun getPicoYPlacaData(): Flow<Resource<PicoPlacaResponse>> = callbackFlow {
        trySend(Resource.Loading)

        val collectionPath = "configuracion"
        val documentPath = "pico_y_placa"

        val documentRef = firestore.collection(collectionPath).document(documentPath)

        val subscription = documentRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                // Fallback to default local data if Firestore is offline or empty
                trySend(Resource.Success(defaultFallbackResponse, false))
                return@addSnapshotListener
            }

            try {
                val data = snapshot.toObject(PicoPlacaResponse::class.java)
                if (data != null && data.cities.isNotEmpty()) {
                    val isFromCache = snapshot.metadata.isFromCache
                    trySend(Resource.Success(data, isFromCache))
                } else {
                    trySend(Resource.Success(defaultFallbackResponse, false))
                }
            } catch (e: Exception) {
                trySend(Resource.Success(defaultFallbackResponse, false))
            }
        }

        awaitClose {
            subscription.remove()
        }
    }
}
