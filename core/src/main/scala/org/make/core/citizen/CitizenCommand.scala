package org.make.core.citizen

sealed trait CitizenCommand

case class RegisterCommand() extends CitizenCommand
case class UpdateProfileCommand() extends CitizenCommand

case object GetCitizen

