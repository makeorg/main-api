package org.make.core.citizen

import java.time.LocalDate

//
// file containing classes and services of the citizen domain
//

case class Citizen (
                   email: String,
                   dateOfBirth: LocalDate,
                   firstName: String,
                   lastName: String
                   )

