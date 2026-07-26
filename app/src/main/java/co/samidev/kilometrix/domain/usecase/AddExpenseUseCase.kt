package co.samidev.kilometrix.domain.usecase

import co.samidev.kilometrix.domain.model.VehicleExpense
import co.samidev.kilometrix.domain.repository.ExpenseRepository
import javax.inject.Inject

class AddExpenseUseCase @Inject constructor(
    private val expenseRepository: ExpenseRepository
) {
    suspend operator fun invoke(expense: VehicleExpense): Result<String> {
        return expenseRepository.addExpense(expense)
    }
}
