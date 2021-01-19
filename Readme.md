# Replicating Offsetstore being cleared

Bare minimum project to replicate the offsetstore being reset to 0, when the cassandra cluster goes down.

Create the config paths
```
mkdir -p cassandra/cassandra-1/config
mkdir -p cassandra/cassandra-1/data
mkdir -p cassandra/cassandra-1/logs
mkdir -p cassandra/cassandra-2/config
mkdir -p cassandra/cassandra-2/data
mkdir -p cassandra/cassandra-2/logs
mkdir -p cassandra/cassandra-3/config
mkdir -p cassandra/cassandra-3/data
mkdir -p cassandra/cassandra-3/logs
```

Start cassandra docker
```
# do not run as daemon so it's easy to kill the process
docker-compose up
```


Run the app
```
sbt -mem 4048 -jvm-debug 5005
sbt:lagom-offset> lagomServiceLocatorStart
sbt:lagom-offset> lagom-offset/run 
```

Hit random generation, maybe create a lot of events
```
curl -X POST -v 127.0.0.1:62928/random/generate/2000
```

Check when the logs updates the offsetstore

Kill cassandra process