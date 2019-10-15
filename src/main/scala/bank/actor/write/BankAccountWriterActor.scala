package bank.actor.write

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, ReceiveTimeout, Status}
import akka.cluster.sharding.ShardRegion
import akka.event.LoggingReceive
import akka.pattern.{ask, pipe}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.util.Timeout
import bank.AppConfig
import bank.actor.Messages._
import bank.domain.BankAccount
import bank.domain.BankAccount.{BankAccountCommand, BankAccountEvent, Deposited, Withdrawn}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class BankAccountWriterActor extends Actor with ActorSharding with PersistentActor with ActorLogging {

  override implicit val system: ActorSystem = context.system
  private implicit val timeout: Timeout = Timeout(AppConfig.askTimeout)

  override def persistenceId: String = s"${BankAccountWriterActor.name}-${self.path.name}"

  var state: BankAccount = BankAccount.empty

  context.setReceiveTimeout(120.seconds)
  private def receivePassivate: Receive = {
    case ReceiveTimeout        => context.parent ! ShardRegion.Passivate
    case ShardRegion.Passivate => context.stop(self)
  }

  override def receiveCommand: Receive = {
    case bankOperation: BankAccount.BankAccountCommand =>
      bankOperation.applyTo(state) match {

        case Right(Some(event: Withdrawn)) =>
          context.become(receiveStatus(sender, event))
          val future = personRegion ? CheckAccountProperty(event.owner, event.iban)
          future pipeTo self

        case Right(Some(event: Deposited)) =>
          context.become(receiveStatus(sender, event))
          val future = personRegion ? CheckAccountProperty(event.owner, event.iban)
          future pipeTo self

        case Right(Some(event)) => persistEvent(event)

        case Right(None) =>
          if (bankOperation.isInstanceOf[BankAccount.Create]) {
            sender() ! AccountAlreadyCreated
          } else {
            sender ! Done
          }
        case Left(error) => sender() ! error
      }
  }

  private def receiveStatus(sender: ActorRef, event: BankAccountEvent): Receive = {
    case AccountPropertyConfimed =>
      persistEvent(event, sender)
      context.unbecome()
      unstashAll()
    case AccountPropertyNotConfimed =>
      sender ! "You do not have account with that iban"
      context.unbecome()
      unstashAll()
    case Status.Failure =>
      sender ! "Failure"
      context.unbecome()
      unstashAll()
    case c: BankAccountCommand =>
      stash()
    case error: String =>
      context.unbecome()
      sender ! error
      unstashAll()
  }

  private def persistEvent(event: BankAccountEvent, actor: ActorRef): Unit = {
    persist(event) { _ =>
      state = update(state, event)
      log.info(event.toString)
      if (lastSequenceNr % snapShotInterval == 0 && lastSequenceNr != 0) {
        saveSnapshot(state)
      }
      actor ! Done
    }
  }

  private def persistEvent(event: BankAccountEvent): Unit = {
    persistEvent(event, sender)
  }

  protected def update(state: BankAccount, event: BankAccount.BankAccountEvent): BankAccount = event.applyTo(state)

  val snapShotInterval = 10

  override def receiveRecover: Receive = receivePassivate orElse LoggingReceive {
    case RecoveryCompleted => log.info("Recovery completed!")
    case event: BankAccount.BankAccountEvent =>
      state = update(state, event)
    case SnapshotOffer(_, snapshot: BankAccount) => state = snapshot
    case unknown                                 => log.error(s"Received unknown message in receiveRecover:$unknown")
  }

}

object BankAccountWriterActor {

  val numberOfShards = 100

  val name = "bank-account-writer-actor"

  val bankAccountDetailsTag: String = "bank-account"

  def props(): Props = Props(new BankAccountWriterActor())

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case m: BankAccountCommand => (m.iban, m)
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    def computeShardId(entityId: ShardRegion.EntityId): ShardRegion.ShardId =
      (math.abs(entityId.hashCode()) % numberOfShards).toString

    {
      case m: BankAccountCommand       => computeShardId(m.iban.toString)
      case ShardRegion.StartEntity(id) => computeShardId(id)
    }
  }

}
