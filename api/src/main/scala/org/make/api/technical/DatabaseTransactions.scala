/*
 *  Make.org Core API
 *  Copyright (C) 2018 Make.org
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

package org.make.api.technical

import scalikejdbc.{DBConnection, DBSession, Tx, TxBoundary}

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

object DatabaseTransactions {

  private val retries = 5

  implicit class RichDatabase(val self: DBConnection) extends AnyVal {

    def retryableExceptionTxBoundary[A]: TxBoundary[A] = new TxBoundary[A] {
      def finishTx(result: A, tx: Tx): A = {
        finishInternal(result, tx, retries)
      }

      @SuppressWarnings(Array("org.wartremover.warts.Throw"))
      @tailrec
      private def finishInternal(result: A, tx: Tx, retries: Int): A = {
        (retries, Try(tx.commit())) match {
          case (_, Success(_)) => result
          case (0, Failure(e)) => throw e
          case (retry, _)      => finishInternal(result, tx, retry - 1)
        }
      }
    }

    def retryableTx[A](
      execution: DBSession => A
    )(implicit boundary: TxBoundary[A] = retryableExceptionTxBoundary[A]): A = {
      self.localTx(execution)(boundary)
    }
  }

}
