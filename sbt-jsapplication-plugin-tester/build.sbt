lazy val root = (project in file(".")).enablePlugins(SbtWeb)

libraryDependencies += "org.webjars" % "jquery" % "2.1.1"

libraryDependencies ++= Seq(
  "org.webjars" % "dustjs-linkedin" % "2.4.0-1"
)

//includeFilter in (Assets, JSApplicationKeys.jsapplication) := "*.jsapplication"

//JSApplicationKeys.compilationLevel in Assets := "SIMPLE"