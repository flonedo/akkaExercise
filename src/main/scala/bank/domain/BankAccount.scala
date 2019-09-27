package bank.domain

import bank.domain.Domain.{DomainCommand, DomainEntity, DomainEvent}

case class BankAccount(iban: String, balance: Double) extends DomainEntity

object BankAccount {

  val empty = BankAccount("", 0)

  sealed trait BankAccountEvent extends DomainEvent[BankAccount] {
    val iban: String
  }

  sealed trait BankAccountCommand extends DomainCommand[BankAccount, BankAccountEvent] {
    val iban: String
  }

  abstract class BankAccountLogic(iban: String, amount: Double) extends BankAccountCommand {
     def applyTo(domainEntity: BankAccount, callback: () => Either[String, Option[BankAccountEvent]]): Either[String, Option[BankAccountEvent]] =
      if (amount < 0) {
        Left("Negative amount")
      } else if (amount == 0) {
        Right(None)
      } else {
        if (domainEntity.iban == iban) {
          callback()
        } else {
          Left("Wrong IBAN")
        }
      }
  }

  case class Deposit(override val iban: String, amount: Double) extends BankAccountLogic(iban = iban, amount = amount) {
    override def applyTo(domainEntity: BankAccount): Either[String, Option[BankAccountEvent]] =
      super.applyTo(domainEntity, () => {
        Right(Some(Deposited(iban, amount)))
      })
  }

  case class Deposited(iban: String, amount: Double) extends BankAccountEvent {
    override def applyTo(domainEntity: BankAccount): BankAccount =
      domainEntity.copy(balance = domainEntity.balance + amount)
  }

  case class Withdraw(override val iban: String, amount: Double) extends BankAccountLogic(iban = iban, amount = amount) {
    override def applyTo(domainEntity: BankAccount): Either[String, Option[BankAccountEvent]] =
      super.applyTo(domainEntity, () => {
        if (domainEntity.balance >= amount) {
          Right(Some(Withdrawn(iban, amount)))
        } else {
          Left("Not enough money")
        }
      })
  }

  case class Withdrawn(iban: String, amount: Double) extends BankAccountEvent {
    override def applyTo(domainEntity: BankAccount): BankAccount =
      domainEntity.copy(balance = domainEntity.balance - amount)
  }

}
