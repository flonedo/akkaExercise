package bank.projector

import akka.actor.{Actor, ActorSystem, Props}
import bank.actor.ActorSharding

class BankAccountEventProjectorActor extends Actor {

  // aprire stream in lettura sulla coda defli eventi persistiti su cassandra

  override implicit val system: ActorSystem = context.system

  override def receive: Receive = ???

}

object BankAccountEventProjectorActor {

  val name = "bank-account-event-projector-actor"

  def props: Props = Props(new BankAccountEventProjectorActor())

}
