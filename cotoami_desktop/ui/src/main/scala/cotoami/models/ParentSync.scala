package cotoami.models

import com.softwaremill.quicklens._
import cotoami.backend.{Id, Node, ParentSyncEndJson, ParentSyncProgressJson}

case class ParentSync(
    syncing: Map[Id[Node], ParentSyncProgressJson] = Map.empty,
    synced: Seq[ParentSyncEndJson] = Seq.empty
) {
  def progress(progress: ParentSyncProgressJson): ParentSync =
    this.modify(_.syncing).using(_ + (Id(progress.node_id) -> progress))

  def end(end: ParentSyncEndJson): ParentSync =
    this
      .modify(_.syncing).using(_ - Id(end.node_id))
      .modify(_.synced).using(end +: _)

  lazy val remaining: Double =
    this.syncing.values.foldLeft(0d)((remaining, progress) =>
      remaining + (progress.total - progress.progress)
    )

  final val ManyThreshold = 100d

  def comingManyChanges: Boolean = this.remaining > ManyThreshold
}
