package bank.domain

import bank.domain.Domain.{DomainCommand, DomainEntity, DomainEvent}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class Person(fullName: String, bankAccounts: Vector[String]) extends DomainEntity

object Person {

  val empty = Person("", Vector.empty[String])

  def checkAccount(domainEntity: Person, owner: String, iban: String): Boolean = {
    if (domainEntity.fullName == owner) {
      domainEntity.bankAccounts.contains(iban)
    } else {
      false
    }
  }

  sealed trait PersonEvent extends DomainEvent[Person] {
    val fullName: String
  }

  sealed trait PersonCommand extends DomainCommand[Person, PersonEvent] {
    val fullName: String
  }

  implicit val dCloseBankAccount: Decoder[CloseBankAccount] = deriveDecoder
  implicit val eCloseBankAccount: Encoder[CloseBankAccount] = deriveEncoder
  case class CloseBankAccount(fullName: String, iban: String) extends PersonCommand {
    override def applyTo(domainEntity: Person): Either[String, Option[PersonEvent]] = {
      if (fullName == domainEntity.fullName) {
        if (domainEntity.bankAccounts.contains(iban)) {
          Right(Some(ClosedBankAccount(fullName, iban)))
        } else {
          Right(None)
        }
      } else {
        Left("Wrong person")
      }
    }
  }

  case class ClosedBankAccount(fullName: String, iban: String) extends PersonEvent {
    override def applyTo(domainEntity: Person): Person = {
      domainEntity.copy(bankAccounts = domainEntity.bankAccounts.filter(_ != iban))
    }
  }

  implicit val dOpenBankAccount: Decoder[OpenBankAccount] = deriveDecoder
  implicit val eOpenBankAccount: Encoder[OpenBankAccount] = deriveEncoder
  case class OpenBankAccount(fullName: String) extends PersonCommand {
    override def applyTo(domainEntity: Person): Either[String, Option[PersonEvent]] = {
      if (fullName == domainEntity.fullName) {
        val iban: String = java.util.UUID.randomUUID.toString
        Right(Some(OpenedBankAccount(fullName, iban)))
      } else {
        Left("Wrong person")
      }
    }
  }

  case class OpenedBankAccount(fullName: String, iban: String) extends PersonEvent {
    override def applyTo(domainEntity: Person): Person = {
      domainEntity.copy(
        bankAccounts = domainEntity.bankAccounts :+ iban
      )
    }
  }

  implicit val dCreatePerson: Decoder[CreatePerson] = deriveDecoder
  implicit val eCreatePerson: Encoder[CreatePerson] = deriveEncoder
  case class CreatePerson(fullName: String) extends PersonCommand {
    override def applyTo(domainEntity: Person): Either[String, Option[CreatedPerson]] = {
      domainEntity match {
        case Person.empty                           => Right(Some(CreatedPerson(fullName)))
        case _ if domainEntity.fullName == fullName => Right(None)
        case _                                      => Left("error: person data is already initialized")
      }
    }
  }

  case class CreatedPerson(fullName: String) extends PersonEvent {
    def applyTo(domainEntity: Person): Person = {
      domainEntity.copy(fullName = fullName)
    }
  }

}
