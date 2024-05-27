package cotoami.subparts

import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.HTMLElement

import java.time._

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cats.effect.IO
import com.softwaremill.quicklens._

import fui.FunctionalUI._
import cotoami.utils.{Log, Validation}
import cotoami.backend._
import cotoami.components.{
  materialSymbol,
  optionalClasses,
  ScrollArea,
  SplitPane,
  ToolButton
}

object FormCoto {
  val StorageKeyPrefix = "FormCoto."

  def init(id: String, autoSave: Boolean): (Model, Cmd[Msg]) =
    Model(id, autoSave = autoSave) match {
      case model => (model, model.restore)
    }

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      id: String,
      form: Form = CotoForm(),
      focused: Boolean = false,
      editorBeingResized: Boolean = false,
      autoSave: Boolean = false,
      inPreview: Boolean = false
  ) {
    def editorId: String = s"${this.id}-editor"

    def folded: Boolean =
      !this.focused && !this.editorBeingResized && this.isBlank

    def isBlank: Boolean =
      this.form match {
        case CotoForm(content)     => content.isBlank
        case CotonomaForm(name, _) => name.isBlank
      }

    def readyToPost: Boolean = !this.isBlank && (this.form match {
      case form: CotoForm              => form.validate.validated
      case CotonomaForm(_, validation) => validation.validated
    })

    def clear: Model =
      this.copy(
        form = this.form match {
          case form: CotoForm     => CotoForm()
          case form: CotonomaForm => CotonomaForm()
        },
        inPreview = false
      )

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
          restoreCoto.map(CotoRestored)

        case _ => Cmd.none
      }

    private def restoreCoto: Cmd[Option[String]] = Cmd(IO {
      Some(Option(dom.window.localStorage.getItem(this.storageKey)))
    })
  }

  /////////////////////////////////////////////////////////////////////////////
  // WaitingPost
  /////////////////////////////////////////////////////////////////////////////

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
        WaitingPost(postId, form.content, form.summary, false, postedIn)
      )

    def addCotonoma(
        postId: String,
        form: CotonomaForm,
        postedIn: Cotonoma
    ): WaitingPosts =
      this.add(
        WaitingPost(postId, None, Some(form.name), true, postedIn)
      )

    def setError(postId: String, error: String): WaitingPosts =
      this.modify(_.posts.eachWhere(_.postId == postId).error).setTo(
        Some(error)
      )

    def remove(postId: String): WaitingPosts =
      this.modify(_.posts).using(_.filterNot(_.postId == postId))
  }

  /////////////////////////////////////////////////////////////////////////////
  // Form
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Form

  case class CotoForm(coto: String = "") extends Form with CotoContent {
    override def summary: Option[String] =
      if (this.hasSummary)
        Some(this.firstLine.stripPrefix(CotoForm.SummaryPrefix).trim)
      else
        None

    override def content: Option[String] =
      if (this.hasSummary)
        Some(this.coto.stripPrefix(this.firstLine).trim)
      else
        Some(this.coto)

    override def isCotonoma: Boolean = false

    def validate: Validation.Result =
      Validation.Result(errors = this.summary.map(Coto.validateSummary(_)))

    private def hasSummary: Boolean =
      this.coto.startsWith(CotoForm.SummaryPrefix)

    private def firstLine = this.coto.linesIterator.next()
  }
  object CotoForm {
    // A top-level heading as the first line will be used as a summary.
    // cf. https://spec.commonmark.org/0.31.2/#atx-headings
    val SummaryPrefix = "# "
  }

  case class CotonomaForm(
      name: String = "",
      validation: Validation.Result = Validation.Result()
  ) extends Form {
    // Do validations that can be done at frontend.
    def validate: Form =
      this.modify(_.validation).setTo(
        if (this.name.isEmpty())
          Validation.Result()
        else
          Cotonoma.validateName(this.name) match {
            case errors if errors.isEmpty => Validation.Result()
            case errors                   => Validation.Result(errors)
          }
      )
  }

  /////////////////////////////////////////////////////////////////////////////
  // update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg
  case object SetCotoForm extends Msg
  case object SetCotonomaForm extends Msg
  case class CotoRestored(coto: Option[String]) extends Msg
  case class CotoInput(coto: String) extends Msg
  case class CotonomaNameInput(name: String) extends Msg
  case class SetFocus(focus: Boolean) extends Msg
  case object EditorResizeStart extends Msg
  case object EditorResizeEnd extends Msg
  case object TogglePreview extends Msg
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

      case (CotoRestored(Some(coto)), form: CotoForm) =>
        (
          if (form.coto.isBlank())
            model.copy(form = form.copy(coto = coto))
          else
            model,
          waitingPosts,
          log,
          Seq()
        )

      case (CotoInput(coto), form: CotoForm) =>
        model.copy(form = form.copy(coto = coto)) match {
          case model => (model, waitingPosts, log, Seq(model.save))
        }

      case (CotonomaNameInput(name), form: CotonomaForm) => {
        form.copy(name = name).validate match {
          case form =>
            (
              model.copy(form = form),
              waitingPosts,
              log,
              Seq.empty
            )
        }
      }

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

      case (TogglePreview, _) =>
        (model.modify(_.inPreview).using(!_), waitingPosts, log, Seq.empty)

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

      case (Post, form: CotonomaForm) => {
        val postId = WaitingPost.newPostId()
        model.clear match {
          case model =>
            (
              model,
              waitingPosts.addCotonoma(postId, form, currentCotonoma),
              log,
              Seq(postCotonoma(postId, form, currentCotonoma.id))
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
      .send(
        Commands.PostCoto(form.content.getOrElse(""), form.summary, post_to)
      )
      .map(CotoPosted(postId, _))

  private def postCotonoma(
      postId: String,
      form: CotonomaForm,
      post_to: Id[Cotonoma]
  ): Cmd[Msg] =
    Commands
      .send(Commands.PostCotonoma(form.name, post_to))
      .map(CotonomaPosted(postId, _))

  /////////////////////////////////////////////////////////////////////////////
  // view
  /////////////////////////////////////////////////////////////////////////////

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
      headerTools(model, dispatch),
      model.form match {
        case form: CotoForm =>
          SplitPane(
            vertical = false,
            initialPrimarySize = editorHeight,
            resizable = !model.folded,
            className = None,
            onResizeStart = Some(() => dispatch(EditorResizeStart)),
            onResizeEnd = Some(() => dispatch(EditorResizeEnd)),
            onPrimarySizeChanged = Some(onEditorHeightChanged)
          )(
            if (model.inPreview)
              SplitPane.Primary(
                className = Some("coto-preview"),
                onClick = None
              )(
                ScrollArea(
                  scrollableElementId = None,
                  autoHide = true,
                  bottomThreshold = None,
                  onScrollToBottom = () => ()
                )(
                  section(className := "coto-preview")(
                    form.summary.map(section(className := "summary")(_)),
                    div(className := "content")(
                      ViewCoto.sectionCotoContentDetails(form)
                    )
                  )
                )
              )
            else
              SplitPane.Primary(
                className = Some("coto-editor"),
                onClick = None
              )(
                textarea(
                  id := model.editorId,
                  placeholder := "Write your Coto in Markdown here",
                  value := form.coto,
                  onFocus := (_ => dispatch(SetFocus(true))),
                  onBlur := (_ => dispatch(SetFocus(false))),
                  onChange := (e => dispatch(CotoInput(e.target.value))),
                  onKeyDown := (e =>
                    if (model.readyToPost && detectCtrlEnter(e)) {
                      dispatch(Post)
                    }
                  )
                )
              ),
            SplitPane.Secondary(className = None, onClick = None)(
              divPost(
                model,
                form.validate,
                operatingNode,
                currentCotonoma,
                dispatch
              )
            )
          )

        case CotonomaForm(cotonomaName, validation) =>
          Fragment(
            input(
              `type` := "text",
              name := "cotonomaName",
              placeholder := "New cotonoma name",
              value := cotonomaName,
              Validation.ariaInvalid(validation),
              onFocus := (_ => dispatch(SetFocus(true))),
              onBlur := (_ => dispatch(SetFocus(false))),
              onChange := (e => dispatch(CotonomaNameInput(e.target.value))),
              onKeyDown := (e =>
                if (model.readyToPost && detectCtrlEnter(e)) {
                  dispatch(Post)
                }
              )
            ),
            divPost(model, validation, operatingNode, currentCotonoma, dispatch)
          )
      }
    )

  private def headerTools(model: Model, dispatch: Msg => Unit): ReactElement =
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
    )

  private def divPost(
      model: Model,
      validation: Validation.Result,
      operatingNode: Node,
      currentCotonoma: Cotonoma,
      dispatch: Msg => Unit
  ): ReactElement =
    div(className := "post")(
      Validation.sectionValidationError(validation),
      section(className := "post")(
        address(className := "poster")(
          nodeImg(operatingNode),
          operatingNode.name
        ),
        div(className := "buttons")(
          Option.when(model.form.isInstanceOf[CotoForm]) {
            button(
              className := "preview contrast outline",
              disabled := !model.readyToPost,
              onClick := (_ => dispatch(TogglePreview))
            )(
              if (model.inPreview)
                "Edit"
              else
                "Preview"
            )
          },
          button(
            className := "post",
            disabled := !model.readyToPost,
            onClick := (_ => dispatch(Post))
          )(
            "Post to ",
            span(className := "target-cotonoma")(
              currentCotonoma.abbreviateName(15)
            ),
            span(className := "shortcut-help")("(Ctrl + Enter)")
          )
        )
      )
    )
}
