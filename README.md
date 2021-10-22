[![build status](https://gitlab.com/makeorg-scala/make-api/badges/preproduction/build.svg)](https://gitlab.com/makeorg-scala/make-api/commits/preproduction)
[![coverage report](https://gitlab.com/makeorg-scala/make-api/badges/preproduction/coverage.svg)](https://gitlab.com/makeorg-scala/make-api/commits/preproduction)

## Setting up the dev environment with IntelliJ Idea

- Install the _scalafmt_ IntelliJ plugin
- Install the _scala_ IntelliJ plugin
- Check _sbt shell - use for: build_ in _Build > Build tools > sbt_ settings

## Setting up the dev environment with Code

### Installing Metals

- Install code (`yaourt -S code`)
- Install the `Metals` extension
- Configure metals, as defined in [the documentation](https://scalameta.org/metals/docs/editors/vscode.html)

### Using a custom bloop server

You should consider configuring the memory allocated to the bloop server.
So far, there is no way to do it if metals starts the bloop server, so you should start it yourself.

To do it:
- Install Bloop (`yaourt -S bloop`)
- Configure the bloop jvm (sudo echo '-Xmx2G' > /usr/lib/bloop/.jvmopts)
- Start the bloop server (`bloop server`)

As a bonus, you can now use the `bloop` commands, that will share the compilation made by code

To avoid keeping a shell always open, you can setup a systemd job.

Create the directory `~/.config/systemd/user`
edit the file `.config/systemd/user/bloop.service` with content

```
[Unit]
Description=Bloop server

[Service]
ExecStart=/usr/bin/bloop server

[Install]
WantedBy=basic.target
```

you can now control the bloop server using `systemctl`:
- `systemctl --user start bloop` to start the server
- `systemctl --user stop bloop` to stop the server
- `systemctl --user status bloop` to get the server status

If you want it to start automatically when you connect to your account, 
just use `systemctl --user enable bloop`


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

or

```
sbt api/run
```

### Running the app in Debug Mode:

There are two methods here:

- Create a **sbt task** from idea (intellij).
    - 'Edit configurations' -> 'Add new configuration' -> 'SBT Task'
    - Define the task as `api/run`
- Run MakeMain in Debug mode from idea. This method requires two VM options to be defined:
    - Set the javaagent: type `sbt "show api/kanelaRunnerOptions"` and copy the javaagent in vm parameters.

If you use Metals, just click on run or debug in the MakeMain class

### Run tests

To run Unit Tests: `sbt test`
To run Integration Tests: `sbt it:test`
If you want to run one test class : `sbt "testOnly *org.make.ref.to.class"`
If you want to run one specific integration test : `sbt "it:testOnly *org.make.ref.to.class -- -z testName"`

## Access the Api documentation

When application is running you can access to the swagger interface from the address: [http://localhost:9000/swagger](http://localhost:9000/swagger)

## Make the images available to the front

if your images, uploaded on the API are not visible (with an authentication error), run the following script:

```
./init-swift.sh
```

## Adding hooks to automatically fix the style

create a file `local.sbt` in the base directory, and add in it:

`gitPreCommitHook := Some(baseDirectory.value / "bin"/ "fix-style.sh")`

This local.sbt file can be used to add some custom sbt config that will not be shared between users.
_Use with care, it will not be on the CI server, so everything must work even without it._

## Secrets conf

Some parts of the configuration contain sensitive pieces of information and are defined
in the secrets of the API. The following explains how to define these informations locally.  

In order to send mails via Mailjet, pull data from Webflow or use the google social connect, create
the file `/etc/make/make-api.conf` with the following content:

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

  webflow {
    token = "webflow-token"
    token = ${?WEBFLOW_TOKEN}
  }

  social-providers {
    google {
      api-key = "put here your google api key with access to the people API"
      client-id = "put here your google client id to use the api connect form"
    }
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

Releasing the application consists in packaging the docker image lacally,
then tag and push (using `docker push`) the created image.

## Misc

- The netty version has been forced, so when new dependencies are added, it may be a good idea to exclude netty if it includes it

## Secure connection to cockroach

See: https://forum.cockroachlabs.com/t/connecting-to-an-ssl-secure-server-using-jdbc-java-and-client-certificate-authentication/400

## License

This project is licenced under the AGPL license V3 or later. See [license](LICENSE.md)

