name := "ogss"

version := "0.5"

scalaVersion := "2.12.19"

javacOptions ++= Seq("-encoding", "UTF-8")

scalacOptions += "-target:jvm-1.8"
javacOptions ++= Seq("-source", "1.8")

compileOrder := CompileOrder.JavaThenScala


libraryDependencies ++= Seq(
	"junit" % "junit" % "4.13.2" % "test",
    "org.scalatest" %% "scalatest" % "3.2.18" % "test"
)

(testOptions in Test) += Tests.Argument(TestFrameworks.ScalaTest, "-u", "target/tests")

exportJars := true

mainClass := Some("ogss.main.CommandLine")

unmanagedResourceDirectories in Compile += { baseDirectory.value / "jar-extras" }

libraryDependencies += "org.scala-lang.modules" %% "scala-parser-combinators" % "2.3.0"

libraryDependencies += "com.github.scopt" %% "scopt" % "4.1.0"

libraryDependencies += "org.json" % "json" % "20160810"


lazy val root = (project in file(".")).
  enablePlugins(BuildInfoPlugin).
  settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "ogss"
  )

assemblyJarName in assembly := "ogss.jar"
assemblyMergeStrategy in assembly := {
  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
  case _ => MergeStrategy.first
}
test in assembly := {}
