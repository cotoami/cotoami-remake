package cotoami.updates

import scala.util.chaining._
import com.softwaremill.quicklens._

import fui.{Browser, Cmd}
import cotoami.{Model, Msg}
import cotoami.models.{Coto, Cotonoma, Id, Node}
import cotoami.repository.Links
import cotoami.backend.CotonomaDetails
import cotoami.subparts.SectionGeomap

object DatabaseFocus {

  def node(nodeId: Option[Id[Node]], model: Model): (Model, Cmd.Batch[Msg]) =
    model
      .modify(_.repo).using(_.unfocus)
      .modify(_.repo.nodes).using(_.focus(nodeId))
      .modify(_.search).using(_.clear)
      .modify(_.timeline).using(_.onFocusChange)
      .modify(_.flowInput).using(_.onFocusChange)
      .pipe { model =>
        val (navCotonomas, fetchRecentCotonomas) =
          model.navCotonomas.fetchRecent()(model)
        val (timeline, fetchTimeline) = model.timeline.fetchFirst(model.repo)
        (
          model.copy(navCotonomas = navCotonomas, timeline = timeline),
          Cmd.Batch(
            fetchRecentCotonomas,
            fetchTimeline,
            model.repo.fetchGraph,
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
      nodeId != model.repo.nodes.focusedId ||
        // or no recent cotonomas has been loaded yet
        // (which means the page being reloaded)
        model.repo.cotonomas.recentIds.isEmpty
    model
      .modify(_.repo).using(_.onFocusChange)
      .modify(_.repo.nodes).using(_.focus(nodeId))
      .modify(_.repo.cotonomas).using(_.focus(Some(cotonomaId)))
      .modify(_.repo.cotos).using(_.clear())
      .modify(_.repo.links).setTo(Links())
      .modify(_.search).using(_.clear)
      .modify(_.timeline).using(_.onFocusChange)
      .modify(_.flowInput).using(_.onFocusChange)
      .pipe { model =>
        val (navCotonomas, fetchRecentCotonomas) =
          if (shouldRecentFetchCotonomas)
            model.navCotonomas.fetchRecent()(model)
          else
            (model.navCotonomas, Cmd.none)
        val (timeline, fetchTimeline) = model.timeline.fetchFirst(model.repo)
        (
          model.copy(navCotonomas = navCotonomas, timeline = timeline),
          Cmd.Batch(
            CotonomaDetails.fetch(cotonomaId)
              .map(Msg.FocusedCotonomaDetailsFetched),
            fetchRecentCotonomas,
            fetchTimeline,
            model.repo.fetchGraph
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
      .modify(_.repo.cotos).using(_.focus(cotoId))
      .pipe { model =>
        model.repo.cotos.focused match {
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
              model.repo.lazyFetchGraphFrom(cotoId)
            )
          case None => (model, Cmd.none)
        }
      }
  }
}
