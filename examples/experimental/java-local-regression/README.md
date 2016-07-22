# Java Local Regression Engine

## Configuration

### Data Source

This sample regression engine reads data from file system.

Edit the file path in `engine.json`, change `filepath` of `datasource` to an absolute path that points to
[lr_data.py](../data/lr_data.txt)

```
$ cat engine.json
...
"datasource": {
  "filepath": <absolute_path_to_lr_data.txt>
}
...

```

### Algorithms

This engine comes with two algorithms.

1. OLS. It is the standard ordinary least square algorithm. It takes no parameter.
2. Default. This algorithm always return the same value `v` defined in the parameter.

Below is an example of `algorithms` defines in `engine.json`, it invokes three algorithms,

1. The standard OLS algorithm,
2. A default algorithm which always return 2.0,
3. A default algorithm which always return 4.0.

```json
"algorithms": [
  { "name": "OLS", "params": {} },
  { "name": "Default", "params": { "v": 2.0 } },
  { "name": "Default", "params": { "v": 4.0 } }
]
```

The `Serving` class returns the average of all these prediction to the user.

## Register engine, train, and deploy.

```
$ pio build
$ pio train
$ pio deploy --port 9997
```

## Query the Engine Instance

```
$ curl -X POST http://localhost:9997/queries.json -d \
  '[1.80,0.87,2.41,0.35,-0.21,1.35,0.51,1.55,-0.20,1.32]'
2.2434392991944025
```

2.24... is the prediction result.

## Evaluation

You can evaluate the engine instance too.
```
$  pio eval --batch JavaRegressionEval \
--metrics-class org.apache.predictionio.examples.java.regression.MeanSquareMetrics

...
2014-09-24 03:23:07,170 INFO  spark.SparkContext - Job finished: collect at Workflow.scala:695, took 0.092829 s
2014-09-24 03:23:07,284 WARN  workflow.CoreWorkflow$ - java.lang.String is not a NiceRendering instance.
2014-09-24 03:23:07,296 INFO  workflow.CoreWorkflow$ - Saved engine instance with ID: OCCUucs7QBOOG--9kIWFEw
```

And you will see you result in PredictionIO dashboard. To start dashboard:
```
$ pio dashboard
```
