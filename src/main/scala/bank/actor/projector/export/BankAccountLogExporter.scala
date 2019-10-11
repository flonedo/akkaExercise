package bank.actor.projector.export

import bank.domain.BankAccount.BankAccountEvent

class BankAccountLogExporter(eventsFile: String, offsetFile: String) extends CommonLogExporter[BankAccountEvent] {

  override val name: String = "bank-account-export"
  override val eventsTag: String = "bank-account-details"

  override val eventsFilePath: String = eventsFile
  override val offsetFilePath: String = offsetFile

}
