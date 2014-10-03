Temporary place for examples and tests
======================================

HBPeventsTest
```
$ cd $PIO_HOME
$ ./make-distribution.sh
$ $SPARK_HOME/bin/spark-submit \
  --class "io.prediction.data.storage.examples.HBPEventsTest" \
  --master local[4] \
  assembly/pio-assembly-0.8.1-SNAPSHOT.jar <appId>
```
