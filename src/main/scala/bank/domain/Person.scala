package bank.domain

import bank.domain.Domain.{DomainCommand, DomainEntity, DomainEvent}

case class Person(fullName: String, bankAccounts: Vector[BankAccount]) extends DomainEntity

object Person {

  sealed trait PersonEvent extends DomainEvent[Person] {
    val fullName: String
  }

  sealed trait PersonCommand extends DomainCommand[Person, PersonEvent]{
    val fullName: String
  }

  case class CloseBankAccount(fullName: String, bankAccount: BankAccount) extends PersonCommand {
    override def applyTo(domainEntity: Person): Either[String, Option[PersonEvent]] = {
      if (fullName == domainEntity.fullName) {
        if (domainEntity.bankAccounts.contains(bankAccount)) {
          Right(Some(ClosedBankAccount(fullName, bankAccount)))
        }
        else Right(None)
      }
      else Left("Wrong person")
    }
  }

  case class ClosedBankAccount(fullName: String, bankAccount: BankAccount) extends PersonEvent {
    override def applyTo(domainEntity: Person): Person =
      domainEntity.copy(bankAccounts = domainEntity.bankAccounts.filter(_ != bankAccount))
  }

  case class OpenBankAccount(fullName: String) extends PersonCommand {
    override def applyTo(domainEntity: Person): Either[String, Option[PersonEvent]] = {
      if (fullName == domainEntity.fullName) {
        Right(Some(OpenedBankAccount(fullName)))
      }
      else Left("Wrong person")
    }
  }

  case class OpenedBankAccount(fullName: String) extends PersonEvent {
    override def applyTo(domainEntity: Person): Person =
      domainEntity.copy(bankAccounts = domainEntity.bankAccounts :+ BankAccount(iban = java.util.UUID.randomUUID().toString, balance = 0))
  }


}
