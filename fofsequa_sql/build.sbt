name := "fofsequa_sql"

version := "0.2"

scalaVersion := "2.13.3"
mainClass in (Compile, run) := Some("Fofsequa_sql")

libraryDependencies += "org.nanquanu" %% "fofsequa" % "0.4-SNAPSHOT"
libraryDependencies += "org.nanquanu" %% "fofsequa-eprover-reasoner" % "0.4-SNAPSHOT"
