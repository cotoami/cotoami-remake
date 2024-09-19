package cotoami.models

import com.softwaremill.quicklens._

case class ParentSync(
    syncing: Seq[ParentSyncProgress] = Seq.empty,
    synced: Seq[ParentSyncEnd] = Seq.empty
) {
  def progress(progress: ParentSyncProgress): ParentSync =
    // Update the progress with the same `node_id` as the given one
    // or prepend it to the list if not found.
    this.modify(_.syncing).using(oldSeq => {
      val (newSeq, found) =
        oldSeq.foldLeft((Seq.empty[ParentSyncProgress], false)) {
          case ((newSeq, found), item) =>
            if (item.nodeId == progress.nodeId)
              (newSeq :+ progress, true)
            else
              (newSeq :+ item, found)
        }
      if (found) newSeq else progress +: oldSeq
    })

  def end(end: ParentSyncEnd): ParentSync =
    this
      .modify(_.syncing).using(_.filterNot(_.nodeId == end.nodeId))
      .modify(_.synced).using(synced =>
        if (end.noChanges)
          synced
        else
          end +: synced
      )

  lazy val remaining: Double =
    this.syncing.foldLeft(0d)((remaining, progress) =>
      remaining + (progress.total - progress.progress)
    )

  final val ManyThreshold = 100d

  def comingManyChanges: Boolean = this.remaining > ManyThreshold
}

case class ParentSyncProgress(nodeId: Id[Node], progress: Double, total: Double)

case class ParentSyncEnd(
    nodeId: Id[Node],
    range: Option[(Double, Double)],
    error: Option[String]
) {
  def noChanges: Boolean = this.range.isEmpty && this.error.isEmpty
}
