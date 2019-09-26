package bank.domain

object Domain {

  trait DomainEntity

  trait DomainEvent[T <: DomainEntity] {
    def applyTo(domainEntity: T): T
  }

  trait DomainCommand[T <: DomainEntity, +E <: DomainEvent[T]] {
    def applyTo(domainEntity: T): Either[String, Option[E]]
  }
}
