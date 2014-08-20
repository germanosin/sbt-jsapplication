sbtPlugin := true

organization := "com.github.germanosin.sbt"

name := "sbt-jsapplication"

version := "1.0.0"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  "com.google.javascript" % "closure-compiler" % "v20140730",
  "com.typesafe" % "config" % "1.2.1"
)

resolvers ++= Seq(
  "Typesafe Releases Repository" at "http://repo.typesafe.com/typesafe/releases/",
  Resolver.url("sbt snapshot plugins", url("http://repo.scala-sbt.org/scalasbt/sbt-plugin-snapshots"))(Resolver.ivyStylePatterns),
  Resolver.sonatypeRepo("snapshots"),
  "Typesafe Snapshots Repository" at "http://repo.typesafe.com/typesafe/snapshots/",
  "Maven Central" at "http://central.maven.org/maven2/"
)


addSbtPlugin("com.typesafe.sbt" % "sbt-web" % "1.0.3-20140717-c49ba1a")

publishMavenStyle := true

publishTo := {
  val nexus = "https://oss.sonatype.org"
  if (isSnapshot.value) Some("snapshots" at s"$nexus/content/repositories/snapshots")
  else Some("releases" at s"$nexus/service/local/staging/deploy/maven2")
}

pomExtra := (
  <url>http://github.com/germanosin/sbt-jsapplication</url>
    <licenses>
      <license>
        <name>MIT License</name>
        <url>http://opensource.org/licenses/mit-license.php</url>
      </license>
    </licenses>
    <scm>
      <url>git@github.com:germanosin/sbt-jsapplication.git</url>
      <connection>scm:git:git@github.com:germanosin/sbt-jsapplication.git</connection>
    </scm>
    <developers>
      <developer>
        <id>germanosin</id>
        <name>German Osin</name>
      </developer>
    </developers>
  )
