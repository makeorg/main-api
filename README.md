## Running the app :

```
sbt api/run -Dconfig.resource=default-application.conf
```

It is possible to start this sbt task from idea, allowing to start in debug mode


## Use head plugin to see what's inside elasticsearch

Have docker installed and run the following:

```
docker run --rm -it -p 9100:9100 mobz/elasticsearch-head:5
```

then you can connect to your $DOCKER_HOST (most likely localhost) 
on port 9100 with your browser to have the stats.

## Using embedded leveldb database 

the configuration for leveldb is:

```
akka {
  
  ...
  
  persistence {
    journal {
      plugin = akka.persistence.journal.leveldb
      leveldb {
        dir = "target/persistence/journal"
        native = on
      }
    }
    snapshot-store {
      plugin = akka.persistence.snapshot-store.local
      local.dir = "target/persistence/snapshots"
    }
  }
}  
```
