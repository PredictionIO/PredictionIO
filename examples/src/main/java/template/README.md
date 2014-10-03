# Template Engine

Run with Runner:

    $ $SPARK_HOME/bin/spark-submit --jars  engines/target/scala-2.10/engines-assembly-0.8.1-SNAPSHOT-deps.jar,engines/target/scala-2.10/engines_2.10-0.8.1-SNAPSHOT.jar --deploy-mode "client" --class "myengine.Runner"  core/target/scala-2.10/core_2.10-0.8.1-SNAPSHOT.jar
