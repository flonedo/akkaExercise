package bank

import akka.actor.{ActorSystem, PoisonPill, Props}
import akka.cluster.Cluster
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.event.LoggingAdapter
import akka.stream.UniqueKillSwitch
import bank.actor.{ActorSharding, BankAccountWriterActor, PersonWriterActor}
import bank.projector.BankAccountEventProjectorActor

import scala.concurrent.duration._
import scala.concurrent.Await

object BankApp extends ActorSharding with App {

  override implicit val system: ActorSystem = ActorSystem("bank")

  private lazy val cluster = Cluster(system)
  private implicit lazy val logger: LoggingAdapter = system.log

  cluster.registerOnMemberUp({
    logger.info(s"Member up: ${cluster.selfAddress}")
    createClusterShardingActors()
  })

  cluster.registerOnMemberRemoved({
    logger.info(s"Member removed: ${cluster.selfAddress}")
    cluster.leave(cluster.selfAddress)
  })

  private def startSystem(): Unit = {
    createClusterShardingActors()
    // This will start the server until the return key is pressed
    stopSystem()
  }

  private def stopSystem(): Unit = {
    logger.info(s"Terminating member: ${cluster.selfAddress}")
    system.terminate()
    Await.result(system.whenTerminated, 60 seconds)
  }

  private def createClusterShardingActors(): Unit = {
    ClusterSharding(system).start(
      typeName = BankAccountWriterActor.name,
      entityProps = Props[BankAccountWriterActor],
      settings = ClusterShardingSettings(system),
      extractEntityId = BankAccountWriterActor.extractEntityId,
      extractShardId = BankAccountWriterActor.extractShardId
    )
    ClusterSharding(system).start(
      typeName = PersonWriterActor.name,
      entityProps = Props[PersonWriterActor],
      settings = ClusterShardingSettings(system),
      extractEntityId = PersonWriterActor.extractEntityId,
      extractShardId = PersonWriterActor.extractShardId
    )
  }

  private def createClusterSingletonActors(): Unit = {
    system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = BankAccountEventProjectorActor.props,
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system)
      ),
      BankAccountEventProjectorActor.name
    )
  }

}
