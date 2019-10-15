package bank.actor

object Messages {

  case object Done
  case object AccountAlreadyCreated
  case object AccountPropertyConfimed
  case object AccountPropertyNotConfimed

  case class CheckAccountProperty(owner: String, iban: String)
}
