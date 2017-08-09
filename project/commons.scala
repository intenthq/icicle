import sbt._
import Keys._

object Commons {
  val appVersion = "2.0.0"

  val pomInfo = (
    <url>https://github.com/intenthq/icicle</url>
    <licenses>
      <license>
        <name>The MIT License (MIT)</name>
        <url>https://github.com/intenthq/icicle/blob/master/LICENSE</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
    <scm>
      <url>git@github.com:intenthq/icicle.git</url>
      <connection>scm:git:git@github.com:intenthq/icicle.git</connection>
    </scm>
    <developers>
      <developer>
        <id>intenthq</id>
        <name>Intent HQ</name>
      </developer>
    </developers>
  )

  val settings: Seq[Def.Setting[_]] = Defaults.itSettings ++ Seq(
    scalaVersion := "2.11.11",
    organization := "com.intenthq.icicle",
    version := appVersion,
    publishMavenStyle := true,
    crossPaths := false,
    autoScalaLibrary := false,
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    pomExtra := pomInfo,
    resolvers += Opts.resolver.mavenLocalFile,
    libraryDependencies ++= Seq(
      "org.specs2" %% "specs2-core" % "3.8.9" % "it,test",
      "org.specs2" %% "specs2-mock" % "3.8.9" % "it,test"
    )
  )
}
