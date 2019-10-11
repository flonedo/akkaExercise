package bank.actor.projector

import akka.actor.{ActorSystem, Props}
import bank.actor.projector.export.PersonLogExporter
import bank.actor.write.PersonWriterActor
import bank.domain.Person.PersonEvent

class PersonEventProjectorActor(indexer: PersonLogExporter) extends CommonEventProjector[PersonEvent] {

  override implicit val system: ActorSystem = context.system

  override val indexerInside: PersonLogExporter = indexer

  override val detailsTag: String = PersonWriterActor.personDetailsTag

}

object PersonEventProjectorActor {

  val name = "person-event-projector-actor"

  def props(indexer: PersonLogExporter): Props =
    Props(new PersonEventProjectorActor(indexer))

}
