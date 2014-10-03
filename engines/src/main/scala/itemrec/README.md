ItemRecommendation engine development
=====================================

## Register engine directly. Useful for testing after engine code change.
```
$ cd $PIO_HOME/engines/
$ $PIO_HOME/bin/pio register --engine-json src/main/scala/itemrec/examples/engine.json
$ $PIO_HOME/bin/pio train \
  --engine-json src/main/scala/itemrec/examples/engine.json \
  --params-path src/main/scala/itemrec/examples/params \
  -ap ncMahoutItemBasedAlgo.json
$ $PIO_HOME/bin/pio deploy \
  --engine-json src/main/scala/itemrec/examples/engine.json \
  --port 9997
```


## Register engine through distribution. Useful for params testing.
```
$ ./make_distribtuion.sh
$ cd $PIO_HOME/engines/src/main/scala/itemrec/examples/examples/
$ $PIO_HOME/bin/pio register
$ $PIO_HOME/bin/pio train -ap ncMahoutItemBasedAlgo.json
```

## After deploy, you can get predictions

Show engine status:
```
curl -i -X GET http://localhost:9997
```

Get predictions
```
curl -i -X POST http://localhost:9997/queries.json -d '{"uid": "12", "n": 4}'
```
