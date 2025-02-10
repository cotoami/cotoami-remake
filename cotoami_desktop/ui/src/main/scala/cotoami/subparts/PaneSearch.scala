package cotoami.subparts

import scala.util.chaining._

import slinky.core.facade.ReactElement
import slinky.web.html._
import com.softwaremill.quicklens._

import fui.Cmd
import cotoami.{Context, Into, Model, Msg => AppMsg}
import cotoami.models.{Coto, PaginatedIds}
import cotoami.repositories.Domain
import cotoami.backend.{ErrorJson, PaginatedCotos}

object PaneSearch {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      query: String = "",

      // To avoid rendering old results unintentionally
      fetchNumber: Int = 0,

      // Search results
      cotoIds: PaginatedIds[Coto] = PaginatedIds(),

      // State
      imeActive: Boolean = false,
      loading: Boolean = false
  ) {
    def inputQuery(query: String): (Model, Cmd[AppMsg]) =
      if (query.isBlank())
        (clear.copy(query = query), Cmd.none)
      else {
        if (imeActive)
          (copy(query = query), Cmd.none)
        else
          copy(query = query).fetchFirst
      }

    def clear: Model =
      copy(query = "", cotoIds = PaginatedIds(), loading = false)

    def fetchFirst: (Model, Cmd.One[AppMsg]) =
      (
        copy(loading = true),
        fetch(query, 0, fetchNumber + 1)
      )

    def appendPage(cotos: PaginatedCotos, fetchNumber: Int): Model =
      this
        .modify(_.cotoIds).using(_.appendPage(cotos.page))
        .modify(_.fetchNumber).setTo(fetchNumber)
        .modify(_.loading).setTo(false)
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = AppMsg.PaneSearchMsg(this)
  }

  object Msg {
    case class QueryInput(query: String) extends Msg
    case object ClearQuery extends Msg
    case object ImeCompositionStart extends Msg
    case object ImeCompositionEnd extends Msg
    case class Fetched(number: Int, result: Either[ErrorJson, PaginatedCotos])
        extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Domain, Cmd[AppMsg]) = {
    val default = (model, context.domain, Cmd.none)
    msg match {
      case Msg.QueryInput(query) =>
        model.inputQuery(query).pipe { case (model, cmd) =>
          default.copy(_1 = model, _3 = cmd)
        }

      case Msg.ClearQuery => default.copy(_1 = model.clear)

      case Msg.ImeCompositionStart =>
        default.copy(_1 = model.copy(imeActive = true))

      case Msg.ImeCompositionEnd =>
        model.fetchFirst.pipe { case (model, cmd) =>
          default.copy(
            _1 = model.copy(imeActive = false),
            _3 = cmd
          )
        }

      case Msg.Fetched(number, Right(cotos)) =>
        if (number > model.fetchNumber)
          default.copy(
            _1 = model.appendPage(cotos, number),
            _2 = context.domain.importFrom(cotos)
          )
        else
          default

      case Msg.Fetched(_, Left(e)) =>
        default.copy(
          _1 = model.copy(loading = false),
          _3 = cotoami.error("Couldn't search cotos.", e)
        )
    }
  }

  private def fetch(
      query: String,
      pageIndex: Double,
      fetchNumber: Int
  ): Cmd.One[AppMsg] =
    if (!query.isBlank())
      PaginatedCotos.search(
        query,
        None,
        None,
        false,
        pageIndex
      ).map(Msg.Fetched(fetchNumber, _).into)
    else
      Cmd.none

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(
      model: Model
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    section(className := "search")(
    )
}
