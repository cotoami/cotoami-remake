package cotoami.updates

import scala.util.chaining._
import com.softwaremill.quicklens._

import fui.{Browser, Cmd}
import cotoami.utils.facade.Nullable
import cotoami.{Model, Msg}
import cotoami.models._
import cotoami.repository._
import cotoami.backend.{
  ChangeJson,
  ChangelogEntryJson,
  CotoBackend,
  CotoDetails,
  CotoJson,
  CotonomaBackend,
  CotonomaJson,
  ItoBackend,
  NodeBackend
}
import cotoami.subparts.SectionPins

object Changelog {

  def apply(log: ChangelogEntryJson, model: Model): (Model, Cmd[Msg]) = {
    val expectedNumber = model.repo.lastChangeNumber + 1
    if (log.serial_number == expectedNumber)
      applyChange(log.change, model)
        .modify(_._1.repo.lastChangeNumber).setTo(log.serial_number)
    else
      (
        model.info(
          s"Unexpected change number (expected: ${expectedNumber})",
          Some(log.serial_number.toString())
        ),
        Browser.send(Msg.ReloadRepository)
      )
  }

  private def applyChange(
      change: ChangeJson,
      model: Model
  ): (Model, Cmd[Msg]) = {
    // Handle changes in order of assumed their frequency:
    // CreateCoto
    for (json <- change.CreateCoto.toOption) {
      return createCoto(json, model)
    }

    // CreateCotonoma
    for (json <- change.CreateCotonoma.toOption) {
      return createCotonoma(json, model)
    }

    // CreateIto
    for (json <- change.CreateIto.toOption) {
      val ito = ItoBackend.toModel(json)
      if (model.repo.itos.hasDuplicateOrder(ito))
        return (
          model,
          Cmd.Batch(
            Root.fetchGraphFromCoto(ito.targetCotoId),
            // Insertion: refresh sibling itos if there's an order duplication.
            Root.fetchOutgoingItos(ito.sourceCotoId)
          )
        )
      else
        return (
          model.modify(_.repo.itos).using(_.put(ito)),
          Cmd.Batch(
            Root.fetchGraphFromCoto(ito.targetCotoId),
            if (model.repo.isPin(ito))
              // Scroll to the just added pin
              Browser.send(SectionPins.Msg.ScrollToPin(ito).into)
            else
              Cmd.none
          )
        )
    }

    // DeleteIto
    for (json <- change.DeleteIto.toOption) {
      val itoId: Id[Ito] = Id(json.ito_id)
      return (
        model.modify(_.repo.itos).using(_.delete(itoId)),
        Cmd.none
      )
    }

    // ChangeItoOrder
    for (json <- change.ChangeItoOrder.toOption) {
      val itoId: Id[Ito] = Id(json.ito_id)
      return (
        model,
        model.repo.itos.get(itoId)
          .map(ito => Root.fetchOutgoingItos(ito.sourceCotoId))
          .getOrElse(Cmd.none)
      )
    }

    // EditCoto
    for (json <- change.EditCoto.toOption) {
      return (model, updateCoto(Id(json.coto_id)))
    }

    // DeleteCoto
    for (json <- change.DeleteCoto.toOption) {
      val cotoId: Id[Coto] = Id(json.coto_id)
      return (
        model.modify(_.repo).using(_.deleteCoto(cotoId)),
        // Update the original coto if it's a repost
        model.repo.cotos.get(cotoId).flatMap(_.repostOfId)
          .map(updateCoto)
          .getOrElse(Cmd.none)
      )
    }

    // EditIto
    for (json <- change.EditIto.toOption) {
      return (model, Root.fetchIto(Id(json.ito_id)))
    }

    // RenameCotonoma
    for (json <- change.RenameCotonoma.toOption) {
      val cotonomaId: Id[Cotonoma] = Id(json.cotonoma_id)
      return touchCotonoma(
        cotonomaId,
        json.updated_at,
        model.repo.cotonomas
      ).pipe { case (cotonomas, cmd) =>
        (
          model.modify(_.repo.cotonomas).setTo(cotonomas),
          Cmd.Batch(
            cmd,
            cotonomas.get(cotonomaId)
              .map(_.cotoId)
              .map(updateCoto)
              .getOrElse(Cmd.none)
          )
        )
      }
    }

    // PromoteJson
    for (json <- change.Promote.toOption) {
      return (model, promote(Id(json.coto_id)))
    }

    // UpsertNode
    for (json <- change.UpsertNode.toOption) {
      val node = NodeBackend.toModel(json)
      return (model.modify(_.repo.nodes).using(_.put(node)), Cmd.none)
    }

    // CreateNode
    for (json <- change.CreateNode.toOption) {
      return model.modify(_.repo.nodes).using(
        _.put(NodeBackend.toModel(json.node))
      ).pipe { model =>
        Nullable.toOption(json.root)
          .map(createCotonoma(_, model))
          .getOrElse((model, Cmd.none))
      }
    }

    // RenameNode
    for (json <- change.RenameNode.toOption) {
      val nodeId: Id[Node] = Id(json.node_id)
      return (
        model
          .modify(_.repo.nodes).using(_.rename(nodeId, json.name))
          .modify(_.geomap).using(_.refreshMarkers),
        Root.fetchNodeDetails(nodeId)
      )
    }

    // SetNodeIcon
    for (json <- change.SetNodeIcon.toOption) {
      return (
        model
          .modify(_.repo.nodes).using(
            _.setIcon(Id(json.node_id), json.icon)
          )
          .modify(_.geomap).using(_.refreshMarkers),
        Cmd.none
      )
    }

    (model, Cmd.none)
  }

