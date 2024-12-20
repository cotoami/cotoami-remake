package cotoami.subparts

import scala.util.chaining._
import scala.scalajs.js
import org.scalajs.dom

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._
import slinky.web.SyntheticMouseEvent

import cats.effect.IO
import com.softwaremill.quicklens._

import fui._
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.utils.Validation
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
import cotoami.subparts.Editor._
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
      inPreview: Boolean = false,
      posting: Boolean = false
  ) {
    def hasContents: Boolean = form.hasContents

    def readyToPost: Boolean = !posting && form.readyToPost

    def clear: Model =
      copy(
        form = form match {
          case form: CotoForm.Model     => CotoForm.Model()
          case form: CotonomaForm.Model => CotonomaForm.Model()
        },
        folded = true,
        inPreview = false
      )

    def save: Cmd.One[AppMsg] =
      form match {
        case form: CotoForm.Model =>
          Cmd(IO {
            dom.window.localStorage.setItem(StorageKey, form.textContent)
            None
          })
        case _ => Cmd.none
      }

    def restore: Cmd.One[AppMsg] =
      form match {
        case form: CotoForm.Model =>
          restoreTextContent.map(Msg.TextContentRestored(_).into)
        case _ => Cmd.none
      }

    private def restoreTextContent: Cmd.One[Option[String]] = Cmd(IO {
      Some(Option(dom.window.localStorage.getItem(StorageKey)))
    })
  }

  /////////////////////////////////////////////////////////////////////////////
  // update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = AppMsg.FlowInputMsg(this)
  }

  object Msg {
    case object SetCotoForm extends Msg
    case object SetCotonomaForm extends Msg
    case class TextContentRestored(content: Option[String]) extends Msg
    case class SetFolded(folded: Boolean) extends Msg
    case object TogglePreview extends Msg
    case object Post extends Msg
    case class PostCoto(
        form: CotoForm.Model,
        mediaContent: Option[(String, String)]
    ) extends Msg
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
  ): (Model, Geomap, WaitingPosts, Cmd[AppMsg]) = {
    val default = (model, geomap, waitingPosts, Cmd.none)
    (msg, model.form, context.domain.currentCotonoma) match {
      case (Msg.SetCotoForm, _, _) =>
        model.copy(form = CotoForm.Model()) match {
          case model =>
            default.copy(
              _1 = model,
              _4 = model.restore
            )
        }

      case (Msg.SetCotonomaForm, cotoForm: CotoForm.Model, _) =>
        default.copy(
          _1 = model.copy(form = CotonomaForm.Model()),
          _2 =
            // If the focused location has been set by EXIF info,
            // Swithcing to CotonomaForm will abandon the focused location
            // as well as the media content.
            if (geomap.focusedLocation == cotoForm.mediaLocation)
              geomap.copy(focusedLocation = None)
            else
              geomap
        )

      case (Msg.TextContentRestored(Some(content)), form: CotoForm.Model, _) =>
        default.copy(
          _1 =
            if (form.textContent.isBlank)
              model.copy(
                form = form.copy(textContent = content),
                folded = content.isBlank
              )
            else
              model.copy(folded = false),
          _4 = cotoami.info("Coto draft restored")
        )

      case (Msg.SetFolded(folded), _, _) =>
        default.copy(_1 = model.copy(folded = folded))

      case (Msg.TogglePreview, _, _) =>
        default.copy(_1 = model.modify(_.inPreview).using(!_))

      case (Msg.Post, form: CotoForm.Model, Some(cotonoma)) =>
        model.copy(posting = true) match {
          case model =>
            form.mediaContent match {
              case Some(blob) =>
                default.copy(
                  _1 = model,
                  _4 = Browser.encodeAsBase64(blob, true).map {
                    case Right(base64) =>
                      Msg.MediaContentEncoded(Right((base64, blob.`type`))).into
                    case Left(e) =>
                      Msg.MediaContentEncoded(
                        Left("Media content encoding error.")
                      ).into
                  }
                )
              case None => {
                update(Msg.PostCoto(form, None), model, geomap, waitingPosts)
              }
            }
        }

      case (Msg.Post, form: CotonomaForm.Model, Some(cotonoma)) => {
        val postId = WaitingPost.newPostId()
        model.copy(posting = true).clear match {
          case model =>
            default.copy(
              _1 = model,
              _2 = geomap.copy(focusedLocation = None),
              _3 = waitingPosts.addCotonoma(postId, form.name, cotonoma),
              _4 = postCotonoma(
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
              _4 = Cmd.Batch(
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

      case (
            Msg.MediaContentEncoded(Right(mediaContent)),
            form: CotoForm.Model,
            _
          ) =>
        update(
          Msg.PostCoto(form, Some(mediaContent)),
          model,
          geomap,
          waitingPosts
        )

      case (Msg.MediaContentEncoded(Left(e)), _, _) =>
        default.copy(
          _1 = model.copy(posting = false),
          _4 = cotoami.error(e, None)
        )

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

      case (_, _, _) => default
    }
  }

  private def postCoto(
      postId: String,
      form: CotoForm.Model,
      mediaContent: Option[(String, String)],
      location: Option[Geolocation],
      timeRange: Option[DateTimeRange],
      postTo: Id[Cotonoma]
  ): Cmd.One[AppMsg] =
    CotoBackend.post(
      form.content,
      form.summary,
      mediaContent,
      location,
      timeRange,
      postTo
    )
      .map(Msg.CotoPosted(postId, _).into)

  private def postCotonoma(
      postId: String,
      form: CotonomaForm.Model,
      location: Option[Geolocation],
      timeRange: Option[DateTimeRange],
      postTo: Id[Cotonoma]
  ): Cmd.One[AppMsg] =
    CotonomaBackend.post(form.name, location, timeRange, postTo)
      .map(Msg.CotonomaPosted(postId, _).into)

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
          ("flow-input", true),
          ("folded", model.folded)
        )
      )
    )(
      headerTools(model),
      model.form match {
        case form: CotoForm.Model =>
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

        case form: CotonomaForm.Model =>
          formCotonoma(form, model, operatingNode, currentCotonoma, geomap)
      }
    )

  private def sectionMediaPreview(mediaContent: dom.Blob, form: CotoForm.Model)(
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
      form: CotoForm.Model,
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
      form: CotonomaForm.Model,
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
          disabled := model.form.isInstanceOf[CotoForm.Model],
          onClick := (_ => dispatch(Msg.SetCotoForm))
        )(
          span(className := "label")(
            materialSymbol("chat"),
            "Coto"
          )
        ),
        button(
          className := "new-cotonoma default",
          disabled := model.form.isInstanceOf[CotonomaForm.Model],
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
