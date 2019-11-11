package bank.actor.adapter

import akka.persistence.journal.{EventAdapter, EventSeq, Tagged}
import bank.actor.write.BankAccountWriterActor
import bank.domain.BankAccount

class BankAccountAdapter extends EventAdapter {
  override def manifest(event: Any): String = event.getClass.getSimpleName

  val tag = Set(BankAccountWriterActor.bankAccountDetailsTag)

  override def fromJournal(event: Any, manifest: String): EventSeq = event match {
    case event: BankAccount.Created   => EventSeq(event)
    case event: BankAccount.Deposited => EventSeq(event)
    case event: BankAccount.Withdrawn => EventSeq(event)
  }

  override def toJournal(event: Any): Any = event match {
    case event: BankAccount.Created   => Tagged(event, tag)
    case event: BankAccount.Deposited => Tagged(event, tag)
    case event: BankAccount.Withdrawn => Tagged(event, tag)

  }

}
