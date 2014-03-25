How to build and run API server
===============================

## Prereq

1. Install [sbt](http://www.scala-sbt.org/release/docs/Getting-Started/Setup.html).
2. Install [play framework](http://www.playframework.com/download).

## Build and run
```
# From the repository root. API server depends on other pio modules, need to compile and publish them first.
sbt commons/publish output/publish

# Go to API server directory and run play framework
cd server/api/
play

# In play framework console
run
# Or, if you need to specify port number.
run -Dhttp.port=8000

# Everytime when pio modules are updated (and published).
# Need to update the dependency in Play console.
update 
```
