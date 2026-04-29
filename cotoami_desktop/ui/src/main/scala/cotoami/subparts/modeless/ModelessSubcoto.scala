package cotoami.subparts.modeless

import scala.util.chaining._

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import marubinotto.fui.{Browser, Cmd}
import marubinotto.components.{materialSymbol, ScrollArea}

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Coto, Cotonoma, Id, Ito}
import cotoami.backend.{CotoBackend, ErrorJson}
import cotoami.subparts.{PartsCoto, SelectCotonoma}
import cotoami.subparts.EditorCoto._
import cotoami.subparts.SelectCotonoma.ExistingCotonoma
import cotoami.subparts.SectionGeomap.{Model => Geomap}

object ModelessSubcoto {

  val DialogId = ModelessDialogId.Subcoto

  case class Model(
      sourceCotoId: Id[Coto],
      targetCotonomas: Seq[ExistingCotonoma],
      postTo: Option[ExistingCotonoma],
      order: Option[Int] = None,
      cotoForm: CotoForm.Model = CotoForm.Model(),
      posting: Boolean = false,
      error: Option[String] = None
  ) {
    def readyToPost: Boolean =
      !posting && postTo.isDefined && cotoForm.hasValidContents

    def post: (Model, Cmd.One[AppMsg]) =
      (
        copy(posting = true),
        postTo
          .map(target =>
            CotoBackend.postSubcoto(
              sourceCotoId,
              cotoForm.toBackendInput,
              target.cotonoma.id,
              order
            )
          )
          .getOrElse(Cmd.none)
          .map(Msg.Posted(_).into)
      )
  }

  object Model {
    def apply(
        sourceCotoId: Id[Coto],
        order: Option[Int],
        defaultCotonomaId: Option[Id[Cotonoma]]
    )(using context: Context): Model = {
      val repo = context.repo
      val postedInIds =
        repo.cotos.get(sourceCotoId).map(_.postedInIds).getOrElse(Seq.empty)

      var targetCotonomaIds =
        repo.cotonomas.getByCotoId(sourceCotoId) match {
          case Some(sourceCotonoma) =>
            ((sourceCotonoma.id +: postedInIds) ++ repo.currentCotonomaId)
          case None =>
            repo.currentCotonomaId match {
              case Some(current) =>
                if (postedInIds.contains(current)) current +: postedInIds
                else postedInIds :+ current
              case None => postedInIds
            }
        }
      targetCotonomaIds =
        (defaultCotonomaId ++ targetCotonomaIds).toSeq.distinct

      val targetCotonomas =
        targetCotonomaIds.map(repo.cotonomas.get).flatten.map(cotonoma =>
          new ExistingCotonoma(
            cotonoma,
            !repo.canPostCotoTo(cotonoma) ||
              !repo.nodes.canEditItosIn(cotonoma.nodeId)
          )
        )

      Model(
        sourceCotoId = sourceCotoId,
        targetCotonomas = targetCotonomas,
        postTo = targetCotonomas.find(!_.isDisabled),
        order = order
      )
    }
  }

  sealed trait Msg extends Into[AppMsg] {
    override def into: AppMsg = AppMsg.ModelessSubcotoMsg(this)
  }

  object Msg {
    case class Open(
        sourceCotoId: Id[Coto],
        order: Option[Int],
        defaultCotonomaId: Option[Id[Cotonoma]]
    ) extends Msg
    case object Focus extends Msg
    case object Close extends Msg
    case class CotoFormMsg(submsg: CotoForm.Msg) extends Msg
    case class TargetCotonomaSelected(dest: Option[ExistingCotonoma]) extends Msg
    case object Post extends Msg
    case class Posted(result: Either[ErrorJson, (Coto, Ito)]) extends Msg
  }

  def dialogOrderAction(msg: Msg): Option[ModelessDialogOrder.Action] =
    msg match {
      case Msg.Open(_, _, _) => Some(ModelessDialogOrder.Action.Focus)
      case Msg.Focus         => Some(ModelessDialogOrder.Action.Focus)
      case Msg.Close         => Some(ModelessDialogOrder.Action.Close)
      case _                 => None
    }

  def open(
      sourceCotoId: Id[Coto],
      order: Option[Int],
      defaultCotonomaId: Option[Id[Cotonoma]]
  ): Cmd.One[AppMsg] =
    Browser.send(Msg.Open(sourceCotoId, order, defaultCotonomaId).into)

  def close: Cmd.One[AppMsg] =
    Browser.send(Msg.Close.into)

