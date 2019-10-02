package bank.domain

import bank.domain.Domain.{DomainCommand, DomainEntity, DomainEvent}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class BankAccount(iban: String, balance: Double) extends DomainEntity

object BankAccount {

  val empty = BankAccount("", 0)

  sealed trait BankAccountEvent extends DomainEvent[BankAccount] {
    val iban: String
  }

  sealed trait BankAccountCommand extends DomainCommand[BankAccount, BankAccountEvent] {
    val iban: String
  }
  implicit val dDeposit: Decoder[Deposit] = deriveDecoder[Deposit]
  implicit val eDeposit: Encoder[Deposit] = deriveEncoder[Deposit]
  case class Deposit(iban: String, amount: Double) extends BankAccountCommand {
    override def applyTo(domainEntity: BankAccount): Either[String, Option[BankAccountEvent]] =
      if (amount < 0) {
        Left("Negative amount")
      } else if (amount == 0) {
        Right(None)
      } else {
        if (domainEntity.iban == iban) {
          Right(Some(Deposited(iban, amount)))
        } else {
          Left("Wrong IBAN")
        }
      }
  }

  case class Deposited(iban: String, amount: Double) extends BankAccountEvent {
    override def applyTo(domainEntity: BankAccount): BankAccount =
      domainEntity.copy(balance = domainEntity.balance + amount)
  }

  implicit val dWithdraw: Decoder[Withdraw] = deriveDecoder[Withdraw]
  implicit val eWithdraw: Encoder[Withdraw] = deriveEncoder[Withdraw]
  case class Withdraw(iban: String, amount: Double) extends BankAccountCommand {
    override def applyTo(domainEntity: BankAccount): Either[String, Option[Withdrawn]] =
      if (amount < 0) {
        Left("Negative amount")
      } else if (amount == 0) {
        Right(None)
      } else {
        if (domainEntity.iban == iban) {
          if (domainEntity.balance >= amount) {
            Right(Some(Withdrawn(iban, amount)))
          } else {
            Left("Insufficient funds")
          }
        } else {
          Left("Wrong IBAN")
        }
      }
  }

  case class Withdrawn(iban: String, amount: Double) extends BankAccountEvent {
    override def applyTo(domainEntity: BankAccount): BankAccount =
      domainEntity.copy(balance = domainEntity.balance - amount)
  }

  implicit val dCreate: Decoder[Create] = deriveDecoder[Create]
  implicit val eCreate: Encoder[Create] = deriveEncoder[Create]
  case class Create(iban: String) extends BankAccountCommand {
    def applyTo(domainEntity: BankAccount): Either[String, Option[Created]] = {
      domainEntity match {
        case BankAccount.empty              => Right(Some(Created(iban)))
        case _ if domainEntity.iban == iban => Right(None)
        case _                              => Left("error: bank account is already initialized")
      }
    }
  }

  case class Created(iban: String) extends BankAccountEvent {
    def applyTo(domainEntity: BankAccount): BankAccount = {
      domainEntity.copy(iban = iban)
    }
  }

}
