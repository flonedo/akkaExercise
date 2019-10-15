package actor

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.persistence.query.TimeBasedUUID
import akka.testkit.{DefaultTimeout, ImplicitSender, TestKit}
import bank.AppConfig
import bank.actor.projector.BankAccountEventProjectorActor
import bank.actor.projector.export.BankAccountLogExporter
import bank.actor.write.BankAccountWriterActor
import bank.domain.BankAccount
import bank.domain.BankAccount.BankAccountEvent
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, Inside, Matchers}

class BankAccountEventProjectorActorSpec
    extends TestKit(ActorSystem(AppConfig.serviceName))
    with DefaultTimeout
    with ImplicitSender
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with Inside {

  val dbFilePath: String = AppConfig.dbFilePath
  val offsetFilePath: String = AppConfig.offsetFilePath
  val projector: ActorRef = system.actorOf(
    ClusterSingletonManager.props(
      singletonProps = BankAccountEventProjectorActor.props(new BankAccountLogExporter(dbFilePath, offsetFilePath)),
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(system)
    ),
    BankAccountEventProjectorActor.name
  )

  val cluster: ActorRef = ClusterSharding(system).start(
    typeName = BankAccountWriterActor.name,
    entityProps = BankAccountWriterActor.props(),
    settings = ClusterShardingSettings(system),
    extractEntityId = BankAccountWriterActor.extractEntityId,
    extractShardId = BankAccountWriterActor.extractShardId
  )

  val timeBasedUUID = TimeBasedUUID(UUID.fromString("50554d6e-29bb-11e5-b345-feff819cdc9f"))

  override def afterAll: Unit = {
    shutdown()
  }

  "projection" when {
    "some events are received" should {
      "generate updates" in {
        val event1 = BankAccount.Created("someiban")
        val event2 = BankAccount.Deposited("owner", "someiban", 2)
        val event3 = BankAccount.Withdrawn("owner", "someiban", 1)
        val events: Seq[BankAccountEvent] = Seq(event1, event2, event3)

        val indexer = new BankAccountLogExporter(dbFilePath, offsetFilePath)
        val esRequests =
          indexer.indexEvents(events, timeBasedUUID)

        esRequests should be(Right(timeBasedUUID))

      }
    }
  }

  "projector" when {
    "some events are received" should {
      "generate updates" in {
        val event = BankAccount.Created("someiban")
        val indexer = new BankAccountLogExporter(dbFilePath, offsetFilePath)
        val esRequests = indexer.project(event, timeBasedUUID)
        esRequests should be(Right(timeBasedUUID))

      }
    }
  }

  "offset writer" when {
    "some offsets are received" should {
      "generate updates" in {
        val indexer = new BankAccountLogExporter(dbFilePath, offsetFilePath)
        val esRequests = indexer.writeOffset(timeBasedUUID)
        esRequests should be(Right(timeBasedUUID))

      }
    }
  }

  "offset reader" when {
    "some offset requests are received received" should {
      "read latest indexed offset from file" in {
        val indexer = new BankAccountLogExporter(dbFilePath, offsetFilePath)
        val esRequests = indexer.readOffset()
        esRequests should be(Some(timeBasedUUID))

      }
    }
  }
}
