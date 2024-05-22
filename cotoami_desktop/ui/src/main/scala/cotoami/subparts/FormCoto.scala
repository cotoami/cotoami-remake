package cotoami.subparts

import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.HTMLElement

import java.time._

import slinky.core.facade.ReactElement
import slinky.web.html._

import cats.effect.IO
import com.softwaremill.quicklens._

import fui.FunctionalUI._
import cotoami.utils.Log
import cotoami.backend._
import cotoami.components.{
  materialSymbol,
  optionalClasses,
  SplitPane,
  ToolButton
}

object FormCoto {
  val StorageKeyPrefix = "FormCoto."

  case class Model(
      id: String,
      form: Form = CotoForm(),
      focused: Boolean = false,
      editorBeingResized: Boolean = false,
      autoSave: Boolean = false
  ) {
    def editorId: String = s"${this.id}-editor"

    def folded: Boolean =
      !this.focused && !this.editorBeingResized && this.isBlank

    def isBlank: Boolean =
      this.form match {
        case CotoForm(content)  => content.isBlank
        case CotonomaForm(name) => name.isBlank
      }

    def clear: Model =
      this.copy(form = this.form match {
        case CotoForm(_)     => CotoForm()
        case CotonomaForm(_) => CotonomaForm()
      })

    def storageKey: String = StorageKeyPrefix + this.id

    def save: Cmd[Msg] =
      (autoSave, form) match {
        case (true, CotoForm(content)) =>
          Cmd(IO {
            dom.window.localStorage.setItem(this.storageKey, content)
            None
          })

        case _ => Cmd.none
      }

    def restore: Cmd[Msg] =
      (autoSave, form) match {
        case (true, form: CotoForm) =>
          restoreCotoContent.map(CotoContentRestored)

        case _ => Cmd.none
      }

    private def restoreCotoContent: Cmd[Option[String]] = Cmd(IO {
      Some(Option(dom.window.localStorage.getItem(this.storageKey)))
    })
  }

  case class WaitingPost(
      postId: String,
      content: Option[String],
      summary: Option[String],
      isCotonoma: Boolean,
      postedIn: Cotonoma,
      error: Option[String] = None
  ) extends CotoContent

  object WaitingPost {
    def newPostId(): String =
      Instant.now().toEpochMilli().toString
  }

  case class WaitingPosts(posts: Seq[WaitingPost] = Seq.empty) {
    def isEmpty: Boolean = this.posts.isEmpty

    def add(post: WaitingPost): WaitingPosts =
      this.modify(_.posts).using(post +: _)

    def addCoto(
        postId: String,
        form: CotoForm,
        postedIn: Cotonoma
    ): WaitingPosts =
      this.add(
        WaitingPost(postId, Some(form.content), form.summary, false, postedIn)
      )

    def setError(postId: String, error: String): WaitingPosts =
      this.modify(_.posts.eachWhere(_.postId == postId).error).setTo(
        Some(error)
      )

    def remove(postId: String): WaitingPosts =
      this.modify(_.posts).using(_.filterNot(_.postId == postId))
  }

  def init(id: String, autoSave: Boolean): (Model, Cmd[Msg]) =
    Model(id, autoSave = autoSave) match {
      case model => (model, model.restore)
    }

  sealed trait Form
  case class CotoForm(content: String = "") extends Form {
    val summary: Option[String] = None
  }
  case class CotonomaForm(name: String = "") extends Form

  sealed trait Msg
  case object SetCotoForm extends Msg
  case object SetCotonomaForm extends Msg
  case class CotoContentRestored(content: Option[String]) extends Msg
  case class CotoContentInput(content: String) extends Msg
  case class CotonomaNameInput(name: String) extends Msg
  case class SetFocus(focus: Boolean) extends Msg
  case object EditorResizeStart extends Msg
  case object EditorResizeEnd extends Msg
  case object Post extends Msg
  case class CotoPosted(postId: String, result: Either[ErrorJson, CotoJson])
      extends Msg
  case class CotonomaPosted(
      postId: String,
      result: Either[ErrorJson, js.Tuple2[CotonomaJson, CotoJson]]
  ) extends Msg

