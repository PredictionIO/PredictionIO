# Testing PredictionIO

Intention of this subdirectory is to amass different types of tests other than unit-tests and also to make developers life easier giving them with means to check the application deterministically for different configurations.
Moreover, it provides testing scenarios for **TravisCI** to be run on pull requests and commits.


## Integration Tests
These tests are mostly user-functionality tests. They check logic and reliability of the system.
In order to get familiar with their structure, please see [README](pio_tests/README.md).

## Docker image
After introducing some changes, a developer would like to try them against different configurations, namely to see if everything works as expected e.g. when you change the data repository for the events or meta-data.
A good way to that is to use the docker image with installed and running dependencies.

To download the image run:
```
$ docker pull predictionio/pio-testing
```

The most convenient way to make use of it is to execute ***run_docker.sh*** script passing it the configuration, the path to PredictionIO's repository with archived snapshot and the command to run. When no command is provided it opens a bash shell inside the docker image. Example of usage:
```sh
$ ./run_docker.sh ELASTICSEARCH HBASE LOCALFS \
    ~/projects/incubator-preadictionio "echo 'All tests passed...'"
```

Directory structure inside the image:
* ***/PredictionIO*** - extracted snapshot (***/PredictionIO/bin*** is also already added to PATH)
* ***/pio_host*** - mounted path to repository
* ***/tests/pio_tests*** - copy of integration tests
* ***/vendors*** - directory with installed services
* ***/drivers*** - jars with database drivers
