# Parallel Regression Engine

## Configuration

This sample regression engine reads data from file system.

Edit the file path in `datasource.json`, change `filepath` to an absolute path that points to
[lr_data.py](../data/lr_data.txt)

```
$ cat params/datasource.json
{
  "filepath": <absolute_path_to_lr_data.txt>,
  "k": 3,
  "seed": 9527
}

```

## Register engine, train, and deploy.

```
$ pio register
$ pio train
$ pio deploy --port 9998
```

## Query the Engine Instance

```
$ curl -X POST http://localhost:9998/queries.json -d \
  '[1.80,0.87,2.41,0.35,-0.21,1.35,0.51,1.55,-0.20,1.32]'

0.8912731719174509
```

0.89... is the prediction result.
