package cotoami.updates

import scala.util.chaining._
import com.softwaremill.quicklens._

import marubinotto.fui.{Browser, Cmd}
import marubinotto.facade.Nullable

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
  NodeBackend,
  NodeDetails
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
            Root.fetchSiblingItoGroup(ito.sourceCotoId, ito.nodeId)
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
          .map(ito => Root.fetchSiblingItoGroup(ito.sourceCotoId, ito.nodeId))
          .getOrElse(Cmd.none)
      )
    }

    // EditCoto
    for (json <- change.EditCoto.toOption) {
      return (model, updateCoto(Id(json.coto_id)))
    }

    // DeleteCoto
    for (json <- change.DeleteCoto.toOption) {
      return deleteCoto(Id(json.coto_id), model)
    }

    // EditIto
    for (json <- change.EditIto.toOption) {
      return (model, Root.fetchIto(Id(json.ito_id)))
    }

    // RenameCotonoma
    for (json <- change.RenameCotonoma.toOption) {
      val cotonomaId: Id[Cotonoma] = Id(json.cotonoma_id)
      return model
        .pipe(touchCotonoma(Some(cotonomaId), json.updated_at))
        .pipe(
          addCmd((_: Model) =>
            model.repo.cotonomas.get(cotonomaId)
              .map(_.cotoId)
              .map(updateCoto)
              .getOrElse(Cmd.none)
          )
        )
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
      return (model, updateNode(Id(json.node_id)))
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
  ): (Model, Cmd[Msg]) = {
    val coto = CotoBackend.toModel(cotoJson)

    // Post it to the timeline
    val timeline =
      (model.repo.nodes.focused, model.repo.cotonomas.focused) match {
        // all posts
        case (None, None) => model.timeline.post(coto.id)
        // only if it's for the focused node
        case (Some(focusedNode), None) =>
          if (coto.nodeId == focusedNode.id)
            model.timeline.post(coto.id)
          else
            model.timeline
        // only if it's for the focused cotonoma
        case (_, Some(focusedCotonoma)) =>
          if (coto.postedInId == Some(focusedCotonoma.id))
            model.timeline.post(coto.id)
          else
            model.timeline
      }

    model
      .modify(_.repo.cotos).using(_.put(coto))
      .modify(_.repo.cotonomas).using(_.incrementTotalPosts(coto))
      .modify(_.repo.nodes).using(
        _.updateOthersLastPostedAt(coto)
      )
      .modify(_.timeline).setTo(timeline)
      .pipe(touchCotonoma(coto.postedInId, coto.createdAtUtcIso))
      .pipe(
        addCmd(_ =>
          // Fetch the updated original if this is a repost
          coto.repostOfId.map(updateCoto).getOrElse(Cmd.none)
        )
      )
      .pipe(addCmd(_.repo.updateUnreadBadge))
  }

  private def createCotonoma(
      jsonPair: (CotonomaJson, CotoJson),
      model: Model
  ): (Model, Cmd[Msg]) = {
    val cotonoma = CotonomaBackend.toModel(jsonPair._1)
    val coto = CotoBackend.toModel(jsonPair._2)
    model
      .modify(_.repo.cotonomas).using(_.post(cotonoma, coto))
      .pipe(createCoto(jsonPair._2, _))
  }

  def touchCotonoma(id: Option[Id[Cotonoma]], updatedAtUtcIso: String)(
      model: Model
  ): (Model, Cmd.One[Msg]) = {
    id.map(id =>
      model.repo.touchCotonoma(id, updatedAtUtcIso).pipe { case (repo, cmd) =>
        (model.copy(repo = repo), cmd)
      }
    ).getOrElse((model, Cmd.none))
  }

  private def updateNode(id: Id[Node]): Cmd.One[Msg] =
    NodeDetails.fetch(id).map(Msg.NodeUpdated(_))

  private def updateCoto(id: Id[Coto]): Cmd.One[Msg] =
    CotoDetails.fetch(id).map(Msg.CotoUpdated(_))

  private def promote(id: Id[Coto]): Cmd.One[Msg] =
    CotonomaBackend.fetchByCotoId(id).map(Msg.Promoted(_))

  private def deleteCoto(
      cotoId: Id[Coto],
      model: Model
  ): (Model, Cmd.Batch[Msg]) =
    (
      model.modify(_.repo).using(_.deleteCoto(cotoId)),
      Cmd.Batch(
        // Update the original coto if it's a repost
        model.repo.cotos.get(cotoId).flatMap(_.repostOfId)
          .map(updateCoto)
          .getOrElse(Cmd.none),

        // UnfocusCotonoma if the deleted coto is the current cotonoma
        if (model.repo.isCurrentCotonoma(cotoId))
          Browser.send(Msg.UnfocusCotonoma)
        else
          Cmd.none,

        // If the coto was posted by others, update othersLastPostedAt
        if (!model.repo.postedBySelf(cotoId))
          Root.fetchOthersLastPostedAt
        else
          Cmd.none
      )
    )
}
