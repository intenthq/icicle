import Commons._

scalaVersion in ThisBuild := "2.11.11"

lazy val root: Project = Project(
  id        = "root",
  base      = file("."),
  settings  = Defaults.coreDefaultSettings ++ Seq(
    publishArtifact := false,
    publishTo := Some(Resolver.file("Unused transient repository", file("target/unusedrepo")))
  ),
  aggregate = Seq(core, jedis)
)

lazy val core = (project in file("icicle-core")).
  configs(IntegrationTest).
  settings(name := "icicle-core").
  settings(Commons.settings: _*).
  settings(
    libraryDependencies ++= Seq(
      "commons-codec" % "commons-codec" % "1.10",
      "org.slf4j" % "slf4j-api" % "1.7.25",
      "redis.clients" % "jedis" % "2.9.0" % "it,test"
    )
  )

lazy val jedis = (project in file("icicle-jedis")).
  configs(IntegrationTest).
  settings(name := "icicle-jedis").
  settings(Commons.settings: _*).
  settings(
    libraryDependencies ++= Seq(
      "redis.clients" % "jedis" % "2.9.0"
    )
  ).dependsOn(core)
