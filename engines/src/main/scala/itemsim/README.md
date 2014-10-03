# ItemSim Engine development

## Register engine directly. Useful for testing after engine code change.
```
$ cd $PIO_HOME/engines/
$ $PIO_HOME/bin/pio register --engine-json src/main/scala/itemsim/examples/engine.json
$ $PIO_HOME/bin/pio train \
  --engine-json src/main/scala/itemsim/examples/engine.json \
  --params-path src/main/scala/itemsim/examples/params \
  -ap ncMahoutItemBasedAlgo.json
$ $PIO_HOME/bin/pio deploy \
  --engine-json src/main/scala/itemsim/examples/engine.json \
  --port 9997
```


## Register engine through distribution. Useful for params testing.
```
$ ./make_distribtuion.sh
$ cd $PIO_HOME/engines/src/main/scala/itemsim/examples/examples/
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
curl -i -X POST http://localhost:9997/queries.json -d '{"iids": ["12"], "n": 4}'

curl -i -X POST http://localhost:9997/queries.json -d '{"iids": ["12", "1", "19", "21"], "n": 5}'
```
