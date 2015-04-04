name := "sclip"

version := "0.2-SNAPSHOT"

organization := "org.nbrahms"

scalaVersion := "2.10.5"

scalacOptions ++= Seq("-deprecation", "-feature")

scalaSource in Compile := baseDirectory.value / "src"

scalaSource in Test := baseDirectory.value / "test-src"

unmanagedSourceDirectories in Compile <<= (scalaSource in Compile)(Seq(_))

unmanagedSourceDirectories in Test <<= (scalaSource in Test)(Seq(_))

sourcesInBase := false

resolvers ++= Seq(
  "Artifactory at repo.scala-sbt.org" at "http://repo.scala-sbt.org/scalasbt/libs-releases",
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots")
)

libraryDependencies ++= Seq(
  if (scalaVersion.value startsWith "2.10") {
    "com.chuusai" % ("shapeless_" + scalaVersion.value) % "2.0.0"
  } else "com.chuusai" %% "shapeless" % "2.0.0",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test"
)

publishTo := Some(Resolver.file("file",  new File( "releases" )) )
