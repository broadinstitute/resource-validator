package com.broadinstitute.dsp

import org.broadinstitute.dsde.workbench.google2.GoogleDiskService

final case class DiskCheckerDeps[F[_]](checkRunnerDeps: CheckRunnerDeps[F], googleDiskService: GoogleDiskService[F])
