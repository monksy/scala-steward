/*
 * Copyright 2018-2025 Scala Steward contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scalasteward.core.buildtool.giter8

import cats.Monad
import cats.syntax.all.*
import org.scalasteward.core.buildtool.BuildRoot
import org.scalasteward.core.data.Repo
import org.scalasteward.core.io.{ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.util.Nel
import org.typelevel.log4cats.Logger

final class Giter8Alg[F[_]](implicit
    logger: Logger[F],
    processAlg: ProcessAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: Monad[F]
) {
  private val templateDir = "src/main/g8"
  private val renderedDir = "target/g8"

  def getRenderedGiter8BuildRoot(repo: Repo): F[Option[BuildRoot]] =
    workspaceAlg.repoDir(repo).flatMap { repoDir =>
      if ((repoDir / templateDir).isDirectory) render(repo, repoDir)
      else none[BuildRoot].pure[F]
    }

  private def render(repo: Repo, repoDir: better.files.File): F[Option[BuildRoot]] = {
    val renderedBuildRoot = BuildRoot(repo, renderedDir, includeMetaBuilds = false)
    val command = Nel.of(
      "sbt",
      "--server",
      "-Dsbt.color=false",
      "-Dsbt.log.noformat=true",
      "-Dsbt.supershell=false",
      "-Dsbt.server.forcestart=true",
      "g8"
    )

    logger.info(s"Render Giter8 template in $templateDir") >>
      processAlg.execSandboxed(command, repoDir).void >>
      Option
        .when((repoDir / renderedDir / "build.sbt").isRegularFile)(renderedBuildRoot)
        .fold(
          logger
            .warn(s"Rendered Giter8 template does not contain $renderedDir/build.sbt")
            .as(Option.empty[BuildRoot])
        )(buildRoot => Option(buildRoot).pure[F])
  }
}

object Giter8Alg {
  def create[F[_]](implicit
      logger: Logger[F],
      processAlg: ProcessAlg[F],
      workspaceAlg: WorkspaceAlg[F],
      F: Monad[F]
  ): Giter8Alg[F] = new Giter8Alg
}
