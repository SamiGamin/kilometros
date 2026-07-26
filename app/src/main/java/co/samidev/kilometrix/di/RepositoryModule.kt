package co.samidev.kilometrix.di

import co.samidev.kilometrix.data.repository.ExpenseRepositoryImpl
import co.samidev.kilometrix.data.repository.PicoYPlacaRepositoryImpl
import co.samidev.kilometrix.data.repository.UserRepositoryImpl
import co.samidev.kilometrix.data.repository.VehicleRepositoryImpl
import co.samidev.kilometrix.domain.repository.ExpenseRepository
import co.samidev.kilometrix.domain.repository.PicoYPlacaRepository
import co.samidev.kilometrix.domain.repository.UserRepository
import co.samidev.kilometrix.domain.repository.VehicleRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPicoYPlacaRepository(
        picoYPlacaRepositoryImpl: PicoYPlacaRepositoryImpl
    ): PicoYPlacaRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(
        userRepositoryImpl: UserRepositoryImpl
    ): UserRepository

    @Binds
    @Singleton
    abstract fun bindVehicleRepository(
        vehicleRepositoryImpl: VehicleRepositoryImpl
    ): VehicleRepository

    @Binds
    @Singleton
    abstract fun bindExpenseRepository(
        expenseRepositoryImpl: ExpenseRepositoryImpl
    ): ExpenseRepository

    companion object {
        @Provides
        @Singleton
        fun provideFirebaseFirestore(): FirebaseFirestore {
            return FirebaseFirestore.getInstance()
        }

        @Provides
        @Singleton
        fun provideFirebaseAuth(): FirebaseAuth {
            return FirebaseAuth.getInstance()
        }
    }
}
