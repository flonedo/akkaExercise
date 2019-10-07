package bank.actor.adapter

import akka.persistence.journal.{EventAdapter, EventSeq, Tagged}
import bank.actor.write.PersonWriterActor
import bank.domain.Person

class PersonAdapter extends EventAdapter {
  override def manifest(event: Any): String = event.getClass.getSimpleName

  private val tag = Set(PersonWriterActor.personDetailsTag)
  override def fromJournal(event: Any, manifest: String): EventSeq = event match {
    case event: Person.CreatedPerson     => EventSeq(event)
    case event: Person.OpenedBankAccount => EventSeq(event)
    case event: Person.ClosedBankAccount => EventSeq(event)
  }

  override def toJournal(event: Any): Any = event match {
    case event: Person.CreatedPerson     => Tagged(event, tag)
    case event: Person.OpenedBankAccount => Tagged(event, tag)
    case event: Person.ClosedBankAccount => Tagged(event, tag)
  }
}
