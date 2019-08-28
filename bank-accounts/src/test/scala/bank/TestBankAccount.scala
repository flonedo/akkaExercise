package bank
import bank.domain.BankAccount
import org.scalatest.FunSuite

class TestBankAccount extends FunSuite {

	def accountWithMoney(iban: String) = BankAccount(iban, 10000.0)
	def accountWithoutMoney(iban: String) = BankAccount(iban, 0.0)

	test("Deposit money into an empty bank account") {
		val account = accountWithoutMoney("test")
		for(x <- 1 to 100) {
			val x = scala.util.Random.nextInt(1000)
			val action = BankAccount.Deposit(account.iban, x).applyTo(account)
			assert(action.isRight, "Error while depositing money into empty account")
		}
	}

	test("Deposit money into a non-empty bank account") {
		val account = accountWithMoney("test2")
		for(x <- 1 to 100) {
			val x = scala.util.Random.nextInt(1000)
			val action = BankAccount.Deposit(account.iban, x).applyTo(account)
			assert(action.isRight, "Error while depositing money into non-empty account")
		}
	}

	test("Withdraw money from an empty account") {
		val account = accountWithoutMoney("test3")
		for(x <- 1 to 100) {
			val x = scala.util.Random.nextInt(1000)
			val action = BankAccount.Withdraw(account.iban, x).applyTo(account)
			assert(action.isLeft, "Error while withdrawing money from an empty account")
		}
	}

	test("Withdraw money from a non-empty account") {
		val account = accountWithoutMoney("test4")
		for(x <- 1 to 100) {
			val x = scala.util.Random.nextInt(5000)
			val action = BankAccount.Withdraw(account.iban, x).applyTo(account)
			if(x > account.balance)
				assert(action.isLeft, "Error while withdrawing money from a non-empty account")
			else
				assert(action.isRight, "Error while withdrawing money from a non-empty account")
		}
	}

	test("Deposit money into an empty bank account with wrong IBAN") {
		val account = accountWithoutMoney("test4")
		val action = BankAccount.Deposit("wrong", 10).applyTo(account)
		assert(action.isLeft, "Error while depositing money into an empty bank account with wrong IBAN")
	}

	test("Withdraw money into an empty bank account with wrong IBAN") {
		val account = accountWithoutMoney("test5")
		val action = BankAccount.Withdraw("wrong", 10).applyTo(account)
		assert(action.isLeft, "Error while withdrawing money into an empty bank account with wrong IBAN")
	}
}
