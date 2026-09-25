package co.samidev.kilometrix.data.sync

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resumen de monedero de Kipu para sincronización y configuración en Kilometrix.
 */
data class KipuWalletSummary(
    val id: String = "",
    val name: String = "",
    val currency: String = "COP",
    val currentBalanceMinor: Long = 0L,
    val icon: String = "wallet"
)

private fun DocumentSnapshot.extractNumber(vararg fieldNames: String): Long? {
    for (name in fieldNames) {
        val raw = get(name) ?: continue
        val value = when (raw) {
            is Number -> raw.toLong()
            is String -> raw.toDoubleOrNull()?.toLong()
            else -> null
        }
        if (value != null) return value
    }
    return null
}

private fun DocumentSnapshot.extractWalletBalance(): Long {
    return extractNumber(
        "currentBalanceMinor",
        "currentBalance",
        "balanceMinor",
        "balance",
        "saldoMinor",
        "saldo"
    ) ?: extractNumber(
        "initialBalanceMinor",
        "initialBalance",
        "saldoInicial",
        "saldoInicialMinor"
    ) ?: 0L
}

private fun DocumentSnapshot.extractInitialBalance(): Long {
    return extractNumber(
        "initialBalanceMinor",
        "initialBalance",
        "saldoInicial",
        "saldoInicialMinor"
    ) ?: 0L
}

