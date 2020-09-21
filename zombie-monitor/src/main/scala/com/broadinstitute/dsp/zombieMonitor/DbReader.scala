package com.broadinstitute.dsp
package zombieMonitor

import cats.effect.{Async, _}
import doobie._
import doobie.implicits._
import fs2.Stream
import DbReaderImplicits._

trait DbReader[F[_]] {
  def getDisksToDeleteCandidate: Stream[F, Disk]
}

object DbReader {
  implicit def apply[F[_]](implicit ev: DbReader[F]): DbReader[F] = ev

  def impl[F[_]: ContextShift](xa: Transactor[F])(implicit F: Async[F]): DbReader[F] = new DbReader[F] {

    override def getDisksToDeleteCandidate: Stream[F, Disk] =
      sql"""select googleProject, name from PERSISTENT_DISK where status="Ready" OR status="Creating";
        """.query[Disk].stream.transact(xa)
  }
}
