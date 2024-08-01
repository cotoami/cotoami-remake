package cotoami.subparts

import scala.scalajs.js
import org.scalajs.dom

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cats.effect.IO
import com.softwaremill.quicklens._

import fui._
import cotoami.Context
import cotoami.utils.{Log, Validation}
import cotoami.backend._
import cotoami.models.{WaitingPost, WaitingPosts}
import cotoami.components.{
  materialSymbol,
  optionalClasses,
  toolButton,
  ScrollArea,
  SplitPane
}

object FormCoto {
  final val StorageKeyPrefix = "FormCoto."

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
      folded: Boolean = true,
      imeActive: Boolean = false,
      autoSave: Boolean = false,
      inPreview: Boolean = false,
      posting: Boolean = false
  ) {
    def editorId: String = s"${this.id}-editor"

    def hasContents: Boolean =
      this.form match {
        case CotoForm(cotoInput, mediaContent) =>
          !cotoInput.isBlank || mediaContent.isDefined
        case CotonomaForm(name, _) => !name.isBlank
      }

    def readyToPost: Boolean =
      this.hasContents && !this.posting && (this.form match {
        case form: CotoForm =>
          form.validate.validated || form.mediaContent.isDefined
        case CotonomaForm(_, validation) => validation.validated
      })

    def clear: Model =
      this.copy(
        form = this.form match {
          case form: CotoForm     => CotoForm()
          case form: CotonomaForm => CotonomaForm()
        },
        folded = true,
        inPreview = false
      )

    def storageKey: String = StorageKeyPrefix + this.id

    def save: Cmd[Msg] =
      (this.autoSave, this.form) match {
        case (true, CotoForm(cotoInput, _)) =>
          Cmd(IO {
            dom.window.localStorage.setItem(this.storageKey, cotoInput)
            None
          })

        case _ => Cmd.none
      }

    def restore: Cmd[Msg] =
      (this.autoSave, this.form) match {
        case (true, form: CotoForm) =>
          restoreCoto.map(Msg.CotoRestored)

        case _ => Cmd.none
      }

    private def restoreCoto: Cmd[Option[String]] = Cmd(IO {
      Some(Option(dom.window.localStorage.getItem(this.storageKey)))
    })
  }

  /////////////////////////////////////////////////////////////////////////////
  // Form
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Form

  case class CotoForm(
      cotoInput: String = "",
      mediaContent: Option[dom.Blob] = None
  ) extends Form {
    def summary: Option[String] =
      if (this.hasSummary)
        Some(this.firstLine.stripPrefix(CotoForm.SummaryPrefix).trim)
      else
        None

    def content: String =
      if (this.hasSummary)
        this.cotoInput.stripPrefix(this.firstLine).trim
      else
        this.cotoInput.trim

    def validate: Validation.Result =
      if (this.cotoInput.isBlank)
        Validation.Result.notYetValidated
      else {
        val errors =
          this.summary.map(Coto.validateSummary(_)).getOrElse(Seq.empty) ++
            Coto.validateContent(this.content)
        Validation.Result(errors)
      }

    private def hasSummary: Boolean =
      this.cotoInput.startsWith(CotoForm.SummaryPrefix)

    private def firstLine = this.cotoInput.linesIterator.next()
  }

  object CotoForm {
    // A top-level heading as the first line will be used as a summary.
    // cf. https://spec.commonmark.org/0.31.2/#atx-headings
    val SummaryPrefix = "# "
  }

  case class CotonomaForm(
      nameInput: String = "",
      validation: Validation.Result = Validation.Result.notYetValidated
  ) extends Form {
    def name: String = this.nameInput.trim

    def validate(nodeId: Id[Node]): (CotonomaForm, Seq[Cmd[Msg]]) = {
      val (validation, cmds) =
        if (this.name.isEmpty())
          (Validation.Result.notYetValidated, Seq.empty)
        else
          Cotonoma.validateName(this.name) match {
            case errors if errors.isEmpty =>
              (
                // Now that the local validation has passed,
                // wait for backend validation to be done.
                Validation.Result.notYetValidated,
                Seq(
                  Cotonoma.fetchByName(this.name, nodeId)
                    .map(Msg.CotonomaByName(this.name, _))
                )
              )
            case errors => (Validation.Result(errors), Seq.empty)
          }
      (this.copy(validation = validation), cmds)
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg

  object Msg {
    case object SetCotoForm extends Msg
    case object SetCotonomaForm extends Msg
    case class CotoRestored(coto: Option[String]) extends Msg
    case class CotoInput(coto: String) extends Msg
    case class CotonomaNameInput(name: String) extends Msg
    case class FileInput(file: dom.Blob) extends Msg
    case object ImeCompositionStart extends Msg
    case object ImeCompositionEnd extends Msg
    case class CotonomaByName(
        name: String,
        result: Either[ErrorJson, Cotonoma]
    ) extends Msg
    case class SetFolded(folded: Boolean) extends Msg
    case object TogglePreview extends Msg
    case object Post extends Msg
    case class MediaContentEncoded(result: Either[String, (String, String)])
        extends Msg
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
  )(implicit context: Context): (Model, WaitingPosts, Log, Seq[Cmd[Msg]]) = {
    val default = (model, waitingPosts, context.log, Seq.empty)
    (msg, model.form, context.domain.currentCotonoma) match {
      case (Msg.SetCotoForm, _, _) =>
        model.copy(form = CotoForm()) match {
          case model =>
            default.copy(
              _1 = model,
              _4 = Seq(model.restore)
            )
        }

      case (Msg.SetCotonomaForm, _, _) =>
        default.copy(_1 = model.copy(form = CotonomaForm()))

      case (Msg.CotoRestored(Some(coto)), form: CotoForm, _) =>
        default.copy(
          _1 =
            if (form.cotoInput.isBlank)
              model.copy(
                form = form.copy(cotoInput = coto),
                folded = coto.isBlank
              )
            else
              model.copy(folded = false),
          _3 = context.log.info("Coto draft restored")
        )

      case (Msg.CotoInput(coto), form: CotoForm, _) =>
        model.copy(form = form.copy(cotoInput = coto)) match {
          case model =>
            default.copy(
              _1 = model,
              _4 = Seq(model.save)
            )
        }

      case (
            Msg.CotonomaNameInput(name),
            form: CotonomaForm,
            Some(cotonoma)
          ) => {
        val (newForm, cmds) =
          form.copy(nameInput = name).validate(cotonoma.nodeId)
        default.copy(
          _1 = model.copy(form = newForm),
          _4 =
            if (!model.imeActive)
              cmds
            else
              Seq.empty
        )
      }

      case (Msg.FileInput(file), form: CotoForm, _) =>
        default.copy(
          _1 = model.copy(form = form.copy(mediaContent = Some(file)))
        )

      case (Msg.ImeCompositionStart, _, _) =>
        default.copy(_1 = model.copy(imeActive = true))

      case (Msg.ImeCompositionEnd, form, currentCotonoma) =>
        model.copy(imeActive = false) match {
          case model => {
            (form, currentCotonoma) match {
              case (form: CotonomaForm, Some(cotonoma)) => {
                val (newForm, cmds) = form.validate(cotonoma.nodeId)
                default.copy(
                  _1 = model.copy(form = newForm),
                  _4 = cmds
                )
              }
              case _ => default
            }
          }
        }

      case (
            Msg.CotonomaByName(name, Right(cotonoma)),
            form: CotonomaForm,
            _
          ) =>
        if (cotonoma.name == form.name)
          form.modify(_.validation).setTo(
            Validation.Result(
              Validation.Error(
                "cotonoma-already-exists",
                s"The cotonoma \"${cotonoma.name}\" already exists in this node.",
                Map("name" -> cotonoma.name, "id" -> cotonoma.id.uuid)
              )
            )
          ) match {
            case form => default.copy(_1 = model.copy(form = form))
          }
        else
          default

      case (Msg.CotonomaByName(name, Left(error)), form: CotonomaForm, _) =>
        if (name == form.name && error.code == "not-found")
          form.copy(validation = Validation.Result.validated) match {
            case form => default.copy(_1 = model.copy(form = form))
          }
        else
          default.copy(_3 =
            context.log.error(
              "CotonomaByName error.",
              Some(js.JSON.stringify(error))
            )
          )

      case (Msg.SetFolded(folded), _, _) =>
        default.copy(_1 = model.copy(folded = folded))

      case (Msg.TogglePreview, _, _) =>
        default.copy(_1 = model.modify(_.inPreview).using(!_))

      case (Msg.Post, form: CotoForm, Some(cotonoma)) =>
        model.copy(posting = true).clear match {
          case model =>
            form.mediaContent match {
              case Some(blob) =>
                default.copy(
                  _1 = model,
                  _4 = Seq(
                    Browser.encodeAsBase64(blob, true).map {
                      case Right(base64) =>
                        Msg.MediaContentEncoded(Right((base64, blob.`type`)))
                      case Left(e) =>
                        Msg.MediaContentEncoded(
                          Left("Media content encoding error.")
                        )
                    },
                    model.save
                  )
                )
              case None => {
                val postId = WaitingPost.newPostId()
                default.copy(
                  _1 = model,
                  _2 = waitingPosts.addCoto(
                    postId,
                    form.content,
                    form.summary,
                    None,
                    cotonoma
                  ),
                  _4 = Seq(
                    postCoto(postId, form, None, cotonoma.id),
                    model.save
                  )
                )
              }
            }
        }

      case (Msg.Post, form: CotonomaForm, Some(cotonoma)) => {
        val postId = WaitingPost.newPostId()
        model.copy(posting = true).clear match {
          case model =>
            default.copy(
              _1 = model,
              _2 = waitingPosts.addCotonoma(postId, form.name, cotonoma),
              _4 = Seq(postCotonoma(postId, form, cotonoma.id))
            )
        }
      }

      case (
            Msg.MediaContentEncoded(Right(mediaContent)),
            form: CotoForm,
            Some(cotonoma)
          ) => {
        val postId = WaitingPost.newPostId()
        default.copy(
          _2 = waitingPosts.addCoto(
            postId,
            form.content,
            form.summary,
            Some(mediaContent),
            cotonoma
          ),
          _4 = Seq(
            postCoto(postId, form, Some(mediaContent), cotonoma.id)
          )
        )
      }

      case (Msg.MediaContentEncoded(Left(e)), _, _) =>
        default.copy(
          _1 = model.copy(posting = false),
          _3 = context.log.error(e, None)
        )

      case (Msg.CotoPosted(postId, Right(coto)), _, _) =>
        default.copy(
          _1 = model.copy(posting = false),
          _2 = waitingPosts.remove(postId),
          _3 = context.log.info("Coto posted.", Some(coto.id.uuid))
        )

      case (Msg.CotoPosted(postId, Left(e)), _, _) => {
        val error = js.JSON.stringify(e)
        default.copy(
          _1 = model.copy(posting = false),
          _2 = waitingPosts.setError(
            postId,
            s"Couldn't post this coto: ${error}"
          ),
          _3 = context.log.error("Couldn't post a coto.", Some(error))
        )
      }

      case (Msg.CotonomaPosted(postId, Right((cotonoma, _))), _, _) =>
        default.copy(
          _1 = model.copy(posting = false),
          _2 = waitingPosts.remove(postId),
          _3 = context.log.info("Cotonoma posted.", Some(cotonoma.name))
        )

      case (Msg.CotonomaPosted(postId, Left(e)), _, _) => {
        val error = js.JSON.stringify(e)
        default.copy(
          _1 = model.copy(posting = false),
          _2 = waitingPosts.setError(
            postId,
            s"Couldn't post this cotonoma: ${error}"
          ),
          _3 = context.log.error("Couldn't post a cotonoma.", Some(error))
        )
      }

      case (_, _, _) => default
    }
  }

  private def postCoto(
      postId: String,
      form: CotoForm,
      mediaContent: Option[(String, String)],
      postTo: Id[Cotonoma]
  ): Cmd[Msg] =
    Coto.post(form.content, form.summary, mediaContent, postTo)
      .map(Msg.CotoPosted(postId, _))

  private def postCotonoma(
      postId: String,
      form: CotonomaForm,
      postTo: Id[Cotonoma]
  ): Cmd[Msg] =
    Cotonoma.post(form.name, postTo).map(Msg.CotonomaPosted(postId, _))

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
          Fragment(
            form.mediaContent.map(blob => {
              val url = dom.URL.createObjectURL(blob)
              section(className := "media-preview")(
                img(
                  src := url,
                  onLoad := (_ => dom.URL.revokeObjectURL(url))
                )
              )
            }),
            formCoto(
              form,
              model,
              operatingNode,
              currentCotonoma,
              editorHeight,
              onEditorHeightChanged,
              dispatch
            )
          )

        case form: CotonomaForm =>
          formCotonoma(form, model, operatingNode, currentCotonoma, dispatch)
      }
    )

  private def formCoto(
      form: CotoForm,
      model: Model,
      operatingNode: Node,
      currentCotonoma: Cotonoma,
      editorHeight: Int,
      onEditorHeightChanged: Int => Unit,
      dispatch: Msg => Unit
  ): ReactElement =
    SplitPane(
      vertical = false,
      initialPrimarySize = editorHeight,
      resizable = !model.folded,
      className = None,
      onResizeStart = None,
      onResizeEnd = None,
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
                ViewCoto.sectionTextContent(Some(form.content))
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
            placeholder := "Write your Coto in Markdown",
            value := form.cotoInput,
            onFocus := (_ => dispatch(Msg.SetFolded(false))),
            onChange := (e => dispatch(Msg.CotoInput(e.target.value))),
            onCompositionStart := (_ => dispatch(Msg.ImeCompositionStart)),
            onCompositionEnd := (_ => dispatch(Msg.ImeCompositionEnd)),
            onKeyDown := (e =>
              if (model.readyToPost && detectCtrlEnter(e)) {
                dispatch(Msg.Post)
              }
            )
          ),
          InputFile(
            accept = js.Dictionary("image/*" -> js.Array[String]()),
            message = "Drop an image file here, or click to select one",
            onSelect = file => dispatch(Msg.FileInput(file))
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

  private def formCotonoma(
      form: CotonomaForm,
      model: Model,
      operatingNode: Node,
      currentCotonoma: Cotonoma,
      dispatch: Msg => Unit
  ): ReactElement =
    Fragment(
      input(
        `type` := "text",
        name := "cotonomaName",
        placeholder := "New cotonoma name",
        value := form.nameInput,
        Validation.ariaInvalid(form.validation),
        onFocus := (_ => dispatch(Msg.SetFolded(false))),
        onBlur := (_ => dispatch(Msg.SetFolded(!model.hasContents))),
        onChange := (e => dispatch(Msg.CotonomaNameInput(e.target.value))),
        onCompositionStart := (_ => dispatch(Msg.ImeCompositionStart)),
        onCompositionEnd := (_ => dispatch(Msg.ImeCompositionEnd)),
        onKeyDown := (e =>
          if (model.readyToPost && detectCtrlEnter(e)) {
            dispatch(Msg.Post)
          }
        )
      ),
      divPost(model, form.validation, operatingNode, currentCotonoma, dispatch)
    )

  private def headerTools(model: Model, dispatch: Msg => Unit): ReactElement =
    header(className := "tools")(
      section(className := "coto-type-switch")(
        button(
          className := "new-coto default",
          disabled := model.form.isInstanceOf[CotoForm],
          onClick := (_ => dispatch(Msg.SetCotoForm))
        )(
          span(className := "label")(
            materialSymbol("text_snippet"),
            "Coto"
          )
        ),
        button(
          className := "new-cotonoma default",
          disabled := model.form.isInstanceOf[CotonomaForm],
          onClick := (_ => dispatch(Msg.SetCotonomaForm))
        )(
          span(className := "label")(
            materialSymbol("topic"),
            "Cotonoma"
          )
        )
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
          spanNode(operatingNode)
        ),
        div(className := "buttons")(
          model.form match {
            case form: CotoForm =>
              Some(
                Fragment(
                  toolButton(
                    symbol = "arrow_drop_up",
                    tip = "Fold",
                    classes = "fold",
                    onClick = () => dispatch(Msg.SetFolded(true))
                  ),
                  button(
                    className := "preview contrast outline",
                    disabled := !form.validate.validated,
                    onClick := (_ => dispatch(Msg.TogglePreview))
                  )(
                    if (model.inPreview)
                      "Edit"
                    else
                      "Preview"
                  )
                )
              )
            case _ => None
          },
          button(
            className := "post",
            disabled := !model.readyToPost,
            aria - "busy" := model.posting.toString(),
            onClick := (_ => dispatch(Msg.Post))
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
