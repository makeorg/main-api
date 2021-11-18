/*
 *  Make.org Core API
 *  Copyright (C) 2021 Make.org
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

name := "benchmarks"

Jmh / sourceDirectory     := (Test / sourceDirectory).value
Jmh / classDirectory      := (Test / classDirectory).value
Jmh / dependencyClasspath := (Test / dependencyClasspath).value
// rewire tasks, so that 'jmh:run' invokes 'jmh:compile' (otherwise a clean 'jmh:run' would fail)
Jmh / compile := (Jmh / compile).dependsOn(Test / compile).value
Jmh / run     := (Jmh / run).dependsOn(Jmh / Keys.compile).evaluated
