package bank.actor.projector.export

import bank.domain.Person.PersonEvent

class PersonLogExporter(eventsFile: String, offsetFile: String) extends CommonLogExporter[PersonEvent] {

  override val name: String = "person-export"
  override val eventsTag: String = "person-details"

  override val eventsFilePath: String = eventsFile
  override val offsetFilePath: String = offsetFile

}
