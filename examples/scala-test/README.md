# My First "Hello World" Engine

## 1. DataSource

## 2. Algorithm

## 3. Deploy

Prepare training data:
```
$ cd $PIO_HOME/examples/scala-local-helloworld
$ cp ../data/helloworld/data1.csv ../data/helloworld/data.csv
```

Register engine:

```
$ ../../bin/pio register
```

Train:

```
$ ../../bin/pio train
```

Example output:

```
2014-08-05 17:06:02,638 INFO  APIDebugWorkflow$ - Metrics is null. Stop here
2014-08-05 17:06:02,769 INFO  APIDebugWorkflow$ - Run information saved with ID: 201408050005
```

Deploy:

```
$ ../../bin/pio deploy
```

Retrieve prediction:

```
$ curl -H "Content-Type: application/json" -d '{ "day": "Mon" }' http://localhost:8000/queries.json
```

Output:

```
{"temperature":75.5}
```

Retrieve prediction:

```
$ curl -H "Content-Type: application/json" -d '{ "day": "Tue" }' http://localhost:8000/queries.json
```

Output:
```
{"temperature":80.5}
```

## 4. Re-training

Re-train with new data:

```
$ cd $PIO_HOME/examples/scala-local-helloworld
$ cp ../data/helloworld/data2.csv ../data/helloworld/data.csv
```

```
$ ../../bin/pio train
$ ../../bin/pio deploy
```

```
$ curl -H "Content-Type: application/json" -d '{ "day": "Mon" }' http://localhost:8000/queries.json

{"temperature":76.66666666666667}
```
