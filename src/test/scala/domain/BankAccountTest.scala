package domain

import bank.domain.BankAccount
import org.scalatest.FunSuite

class TestBankAccount extends FunSuite {

  def accountWithMoney(iban: String) = BankAccount(iban, 10000.0)

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

  test("Deposit a negative amount") {
    val account = accountWithMoney("test")
    val action = BankAccount.Deposit(account.iban, -10.0).applyTo(account)
    assert(action.left.get equals "Negative amount")
  }

  test("Deposit a neutral amount") {
    val account = accountWithMoney("test")
    val action = BankAccount.Deposit(account.iban, 0.0).applyTo(account)
    assert(action.right.get equals None)
  }

  test("Deposit a positive amount") {
    val account = accountWithMoney("test")
    val action = BankAccount.Deposit(account.iban, 10.0).applyTo(account)
    def evento = action.right.get.get
    assert(account.balance + 10.0 == evento.applyTo(account).balance)
  }

  test("Withdraw a negative amount") {
    val account = accountWithMoney("test")
    val action = BankAccount.Withdraw(account.iban, -10.0).applyTo(account)
    assert(action.left.get equals "Negative amount")
  }

  test("Withdraw a neutral amount") {
    val account = accountWithMoney("test")
    val action = BankAccount.Withdraw(account.iban, 0.0).applyTo(account)
    assert(action.right.get equals None)
  }

  test("Withdraw a positive amount greater than the balance") {
    val account = accountWithMoney("test")
    val action = BankAccount.Withdraw(account.iban, 100000.0).applyTo(account)
    assert(action.left.get equals "Not enought money")
  }

  test("Withdraw a positive amount less than the balance") {
    val account = accountWithMoney("test")
    val action = BankAccount.Withdraw(account.iban, 150.0).applyTo(account)
    def evento = action.right.get.get
    assert(account.balance - 150.0 == evento.applyTo(account).balance)
  }

  test("Withdraw money") {
    def testing(n: Int, account: BankAccount): Unit = {
      if (n == 0) return
      //Do action
      val x = scala.util.Random.nextInt(1000) - scala.util.Random.nextInt(1000)
      val action = BankAccount.Withdraw(account.iban, x).applyTo(account)

      //Testing
      if (x > 0) {
        if (account.balance >= x) {
          def evento = action.right.get.get
          assert(account.balance - x == evento.applyTo(account).balance, "Error balance")
          testing(n - 1, evento.applyTo(account))
        } else assert(action.left.get equals "Not enought money")
      } else {
        if (x == 0) assert(action.right.get equals None)
        else assert(action.left.get equals "Negative amount")
        testing(n - 1, account)
      }

    }
    testing(100, accountWithMoney("test"))
  }

  test("Deposit money") {
    def testing(n: Int, account: BankAccount): Unit = {
      if (n == 0) return
      //Do action
      val x = scala.util.Random.nextInt(1000) - scala.util.Random.nextInt(1000)
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
    testing(100, accountWithMoney("test"))
  }
}
