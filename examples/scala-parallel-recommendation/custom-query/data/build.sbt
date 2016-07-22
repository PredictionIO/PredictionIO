name := "import-movielenses"

organization := "org.template.recommendation"

def provided  (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")

libraryDependencies ++= provided(
  "org.apache.predictionio" % "client" % "0.8.3" withSources() withJavadoc())
