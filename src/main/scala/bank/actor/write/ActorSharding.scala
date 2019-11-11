package bank.actor.write

import akka.actor.{ActorRef, ActorSystem}
import akka.cluster.sharding.ClusterSharding

trait ActorSharding {

  /** Provides either an ActorSystem for spawning actors. */
  implicit val system: ActorSystem

  def accountRegion: ActorRef = ClusterSharding(system).shardRegion("bank-account-writer-actor")
  def websocketRegion: ActorRef = ClusterSharding(system).shardRegion("websocket-handler-actor")
  //def personRegion: ActorRef = ClusterSharding(system).shardRegion("person-writer-actor")
}
