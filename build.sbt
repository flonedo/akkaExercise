name := "akkaexercise"

version := "0.1"

scalaVersion := "2.13.1"

scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-unchecked",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-value-discard",
  "-Xfuture",
  "-Xexperimental"
)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-cluster-sharding" % "2.5.25",
  "com.typesafe.akka" %% "akka-actor" % "2.5.25",
  "org.scalatest" %% "scalatest" % "3.0.8" % "test",
  "com.typesafe.akka" %% "akka-persistence-cassandra" % "0.99",
  "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % "0.99"
)
