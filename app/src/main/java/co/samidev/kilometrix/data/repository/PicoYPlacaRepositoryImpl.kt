package co.samidev.kilometrix.data.repository

import co.samidev.kilometrix.core.util.Resource
import co.samidev.kilometrix.domain.model.PicoPlacaResponse
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

    override fun getPicoYPlacaData(): Flow<Resource<PicoPlacaResponse>> = callbackFlow {
        trySend(Resource.Loading)

        val collectionPath = "configuracion"
        val documentPath = "pico_y_placa_dev"

        val documentRef = firestore.collection(collectionPath).document(documentPath)

        val subscription = documentRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Resource.Error(error))
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                try {
                    val data = snapshot.toObject(PicoPlacaResponse::class.java)
                    if (data != null) {
                        val isFromCache = snapshot.metadata.isFromCache
                        trySend(Resource.Success(data, isFromCache))
                    } else {
                        trySend(Resource.Error(Exception("Failed to parse Pico y Placa data")))
                    }
                } catch (e: Exception) {
                    trySend(Resource.Error(e))
                }
            } else {
                trySend(Resource.Error(Exception("Pico y Placa configuration document does not exist")))
            }
        }

        awaitClose {
            subscription.remove()
        }
    }
}
