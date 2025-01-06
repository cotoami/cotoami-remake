package cotoami.updates

import scala.util.chaining._
import com.softwaremill.quicklens._

import fui.{Browser, Cmd}
import cotoami.{Model, Msg}
import cotoami.models.{Coto, Cotonoma, Id, Node}
import cotoami.repositories.Links
import cotoami.backend.CotonomaDetails
import cotoami.subparts.SectionGeomap

object DatabaseFocus {

  def node(nodeId: Option[Id[Node]], model: Model): (Model, Cmd.Batch[Msg]) =
    model
      .modify(_.domain).using(_.unfocus)
      .modify(_.domain.nodes).using(_.focus(nodeId))
      .modify(_.timeline).using(_.clear)
      .pipe { model =>
        val (navCotonomas, fetchRecentCotonomas) =
          model.navCotonomas.fetchRecent()(model)
        val (timeline, fetchTimeline) = model.timeline.fetchFirst(model.domain)
        (
          model.copy(navCotonomas = navCotonomas, timeline = timeline),
          Cmd.Batch(
            fetchRecentCotonomas,
            fetchTimeline,
            model.domain.fetchGraph,
            Browser.send(SectionGeomap.Msg.DatabaseFocusChanged.into)
          )
        )
      }

  def cotonoma(
      nodeId: Option[Id[Node]],
      cotonomaId: Id[Cotonoma],
      model: Model
  ): (Model, Cmd.Batch[Msg]) = {
    val shouldRecentFetchCotonomas =
      // the focused node is changed
      nodeId != model.domain.nodes.focusedId ||
        // or no recent cotonomas has been loaded yet
        // (which means the page being reloaded)
        model.domain.cotonomas.recentIds.isEmpty
    model
      .modify(_.domain).using(_.resetState)
      .modify(_.domain.nodes).using(_.focus(nodeId))
      .modify(_.domain.cotonomas).using(_.focus(Some(cotonomaId)))
      .modify(_.domain.cotos).using(_.clear())
      .modify(_.domain.links).setTo(Links())
      .modify(_.timeline).using(_.clear)
      .pipe { model =>
        val (navCotonomas, fetchRecentCotonomas) =
          if (shouldRecentFetchCotonomas)
            model.navCotonomas.fetchRecent()(model)
          else
            (model.navCotonomas, Cmd.none)
        val (timeline, fetchTimeline) = model.timeline.fetchFirst(model.domain)
        (
          model.copy(navCotonomas = navCotonomas, timeline = timeline),
          Cmd.Batch(
            CotonomaDetails.fetch(cotonomaId)
              .map(Msg.FocusedCotonomaDetailsFetched),
            fetchRecentCotonomas,
            fetchTimeline,
            model.domain.fetchGraph
          )
        )
      }
  }

  def coto(
      cotoId: Id[Coto],
      moveTo: Boolean,
      model: Model
  ): (Model, Cmd.One[Msg]) = {
    model
      .modify(_.domain.cotos).using(_.focus(cotoId))
      .pipe { model =>
        model.domain.cotos.focused match {
          case Some(focusedCoto) =>
            (
              focusedCoto.geolocation match {
                case Some(location) =>
                  model.modify(_.geomap).using(
                    if (moveTo)
                      _.focus(location).moveTo(location)
                    else
                      _.focus(location)
                  )
                case None => model
              },
              model.domain.lazyFetchGraphFrom(cotoId)
            )
          case None => (model, Cmd.none)
        }
      }
  }
}
