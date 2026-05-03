package cotoami.browser

import scala.scalajs.js

import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.fui.Cmd

import cotoami.{Context, Model => CotoamiModel}
import cotoami.backend.{CotonomaBackend, ErrorJson}
import cotoami.models.Cotonoma
import cotoami.subparts.SelectCotonoma
import cotoami.subparts.SelectCotonoma.ExistingCotonoma

object CotonomaSelect {
  val MaxHistorySize = 10

  case class Model(
      query: String = "",
      options: Seq[ExistingCotonoma] = Seq.empty,
      history: Seq[Cotonoma] = Seq.empty,
      loading: Boolean = false
  ) {
    def historyOptions: Seq[ExistingCotonoma] =
      history.map(new ExistingCotonoma(_))

    def remember(cotonoma: Cotonoma): Model =
      copy(history =
        (cotonoma +: history.filterNot(_.id == cotonoma.id))
          .take(MaxHistorySize)
      )
  }

  sealed trait Msg

  object Msg {
    case class QueryInput(query: String) extends Msg
    case class CotonomasFetched(
        query: String,
        result: Either[ErrorJson, js.Array[Cotonoma]]
    ) extends Msg
    case class Selected(cotonoma: Option[Cotonoma]) extends Msg
  }

  sealed trait Effect

  object Effect {
    case class FocusCotonoma(cotonoma: Cotonoma, select: Model) extends Effect
    case class ClearCotonoma(select: Model) extends Effect
  }

  def update(msg: Msg, model: Model): (Model, Cmd[Msg], Option[Effect]) =
    msg match {
      case Msg.QueryInput(query) =>
        if (query.isBlank())
          (
            model.copy(query = query, loading = false),
            Cmd.none,
            None
          )
        else
          (
            model.copy(query = query, loading = true),
            CotonomaBackend.fetchByPartial(query, None)
              .map(Msg.CotonomasFetched(query, _)),
            None
          )

      case Msg.CotonomasFetched(query, Right(cotonomas))
          if query == model.query =>
        (
          model.copy(
            options = cotonomas.map(new ExistingCotonoma(_)).toSeq,
            loading = false
          ),
          Cmd.none,
          None
        )

      case Msg.CotonomasFetched(query, Left(_)) if query == model.query =>
        (
          model.copy(loading = false),
          Cmd.none,
          None
        )

      case Msg.CotonomasFetched(_, _) =>
        (model, Cmd.none, None)

      case Msg.Selected(Some(cotonoma)) =>
        val select = model.copy(query = "", options = Seq.empty, loading = false)
        (
          model,
          Cmd.none,
          Some(Effect.FocusCotonoma(cotonoma, select))
        )

      case Msg.Selected(None) =>
        val select = model.copy(query = "", loading = false)
        (
          model,
          Cmd.none,
          Some(Effect.ClearCotonoma(select))
        )
    }

  def view(
      model: Model,
      app: CotoamiModel,
      dispatch: Msg => Unit
  ): ReactElement = {
    given Context = app
    val focused =
      app.repo.currentCotonoma.map(new ExistingCotonoma(_))
    SelectCotonoma(
      className = "browser-cotonoma-select",
      options =
        if (model.query.isBlank()) model.historyOptions
        else model.options,
      placeholder = Some(app.i18n.text.Cotonoma),
      value = focused,
      onInputChange = Some((input, actionMeta) => {
        if (actionMeta.action == "input-change")
          dispatch(Msg.QueryInput(input))
        else if (input != model.query)
          dispatch(Msg.QueryInput(input))
        input
      }),
      noOptionsMessage =
        Some(_ => div()(app.i18n.text.ModalRepost_typeCotonomaName)),
      isLoading = model.loading,
      isClearable = true,
      onChange = Some((option, _) => {
        dispatch(
          Msg.Selected(
            option.collect { case option: ExistingCotonoma =>
              option.cotonoma
            }
          )
        )
      })
    )
  }
}
