import play.Project._

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

lazy val basicAuth = project.settings(
  version := "0.3-SNAPSHOT",
  name := "play-2-basic-auth",
  libraryDependencies += javaCore
)

organization := "info.schleichardt"

javacOptions ++= Seq("-source", "1.6", "-target", "1.6")//for compatibility with Debian Squeeze

publishMavenStyle := true

publishArtifact in Test := false

publishTo <<= version { (v: String) =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

libraryDependencies in ThisBuild += "com.typesafe.play" %% "play" % play.core.PlayVersion.current

libraryDependencies in ThisBuild += "com.typesafe.play" %% "play-test" % play.core.PlayVersion.current % "test"

pomIncludeRepository := { _ => false }

val githubPath = "schleichardt/play-modules"

pomExtra := (
  <url>https://github.com/{githubPath}</url>
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
    <scm>
      <url>git@github.com:{githubPath}.git</url>
      <connection>scm:git:git@github.com:{githubPath}.git</connection>
    </scm>
    <developers>
      <developer>
        <id>schleichardt</id>
        <name>Michael Schleichardt</name>
        <url>http://michael.schleichardt.info</url>
      </developer>
    </developers>
  )