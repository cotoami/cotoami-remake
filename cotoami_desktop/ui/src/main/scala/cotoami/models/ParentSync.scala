package cotoami.models

import com.softwaremill.quicklens._
import cotoami.backend.{ParentSyncEndJson, ParentSyncProgressJson}

case class ParentSync(
    syncing: Seq[ParentSyncProgressJson] = Seq.empty,
    synced: Seq[ParentSyncEndJson] = Seq.empty
) {
  def progress(progress: ParentSyncProgressJson): ParentSync =
    // Update the progress with the same `node_id` as the given one
    // or prepend it to the list if not found.
    this.modify(_.syncing).using(oldSeq => {
      val (newSeq, found) =
        oldSeq.foldLeft((Seq.empty[ParentSyncProgressJson], false)) {
          case ((newSeq, found), item) =>
            if (item.node_id == progress.node_id)
              (newSeq :+ progress, true)
            else
              (newSeq :+ item, found)
        }
      if (found) newSeq else progress +: oldSeq
    })

  def end(end: ParentSyncEndJson): ParentSync =
    this
      .modify(_.syncing).using(_.filterNot(_.node_id == end.node_id))
      .modify(_.synced).using(end +: _)

  lazy val remaining: Double =
    this.syncing.foldLeft(0d)((remaining, progress) =>
      remaining + (progress.total - progress.progress)
    )

  final val ManyThreshold = 1d

  def comingManyChanges: Boolean = this.remaining > ManyThreshold
}
