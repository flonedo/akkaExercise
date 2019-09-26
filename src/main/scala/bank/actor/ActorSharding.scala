package bank.actor

import akka.actor.{ActorRef, ActorSystem}
import akka.cluster.sharding.ClusterSharding

trait ActorSharding {

  /** Provides either an ActorSystem for spawning actors. */
  implicit val system: ActorSystem

  def accountRegion: ActorRef = ClusterSharding(system).shardRegion("bank-account-writer-actor")
  def personRegion: ActorRef = ClusterSharding(system).shardRegion("person-writer-actor")
}
// TODO mancano tute le config di sharding, remoting, clustering e persistence
