ItemRank engine development
===========================

Personalize the order of a list of items for each user.

## Register engine directly. Useful for testing after engine code change.
```
$ cd engines/
$ $PIO_HOME/bin/pio register \
--engine-json src/main/scala/itemrank/examples/engine.json

$ $PIO_HOME/bin/pio train \
--engine-json src/main/scala/itemrank/examples/engine.json \
--params-path src/main/scala/itemrank/examples/params \
-ap ncMahoutItemBasedAlgo.json

$ $PIO_HOME/bin/pio deploy \
--engine-json src/main/scala/itemrank/examples/engine.json \
--port 8000

```

## After deploy, you can get predictions

Show engine status:
```
$ curl -i -X GET http://localhost:8000
```

Get predictions
```
$ curl -i -X POST http://localhost:8000/queries.json \
-d '{
  "uid" : "2",
  "iids" : ["1", "3", "5", "10", "11"]
}'
```
