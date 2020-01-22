<!--
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

Helm Charts for Apache PredictionIO
============================

## Overview

Helm Charts are packages of pre-configured Kubernetes resources.
Using charts, you can install and manage PredictionIO in the Kubernetes.

## Usage

### Install PredictionIO with PostgreSQL

To install PostgreSQL and PredictionIO, run `helm install` command:

```
helm install --name my-postgresql stable/postgresql -f postgresql.yaml
helm install --name my-pio ./predictionio -f predictionio_postgresql.yaml
```

`postgresql.yaml` and `predictionio_postgresql.yaml` are configuration files for charts.
To access Jupyter for PredictionIO, run `kubectl port-forward` and then open `http://localhost:8888/`.

```
export POD_NAME=$(kubectl get pods --namespace default -l "app.kubernetes.io/name=predictionio,app.kubernetes.io/instance=my-pio" -o jsonpath="{.items[0].metadata.name}")
kubectl port-forward $POD_NAME 8888:8888
```


### Install Spark Cluster

To install Spark cluster, run the following command:

```
helm install --name my-spark ./spark
```

To train a model, run `pio train` as below:

```
pio train -- --master spark://my-spark-master:7077
```

