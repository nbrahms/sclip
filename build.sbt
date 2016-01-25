name := "sclip"

version := "0.2.3-SNAPSHOT"

organization := "org.nbrahms"

// Compilation

scalaVersion := "2.11.5"

scalacOptions ++= Seq("-deprecation", "-feature")

// Project layout

scalaSource in Compile := baseDirectory.value / "src"

scalaSource in Test := baseDirectory.value / "test-src"

unmanagedSourceDirectories in Compile <<= (scalaSource in Compile)(Seq(_))

unmanagedSourceDirectories in Test <<= (scalaSource in Test)(Seq(_))

sourcesInBase := false

// Dependencies

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

// Publishing

publishMavenStyle := true

licenses := Seq("MIT" -> url("http://opensource.org/licenses/MIT"))

homepage := Some(url("http://github.com/nbrahms/sclip"))

pomExtra := (<scm>
    <url>git@github.com:nbrahms/sclip.git</url>
    <connection>scm:git:git@github.com:nbrahms/sclip.git</connection>
  </scm>
  <developers>
    <developer>
      <id>nbrahms</id>
      <name>Nathan Brahms</name>
      <url>http://github.com/nbrahms</url>
    </developer>
  </developers>)

pomIncludeRepository := { _ => false }

publishTo := Some(Resolver.file("file", new File("releases")))
