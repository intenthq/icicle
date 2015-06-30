import Commons._

lazy val root: Project = Project(
  id        = "root",
  base      = file("."),
  settings  = Project.defaultSettings ++ Seq(
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
      "org.apache.commons" % "commons-lang3" % "3.0.1",
      "commons-codec" % "commons-codec" % "1.9",
      "org.slf4j" % "slf4j-simple" % "1.6.6",
      "commons-io" % "commons-io" % "2.4",
      "redis.clients" % "jedis" % "2.4.2" % "it,test"
    )
  )

lazy val jedis = (project in file("icicle-jedis")).
  configs(IntegrationTest).
  settings(name := "icicle-jedis").
  settings(Commons.settings: _*).
  settings(
    libraryDependencies ++= Seq(
      "com.google.code.findbugs" % "jsr305" % "2.0.3",
      "redis.clients" % "jedis" % "2.4.2"
    )
  ).dependsOn(core)
