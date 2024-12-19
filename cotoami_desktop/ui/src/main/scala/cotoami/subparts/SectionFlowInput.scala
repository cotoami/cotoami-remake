package cotoami.subparts

import scala.scalajs.js
import org.scalajs.dom

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._
import slinky.web.SyntheticMouseEvent

import cats.effect.IO
import com.softwaremill.quicklens._

import fui._
import cotoami.Context
import cotoami.utils.{Log, Validation}
import cotoami.models.{
  Coto,
  Cotonoma,
  DateTimeRange,
  Geolocation,
  Id,
  Node,
  WaitingPost,
  WaitingPosts
}
import cotoami.backend.{CotoBackend, CotonomaBackend, ErrorJson}
import cotoami.components.{
  materialSymbol,
  optionalClasses,
  toolButton,
  ScrollArea,
  SplitPane
}
import cotoami.subparts.SectionGeomap.{Model => Geomap}

object SectionFlowInput {
  final val StorageKeyPrefix = "FormCoto."

  def init(id: String, autoSave: Boolean): (Model, Cmd.One[Msg]) =
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
    def editorId: String = s"${id}-editor"

    def hasContents: Boolean =
      form match {
        case form: CotoForm =>
          !form.textContent.isBlank || form.mediaContent.isDefined
        case CotonomaForm(name, _) => !name.isBlank
      }

    def readyToPost: Boolean =
      hasContents && !posting && (form match {
        case form: CotoForm =>
          form.validate.validated || form.mediaContent.isDefined
        case CotonomaForm(_, validation) => validation.validated
      })

    def clear: Model =
      copy(
        form = form match {
          case form: CotoForm     => CotoForm()
          case form: CotonomaForm => CotonomaForm()
        },
        folded = true,
        inPreview = false
      )

    def storageKey: String = StorageKeyPrefix + id

    def save: Cmd.One[Msg] =
      (autoSave, form) match {
        case (true, form: CotoForm) =>
          Cmd(IO {
            dom.window.localStorage.setItem(storageKey, form.textContent)
            None
          })
        case _ => Cmd.none
      }

    def restore: Cmd.One[Msg] =
      (autoSave, form) match {
        case (true, form: CotoForm) =>
          restoreTextContent.map(Msg.TextContentRestored)
        case _ => Cmd.none
      }