  def update(msg: Msg, model: Option[Model])(using
      context: Context
  ): (Option[Model], Geomap, Cmd[AppMsg]) = {
    val default = (model, context.geomap, Cmd.none)

    (msg, model) match {
      case (Msg.Open(sourceCotoId, order, defaultCotonomaId), _) =>
        (
          Some(Model(sourceCotoId, order, defaultCotonomaId)),
          context.geomap,
          Cmd.none
        )

      case (Msg.Focus, _) =>
        default

      case (Msg.Close, _) =>
        default.copy(_1 = None)

      case (_, None) =>
        default

      case (Msg.CotoFormMsg(submsg), Some(current)) =>
        val (form, geomap, subcmd) = CotoForm.update(submsg, current.cotoForm)
        (
          Some(current.copy(cotoForm = form)),
          geomap,
          subcmd.map(Msg.CotoFormMsg.apply).map(_.into)
        )

      case (Msg.TargetCotonomaSelected(target), Some(current)) =>
        default.copy(_1 = Some(current.copy(postTo = target)))

      case (Msg.Post, Some(current)) =>
        current.post.pipe { case (updated, cmd) =>
          default.copy(_1 = Some(updated), _3 = cmd)
        }

      case (Msg.Posted(Right(_)), Some(current)) =>
        (Some(current.copy(posting = false)), context.geomap, close)

      case (Msg.Posted(Left(e)), Some(current)) =>
        default.copy(
          _1 =
            Some(current.copy(posting = false, error = Some(e.default_message)))
        )
    }
  }

  def apply(model: Model)(using
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement = {
    val sourceCoto = context.repo.cotos.get(model.sourceCotoId)
    ModelessDialogFrame(
      dialogClasses = Seq(
        "modeless-subcoto" -> true,
        "with-media-content" -> model.cotoForm.mediaBlob.isDefined
      ),
      title = Fragment(
        span(className := "title-icon")(
          materialSymbol("subdirectory_arrow_right", "arrow"),
          materialSymbol(Coto.IconName)
        ),
        context.i18n.text.ModelessSubcoto_title
      ),
      onClose = () => dispatch(Msg.Close),
      onFocus = () => dispatch(Msg.Focus),
      zIndex = context.modeless.dialogZIndex(DialogId),
      initialWidth =
        "min(calc(var(--max-article-width) + (var(--block-spacing-horizontal) * 2)), calc(100vw - 32px))",
      error = model.error
    )(
      section(className := "source")(
        sourceCoto.map(articleCoto)
      ),
      section(className := "ito")(
        div(className := "ito-icon")(
          materialSymbol("arrow_downward")
        )
      ),
      CotoForm(
        form = model.cotoForm,
        vertical = true,
        onCtrlEnter = Some(() => dispatch(Msg.Post))
      )(using
        context,
        (submsg: CotoForm.Msg) => dispatch(Msg.CotoFormMsg(submsg))
      ),
      sectionPost(model)
    )
  }

  private def articleCoto(coto: Coto)(using
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    article(className := "coto embedded")(
      header()(
        PartsCoto.addressRemoteAuthor(coto)
      ),
      div(className := "body")(
        ScrollArea()(
          PartsCoto.divContentPreview(coto)
        )
      )
    )

  private def sectionPost(
      model: Model
  )(using context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    section(className := "post")(
      div(className := "post-to")(
        div(className := "label")(s"${context.i18n.text.PostTo}:"),
        SelectCotonoma(
          placeholder = Some(s"${context.i18n.text.PostTo}..."),
          menuPlacement = "top",
          options = model.targetCotonomas,
          currentCotonomaId = context.repo.currentCotonomaId,
          currentMarkLabel = Some("current"),
          value = model.postTo,
          onChange = Some((option, _) => {
            dispatch(
              Msg.TargetCotonomaSelected(
                option.collect { case option: ExistingCotonoma =>
                  option
                }
              )
            )
          })
        ),
        div(className := "space")()
      ),
      CotoForm.buttonPreview(model.cotoForm)(using
        context,
        (submsg: CotoForm.Msg) => dispatch(Msg.CotoFormMsg(submsg))
      ),
      button(
        className := "post",
        `type` := "button",
        disabled := !model.readyToPost,
        aria - "busy" := model.posting.toString(),
        onClick := (_ => dispatch(Msg.Post))
      )(
        context.i18n.text.Post,
        span(className := "shortcut-help")("(Ctrl + Enter)")
      )
    )

}
