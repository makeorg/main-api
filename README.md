[![build status](https://gitlab.com/makeorg-scala/make-api/badges/master/build.svg)](https://gitlab.com/makeorg-scala/make-api/commits/master)

## Running the app :

```
sbt api/run -Dconfig.resource=default-application.conf
```

It is possible to start this sbt task from idea, allowing to start in debug mode


In order for the app to start correctly, you need to provide the infrastructure.
To start it, got to your project directory and type:

```
docker-compose up -d
```

## package the application as a docker image locally

in order to build locally the docker image, type from your base directory:

```
sbt publishLocal
```

## releasing the application

In order to start a release, you need:

- To login into the registry, using docker login
- To define your credentials in the ~/.sbt/0.13/credentials.sbt with content :

```scala
credentials ++= Seq(
  Credentials("Sonatype Nexus Repository Manager", "nexus.prod.makeorg.tech", "my-login", "my-password")
)
```

Then type 

```
sbt release
```

and set versions accordingly

## (Optionnal) Use head plugin to see what's inside elasticsearch

Have docker installed and run the following:

```
docker run --rm -it -p 9100:9100 mobz/elasticsearch-head:5
```

then you can connect to your $DOCKER_HOST (most likely localhost) 
on port 9100 with your browser to have the stats.

## Misc

- The netty version has been forced, so when new dependencies are added, it may be a good idea to exclude netty if it includes it
- Because of the that, there may be some weird behaviour once we start tests with several nodes

## Secure connection to cockroach

See: https://forum.cockroachlabs.com/t/connecting-to-an-ssl-secure-server-using-jdbc-java-and-client-certificate-authentication/400

