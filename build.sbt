name := "ogss"

version := "0.1"

scalaVersion := "2.12.8"

javacOptions ++= Seq("-encoding", "UTF-8")

compileOrder := CompileOrder.JavaThenScala


libraryDependencies ++= Seq(
	"junit" % "junit" % "4.12" % "test",
    "org.scalatest" %% "scalatest" % "3.0.5" % "test"
)

(testOptions in Test) += Tests.Argument(TestFrameworks.ScalaTest, "-u", "target/tests")


exportJars := true

mainClass := Some("ogss.main.CommandLine")


libraryDependencies += "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.6"

libraryDependencies += "com.github.scopt" %% "scopt" % "3.5.0"

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
