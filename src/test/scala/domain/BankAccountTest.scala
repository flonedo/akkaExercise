package domain

import bank.domain.BankAccount
import org.scalatest.funsuite.AnyFunSuite

class BankAccountTest extends AnyFunSuite {

  def accountWithMoney(iban: String): BankAccount = BankAccount(iban, 10000.0)

  test("Deposit money with wrong IBAN") {
    val account = accountWithMoney("test")
    val amount = 10
    val action: Either[String, Option[BankAccount.BankAccountEvent]] =
      BankAccount.Deposit("owner", "wrong", amount).applyTo(account)
    assert(action.left.get equals "Wrong IBAN")
  }

  test("Withdraw money with wrong IBAN") {
    val account = accountWithMoney("test")
    val amount = 10
    val action: Either[String, Option[BankAccount.Withdrawn]] =
      BankAccount.Withdraw("owner", "wrong", amount).applyTo(account)
    assert(action.left.get equals "Wrong IBAN")
  }

  test("Deposit a negative amount") {
    val account = accountWithMoney("test")
    val action = BankAccount.Deposit("owner", account.iban, -10.0).applyTo(account)
    assert(action.left.get equals "Negative amount")
  }

  test("Deposit a neutral amount") {
    val account = accountWithMoney("test")
    val action = BankAccount.Deposit("owner", account.iban, 0.0).applyTo(account)
    assert(action.right.get.isEmpty)
  }

  test("Deposit a positive amount") {
    val account = accountWithMoney("test")
    val action = BankAccount.Deposit("owner", account.iban, 10.0).applyTo(account)
    def event: BankAccount.BankAccountEvent = action.right.get.get
    assert(account.balance + 10.0 == event.applyTo(account).balance)
  }

  test("Withdraw a negative amount") {
    val account = accountWithMoney("test")
    val action = BankAccount.Withdraw("owner", account.iban, -10.0).applyTo(account)
    assert(action.left.get equals "Negative amount")
  }

  test("Withdraw a neutral amount") {
    val account = accountWithMoney("test")
    val action = BankAccount.Withdraw("owner", account.iban, 0.0).applyTo(account)
    assert(action.right.get.isEmpty)
  }

  test("Withdraw a positive amount greater than the balance") {
    val account = accountWithMoney("test")
    val action = BankAccount.Withdraw("owner", account.iban, 100000.0).applyTo(account)
    assert(action.left.get equals "Insufficient funds")
  }

  test("Withdraw a positive amount less than the balance") {
    val account = accountWithMoney("test")
    val action = BankAccount.Withdraw("owner", account.iban, 150.0).applyTo(account)
    def event: BankAccount.BankAccountEvent = action.right.get.get
    assert(account.balance - 150.0 == event.applyTo(account).balance)
  }
}
