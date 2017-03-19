import Commons._

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
      "org.apache.commons" % "commons-lang3" % "3.5",
      "commons-codec" % "commons-codec" % "1.10",
      "org.slf4j" % "slf4j-simple" % "1.7.25",
      "commons-io" % "commons-io" % "2.5",
      "redis.clients" % "jedis" % "2.9.0" % "it,test"
    )
  )

lazy val jedis = (project in file("icicle-jedis")).
  configs(IntegrationTest).
  settings(name := "icicle-jedis").
  settings(Commons.settings: _*).
  settings(
    libraryDependencies ++= Seq(
      "com.google.code.findbugs" % "jsr305" % "3.0.1",
      "redis.clients" % "jedis" % "2.9.0"
    )
  ).dependsOn(core)
