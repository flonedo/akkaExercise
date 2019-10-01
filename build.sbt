name := "akkaexercise"

version := "0.1"

scalaVersion := "2.12.10"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-cluster-sharding" % "2.5.25",
  "com.typesafe.akka" %% "akka-cluster-tools" % "2.5.25",
  "com.typesafe.akka" %% "akka-remote" % "2.5.25",
  "com.typesafe.akka" %% "akka-actor" % "2.5.25",
  "com.typesafe.akka" %% "akka-slf4j" % "2.5.25",
  "com.typesafe.akka" %% "akka-discovery" % "2.5.25",
  "com.typesafe.akka" %% "akka-persistence-query" % "2.5.25",
  "com.typesafe.akka" %% "akka-persistence-cassandra" % "0.99",
  "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % "0.99",
  "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % "1.0.3",
  "com.lightbend.akka.management" %% "akka-management-cluster-http" % "1.0.3",
  "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api" % "1.0.3",
  "com.github.TanUkkii007" %% "akka-cluster-custom-downing" % "0.0.12",
  "com.thesamet.scalapb" %% "scalapb-runtime" % "0.9.0" % "protobuf",
  "com.thesamet.scalapb" %% "lenses" % "0.9.0",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.10",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  "ch.qos.logback.contrib" % "logback-jackson" % "0.1.5",
  "ch.qos.logback.contrib" % "logback-json-classic" % "0.1.5",
  "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.5.15.2" % Test,
  "com.typesafe.akka" %% "akka-testkit" % "2.5.25" % Test,
  "org.scalatest" %% "scalatest" % "3.0.8" % Test,
  "org.scalatestplus" %% "scalatestplus-junit" % "1.0.0-SNAP9" % Test
)

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-encoding",
  "utf8",
  "-feature",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-Ydelambdafy:method",
  "-target:jvm-1.8",
  "-language:postfixOps"
)

javacOptions in Compile ++= Seq("-Xlint:unchecked", "-Xlint:deprecation")
