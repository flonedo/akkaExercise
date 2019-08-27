package bank

import bank.domain.{BankAccount, Person}
import org.scalatest.FunSuite

class TestPerson extends FunSuite {

  def personWithAccount(name: String) = Person(name,
    Vector(BankAccount(scala.util.Random.nextInt(22).toString, scala.util.Random.nextDouble())))

  def personWithoutAccount(name: String) = Person(name, Vector.empty)

  test("A person can be open a bank account") {
    val person = personWithoutAccount("First, Last")
    val openAccount = Person.OpenBankAccount(person.fullName).applyTo(person)

    assert(openAccount.isRight, "person must be able to open a new account")

    val openAccountEvent = openAccount.right.get.get
    val updatedPerson = openAccountEvent.applyTo(person)

    assert(updatedPerson.bankAccounts.nonEmpty, "person must have a bank account")
  }

  test(" A person can close a bank account") {
    val person = personWithAccount("First, Last")
    val closeBankAccount = Person.CloseBankAccount(person.fullName, person.bankAccounts.head).applyTo(person)

    assert(closeBankAccount.isRight, "person must be ale to close its account")

    val closedAccountEvent = closeBankAccount.right.get.get
    val updatePerson = closedAccountEvent.applyTo(person)

    assert(updatePerson.bankAccounts.isEmpty, "person must not have a bank account")
  }

  test("A person cannot open a bank account to a different person"){
    val personA = personWithoutAccount("Person A")
    val personB = personWithoutAccount("Person B")
    val openAccount = Person.OpenBankAccount(personA.fullName).applyTo(personB)

    assert(openAccount.isLeft, "personA cannot open a bank account to personB")
    assert(openAccount.left.get == "Wrong person")
  }

  test("A person can close only accounts it owns"){
    val person = personWithAccount("First, Last")
    val account = BankAccount(scala.util.Random.nextInt(22).toString, 42)
    val closeAccount = Person.CloseBankAccount(person.fullName, account).applyTo(person)

    assert(closeAccount.isRight, "Closing a nonexistent account shouldn't cause error")
    assert(closeAccount.right.get.isEmpty, "closing a nonexistent account should result in no events")
  }

  test("A person cannot close another person's account"){
    val personA = personWithAccount("Person A")
    val personB = personWithAccount("Person B")
    val closeAccount = Person.CloseBankAccount(personA.fullName, personB.bankAccounts.head).applyTo(personB)

    assert(closeAccount.isLeft, "a person cannot close another person's account")
  }

}
