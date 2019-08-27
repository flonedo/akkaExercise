package bank.domain

import bank.domain.Domain.{DomainCommand, DomainEntity, DomainEvent}


case class BankAccount(iban: String, balance: Double) extends DomainEntity

object BankAccount {

  sealed trait BankAccountEvent extends DomainEvent[BankAccount] {
    val iban: String
  }

  sealed trait BankAccountCommand extends DomainCommand[BankAccount, BankAccountEvent] {
    val iban: String
  }

  case class Deposit(iban: String, amount: Double) extends BankAccountCommand {
    override def applyTo(domainEntity: BankAccount): Either[String, Option[BankAccountEvent]] =
      if (domainEntity.iban == iban) {
        if(amount == 0) {
          Right(None)
        } else {
          Right(Some(Deposited(iban, amount)))
        }
      } else {
        Left("todo")
      }
  }

  case class Deposited(iban: String, amount: Double) extends BankAccountEvent {
    override def applyTo(domainEntity: BankAccount): BankAccount = domainEntity.copy(balance = domainEntity.balance + amount)
  }


  case class Withdraw(iban: String, amount: Double) extends BankAccountCommand {
    override def applyTo(domainEntity: BankAccount): Either[String, Option[Withdrawn]] = ???
  }

  case class Withdrawn(iban: String, amount: Double) extends BankAccountEvent {
    override def applyTo(domainEntity: BankAccount): BankAccount = {
      ???
    }
  }


}