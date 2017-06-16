[![build status](https://gitlab.com/makeorg-scala/make-api/badges/master/build.svg)](https://gitlab.com/makeorg-scala/make-api/commits/master)
[![coverage report](https://gitlab.com/makeorg-scala/make-api/badges/master/coverage.svg)](https://gitlab.com/makeorg-scala/make-api/commits/master)

## Setting up the dev environment


- Install coursier (sbt plugin) https://github.com/coursier/coursier
- Install the _scalafmt_ IntelliJ plugin
- Install the _scala_ IntelliJ plugin

## Running the app :


In order for the app to start correctly, you need to provide the infrastructure.
To start it, got to your project directory and type:

```
make infra-up
```

when infrastructure is up you can run your app

```
make run
```

It is possible to start this sbt task from idea, allowing to start in debug mode

### Access To Api documentation

When application is running you can access to the swagger interface from the adress: [http://localhost:9000/swagger](http://localhost:9000/swagger)

## package the application as a docker image locally

in order to build locally the docker image, type from your base directory:

```
make package-docker-image
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
make release
```

and set versions accordingly

## Misc

- The netty version has been forced, so when new dependencies are added, it may be a good idea to exclude netty if it includes it
- Because of the that, there may be some weird behaviour once we start tests with several nodes

## Secure connection to cockroach

See: https://forum.cockroachlabs.com/t/connecting-to-an-ssl-secure-server-using-jdbc-java-and-client-certificate-authentication/400

