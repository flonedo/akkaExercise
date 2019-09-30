name := "akkaexercise"

version := "0.1"

scalaVersion := "2.13.1"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-cluster-sharding" % "2.5.25",
  "com.typesafe.akka" %% "akka-actor" % "2.5.25",
  "org.scalatest" %% "scalatest" % "3.0.8" % "test",
  "com.typesafe.akka" %% "akka-persistence-cassandra" % "0.99",
  "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % "0.99",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  "com.typesafe.akka" %% "akka-testkit" % "2.5.25" % Test,
  "org.scalatest" %% "scalatest" % "2.5.25" % Test,
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
