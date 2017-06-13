.PHONY: package-docker-image release run test infra-clean infra-rebuild infra-show-containers infra-show-images infra-show-logs infra-stop infra-up

help:
	@echo "Please use 'make <target>' where <target> is one of"
	@echo "   package-docker-image           to build locally the docker image"
	@echo "   release                        to release the application"
	@echo "   run                            to run app"
	@echo "   test                           to test the application"
	@echo "   infra-clean                    to stop and remove containers, networks, images, and volumes"
	@echo "   infra-rebuild                  to clean and up all"
	@echo "   infra-show-containers          to show all the containers"
	@echo "   infra-show-images              to show all the images"
	@echo "   infra-show-logs                to show logs from containers"
	@echo "   infra-stop                     to stop all the containers"
	@echo "   infra-up                       to create and start all the containers"

DOCKER_COMPOSE_FILE := docker-compose.yaml

package-docker-image:
	sbt publishLocal

release:
	sbt release

run:
	sbt api/run

test:
	sbt test

########################################
#              INFRA                   #
########################################
infra-clean:
	docker-compose -f $(DOCKER_COMPOSE_FILE) down -v --rmi all

infra-rebuild: infra-clean infra-up

infra-show-containers:
	docker-compose -f $(DOCKER_COMPOSE_FILE) ps

infra-show-images:
	docker images -a

infra-show-logs:
	docker-compose -f $(DOCKER_COMPOSE_FILE) logs -ft

infra-stop:
	docker-compose -f $(DOCKER_COMPOSE_FILE) stop

infra-up:
	docker-compose -f $(DOCKER_COMPOSE_FILE) up --build -d
