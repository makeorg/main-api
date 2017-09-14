package org.make.api.technical

import scalikejdbc.{DBConnection, DBSession, Tx, TxBoundary}

import scala.util.{Failure, Success, Try}

object DatabaseTransactions {

  val retries = 5

  implicit class RichDatabase(val self: DBConnection) extends AnyVal {

    def retryableExceptionTxBoundary[A]: TxBoundary[A] = new TxBoundary[A] {
      def finishTx(result: A, tx: Tx): A = {
        finishInternal(result, tx, retries)
      }

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
