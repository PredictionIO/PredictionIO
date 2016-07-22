## Admin API (under development)

### Start Admin HTTP Server without bin/pio (for development)

NOTE: elasticsearch and hbase should be running first.

```
$ sbt/sbt "tools/compile"
$ set -a
$ source conf/pio-env.sh
$ set +a
$ sbt/sbt "tools/run-main org.apache.predictionio.tools.admin.AdminRun"
```

### Unit test (Very minimal)

```
$ set -a
$ source conf/pio-env.sh
$ set +a
$ sbt/sbt "tools/test-only org.apache.predictionio.tools.admin.AdminAPISpec"
```

### Start with pio command adminserver

```
$ pio adminserver
```

Admin Server url defaults to `http://localhost:7071`

The host and port can be specified by using the 'ip' and 'port' parameters

```
$ pio adminserver --ip 127.0.0.1 --port 7080
```

### Current Supported Commands

#### Check status

```
$ curl -i http://localhost:7071/

{"status":"alive"}
```

#### Get list of apps

```
$ curl -i -X GET http://localhost:7071/cmd/app

{"status":1,"message":"Successful retrieved app list.","apps":[{"id":12,"name":"scratch","keys":[{"key":"gtPgVMIr3uthus1QJWFBcIjNf6d1SNuhaOWQAgdLbOBP1eRWMNIJWl6SkHgI1OoN","appid":12,"events":[]}]},{"id":17,"name":"test-ecommercerec","keys":[{"key":"zPkr6sBwQoBwBjVHK2hsF9u26L38ARSe19QzkdYentuomCtYSuH0vXP5fq7advo4","appid":17,"events":[]}]}]}
```

#### Create a new app

```
$ curl -i -X POST http://localhost:7071/cmd/app \
-H "Content-Type: application/json" \
-d '{ "name" : "my_new_app" }'

{"status":1,"message":"App created successfully.","id":19,"name":"my_new_app","keys":[{"key":"","appid":19,"events":[]}]}
```

#### Delete data of app

```
$ curl -i -X DELETE http://localhost:7071/cmd/app/my_new_app/data
```

#### Delete app

```
$ curl -i -X DELETE http://localhost:7071/cmd/app/my_new_app

{"status":1,"message":"App successfully deleted"}
```


## API Doc (To be updated)

### app list:
GET http://localhost:7071/cmd/app

OK Response:
{
  “status”: <STATUS>,
  “message”: <MESSAGE>,
  “apps” : [
    { “name': “<APP_NAME>”,
      “id': <APP_ID>,
      “accessKey' : “<ACCESS_KEY>” },
    { “name': “<APP_NAME>”,
      “id': <APP_ID>,
      “accessKey' : “<ACCESS_KEY>” }, ... ]
}

Error Response:
{“status”: <STATUS>, “message” : “<MESSAGE>”}

### app new
POST http://localhost:7071/cmd/app
Request Body:
{ name”: “<APP_NAME>”, // required
  “id”: <APP_ID>, // optional
  “description”: “<DESCRIPTION>” } // optional

OK Response:
{ “status”: <STATUS>,
  “message”: <MESSAGE>,
  “app” : {
    “name”: “<APP_NAME>”,
    “id”: <APP_ID>,
    “accessKey” : “<ACCESS_KEY>” }
}

Error Response:
{ “status”: <STATUS>, “message” : “<MESSAGE>”}

### app delete
DELETE http://localhost:7071/cmd/app/{appName}

OK Response:
{ "status": <STATUS>, "message" : “<MESSAGE>”}

Error Response:
{ “status”: <STATUS>, “message” : “<MESSAGE>”}

### app data-delete
DELETE http://localhost:7071/cmd/app/{appName}/data

OK Response:
{ "status": <STATUS>, "message" : “<MESSAGE>”}

Error Response:
{ “status”: <STATUS>, “message” : “<MESSAGE>” }


### train TBD

#### Training request:
POST http://localhost:7071/cmd/train
Request body: TBD

OK Response: TBD

Error Response: TBD

#### Get training status:
GET http://localhost:7071/cmd/train/{engineInstanceId}

OK Response: TBD
INIT
TRAINING
DONE
ERROR

Error Response: TBD

### deploy TBD
