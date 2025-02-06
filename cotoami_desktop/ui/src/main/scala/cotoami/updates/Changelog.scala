package cotoami.updates

import scala.util.chaining._
import com.softwaremill.quicklens._

import fui.{Browser, Cmd}
import cotoami.utils.facade.Nullable
import cotoami.{Model, Msg}
import cotoami.models._
import cotoami.repositories._
import cotoami.backend.{
  ChangeJson,
  ChangelogEntryJson,
  CotoBackend,
  CotoJson,
  CotonomaBackend,
  CotonomaJson,
  LinkBackend,
  NodeBackend
}
import cotoami.subparts.SectionPins

object Changelog {

  def apply(log: ChangelogEntryJson, model: Model): (Model, Cmd[Msg]) = {
    val expectedNumber = model.domain.lastChangeNumber + 1
    if (log.serial_number == expectedNumber)
      applyChange(log.change, model)
        .modify(_._1.domain.lastChangeNumber).setTo(log.serial_number)
    else
      (
        model.info(
          s"Unexpected change number (expected: ${expectedNumber})",
          Some(log.serial_number.toString())
        ),
        Browser.send(Msg.ReloadDomain)
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

    // CreateLink
    for (json <- change.CreateLink.toOption) {
      val link = LinkBackend.toModel(json)
      return (
        model
          .modify(_.domain.links).using(_.put(link))
          .modify(_.pins.justPinned).using(justPinned =>
            if (model.domain.isPin(link))
              justPinned + link.targetCotoId
            else
              justPinned
          ),
        Cmd.Batch(
          Domain.fetchGraphFromCoto(link.targetCotoId),
          Browser.send(SectionPins.Msg.ScrollToPin(link).into)
        )
      )
    }

    // DeleteLink
    for (json <- change.DeleteLink.toOption) {
      val linkId: Id[Link] = Id(json.link_id)
      return (
        model.modify(_.domain.links).using(_.delete(linkId)),
        Cmd.none
      )
    }

    // ChangeLinkOrder
    for (json <- change.ChangeLinkOrder.toOption) {
      val linkId: Id[Link] = Id(json.link_id)
      return (
        model,
        model.domain.links.get(linkId)
          .map(link => Domain.fetchOutgoingLinks(link.sourceCotoId))
          .getOrElse(Cmd.none)
      )
    }

    // EditCoto
    for (json <- change.EditCoto.toOption) {
      return (model, Domain.fetchCotoDetails(Id(json.coto_id)))
    }

    // DeleteCoto
    for (json <- change.DeleteCoto.toOption) {
      val cotoId: Id[Coto] = Id(json.coto_id)
      return (
        model.modify(_.domain).using(_.deleteCoto(cotoId)),
        // Update the original coto if it's a repost
        model.domain.cotos.get(cotoId).flatMap(_.repostOfId)
          .map(Domain.fetchCotoDetails)
          .getOrElse(Cmd.none)
      )
    }

    // EditLink
    for (json <- change.EditLink.toOption) {
      return (model, Domain.fetchLink(Id(json.link_id)))
    }

    // RenameCotonoma
    for (json <- change.RenameCotonoma.toOption) {
      val cotonomaId: Id[Cotonoma] = Id(json.cotonoma_id)
      return touchCotonoma(
        cotonomaId,
        json.updated_at,
        model.domain.cotonomas
      ).pipe { case (cotonomas, cmd) =>
        (
          model.modify(_.domain.cotonomas).setTo(cotonomas),
          Cmd.Batch(cmd, Domain.fetchCotonoma(cotonomaId))
        )
      }
    }

    // UpsertNode
    for (json <- change.UpsertNode.toOption) {
      val node = NodeBackend.toModel(json)
      return (model.modify(_.domain.nodes).using(_.put(node)), Cmd.none)
    }

    // CreateNode
    for (json <- change.CreateNode.toOption) {
      return model.modify(_.domain.nodes).using(
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
          .modify(_.domain.nodes).using(_.rename(nodeId, json.name))
          .modify(_.geomap).using(_.refreshMarkers),
        model.domain.nodes.get(nodeId)
          .flatMap(_.rootCotonomaId)
          .map(Domain.fetchCotonoma)
          .getOrElse(Cmd.none)
      )
    }

    // SetNodeIcon
    for (json <- change.SetNodeIcon.toOption) {
      return (
        model
          .modify(_.domain.nodes).using(
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
    val domain = model.domain

    // Register the coto
    val coto = CotoBackend.toModel(cotoJson)
    val cotos = domain.cotos.put(coto)

    // Update the target cotonoma or fetch it if not registered yet
    val (cotonomas, fetchCotonoma) =
      coto.postedInId.map(
        touchCotonoma(_, coto.createdAtUtcIso, domain.cotonomas)
      )
        .getOrElse((domain.cotonomas, Cmd.none))

    // Post the coto to the timeline
    val timeline =
      (domain.nodes.focused, domain.cotonomas.focused) match {
        case (None, None) => model.timeline.post(coto.id) // all posts
        case (Some(node), None) =>
          if (coto.nodeId == node.id)
            model.timeline.post(coto.id) // posts in the focused node
          else
            model.timeline
        case (_, Some(cotonom)) =>
          if (coto.postedInId == Some(cotonom.id))
            model.timeline.post(coto.id) // posts in the focused cotonoma
          else
            model.timeline
      }

    // Refresh geomap markers
    val geomap =
      if (coto.geolocated)
        model.geomap.addOrRemoveMarkers
      else
        model.geomap
    (
      model
        .modify(_.domain.cotos).setTo(cotos)
        .modify(_.domain.cotonomas).setTo(cotonomas)
        .modify(_.timeline).setTo(timeline)
        .modify(_.geomap).setTo(geomap),
      Cmd.Batch(
        fetchCotonoma,
        // Fetch the updated original if this is a repost
        coto.repostOfId.map(Domain.fetchCotoDetails).getOrElse(Cmd.none)
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
      .modify(_.domain.cotonomas).using(_.post(cotonoma, coto))
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
        Domain.fetchCotonoma(id)
      else
        Cmd.none
    )
}