  private def createCoto(
      cotoJson: CotoJson,
      model: Model
  ): (Model, Cmd.Batch[Msg]) = {
    val repo = model.repo

    // Register the coto
    val coto = CotoBackend.toModel(cotoJson)
    val cotos = repo.cotos.put(coto)

    // Update cotonomas
    val (cotonomas, fetchCotonoma) =
      repo.cotonomas.incrementTotalPosts(coto).pipe { cotonomas =>
        // Update the cotonoma's timestamp or fetch it
        coto.postedInId
          .map(touchCotonoma(_, coto.createdAtUtcIso, cotonomas))
          .getOrElse((cotonomas, Cmd.none))
      }

    // Post it to the timeline
    val timeline =
      (repo.nodes.focused, repo.cotonomas.focused) match {
        case (None, None) => model.timeline.post(coto.id) // all posts
        case (Some(node), None) =>
          if (coto.nodeId == node.id)
            model.timeline.post(coto.id) // posts in the focused node
          else
            model.timeline
        case (_, Some(cotonoma)) =>
          if (coto.postedInId == Some(cotonoma.id))
            model.timeline.post(coto.id) // posts in the focused cotonoma
          else
            model.timeline
      }

    (
      model
        .modify(_.repo.cotos).setTo(cotos)
        .modify(_.repo.cotonomas).setTo(cotonomas)
        .modify(_.timeline).setTo(timeline),
      Cmd.Batch(
        fetchCotonoma,
        // Fetch the updated original if this is a repost
        coto.repostOfId.map(updateCoto).getOrElse(Cmd.none)
      )
    )
  }

  private def createCotonoma(
      jsonPair: (CotonomaJson, CotoJson),
      model: Model
  ): (Model, Cmd.Batch[Msg]) = {
    val cotonoma = CotonomaBackend.toModel(jsonPair._1)
    val coto = CotoBackend.toModel(jsonPair._2)
    model
      .modify(_.repo.cotonomas).using(_.post(cotonoma, coto))
      .pipe(createCoto(jsonPair._2, _))
  }

  private def touchCotonoma(
      id: Id[Cotonoma],
      updatedAtUtcIso: String,
      cotonomas: Cotonomas
  ): (Cotonomas, Cmd.One[Msg]) =
    (
      cotonomas
        .update(id)(_.copy(updatedAtUtcIso = updatedAtUtcIso))
        .modify(_.recentIds).using(_.prependId(id)),
      if (!cotonomas.contains(id))
        Root.fetchCotonoma(id)
      else
        Cmd.none
    )

  private def updateCoto(id: Id[Coto]): Cmd.One[Msg] =
    CotoDetails.fetch(id).map(Msg.CotoUpdated(_).into)

  private def promote(id: Id[Coto]): Cmd.One[Msg] =
    CotonomaBackend.fetchByCotoId(id).map(Msg.Promoted(_).into)
}