    private def restoreTextContent: Cmd.One[Option[String]] = Cmd(IO {
      Some(Option(dom.window.localStorage.getItem(storageKey)))
    })
  }

  /////////////////////////////////////////////////////////////////////////////
  // Form
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Form

  case class CotoForm(
      textContent: String = "",
      mediaContent: Option[dom.Blob] = None,
      mediaLocation: Option[Geolocation] = None,
      mediaDateTime: Option[DateTimeRange] = None,
      dateTimeRange: Option[DateTimeRange] = None
  ) extends Form {
    def summary: Option[String] =
      if (hasSummary)
        Some(firstLine.stripPrefix(CotoForm.SummaryPrefix).trim)
      else
        None

    def content: String =
      if (hasSummary)
        textContent.stripPrefix(firstLine).trim
      else
        textContent.trim

    def validate: Validation.Result =
      if (textContent.isBlank)
        Validation.Result.notYetValidated
      else {
        val errors =
          summary.map(Coto.validateSummary(_)).getOrElse(Seq.empty) ++
            Coto.validateContent(content)
        Validation.Result(errors)
      }

    private def hasSummary: Boolean =
      textContent.startsWith(CotoForm.SummaryPrefix)

    private def firstLine = textContent.linesIterator.next()
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
    def name: String = nameInput.trim

    def validate(nodeId: Id[Node]): (CotonomaForm, Cmd.One[Msg]) = {
      val (validation, cmd) =
        if (name.isEmpty())
          (Validation.Result.notYetValidated, Cmd.none)
        else
          Cotonoma.validateName(name) match {
            case Seq() =>
              (
                // Now that the local validation has passed,
                // wait for backend validation to be done.
                Validation.Result.notYetValidated,
                CotonomaBackend.fetchByName(name, nodeId)
                  .map(Msg.CotonomaByName(name, _))
              )
            case errors => (Validation.Result(errors), Cmd.none)
          }
      (copy(validation = validation), cmd)
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg

  object Msg {
    case object SetCotoForm extends Msg
    case object SetCotonomaForm extends Msg
    case class TextContentRestored(content: Option[String]) extends Msg
    case class TextContentInput(content: String) extends Msg
    case class CotonomaNameInput(name: String) extends Msg
    case class FileInput(file: dom.Blob) extends Msg
    case class ExifLocationDetected(result: Either[String, Option[Geolocation]])
        extends Msg
    case class ExifDateTimeDetected(
        result: Either[String, Option[DateTimeRange]]
    ) extends Msg
    case object DeleteMediaContent extends Msg
    case object DeleteDateTimeRange extends Msg
    case object UseMediaDateTime extends Msg
    case object DeleteGeolocation extends Msg
    case object UseMediaGeolocation extends Msg
    case object ImeCompositionStart extends Msg
    case object ImeCompositionEnd extends Msg
    case class CotonomaByName(
        name: String,
        result: Either[ErrorJson, Cotonoma]
    ) extends Msg
    case class SetFolded(folded: Boolean) extends Msg
    case object TogglePreview extends Msg
    case object Post extends Msg
    case class PostCoto(form: CotoForm, mediaContent: Option[(String, String)])
        extends Msg
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
      geomap: Geomap,
      waitingPosts: WaitingPosts
  )(implicit
      context: Context
  ): (Model, Geomap, WaitingPosts, Log, Cmd[Msg]) = {
    val default = (model, geomap, waitingPosts, context.log, Cmd.none)
    (msg, model.form, context.domain.currentCotonoma) match {
      case (Msg.SetCotoForm, _, _) =>
        model.copy(form = CotoForm()) match {
          case model =>
            default.copy(
              _1 = model,
              _5 = model.restore
            )
        }

      case (Msg.SetCotonomaForm, cotoForm: CotoForm, _) =>
        default.copy(
          _1 = model.copy(form = CotonomaForm()),
          _2 =
            // If the focused location has been set by EXIF info,
            // Swithcing to CotonomaForm will abandon the focused location
            // as well as the media content.
            if (geomap.focusedLocation == cotoForm.mediaLocation)
              geomap.copy(focusedLocation = None)
            else
              geomap
        )

      case (Msg.TextContentRestored(Some(content)), form: CotoForm, _) =>
        default.copy(
          _1 =
            if (form.textContent.isBlank)
              model.copy(
                form = form.copy(textContent = content),
                folded = content.isBlank
              )
            else
              model.copy(folded = false),
          _4 = context.log.info("Coto draft restored")
        )

      case (Msg.TextContentInput(content), form: CotoForm, _) =>
        model.copy(form = form.copy(textContent = content)) match {
          case model =>
            default.copy(
              _1 = model,
              _5 = model.save
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
          _5 =
            if (!model.imeActive)
              cmds
            else
              Cmd.none
        )
      }

      case (Msg.FileInput(file), form: CotoForm, _) =>
        default.copy(
          _1 = model.copy(form =
            form.copy(mediaContent = Some(file), mediaLocation = None)
          ),
          _5 = Cmd.Batch(
            Geolocation.fromExif(file).map {
              case Right(location) => Msg.ExifLocationDetected(Right(location))
              case Left(t)         => Msg.ExifLocationDetected(Left(t.toString))
            },
            DateTimeRange.fromExif(file).map {
              case Right(timeRange) =>
                Msg.ExifDateTimeDetected(Right(timeRange))
              case Left(t) => Msg.ExifDateTimeDetected(Left(t.toString))
            }
          )
        )

      case (
            Msg.ExifLocationDetected(Right(location)),
            form: CotoForm,
            _
          ) =>
        default.copy(
          _1 = model.copy(form = form.copy(mediaLocation = location)),
          _2 = location.map(geomap.focus).getOrElse(geomap.unfocus)
        )

      case (Msg.ExifLocationDetected(Left(error)), _, _) =>
        default.copy(
          _4 = context.log.error(
            "EXIF location detection error.",
            Some(error)
          )
        )

      case (Msg.ExifDateTimeDetected(Right(dateTime)), form: CotoForm, _) => {
        println(s"dateTime: ${dateTime.map(_.startUtcIso)}")
        default.copy(
          _1 = model.copy(form =
            form.copy(dateTimeRange = dateTime, mediaDateTime = dateTime)
          )
        )
      }

      case (Msg.ExifDateTimeDetected(Left(error)), _, _) =>
        default.copy(
          _4 = context.log.error("EXIF DateTime detection error.", Some(error))
        )

      case (Msg.DeleteMediaContent, form: CotoForm, _) =>
        default.copy(
          _1 = model.copy(form =
            form.copy(
              mediaContent = None,
              mediaLocation = None,
              mediaDateTime = None
            )
          ),
          _2 = geomap.unfocus
        )

      case (Msg.DeleteDateTimeRange, form: CotoForm, _) =>
        default.copy(
          _1 = model.copy(form = form.copy(dateTimeRange = None))
        )

      case (Msg.UseMediaDateTime, form: CotoForm, _) =>
        default.copy(
          _1 = model.copy(form = form.copy(dateTimeRange = form.mediaDateTime))
        )

      case (Msg.DeleteGeolocation, _, _) =>
        default.copy(_2 = geomap.copy(focusedLocation = None))

      case (Msg.UseMediaGeolocation, form: CotoForm, _) =>
        default.copy(_2 = form.mediaLocation match {
          case Some(location) => geomap.focus(location)
          case None           => geomap
        })

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
                  _5 = cmds
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
            Validation.Error(
              "cotonoma-already-exists",
              s"The cotonoma \"${cotonoma.name}\" already exists in this node.",
              Map("name" -> cotonoma.name, "id" -> cotonoma.id.uuid)
            ).toResult
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
          default.copy(_4 =
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
        model.copy(posting = true) match {
          case model =>
            form.mediaContent match {
              case Some(blob) =>
                default.copy(
                  _1 = model,
                  _5 = Browser.encodeAsBase64(blob, true).map {
                    case Right(base64) =>
                      Msg.MediaContentEncoded(Right((base64, blob.`type`)))
                    case Left(e) =>
                      Msg.MediaContentEncoded(
                        Left("Media content encoding error.")
                      )
                  }
                )
              case None => {
                update(Msg.PostCoto(form, None), model, geomap, waitingPosts)
              }
            }
        }

      case (Msg.Post, form: CotonomaForm, Some(cotonoma)) => {
        val postId = WaitingPost.newPostId()
        model.copy(posting = true).clear match {
          case model =>
            default.copy(
              _1 = model,
              _2 = geomap.copy(focusedLocation = None),
              _3 = waitingPosts.addCotonoma(postId, form.name, cotonoma),
              _5 = postCotonoma(
                postId,
                form,
                geomap.focusedLocation,
                None,
                cotonoma.id
              )
            )
        }
      }

      case (Msg.PostCoto(form, mediaContent), _, Some(cotonoma)) => {
        val postId = WaitingPost.newPostId()
        model.clear match {
          case model =>
            default.copy(
              _1 = model,
              _2 = geomap.copy(focusedLocation = None),
              _3 = waitingPosts.addCoto(
                postId,
                form.content,
                form.summary,
                mediaContent,
                cotonoma
              ),
              _5 = Cmd.Batch(
                postCoto(
                  postId,
                  form,
                  mediaContent,
                  geomap.focusedLocation,
                  form.dateTimeRange,
                  cotonoma.id
                ),
                model.save
              )
            )
        }
      }

      case (Msg.MediaContentEncoded(Right(mediaContent)), form: CotoForm, _) =>
        update(
          Msg.PostCoto(form, Some(mediaContent)),
          model,
          geomap,
          waitingPosts
        )

      case (Msg.MediaContentEncoded(Left(e)), _, _) =>
        default.copy(
          _1 = model.copy(posting = false),
          _4 = context.log.error(e, None)
        )

      case (Msg.CotoPosted(postId, Right(coto)), _, _) =>
        default.copy(
          _1 = model.copy(posting = false),
          _3 = waitingPosts.remove(postId)
        )

      case (Msg.CotoPosted(postId, Left(e)), _, _) => {
        val error = js.JSON.stringify(e)
        default.copy(
          _1 = model.copy(posting = false),
          _3 = waitingPosts.setError(
            postId,
            s"Couldn't post this coto: ${error}"
          ),
          _4 = context.log.error("Couldn't post a coto.", Some(error))
        )
      }

      case (Msg.CotonomaPosted(postId, Right((cotonoma, _))), _, _) =>
        default.copy(
          _1 = model.copy(posting = false),
          _3 = waitingPosts.remove(postId),
          _4 = context.log.info("Cotonoma posted.", Some(cotonoma.name))
        )

      case (Msg.CotonomaPosted(postId, Left(e)), _, _) => {
        val error = js.JSON.stringify(e)
        default.copy(
          _1 = model.copy(posting = false),
          _3 = waitingPosts.setError(
            postId,
            s"Couldn't post this cotonoma: ${error}"
          ),
          _4 = context.log.error("Couldn't post a cotonoma.", Some(error))
        )
      }

      case (_, _, _) => default
    }
  }

  private def postCoto(
      postId: String,
      form: CotoForm,
      mediaContent: Option[(String, String)],
      location: Option[Geolocation],
      timeRange: Option[DateTimeRange],
      postTo: Id[Cotonoma]
  ): Cmd.One[Msg] =
    CotoBackend.post(
      form.content,
      form.summary,
      mediaContent,
      location,
      timeRange,
      postTo
    )
      .map(Msg.CotoPosted(postId, _))

  private def postCotonoma(
      postId: String,
      form: CotonomaForm,
      location: Option[Geolocation],
      timeRange: Option[DateTimeRange],
      postTo: Id[Cotonoma]
  ): Cmd.One[Msg] =
    CotonomaBackend.post(form.name, location, timeRange, postTo)
      .map(Msg.CotonomaPosted(postId, _))

  /////////////////////////////////////////////////////////////////////////////
  // view
  /////////////////////////////////////////////////////////////////////////////

  def apply(
      model: Model,
      operatingNode: Node,
      currentCotonoma: Cotonoma,
      geomap: Geomap,
      editorHeight: Int,
      onEditorHeightChanged: Int => Unit
  )(implicit context: Context, dispatch: Msg => Unit): ReactElement =
    section(
      className := optionalClasses(
        Seq(
          ("form-coto", true),
          ("folded", model.folded)
        )
      )
    )(
      headerTools(model),
      model.form match {
        case form: CotoForm =>
          Fragment(
            form.mediaContent.map(blob => {
              SplitPane(
                vertical = false,
                initialPrimarySize = 300,
                className = Some("coto-form-with-media"),
                primary = SplitPane.Primary.Props()(
                  sectionMediaPreview(blob, form)
                ),
                secondary = SplitPane.Secondary.Props()(
                  formCoto(
                    form,
                    model,
                    operatingNode,
                    currentCotonoma,
                    geomap,
                    editorHeight,
                    onEditorHeightChanged
                  )
                )
              ): ReactElement
            }).getOrElse(
              formCoto(
                form,
                model,
                operatingNode,
                currentCotonoma,
                geomap,
                editorHeight,
                onEditorHeightChanged
              )
            )
          )

        case form: CotonomaForm =>
          formCotonoma(form, model, operatingNode, currentCotonoma, geomap)
      }
    )

  private def sectionMediaPreview(mediaContent: dom.Blob, form: CotoForm)(
      implicit dispatch: Msg => Unit
  ): ReactElement = {
    val url = dom.URL.createObjectURL(mediaContent)
    section(className := "media-preview")(
      div(className := "media-content")(
        img(
          src := url,
          onLoad := (_ => dom.URL.revokeObjectURL(url))
        ),
        toolButton(
          symbol = "close",
          tip = "Delete",
          classes = "delete",
          onClick = _ => dispatch(Msg.DeleteMediaContent)
        )
      )
    )
  }

  private def formCoto(
      form: CotoForm,
      model: Model,
      operatingNode: Node,
      currentCotonoma: Cotonoma,
      geomap: Geomap,
      editorHeight: Int,
      onEditorHeightChanged: Int => Unit
  )(implicit context: Context, dispatch: Msg => Unit): ReactElement =
    SplitPane(
      vertical = false,
      initialPrimarySize = editorHeight,
      resizable = !model.folded,
      onPrimarySizeChanged = Some(onEditorHeightChanged),
      primary =
        if (model.inPreview)
          SplitPane.Primary.Props(className = Some("coto-preview"))(
            ScrollArea()(
              section(className := "coto-preview")(
                form.summary.map(section(className := "summary")(_)),
                div(className := "content")(
                  ViewCoto.sectionTextContent(Some(form.content))
                )
              )
            )
          )
        else
          SplitPane.Primary.Props(className = Some("coto-editor"))(
            textarea(
              id := model.editorId,
              placeholder := "Write your Coto in Markdown",
              value := form.textContent,
              onFocus := (_ => dispatch(Msg.SetFolded(false))),
              onChange := (e => dispatch(Msg.TextContentInput(e.target.value))),
              onCompositionStart := (_ => dispatch(Msg.ImeCompositionStart)),
              onCompositionEnd := (_ => dispatch(Msg.ImeCompositionEnd)),
              onKeyDown := (e =>
                if (model.readyToPost && detectCtrlEnter(e)) {
                  dispatch(Msg.Post)
                }
              )
            ),
            div(className := "input-image")(
              InputFile(
                accept = js.Dictionary("image/*" -> js.Array[String]()),
                message = "Drop an image file here, or click to select one",
                onSelect = file => dispatch(Msg.FileInput(file))
              )
            )
          ),
      secondary = SplitPane.Secondary.Props()(
        ulAttributes(
          form.dateTimeRange,
          form.mediaDateTime,
          geomap.focusedLocation,
          form.mediaLocation
        ),
        div(className := "post")(
          Validation.sectionValidationError(form.validate),
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
              button(
                className := "preview contrast outline",
                disabled := !form.validate.validated,
                onClick := (_ => dispatch(Msg.TogglePreview))
              )(
                if (model.inPreview)
                  "Edit"
                else
                  "Preview"
              ),
              buttonPost(model, currentCotonoma)
            )
          )
        )
      )
    )

  private def formCotonoma(
      form: CotonomaForm,
      model: Model,
      operatingNode: Node,
      currentCotonoma: Cotonoma,
      geomap: Geomap
  )(implicit context: Context, dispatch: Msg => Unit): ReactElement =
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
      ulAttributes(None, None, geomap.focusedLocation, None),
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

  private def ulAttributes(
      dateTimeRange: Option[DateTimeRange],
      mediaDateTime: Option[DateTimeRange],
      location: Option[Geolocation],
      mediaLocation: Option[Geolocation]
  )(implicit context: Context, dispatch: Msg => Unit): Option[ReactElement] =
    Seq(
      liAttributeDateTimeRange(dateTimeRange, mediaDateTime),
      liAttributeGeolocation(location, mediaLocation)
    ).flatten match {
      case Seq() => None
      case attributes =>
        Some(ul(className := "attributes")(attributes: _*))
    }

  private def liAttributeDateTimeRange(
      dateTimeRange: Option[DateTimeRange],
      mediaDateTime: Option[DateTimeRange]
  )(implicit context: Context, dispatch: Msg => Unit): Option[ReactElement] =
    Option.when(dateTimeRange.isDefined || mediaDateTime.isDefined) {
      li(className := "attribute time-range")(
        div(className := "attribute-name")(
          materialSymbol("calendar_month"),
          "Date"
        ),
        div(className := "attribute-value")(
          dateTimeRange.map(range => context.time.formatDateTime(range.start))
        ),
        Option.when(mediaDateTime.isDefined && dateTimeRange != mediaDateTime) {
          divUseMediaMetadata(
            "Use the image timestamp",
            _ => dispatch(Msg.UseMediaDateTime)
          )
        },
        Option.when(dateTimeRange.isDefined) {
          divAttributeDelete(_ => dispatch(Msg.DeleteDateTimeRange))
        }
      )
    }

  private def liAttributeGeolocation(
      location: Option[Geolocation],
      mediaLocation: Option[Geolocation]
  )(implicit dispatch: Msg => Unit): Option[ReactElement] =
    Option.when(location.isDefined || mediaLocation.isDefined) {
      li(className := "attribute geolocation")(
        div(className := "attribute-name")(
          materialSymbol("location_on"),
          "Location"
        ),
        div(className := "attribute-value")(
          location.map(location =>
            Fragment(
              div(className := "longitude")(
                span(className := "label")("longitude:"),
                span(className := "value longitude")(location.longitude)
              ),
              div(className := "latitude")(
                span(className := "label")("latitude:"),
                span(className := "value latitude")(location.latitude)
              )
            )
          )
        ),
        Option.when(mediaLocation.isDefined && location != mediaLocation) {
          divUseMediaMetadata(
            "Use the image location",
            _ => dispatch(Msg.UseMediaGeolocation)
          )
        },
        Option.when(location.isDefined) {
          divAttributeDelete(_ => dispatch(Msg.DeleteGeolocation))
        }
      )
    }

  private def divUseMediaMetadata(
      label: String,
      onClick: SyntheticMouseEvent[_] => Unit
  ): ReactElement =
    div(className := "use-media-metadata")(
      button(
        className := "default",
        slinky.web.html.onClick := onClick
      )(label)
    )

  private def divAttributeDelete(
      onClick: SyntheticMouseEvent[_] => Unit
  ): ReactElement =
    div(className := "attribute-delete")(
      toolButton(
        symbol = "close",
        tip = "Delete",
        classes = "delete",
        onClick = onClick
      )
    )

  private def headerTools(model: Model)(implicit
      dispatch: Msg => Unit
  ): ReactElement =
    header(className := "tools")(
      section(className := "coto-type-switch")(
        button(
          className := "new-coto default",
          disabled := model.form.isInstanceOf[CotoForm],
          onClick := (_ => dispatch(Msg.SetCotoForm))
        )(
          span(className := "label")(
            materialSymbol("chat"),
            "Coto"
          )
        ),
        button(
          className := "new-cotonoma default",
          disabled := model.form.isInstanceOf[CotonomaForm],
          onClick := (_ => dispatch(Msg.SetCotonomaForm))
        )(
          span(className := "label")(
            materialSymbol("folder"),
            "Cotonoma"
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
  )(implicit dispatch: Msg => Unit): ReactElement =
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
}
