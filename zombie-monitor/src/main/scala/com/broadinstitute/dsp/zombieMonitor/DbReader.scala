package com.broadinstitute.dsp
package zombieMonitor

import cats.effect.{Async, _}
import cats.implicits._
import doobie._
import doobie.implicits._
import fs2.Stream

trait DbReader[F[_]] {
  def getDeletedRuntimes: Stream[F, Runtime]
}

object DbReader {
  implicit def apply[F[_]](implicit ev: DbReader[F]): DbReader[F] = ev

  def impl[F[_]: ContextShift](xa: Transactor[F])(implicit F: Async[F]): DbReader[F] = new DbReader[F] {

    /**
     * AOU reuses runtime names, hence exclude any aou runtimes that have the same names that're still "alive"
     */
    //TODO: fill out the SQL. Function name should be udpated as well
    override def getDeletedRuntimes: Stream[F, Runtime] = ???
  }
}
