package cotoami.subparts

import scala.util.chaining._
import scala.scalajs.js
import org.scalajs.dom

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cats.effect.IO
import com.softwaremill.quicklens._

import fui._
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.utils.Validation
import cotoami.models.{Coto, Cotonoma, Id, Node, WaitingPost, WaitingPosts}
import cotoami.backend.{CotoBackend, CotonomaBackend, ErrorJson}
import cotoami.components.{materialSymbol, optionalClasses, SplitPane}
import cotoami.subparts.EditorCoto._
import cotoami.subparts.SectionGeomap.{Model => Geomap}

object SectionFlowInput {
  final val StorageKey = "FlowInput"

  def init: (Model, Cmd.One[AppMsg]) =
    Model().pipe(model => (model, model.restore))

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      form: Form = CotoForm.Model(),
      folded: Boolean = true,
      posting: Boolean = false
  ) {
    def onCotonomaChange: Model =
      copy(posting = false).pipe { model =>
        form match {
          case form: CotoForm.Model if form.hasContents => model
          case _ =>
            model.copy(
              form = CotoForm.Model(),
              folded = true
            )
        }
      }

    def hasContents: Boolean = form.hasContents

    def readyToPost: Boolean = !posting && form.hasValidContents

    def clear: Model =
      copy(
        form = form match {
          case form: CotoForm.Model     => CotoForm.Model()
          case form: CotonomaForm.Model => CotonomaForm.Model()
        },
        folded = true
      )

    def save: Cmd.One[AppMsg] =
      form match {
        case form: CotoForm.Model =>
          Cmd(IO {
            dom.window.localStorage.setItem(StorageKey, form.contentInput)
            None
          })
        case _ => Cmd.none
      }

    def restore: Cmd.One[AppMsg] =
      form match {
        case form: CotoForm.Model =>
          restoreTextContent.map(Msg.ContentRestored(_).into)
        case _ => Cmd.none
      }

    private def restoreTextContent: Cmd.One[Option[String]] = Cmd(IO {
      Some(Option(dom.window.localStorage.getItem(StorageKey)))
    })
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = AppMsg.FlowInputMsg(this)
  }

  object Msg {
    case object SetCotoForm extends Msg
    case object SetCotonomaForm extends Msg
    case class CotoFormMsg(submsg: CotoForm.Msg) extends Msg
    case class CotonomaFormMsg(submsg: CotonomaForm.Msg) extends Msg
    case class ContentRestored(content: Option[String]) extends Msg
    case class SetFolded(folded: Boolean) extends Msg
    case object Post extends Msg
    case class PostCoto(
        form: CotoForm.Model,
        mediaContent: Option[(String, String)]
    ) extends Msg
    case class CotoPosted(postId: String, result: Either[ErrorJson, Coto])
        extends Msg
    case class CotonomaPosted(
        postId: String,
        result: Either[ErrorJson, (Cotonoma, Coto)]
    ) extends Msg
  }

  def update(
      msg: Msg,
      model: Model,
      waitingPosts: WaitingPosts
  )(implicit
      context: Context
  ): (Model, Geomap, WaitingPosts, Cmd[AppMsg]) = {
    val default = (model, context.geomap, waitingPosts, Cmd.none)
    (msg, model.form, context.repo.currentCotonoma) match {
      case (Msg.SetCotoForm, _, _) =>
        model.copy(form = CotoForm.Model()) match {
          case model =>
            default.copy(
              _1 = model,
              _4 = Cmd.Batch(
                model.restore,
                Browser.send(
                  SectionTimeline.Msg.SetOnlyCotonomas(false).into
                )
              )
            )
        }

      case (Msg.SetCotonomaForm, cotoForm: CotoForm.Model, _) =>
        default.copy(
          _1 = model.copy(form = CotonomaForm.Model()),
          _2 =
            // If the focused location has been set by EXIF info,
            // Swithcing to CotonomaForm will abandon the focused location
            // as well as the media content.
            if (context.geomap.focusedLocation == cotoForm.mediaLocation)
              context.geomap.copy(focusedLocation = None)
            else
              context.geomap,
          _4 = Browser.send(
            SectionTimeline.Msg.SetOnlyCotonomas(true).into
          )
        )

      case (Msg.CotoFormMsg(submsg), cotoForm: CotoForm.Model, _) => {
        val (form, geomap, subcmd) = CotoForm.update(submsg, cotoForm)
        model
          .modify(_.form).setTo(form)
          .modify(_.folded).using(folded =>
            if (submsg.isInstanceOf[CotoForm.Msg.ContentInput])
              false
            else
              folded
          )
          .pipe { model =>
            default.copy(
              _1 = model,
              _2 = geomap,
              _4 = submsg match {
                case CotoForm.Msg.ContentInput(_) => model.save
                case _ => subcmd.map(Msg.CotoFormMsg).map(_.into)
              }
            )
          }
      }

      case (
            Msg.CotonomaFormMsg(submsg),
            cotonomaForm: CotonomaForm.Model,
            _
          ) => {
        val (form, subcmd) = CotonomaForm.update(submsg, cotonomaForm)
        model
          .modify(_.form).setTo(form)
          .modify(_.folded).using(folded =>
            if (submsg.isInstanceOf[CotonomaForm.Msg.CotonomaNameInput])
              false
            else
              folded
          )
          .pipe { model =>
            default.copy(
              _1 = model,
              _4 = subcmd.map(Msg.CotonomaFormMsg).map(_.into)
            )
          }
      }

      case (Msg.ContentRestored(Some(content)), form: CotoForm.Model, _) =>
        default.copy(
          _1 =
            if (form.contentInput.isBlank)
              model.copy(
                form = form.copy(contentInput = content),
                folded = content.isBlank
              )
            else
              model.copy(folded = false)
        )

      case (Msg.SetFolded(folded), _, _) =>
        default.copy(_1 = model.copy(folded = folded))

      case (Msg.Post, form: CotoForm.Model, Some(cotonoma)) => {
        val postId = WaitingPost.newPostId()
        model.copy(posting = true).clear.pipe { model =>
          default.copy(
            _1 = model,
            _2 = context.geomap.copy(focusedLocation = None),
            _3 = waitingPosts.addCoto(
              postId,
              form.content,
              form.summary,
              form.mediaBase64,
              cotonoma
            ),
            _4 = Cmd.Batch(
              postCoto(postId, form, context.geomap, cotonoma.id),
              model.save
            )
          )
        }
      }

      case (Msg.Post, form: CotonomaForm.Model, Some(cotonoma)) => {
        val postId = WaitingPost.newPostId()
        model.copy(posting = true).clear match {
          case model =>
            default.copy(
              _1 = model,
              _2 = context.geomap.copy(focusedLocation = None),
              _3 = waitingPosts.addCotonoma(postId, form.name, cotonoma),
              _4 = postCotonoma(postId, form, context.geomap, cotonoma.id)
            )
        }
      }

      case (Msg.CotoPosted(postId, Right(coto)), _, _) =>
        default.copy(
          _1 = model.copy(posting = false),
          _3 = waitingPosts.remove(postId)
        )

      case (Msg.CotoPosted(postId, Left(e)), _, _) => {
        default.copy(
          _1 = model.copy(posting = false),
          _3 = waitingPosts.setError(
            postId,
            s"Couldn't post this coto: ${js.JSON.stringify(e)}"
          ),
          _4 = cotoami.error("Couldn't post a coto.", e)
        )
      }

      case (Msg.CotonomaPosted(postId, Right((cotonoma, _))), _, _) =>
        default.copy(
          _1 = model.copy(posting = false),
          _3 = waitingPosts.remove(postId)
        )

      case (Msg.CotonomaPosted(postId, Left(e)), _, _) => {
        val error = js.JSON.stringify(e)
        default.copy(
          _1 = model.copy(posting = false),
          _3 = waitingPosts.setError(
            postId,
            s"Couldn't post this cotonoma: ${error}"
          ),
          _4 = cotoami.error("Couldn't post a cotonoma.", Some(error))
        )
      }

      case _ => default
    }
  }

  private def postCoto(
      postId: String,
      form: CotoForm.Model,
      geomap: Geomap,
      postTo: Id[Cotonoma]
  ): Cmd.One[AppMsg] =
    CotoBackend.post(
      form.content,
      form.summary,
      form.mediaBase64,
      geomap.focusedLocation,
      form.dateTimeRange,
      postTo
    )
      .map(Msg.CotoPosted(postId, _).into)

  private def postCotonoma(
      postId: String,
      form: CotonomaForm.Model,
      geomap: Geomap,
      postTo: Id[Cotonoma]
  ): Cmd.One[AppMsg] =
    CotonomaBackend.post(form.name, geomap.focusedLocation, None, postTo)
      .map(Msg.CotonomaPosted(postId, _).into)

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(
      model: Model,
      operatingNode: Node,
      currentCotonoma: Cotonoma,
      geomap: Geomap,
      editorHeight: Int,
      onEditorHeightChanged: Int => Unit
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    section(
      className := optionalClasses(
        Seq(
          ("flow-input", true),
          ("folded", model.folded)
        )
      )
    )(
      header(model, currentCotonoma),
      model.form match {
        case form: CotoForm.Model => {
          val divForm = formCoto(
            form,
            model,
            operatingNode,
            currentCotonoma,
            geomap,
            editorHeight,
            onEditorHeightChanged
          )

          CotoForm.sectionMediaPreview(form)(submsg =>
            dispatch(Msg.CotoFormMsg(submsg))
          ) match {
            case Some(mediaPreview) =>
              SplitPane(
                vertical = false,
                initialPrimarySize = 300,
                className = Some("coto-form-with-media"),
                primary = SplitPane.Primary.Props()(mediaPreview),
                secondary = SplitPane.Secondary.Props()(divForm)
              )

            case None => divForm
          }
        }

        case form: CotonomaForm.Model =>
          formCotonoma(form, model, operatingNode, currentCotonoma, geomap)
      }
    )

  private def header(model: Model, currentCotonoma: Cotonoma)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    slinky.web.html.header()(
      section(
        className := "posting-to",
        onDoubleClick := (_ =>
          dispatch(AppMsg.FocusCoto(currentCotonoma.cotoId))
        )
      )(
        context.repo.nodes.get(currentCotonoma.nodeId).map(imgNode(_)),
        currentCotonoma.name
      ),
      section(className := "coto-type-switch")(
        button(
          className := "new-coto default",
          disabled := model.form.isInstanceOf[CotoForm.Model],
          onClick := (_ => dispatch(Msg.SetCotoForm))
        )(
          span(className := "label")(
            materialSymbol(Coto.IconName),
            context.i18n.text.Coto
          )
        ),
        button(
          className := "new-cotonoma default",
          disabled := model.form.isInstanceOf[CotonomaForm.Model],
          onClick := (_ => dispatch(Msg.SetCotonomaForm))
        )(
          span(className := "label")(
            materialSymbol(Cotonoma.IconName),
            context.i18n.text.Cotonoma
          )
        )
      )
    )

  private def formCoto(
      form: CotoForm.Model,
      model: Model,
      operatingNode: Node,
      currentCotonoma: Cotonoma,
      geomap: Geomap,
      editorHeight: Int,
      onEditorHeightChanged: Int => Unit
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    SplitPane(
      vertical = false,
      initialPrimarySize = editorHeight,
      resizable = !model.folded,
      onPrimarySizeChanged = Some(onEditorHeightChanged),
      primary = SplitPane.Primary.Props(className = Some("coto-form-pane"))(
        CotoForm.sectionEditorOrPreview(
          model = form,
          onCtrlEnter = Some(() => dispatch(Msg.Post)),
          onFocus = Some(() => dispatch(Msg.SetFolded(false)))
        )(submsg => dispatch(Msg.CotoFormMsg(submsg)))
      ),
      secondary = SplitPane.Secondary.Props()(
        EditorCoto.ulAttributes(
          form.dateTimeRange,
          form.mediaDateTime,
          geomap.focusedLocation,
          form.mediaLocation
        )(context, submsg => dispatch(Msg.CotoFormMsg(submsg))),
        div(className := "post")(
          CotoForm.sectionValidationError(form),
          section(className := "post")(
            div(className := "fold-button")(
              button(
                className := "default fold",
                onClick := (_ => dispatch(Msg.SetFolded(true)))
              )(
                materialSymbol("arrow_drop_up")
              )
            ),
            addressPoster(operatingNode),
            div(className := "buttons")(
              CotoForm.buttonPreview(model = form)(submsg =>
                dispatch(Msg.CotoFormMsg(submsg))
              ),
              buttonPost(model, currentCotonoma)
            )
          )
        )
      )
    )

  private def formCotonoma(
      form: CotonomaForm.Model,
      model: Model,
      operatingNode: Node,
      currentCotonoma: Cotonoma,
      geomap: Geomap
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    Fragment(
      div(className := "cotonoma-form")(
        CotonomaForm.inputName(
          model = form,
          onFocus = Some(() => dispatch(Msg.SetFolded(false))),
          onBlur = Some(() => dispatch(Msg.SetFolded(!model.hasContents))),
          onCtrlEnter = () => dispatch(Msg.Post)
        )(submsg => dispatch(Msg.CotonomaFormMsg(submsg)))
      ),
      ulAttributes(None, None, geomap.focusedLocation, None)(
        context,
        submsg => dispatch(Msg.CotoFormMsg(submsg))
      ),
      div(className := "post")(
        Validation.sectionValidationError(form.validation),
        section(className := "post")(
          addressPoster(operatingNode),
          div(className := "buttons")(
            buttonPost(model, currentCotonoma)
          )
        )
      )
    )

  private def addressPoster(operatingNode: Node): ReactElement =
    address(className := "poster")(
      spanNode(operatingNode)
    )

  private def buttonPost(
      model: Model,
      currentCotonoma: Cotonoma
  )(implicit dispatch: Into[AppMsg] => Unit): ReactElement =
    button(
      className := "post",
      disabled := !model.readyToPost,
      aria - "busy" := model.posting.toString(),
      onClick := (_ => dispatch(Msg.Post))
    )(
      "Post",
      span(className := "shortcut-help")("(Ctrl + Enter)")
    )
}
