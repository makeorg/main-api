#  Make.org Core API
#  Copyright (C) 2018 Make.org
#
# This program is free software: you can redistribute it and/or modify
#  it under the terms of the GNU Affero General Public License as
#  published by the Free Software Foundation, either version 3 of the
#  License, or (at your option) any later version.
#
#  This program is distributed in the hope that it will be useful,
#  but WITHOUT ANY WARRANTY; without even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#  GNU Affero General Public License for more details.
#
#  You should have received a copy of the GNU Affero General Public License
#  along with this program.  If not, see <https://www.gnu.org/licenses/>.


.PHONY: fixtures package-docker-image release run test-all test-all-with-coverage test-int test-unit infra-clean infra-rebuild infra-show-containers infra-show-images infra-show-logs infra-stop infra-up

help:
	@echo "Please use 'make <target>' where <target> is one of"
	@echo "   fixtures                       to load fixtures data"
	@echo "   fixtures-core                  to load fixtures data for Core"
	@echo "   fixtures-vff                   to load fixtures data for Vff Operation"
	@echo "   create-lpae                    to create LPAE operation"
	@echo "   package-docker-image           to build locally the docker image"
	@echo "   release                        to release the application"
	@echo "   run                            to run app"
	@echo "   test-all                       to test the application"
	@echo "   test-all-with-coverage         to test the application with code coverage"
	@echo "   test-int                       to test the application (integration tests)"
	@echo "   test-unit                      to test the application (unit tests)"
	@echo "   infra-clean                    to stop docker containers"
	@echo "   infra-clean-all                to stop and remove containers, networks, images, and volumes"
	@echo "   infra-rebuild                  to clean and up all"
	@echo "   infra-show-containers          to show all the containers"
	@echo "   infra-show-images              to show all the images"
	@echo "   infra-show-logs                to show logs from containers"
	@echo "   infra-stop                     to stop all the containers"
	@echo "   infra-up                       to create and start all the containers"

DOCKER_COMPOSE_FILE := docker-compose.yaml

fixtures:
	sbt fixtures/gatling:test

fixtures-core:
	sbt "fixtures/gatling:testOnly org.make.fixtures.Core"

fixtures-vff:
	sbt "fixtures/gatling:testOnly org.make.fixtures.Vff"

create-lpae:
	sbt "fixtures/gatling:testOnly org.make.migrations.Feb18_LpaeOperation"
	sbt "fixtures/gatling:testOnly org.make.fixtures.Lpae"

package-docker-image:
	sbt publishLocal

release:
	sbt release

run:
	sbt api/run

test-all: test-unit test-int

test-all-with-coverage:
	sbt clean coverage test it:test
	sbt coverageReport coverageAggregate

test-int:
	sbt it:test

test-unit:
	sbt test


########################################
#              INFRA                   #
########################################
infra-clean:
	docker-compose -f $(DOCKER_COMPOSE_FILE) down

infra-clean-all:
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
	docker-compose -f $(DOCKER_COMPOSE_FILE) up -d
