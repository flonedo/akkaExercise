package bank.actor.projector

import akka.actor.{ActorSystem, Props}
import bank.actor.projector.export.BankAccountLogExporter
import bank.actor.write.BankAccountWriterActor
import bank.domain.BankAccount.BankAccountEvent

class BankAccountEventProjectorActor(indexer: BankAccountLogExporter) extends CommonEventProjector[BankAccountEvent] {

  override implicit val system: ActorSystem = context.system

  override val indexerInside: BankAccountLogExporter = indexer

  override val detailsTag: String = BankAccountWriterActor.bankAccountDetailsTag

}

object BankAccountEventProjectorActor {

  val name = "bank-account-event-projector-actor"

  def props(indexer: BankAccountLogExporter): Props =
    Props(new BankAccountEventProjectorActor(indexer))

}