@Singleton
class KipuWalletResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val db: FirebaseFirestore
) {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_KIPU_WALLET_ID = "kipu_target_wallet_id"
        private const val TAG = "KipuWalletResolver"
    }

    /**
     * Retorna el ID del monedero seleccionado manualmente o null si no se ha configurado.
     */
    fun getSelectedWalletId(): String? {
        return prefs.getString(KEY_KIPU_WALLET_ID, null)
    }

    /**
     * Guarda la preferencia del monedero seleccionado.
     */
    fun setSelectedWalletId(walletId: String) {
        prefs.edit().putString(KEY_KIPU_WALLET_ID, walletId).apply()
    }

    /**
     * Observa en tiempo real los monederos disponibles en Kipu (users/{userId}/wallets).
     */
    fun observeWallets(userId: String): Flow<List<KipuWalletSummary>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = db.collection("users").document(userId).collection("wallets")
            .orderBy("name")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error al observar monederos de Kipu: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }

                val list = snapshot?.documents?.mapNotNull { doc ->
                    val name = doc.getString("name") ?: return@mapNotNull null
                    val currency = doc.getString("currency") ?: "COP"
                    val balanceMinor = doc.extractWalletBalance()
                    val icon = doc.getString("icon") ?: "wallet"

                    KipuWalletSummary(
                        id = doc.id,
                        name = name,
                        currency = currency,
                        currentBalanceMinor = balanceMinor,
                        icon = icon
                    )
                } ?: emptyList()

                trySend(list)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Recalcula y actualiza en Firestore y localmente el saldo real de cada monedero
     * a partir de su saldo inicial y las transacciones registradas (ingresos, gastos, transferencias).
     */
    suspend fun recalculateAndUpdateWalletBalances(userId: String): Result<List<KipuWalletSummary>> {
        if (userId.isBlank()) return Result.failure(Exception("No hay sesión activa"))

        return try {
            val walletsCol = db.collection("users").document(userId).collection("wallets")
            val txCol = db.collection("users").document(userId).collection("transactions")

            // 1. Obtener monederos desde el servidor (o cache si offline)
            val walletsSnapshot = try {
                walletsCol.get(Source.SERVER).await()
            } catch (e: Exception) {
                walletsCol.get().await()
            }

            if (walletsSnapshot.isEmpty) {
                resolveTargetWalletId(userId)
                val refreshedSnap = walletsCol.get().await()
                val list = refreshedSnap.documents.mapNotNull { doc ->
                    val name = doc.getString("name") ?: return@mapNotNull null
                    val currency = doc.getString("currency") ?: "COP"
                    val balanceMinor = doc.extractWalletBalance()
                    val icon = doc.getString("icon") ?: "wallet"
                    KipuWalletSummary(doc.id, name, currency, balanceMinor, icon)
                }
                return Result.success(list)
            }

            // 2. Obtener transacciones contables para auditar y calcular saldo real
            val txSnapshot = try {
                txCol.get(Source.SERVER).await()
            } catch (e: Exception) {
                try { txCol.get().await() } catch (_: Exception) { null }
            }

            val deltasByWallet = mutableMapOf<String, Long>()
            val walletsWithTransactions = mutableSetOf<String>()

            txSnapshot?.documents?.forEach { txDoc ->
                val status = txDoc.getString("status")?.uppercase()
                if (status in listOf("CANCELLED", "VOID", "ANULADO", "REVERSED", "DELETED")) {
                    return@forEach
                }

                val type = txDoc.getString("type")?.uppercase() ?: ""
                val walletId = txDoc.getString("walletId")
                    ?: txDoc.getString("targetWalletId")
                    ?: txDoc.getString("sourceWalletId")
                val destWalletId = txDoc.getString("destinationWalletId")
                    ?: txDoc.getString("destWalletId")

                val amountMinor = txDoc.extractNumber("amountMinor", "amount") ?: 0L
                if (amountMinor <= 0L) return@forEach

                when {
                    type in listOf("INCOME", "INGRESO") -> {
                        if (!walletId.isNullOrBlank()) {
                            walletsWithTransactions.add(walletId)
                            deltasByWallet[walletId] = (deltasByWallet[walletId] ?: 0L) + amountMinor
                        }
                    }
                    type in listOf("EXPENSE", "GASTO") -> {
                        if (!walletId.isNullOrBlank()) {
                            walletsWithTransactions.add(walletId)
                            deltasByWallet[walletId] = (deltasByWallet[walletId] ?: 0L) - amountMinor
                        }
                    }
                    type in listOf("TRANSFER", "TRANSFERENCIA") -> {
                        if (!walletId.isNullOrBlank()) {
                            walletsWithTransactions.add(walletId)
                            deltasByWallet[walletId] = (deltasByWallet[walletId] ?: 0L) - amountMinor
                        }
                        if (!destWalletId.isNullOrBlank()) {
                            walletsWithTransactions.add(destWalletId)
                            deltasByWallet[destWalletId] = (deltasByWallet[destWalletId] ?: 0L) + amountMinor
                        }
                    }
                }
            }

            val updatedSummaries = mutableListOf<KipuWalletSummary>()
            val batch = db.batch()
            var hasUpdates = false

            for (doc in walletsSnapshot.documents) {
                val walletId = doc.id
                val name = doc.getString("name") ?: continue
                val currency = doc.getString("currency") ?: "COP"
                val icon = doc.getString("icon") ?: "wallet"

                val docBalance = doc.extractWalletBalance()
                val initialBalance = doc.extractInitialBalance()
                val hasTx = walletId in walletsWithTransactions

                val realBalance = if (hasTx) {
                    initialBalance + (deltasByWallet[walletId] ?: 0L)
                } else {
                    docBalance
                }

                val currentRawMinor = doc.getLong("currentBalanceMinor")
                if (currentRawMinor != realBalance) {
                    batch.set(
                        doc.reference,
                        mapOf(
                            "currentBalanceMinor" to realBalance,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )
                    hasUpdates = true
                }

                updatedSummaries.add(
                    KipuWalletSummary(
                        id = walletId,
                        name = name,
                        currency = currency,
                        currentBalanceMinor = realBalance,
                        icon = icon
                    )
                )
            }

            if (hasUpdates) {
                try {
                    batch.commit().await()
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo sincronizar batch de balances en Firestore: ${e.message}")
                }
            }

            Result.success(updatedSummaries)
        } catch (e: Exception) {
            Log.e(TAG, "Error recalculando balances de monederos: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Resuelve de forma determinista el ID del monedero contable de Kipu:
     * 1. Si existe un walletId en preferencias y el documento aún existe en Firestore, lo usa.
     * 2. Si no, consulta users/{userId}/wallets y busca uno cuyo nombre contenga "Trabajo" o "Kilometrix".
     * 3. Si no encuentra ninguno coincidente, selecciona el primer monedero disponible.
     * 4. Si la colección de monederos está vacía, crea automáticamente un monedero "Trabajo" compatible con Kipu.
     * 5. Almacena en caché el walletId elegido.
     */
    suspend fun resolveTargetWalletId(userId: String): String {
        val cachedWalletId = getSelectedWalletId()
        val walletsCol = db.collection("users").document(userId).collection("wallets")

        if (!cachedWalletId.isNullOrBlank()) {
            try {
                val cachedDoc = walletsCol.document(cachedWalletId).get().await()
                if (cachedDoc.exists()) {
                    return cachedWalletId
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo verificar el monedero en caché ($cachedWalletId): ${e.message}")
            }
        }

        // Consultar monederos existentes en Firestore
        try {
            val snapshot = walletsCol.get().await()
            val docs = snapshot.documents

            if (docs.isNotEmpty()) {
                val preferredDoc = docs.firstOrNull { doc ->
                    val name = doc.getString("name")?.lowercase() ?: ""
                    name.contains("trabajo") || name.contains("kilometrix")
                } ?: docs.first()

                val resolvedId = preferredDoc.id
                setSelectedWalletId(resolvedId)
                return resolvedId
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error consultando monederos en Firestore: ${e.message}", e)
        }

        // Si no existe ningún monedero, crear uno por defecto compatible con Kipu
        return try {
            val newDocRef = walletsCol.document()
            val defaultWallet = mapOf(
                "name" to "Trabajo",
                "icon" to "briefcase",
                "currency" to "COP",
                "initialBalanceMinor" to 0L,
                "currentBalanceMinor" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
                "schemaVersion" to 1
            )
            newDocRef.set(defaultWallet, SetOptions.merge()).await()
            val createdId = newDocRef.id
            setSelectedWalletId(createdId)
            createdId
        } catch (e: Exception) {
            Log.e(TAG, "Error creando monedero de trabajo por defecto en Kipu: ${e.message}", e)
            ""
        }
    }

    /**
     * Crea un nuevo monedero en Kipu (users/{userId}/wallets) y lo selecciona como el activo.
     */
    suspend fun createWallet(
        userId: String,
        name: String,
        initialBalance: Long = 0L,
        icon: String = "briefcase"
    ): Result<String> {
        if (userId.isBlank()) return Result.failure(Exception("No hay sesión activa"))
        if (name.isBlank()) return Result.failure(Exception("El nombre del monedero no puede estar vacío"))

        return try {
            val walletsCol = db.collection("users").document(userId).collection("wallets")
            val newDocRef = walletsCol.document()
            val newWallet = mapOf(
                "name" to name.trim(),
                "icon" to icon,
                "currency" to "COP",
                "initialBalanceMinor" to initialBalance,
                "currentBalanceMinor" to initialBalance,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
                "schemaVersion" to 1
            )
            newDocRef.set(newWallet, SetOptions.merge()).await()
            val createdId = newDocRef.id
            setSelectedWalletId(createdId)
            Result.success(createdId)
        } catch (e: Exception) {
            Log.e(TAG, "Error creando monedero en Kipu: ${e.message}", e)
            Result.failure(e)
        }
    }
}
