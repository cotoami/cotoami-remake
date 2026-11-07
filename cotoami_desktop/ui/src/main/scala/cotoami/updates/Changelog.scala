package cotoami.updates

import marubinotto.fui.toCmd
import scala.util.chaining._
import com.softwaremill.quicklens._

import marubinotto.fui.{Browser, Cmd}

import cotoami.{Model, Msg}
import cotoami.models._
import cotoami.repository._
import cotoami.backend.{
  ChangeJson,
  ChangelogEntry,
  CotoDetails,
  CotonomaBackend,
  DecodedChange,
  ItoBackend,
  NodeDetails
}
import cotoami.subparts.SectionPins

object Changelog {

  def apply(log: ChangelogEntry, model: Model): (Model, Cmd[Msg]) = {
    val expectedNumber = model.repo.lastChangeNumber + 1
    if (log.serialNumber == expectedNumber)
      applyChange(log.change, model)
        .modify(_._1.repo.lastChangeNumber).setTo(log.serialNumber)
    else
      (
        model.info(
          s"Unexpected change number (expected: ${expectedNumber})",
          Some(log.serialNumber.toString())
        ),
        Browser.send(Msg.ReloadRepository)
      )
  }

  private def applyChange(
      change: DecodedChange,
      model: Model
  ): (Model, Cmd[Msg]) =
    change match {
      case DecodedChange.CreateCoto(coto)     => createCoto(coto, model)
      case DecodedChange.CreateCotonoma(pair) => createCotonoma(pair, model)
      case DecodedChange.UpsertNode(node)     =>
        (model.modify(_.repo.nodes).using(_.put(node)), Cmd.none)
      case DecodedChange.CreateNode(node, root) =>
        val updated = model.modify(_.repo.nodes).using(_.put(node))
        root.map(createCotonoma(_, updated)).getOrElse((updated, Cmd.none))
      case DecodedChange.SetNodeIcon(nodeId, icon) =>
        (
          model.modify(_.repo.nodes).using(_.setIcon(nodeId, icon))
            .modify(_.geomap).using(_.refreshMarkers),
          Cmd.none
        )
      case DecodedChange.Other(json) => applyOtherChange(json, model)
    }

  private def applyOtherChange(
      change: ChangeJson,
      model: Model
  ): (Model, Cmd[Msg]) =
    // Handle changes in order of assumed their frequency:
    change.CreateIto.toOption.map { json =>
      val ito = ItoBackend.toModel(json)
      if (model.repo.itos.hasDuplicateOrder(ito))
        (
          model,
          Root.fetchGraphFromCoto(ito.targetCotoId) ++
            // Insertion: refresh sibling itos if there's an order duplication.
            Root.fetchSiblingItoGroup(ito.sourceCotoId, ito.nodeId)
        )
      else
        (
          model.modify(_.repo.itos).using(_.put(ito)),
          Root.fetchGraphFromCoto(ito.targetCotoId) ++
            (if (model.repo.isPin(ito))
               // Scroll to the just added pin
               Browser.send(SectionPins.Msg.ScrollToPin(ito).into)
             else
               Cmd.none)
        )
    }
      .orElse(
        change.DeleteIto.toOption.map { json =>
          val itoId: Id[Ito] = Id(json.ito_id)
          (
            model.modify(_.repo.itos).using(_.delete(itoId)),
            Cmd.none
          )
        }
      )
      .orElse(
        change.ChangeItoOrder.toOption.map { json =>
          val itoId: Id[Ito] = Id(json.ito_id)
          (
            model,
            model.repo.itos.get(itoId)
              .map(ito =>
                Root.fetchSiblingItoGroup(ito.sourceCotoId, ito.nodeId)
              )
              .getOrElse(Cmd.none)
          )
        }
      )
      .orElse(
        change.EditCoto.toOption.map(json =>
          (model, updateCoto(Id(json.coto_id)))
        )
      )
      .orElse(
        change.DeleteCoto.toOption.map(json =>
          deleteCoto(Id(json.coto_id), model)
        )
      )
      .orElse(
        change.EditIto.toOption.map(json =>
          (model, Root.fetchIto(Id(json.ito_id)))
        )
      )
      .orElse(
        change.RenameCotonoma.toOption.map { json =>
          val cotonomaId: Id[Cotonoma] = Id(json.cotonoma_id)
          model
            .pipe(touchCotonoma(Some(cotonomaId), json.updated_at))
            .pipe(
              addCmd(_ =>
                model.repo.cotonomas.get(cotonomaId)
                  .map(_.cotoId)
                  .map(updateCoto)
                  .getOrElse(Cmd.none)
              )
            )
        }
      )
      .orElse(
        change.Promote.toOption.map(json => (model, promote(Id(json.coto_id))))
      )
      .orElse(
        change.PromoteCoto.toOption.map(json =>
          (model, promote(Id(json.coto_id)))
        )
      )
      .orElse(
        change.RenameNode.toOption.map(json =>
          (model, updateNode(Id(json.node_id)))
        )
      )
      .getOrElse((model, Cmd.none))

  private def createCoto(
      coto: Coto,
      model: Model
  ): (Model, Cmd[Msg]) = {

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
      pair: (Cotonoma, Coto),
      model: Model
  ): (Model, Cmd[Msg]) = {
    val (cotonoma, coto) = pair
    model
      .modify(_.repo.cotonomas).using(_.post(cotonoma, coto))
      .pipe(createCoto(coto, _))
  }

  def touchCotonoma(id: Option[Id[Cotonoma]], updatedAtUtcIso: String)(
      model: Model
  ): (Model, Cmd[Msg]) = {
    id.map(id =>
      model.repo.touchCotonoma(id, updatedAtUtcIso).pipe { case (repo, cmd) =>
        (model.copy(repo = repo), cmd)
      }
    ).getOrElse((model, Cmd.none))
  }

  private def updateNode(id: Id[Node]): Cmd.One[Msg] =
    NodeDetails.fetch(id).toCmd.map(Msg.NodeUpdated(_))

  private def updateCoto(id: Id[Coto]): Cmd.One[Msg] =
    CotoDetails.fetch(id).toCmd.map(Msg.CotoUpdated(_))

  private def promote(id: Id[Coto]): Cmd.One[Msg] =
    CotonomaBackend.fetchByCotoId(id).toCmd.map(Msg.Promoted(_))

  private def deleteCoto(
      cotoId: Id[Coto],
      model: Model
  ): (Model, Cmd[Msg]) =
    (
      model.modify(_.repo).using(_.deleteCoto(cotoId)),
      // Update the original coto if it's a repost.
      model.repo.cotos.get(cotoId).flatMap(_.repostOfId)
        .map(updateCoto)
        .getOrElse(Cmd.none) ++
        (if (model.repo.isCurrentCotonoma(cotoId))
           Browser.send(Msg.UnfocusCotonoma)
         else Cmd.none) ++
        (if (!model.repo.postedBySelf(cotoId))
           Root.fetchOthersLastPostedAt
         else Cmd.none)
    )
}
