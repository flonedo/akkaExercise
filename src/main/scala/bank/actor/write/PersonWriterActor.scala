package bank.actor.write

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, ReceiveTimeout, Status}
import akka.cluster.sharding.ShardRegion
import akka.event.LoggingReceive
import akka.pattern.{ask, pipe}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.util.Timeout
import bank.AppConfig
import bank.actor.Messages._
import bank.domain.Person.{OpenedBankAccount, PersonCommand, PersonEvent}
import bank.domain.{BankAccount, Person}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class PersonWriterActor extends Actor with ActorSharding with PersistentActor with ActorLogging {

  override implicit val system: ActorSystem = context.system
  private implicit val timeout: Timeout = Timeout(AppConfig.askTimeout)

  override def persistenceId: String = s"${PersonWriterActor.name}-${self.path.name}"

  var state: Person = Person.empty

  context.setReceiveTimeout(120.seconds)
  private def receivePassivate: Receive = {
    case ReceiveTimeout        => context.parent ! ShardRegion.Passivate
    case ShardRegion.Passivate => context.stop(self)
  }

  override def receiveCommand: Receive = {
    case personOperation: PersonCommand =>
      personOperation.applyTo(state) match {
        case Right(Some(event @ OpenedBankAccount(_, iban))) =>
          context.become(receiveStatus(sender, event))
          val future = accountRegion ? BankAccount.Create(iban)
          future pipeTo self
        case Right(Some(event)) =>
          persistEvent(event)

        case Right(None) => sender() ! Done
        case Left(error) => sender() ! error
      }
    case CheckAccountProperty(owner, iban) =>
      if (Person.checkAccount(state, owner, iban)) {
        sender ! AccountPropertyConfimed
      } else {
        sender ! AccountPropertyNotConfimed
      }

  }

  private def receiveStatus(sender: ActorRef, event: PersonEvent): Receive = {
    case Done =>
      persistEvent(event, sender)
      context.unbecome()
      unstashAll()
    case AccountAlreadyCreated =>
      sender ! "An account with that iban already exists"
      context.unbecome()
      unstashAll()
    case Status.Failure =>
      sender ! "Failure"
      context.unbecome()
      unstashAll()
    case c: PersonCommand =>
      stash()
    case error: String =>
      context.unbecome()
      sender ! error
      unstashAll()
  }

  private def persistEvent(event: PersonEvent, actor: ActorRef): Unit = {
    persist(event) { _ =>
      state = update(state, event)
      log.info(event.toString)
      if (lastSequenceNr % snapShotInterval == 0 && lastSequenceNr != 0) {
        saveSnapshot(state)
      }
      actor ! Done
    }
  }

  private def persistEvent(event: PersonEvent): Unit = {
    persistEvent(event, sender)
  }

  protected def update(state: Person, event: PersonEvent): Person = event.applyTo(state)

  val snapShotInterval = 10

  override def receiveRecover: Receive = receivePassivate orElse LoggingReceive {
    case RecoveryCompleted => log.info("Recovery completed!")
    case event: PersonEvent =>
      state = update(state, event)
    case SnapshotOffer(_, snapshot: Person) => state = snapshot
    case unknown                            => log.error(s"Received unknown message in receiveRecover:$unknown")
  }

}

object PersonWriterActor {

  val numberOfShards = 100

  val name = "person-writer-actor"

  val personDetailsTag: String = "person"

  def props(): Props = Props(new PersonWriterActor())

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case m: PersonCommand        => (m.fullName, m)
    case c: CheckAccountProperty => (c.owner, c)
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    def computeShardId(entityId: ShardRegion.EntityId): ShardRegion.ShardId =
      (math.abs(entityId.hashCode()) % numberOfShards).toString

    {
      case m: PersonCommand            => computeShardId(m.fullName)
      case c: CheckAccountProperty     => computeShardId(c.owner)
      case ShardRegion.StartEntity(id) => computeShardId(id)
    }
  }

}
