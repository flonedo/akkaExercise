package bank
import bank.domain.BankAccount
import org.scalatest.FunSuite

class TestBankAccount extends FunSuite {

	def accountWithMoney(iban: String) = BankAccount(iban, 100000.0)


	test("Deposit money with wrong IBAN") {
		val account = accountWithMoney("test")
		val action = BankAccount.Deposit("wrong", 10).applyTo(account)
		assert(action.left.get equals "Wrong IBAN")
	}

	test("Withdraw money with wrong IBAN") {
		val account = accountWithMoney("test")
		val action = BankAccount.Withdraw("wrong", 10).applyTo(account)
		assert(action.left.get equals "Wrong IBAN")
	}

	test("Withdraw money") {
		def testing(n: Int, account: BankAccount): Unit = {
			if(n == 0) return
			//Do action
			val x = scala.util.Random.nextInt(1000) - scala.util.Random.nextInt(500)
			val action = BankAccount.Withdraw(account.iban, x).applyTo(account)

			//Testing
			if (x > 0) {
				if (account.balance >= x) {
					def evento = action.right.get.get
					assert(account.balance - x == evento.applyTo(account).balance, "Error balance")
					testing(n - 1, evento.applyTo(account))
				}
				else assert(action.left.get equals "Not enought money")
			} else {
				if (x == 0) assert(action.right.get equals None)
				else assert(action.left.get equals "Negative amount")
				testing(n - 1, account)
			}

		}
		testing(10000, accountWithMoney("test"))
	}

	test("Deposit money") {
		def testing(n: Int, account: BankAccount): Unit = {
			if (n == 0) return
			//Do action
			val x = scala.util.Random.nextInt(1000) - scala.util.Random.nextInt(500)
			val action = BankAccount.Deposit(account.iban, x).applyTo(account)

			//Testing
			if (x > 0) {
				def evento = action.right.get.get
				assert(account.balance + x == evento.applyTo(account).balance, "Error balance")
				testing(n - 1, evento.applyTo(account))
			} else {
				if (x == 0) assert(action.right.get equals None)
				else assert(action.left.get equals "Negative amount")
				testing(n - 1, account)
			}
		}
		testing(10000, accountWithMoney("test"))
	}
}