  def update(
      msg: Msg,
      currentCotonoma: Cotonoma,
      model: Model,
      waitingPosts: WaitingPosts,
      log: Log
  ): (Model, WaitingPosts, Log, Seq[Cmd[Msg]]) =
    (msg, model.form) match {
      case (SetCotoForm, _) =>
        model.copy(form = CotoForm()) match {
          case model => (model, waitingPosts, log, Seq(model.restore))
        }

      case (SetCotonomaForm, _) =>
        (model.copy(form = CotonomaForm()), waitingPosts, log, Seq.empty)

      case (CotoContentRestored(Some(content)), form: CotoForm) =>
        (
          if (form.content.isBlank())
            model.copy(form = form.copy(content = content))
          else
            model,
          waitingPosts,
          log,
          Seq()
        )

      case (CotoContentInput(content), form: CotoForm) =>
        model.copy(form = form.copy(content = content)) match {
          case model => (model, waitingPosts, log, Seq(model.save))
        }

      case (CotonomaNameInput(name), form: CotonomaForm) =>
        (
          model.copy(form = form.copy(name = name)),
          waitingPosts,
          log,
          Seq.empty
        )

      case (SetFocus(focus), _) =>
        (model.copy(focused = focus), waitingPosts, log, Seq.empty)

      case (EditorResizeStart, _) =>
        (model.copy(editorBeingResized = true), waitingPosts, log, Seq.empty)

      case (EditorResizeEnd, _) =>
        (
          model.copy(editorBeingResized = false),
          waitingPosts,
          log,
          // Return the focus to the editor in order for it
          // not to be folded when it's empty.
          Seq(Cmd(IO {
            dom.document.getElementById(model.editorId) match {
              case element: HTMLElement => element.focus()
              case _                    => ()
            }
            None
          }))
        )

      case (Post, form: CotoForm) => {
        val postId = WaitingPost.newPostId()
        model.clear match {
          case model =>
            (
              model,
              waitingPosts.addCoto(postId, form, currentCotonoma),
              log,
              Seq(
                postCoto(postId, form, currentCotonoma.id),
                model.save
              )
            )
        }
      }

      case (CotoPosted(postId, Right(cotoJson)), _) =>
        (
          model,
          waitingPosts.remove(postId),
          log.info("Coto posted.", Some(cotoJson.uuid)),
          Seq.empty
        )

      case (CotoPosted(postId, Left(e)), _) => {
        val error = js.JSON.stringify(e)
        (
          model,
          waitingPosts.setError(
            postId,
            s"Couldn't post this coto: ${error}"
          ),
          log.error("Couldn't post a coto.", Some(error)),
          Seq.empty
        )
      }

      case (CotonomaPosted(postId, Right(cotonoma)), _) =>
        (
          model,
          waitingPosts.remove(postId),
          log.info(
            "Cotonoma posted.",
            Some(js.JSON.stringify(cotonoma._1))
          ),
          Seq.empty
        )

      case (CotonomaPosted(postId, Left(e)), _) => {
        val error = js.JSON.stringify(e)
        (
          model,
          waitingPosts.setError(
            postId,
            s"Couldn't post this cotonoma: ${error}"
          ),
          log.error("Couldn't post a cotonoma.", Some(error)),
          Seq.empty
        )
      }

      case (_, _) => (model, waitingPosts, log, Seq.empty)
    }

  private def postCoto(
      postId: String,
      form: CotoForm,
      post_to: Id[Cotonoma]
  ): Cmd[Msg] =
    Commands
      .send(Commands.PostCoto(form.content, form.summary, post_to))
      .map(CotoPosted(postId, _))

  private def postCotonoma(
      postId: String,
      name: String,
      post_to: Id[Cotonoma]
  ): Cmd[Msg] =
    Commands
      .send(Commands.PostCotonoma(name, post_to))
      .map(CotonomaPosted(postId, _))

  def apply(
      model: Model,
      operatingNode: Node,
      currentCotonoma: Cotonoma,
      editorHeight: Int,
      onEditorHeightChanged: Int => Unit,
      dispatch: Msg => Unit
  ): ReactElement =
    section(
      className := optionalClasses(
        Seq(
          ("form-coto", true),
          ("folded", model.folded)
        )
      )
    )(
      header(className := "tools")(
        section(className := "coto-type-switch")(
          button(
            className := "new-coto default",
            disabled := model.form.isInstanceOf[CotoForm],
            onClick := (_ => dispatch(SetCotoForm))
          )(
            span(className := "label")(
              materialSymbol("text_snippet"),
              "Coto"
            )
          ),
          button(
            className := "new-cotonoma default",
            disabled := model.form.isInstanceOf[CotonomaForm],
            onClick := (_ => dispatch(SetCotonomaForm))
          )(
            span(className := "label")(
              materialSymbol("topic"),
              "Cotonoma"
            )
          )
        ),
        ToolButton(
          classes = "image",
          tip = "Image",
          disabled = !model.form.isInstanceOf[CotoForm],
          symbol = "image"
        ),
        ToolButton(
          classes = "location",
          tip = "Location",
          symbol = "location_on"
        )
      ),
      model.form match {
        case CotoForm(content) =>
          SplitPane(
            vertical = false,
            initialPrimarySize = editorHeight,
            resizable = !model.folded,
            className = None,
            onResizeStart = Some(() => dispatch(EditorResizeStart)),
            onResizeEnd = Some(() => dispatch(EditorResizeEnd)),
            onPrimarySizeChanged = Some(onEditorHeightChanged)
          )(
            SplitPane.Primary(className = Some("coto-editor"), onClick = None)(
              textarea(
                id := model.editorId,
                placeholder := "Write your Coto in Markdown here",
                value := content,
                onFocus := (_ => dispatch(SetFocus(true))),
                onBlur := (_ => dispatch(SetFocus(false))),
                onChange := (e => dispatch(CotoContentInput(e.target.value)))
              )
            ),
            SplitPane.Secondary(className = None, onClick = None)(
              inputFooter(model, operatingNode, currentCotonoma, dispatch)
            )
          )

        case CotonomaForm(cotonomaName) =>
          div()(
            input(
              `type` := "text",
              name := "cotonomaName",
              placeholder := "New cotonoma name",
              value := cotonomaName,
              onFocus := (_ => dispatch(SetFocus(true))),
              onBlur := (_ => dispatch(SetFocus(false))),
              onChange := ((e) => dispatch(CotonomaNameInput(e.target.value)))
            ),
            inputFooter(model, operatingNode, currentCotonoma, dispatch)
          )
      }
    )

  private def inputFooter(
      model: Model,
      operatingNode: Node,
      currentCotonoma: Cotonoma,
      dispatch: Msg => Unit
  ): ReactElement =
    footer(className := "post")(
      address(className := "poster")(
        nodeImg(operatingNode),
        operatingNode.name
      ),
      button(
        className := "post",
        disabled := model.isBlank,
        onClick := (_ => dispatch(Post))
      )(
        "Post to ",
        span(className := "target-cotonoma")(
          currentCotonoma.abbreviateName(15)
        ),
        span(className := "shortcut-help")("(Ctrl + Enter)")
      )
    )
}
