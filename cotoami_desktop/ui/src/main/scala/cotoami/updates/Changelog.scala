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
    for (cotoJson <- change.CreateCoto.toOption) {
      return createCoto(cotoJson, model)
    }

    // CreateCotonoma
    for (cotonomaJson <- change.CreateCotonoma.toOption) {
      return createCotonoma(cotonomaJson, model)
    }

    // CreateLink
    for (linkJson <- change.CreateLink.toOption) {
      val link = LinkBackend.toModel(linkJson)
      return (model.modify(_.domain.links).using(_.put(link)), Cmd.none)
    }

    // DeleteCoto
    for (deleteCotoJson <- change.DeleteCoto.toOption) {
      val cotoId: Id[Coto] = Id(deleteCotoJson.coto_id)
      return (
        model.copy(domain = model.domain.deleteCoto(cotoId)),
        model.domain.cotos.get(cotoId).flatMap(_.repostOfId)
          .map(Domain.fetchCotoDetails)
          .getOrElse(Cmd.none)
      )
    }

    // UpsertNode
    for (nodeJson <- change.UpsertNode.toOption) {
      val node = NodeBackend.toModel(nodeJson)
      return (model.modify(_.domain.nodes).using(_.put(node)), Cmd.none)
    }

    // CreateNode
    for (createNodeJson <- change.CreateNode.toOption) {
      return model.modify(_.domain.nodes).using(
        _.put(NodeBackend.toModel(createNodeJson.node))
      ).pipe { model =>
        Nullable.toOption(createNodeJson.root)
          .map(createCotonoma(_, model))
          .getOrElse((model, Cmd.none))
      }
    }

    // SetNodeIcon
    for (setNodeIconJson <- change.SetNodeIcon.toOption) {
      return (
        model
          .modify(_.domain.nodes).using(
            _.setIcon(Id(setNodeIconJson.node_id), setNodeIconJson.icon)
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
    val coto = CotoBackend.toModel(cotoJson, true)
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
        CotonomaBackend.fetch(id)
          .map(Domain.Msg.CotonomaFetched(_).into)
      else
        Cmd.none
    )
}
