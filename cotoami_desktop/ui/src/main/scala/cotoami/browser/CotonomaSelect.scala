package cotoami.browser

import scala.scalajs.js

import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.components.materialSymbol
import marubinotto.fui.Cmd

import cotoami.{Context, Model => CotoamiModel}
import cotoami.backend.{CotonomaBackend, ErrorJson}
import cotoami.models.{Coto, Cotonoma, Id, Node}
import cotoami.subparts.SelectCotonoma
import cotoami.subparts.SelectCotonoma.{
  CotonomaOption,
  ExistingCotonoma,
  NewCotonoma
}

object CotonomaSelect {
  val MaxHistorySize = 10

  case class Model(
      query: String = "",
      options: Seq[CotonomaOption] = Seq.empty,
      history: Seq[Cotonoma] = Seq.empty,
      selected: Option[CotonomaOption] = None,
      loading: Boolean = false,
      creating: Boolean = false,
      error: Option[String] = None
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
    case class Selected(option: Option[CotonomaOption]) extends Msg
    case object CreateSelected extends Msg
    case class CotonomaCreated(result: Either[ErrorJson, (Cotonoma, Coto)])
        extends Msg
  }

  sealed trait Effect

  object Effect {
    case class FocusCotonoma(cotonoma: Cotonoma, select: Model) extends Effect
    case class ClearCotonoma(select: Model) extends Effect
  }

  def update(
      msg: Msg,
      model: Model
  )(using context: Context): (Model, Cmd[Msg], Option[Effect]) =
    msg match {
      case Msg.QueryInput(query) =>
        if (query.isBlank())
          (
            model.copy(query = query, loading = false, error = None),
            Cmd.none,
            None
          )
        else
          (
            model.copy(query = query, loading = true, error = None),
            CotonomaBackend.fetchByPartial(query, None)
              .map(Msg.CotonomasFetched(query, _)),
            None
          )

      case Msg.CotonomasFetched(query, Right(cotonomas))
          if query == model.query =>
        val targetNodeId = context.repo.nodes.current.map(_.id)
        val newCotonoma =
          targetNodeId
            .filter(nodeId =>
              context.repo.nodes.rootCotonomaId(nodeId).isDefined &&
                !cotonomas.exists(cotonoma =>
                  cotonoma.name == query && cotonoma.nodeId == nodeId
                )
            )
            .map(nodeId => new NewCotonoma(query, nodeId.uuid))
        (
          model.copy(
            options =
              newCotonoma.toSeq ++ cotonomas.map(new ExistingCotonoma(_)).toSeq,
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

      case Msg.Selected(Some(option: ExistingCotonoma)) =>
        val select = model.copy(
          query = "",
          options = Seq.empty,
          selected = None,
          loading = false,
          creating = false,
          error = None
        )
        (
          model,
          Cmd.none,
          Some(Effect.FocusCotonoma(option.cotonoma, select))
        )

      case Msg.Selected(option @ Some(_: NewCotonoma)) =>
        (
          model.copy(
            selected = option,
            query = "",
            options = Seq.empty,
            loading = false,
            error = None
          ),
          Cmd.none,
          None
        )

      case Msg.Selected(None) =>
        val select = model.copy(
          query = "",
          selected = None,
          loading = false,
          creating = false,
          error = None
        )
        (
          model,
          Cmd.none,
          Some(Effect.ClearCotonoma(select))
        )

      case Msg.CreateSelected =>
        model.selected match {
          case Some(dest: NewCotonoma) =>
            val cmd =
              context.repo.nodes
                .get(Id[Node](dest.targetNodeId))
                .flatMap(_.rootCotonomaId)
                .map(cotonomaId =>
                  CotonomaBackend.post(dest.name, None, None, cotonomaId)
                    .map(Msg.CotonomaCreated(_))
                )
                .getOrElse(Cmd.none)
            (
              model.copy(creating = true, error = None),
              cmd,
              None
            )

          case _ =>
            (model, Cmd.none, None)
        }

      case Msg.CotonomaCreated(Right((cotonoma, _))) =>
        val select = model.copy(
          query = "",
          options = Seq.empty,
          selected = None,
          loading = false,
          creating = false,
          error = None
        )
        (
          model,
          Cmd.none,
          Some(Effect.FocusCotonoma(cotonoma, select))
        )

      case Msg.CotonomaCreated(Left(e)) =>
        (
          model.copy(
            creating = false,
            error = Some(e.default_message)
          ),
          Cmd.none,
          None
        )
    }

  def view(
      model: Model,
      app: CotoamiModel,
      dispatch: Msg => Unit
  ): ReactElement = {
    given Context = app
    val focused =
      model.selected
        .orElse(app.repo.currentCotonoma.map(new ExistingCotonoma(_)))
    div(className := "browser-cotonoma-select-with-create")(
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
        allowNewCotonomas = true,
        isLoading = model.loading,
        isClearable = true,
        onChange = Some((option, _) => {
          dispatch(Msg.Selected(option))
        })
      ),
      model.selected.collect { case _: NewCotonoma =>
        button(
          className := "default create-cotonoma",
          `type` := "button",
          disabled := model.creating,
          aria - "busy" := model.creating.toString,
          onClick := (_ => dispatch(Msg.CreateSelected))
        )(materialSymbol("add"))
      },
      model.error.map(error => div(className := "error")(error))
    )
  }
}
