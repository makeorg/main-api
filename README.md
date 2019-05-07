[![build status](https://gitlab.com/makeorg-scala/make-api/badges/preproduction/build.svg)](https://gitlab.com/makeorg-scala/make-api/commits/preproduction)
[![coverage report](https://gitlab.com/makeorg-scala/make-api/badges/preproduction/coverage.svg)](https://gitlab.com/makeorg-scala/make-api/commits/preproduction)

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


#### Running the app in Debug Mode:

There are two methods here:

- Create a **sbt task** from idea (intellij).
    - 'Edit configurations' -> 'Add new configuration' -> 'SBT Task'
    - Define the task as `api/run`
- Run MakeMain in Debug mode from idea. This method requires two VM options to be defined:
    - Set the javaagent: type `sbt "show aspectj:aspectjWeaverOptions"` and copy the javaagent in vm parameters.

## Run tests

To run Unit Tests: `sbt test`
To run Integration Tests: `sbt it:test`
If you want to run one test class : `sbt "testOnly *org.make.ref.to.class"`
If you want to run one specific integration test : `sbt "it:testOnly *org.make.ref.to.class -- -z testName"`

### Access the Api documentation

When application is running you can access to the swagger interface from the address: [http://localhost:9000/swagger](http://localhost:9000/swagger)

## Sending emails

Create the file `/etc/make/make-api.conf` with the following content:

```hocon
make-api {
  mail-jet {
    api-key = "transactional-emails-api-key"
    api-key = ${?MAILJET_API_KEY}
    secret-key = "transactional-emails-secret-key"
    secret-key = ${?MAILJET_SECRET_KEY}
    campaign-api-key = "contact-list-api-key"
    campaign-api-key = ${?CAMPAIGN_MAILJET_API_KEY}
    campaign-secret-key = "contact-list-secret-key"
    campaign-secret-key = ${?CAMPAIGN_MAILJET_SECRET_KEY}
    basic-auth-login = "make-mailjet"
    basic-auth-login = ${?MAILJET_AUTH_LOGIN}
    basic-auth-password = "mail-jet-callback-password"
    basic-auth-password = ${?MAILJET_AUTH_PASSWORD}
  }
}
```

replacing the different secrets with their real value.

## package the application as a docker image locally

in order to build locally the docker image, type from your base directory:

```
make package-docker-image
```

## releasing the application

In order to start a release, you need:

- To login into the registry, using docker login
- To define your credentials in the ~/.sbt/1.0/credentials.sbt with content :

```scala
credentials ++= Seq(
  Credentials("Sonatype Nexus Repository Manager", "nexus.prod.makeorg.tech", global_login, global_password)
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

## License

This project is licenced under the AGPL license V3 or later. See [license](LICENSE.md)