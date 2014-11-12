name := "Scala Command-Line Interface Parser"

version := "0.1-SNAPSHOT"

organization := "N. Brahms"

scalaVersion := "2.10.2"

scalacOptions ++= Seq("-deprecation", "-feature")

scalaSource in Compile := baseDirectory.value / "src"

scalaSource in Test := baseDirectory.value / "test-src"

sourcesInBase := false

resolvers ++= Seq(
  "Artifactory at repo.scala-sbt.org" at "http://repo.scala-sbt.org/scalasbt/libs-releases",
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots")
)

libraryDependencies ++= Seq(
  "com.chuusai" % "shapeless_2.10.4" % "2.0.0",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test"
)
