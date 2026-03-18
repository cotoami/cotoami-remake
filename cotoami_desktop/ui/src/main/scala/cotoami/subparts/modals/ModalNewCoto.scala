package cotoami.subparts.modals

import scala.util.chaining._

import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.optionalClasses
import marubinotto.fui.Cmd

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Coto, Cotonoma}
import cotoami.backend.{CotoBackend, ErrorJson}
import cotoami.subparts.Modal
import cotoami.subparts.EditorCoto._
import cotoami.subparts.SectionGeomap.{Model => Geomap}

object ModalNewCoto {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      cotoForm: CotoForm.Model = CotoForm.Model(),
      posting: Boolean = false,
      error: Option[String] = None
  ) {
    def readyToPost: Boolean =
      !posting && cotoForm.hasValidContents

    def post(postTo: Cotonoma): (Model, Cmd.One[AppMsg]) =
      (
        copy(posting = true),
        CotoBackend.post(cotoForm.toBackendInput, postTo.id)
          .map(Msg.Posted(_).into)
      )
  }

  object Model {
    def apply(cotoForm: CotoForm.Model): (Model, Cmd[AppMsg]) =
      (
        Model(cotoForm = cotoForm),
        cotoForm.scanMediaMetadata.map(Msg.CotoFormMsg.apply).map(_.into)
      )
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    override def into: AppMsg =
      Modal.Msg.NewCotoMsg(this).pipe(AppMsg.ModalMsg.apply)
  }

  object Msg {
    case class CotoFormMsg(submsg: CotoForm.Msg) extends Msg
    case object Post extends Msg
    case class Posted(result: Either[ErrorJson, Coto]) extends Msg
  }

  def update(msg: Msg, model: Model)(using
      context: Context
  ): (Model, Geomap, Cmd[AppMsg]) = {
    val default = (model, context.geomap, Cmd.none)
    msg match {
      case Msg.CotoFormMsg(submsg) =>
        val (form, geomap, subcmd) = CotoForm.update(submsg, model.cotoForm)
        default.copy(
          _1 = model.copy(cotoForm = form),
          _2 = geomap,
          _3 = subcmd.map(Msg.CotoFormMsg.apply).map(_.into)
        )

      case Msg.Post =>
        context.repo.currentCotonoma match {
          case Some(cotonoma) =>
            model.post(cotonoma).pipe { case (model, cmd) =>
              default.copy(_1 = model, _3 = cmd)
            }
          case None => default
        }

      case Msg.Posted(Right(_)) =>
        default.copy(
          _1 = model.copy(posting = false),
          _3 = Modal.close(classOf[Modal.NewCoto])
        )

      case Msg.Posted(Left(e)) =>
        default.copy(
          _1 = model.copy(posting = false, error = Some(e.default_message))
        )
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(model: Model)(using
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    Modal.view(
      dialogClasses = optionalClasses(
        Seq(
          ("new-coto", true),
          ("with-media-content", model.cotoForm.mediaBlob.isDefined)
        )
      ),
      closeButton = Some((classOf[Modal.NewCoto], dispatch)),
      error = model.error
    )(
      Modal.spanTitleIcon(Coto.IconName),
      context.i18n.text.ModalNewCoto_title
    )(
      context.repo.currentCotonoma.map(sectionPostTo),
      CotoForm(
        form = model.cotoForm,
        onCtrlEnter = Some(() => dispatch(Msg.Post))
      )(using
        context,
        (submsg: CotoForm.Msg) => dispatch(Msg.CotoFormMsg(submsg))
      ),
      div(className := "buttons")(
        CotoForm.buttonPreview(model.cotoForm)(using
          context,
          (submsg: CotoForm.Msg) => dispatch(Msg.CotoFormMsg(submsg))
        ),
        button(
          className := "post",
          disabled := !model.readyToPost,
          aria - "busy" := model.posting.toString(),
          onClick := (_ => dispatch(Msg.Post))
        )(
          context.i18n.text.Post,
          span(className := "shortcut-help")("(Ctrl + Enter)")
        )
      )
    )

  private def sectionPostTo(cotonoma: Cotonoma)(using
      context: Context
  ): ReactElement =
    section(className := "post-to")(
      span(className := "label")(s"${context.i18n.text.PostTo}:"),
      span(className := "name")(cotonoma.name)
    )
}
