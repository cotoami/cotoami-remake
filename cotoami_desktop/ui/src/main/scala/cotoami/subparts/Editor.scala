package cotoami.subparts

import scala.util.chaining._
import scala.scalajs.js
import org.scalajs.dom
import com.softwaremill.quicklens._

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._
import slinky.web.SyntheticMouseEvent

import fui.Cmd
import cotoami.Context
import cotoami.utils.Validation
import cotoami.models.{Coto, Cotonoma, DateTimeRange, Geolocation, Id, Node}
import cotoami.backend.{CotonomaBackend, ErrorJson}
import cotoami.components.{materialSymbol, toolButton, ScrollArea}
import cotoami.subparts.SectionGeomap.{Model => Geomap}

object Editor {
  sealed trait Form {
    def hasContents: Boolean
    def readyToPost: Boolean
  }

  object CotoForm {
    case class Model(
        inPreview: Boolean = false,
        summaryInput: String = "",
        contentInput: String = "",
        mediaContent: Option[dom.Blob] = None,
        mediaLocation: Option[Geolocation] = None,
        mediaDateTime: Option[DateTimeRange] = None,
        dateTimeRange: Option[DateTimeRange] = None
    ) extends Form {
      def hasContents: Boolean =
        !summaryInput.isBlank || !contentInput.isBlank || mediaContent.isDefined

      def summary: Option[String] =
        Option.when(!summaryInput.isBlank())(summaryInput.trim)

      def content: String = contentInput.trim

      def validate: Validation.Result =
        if (summaryInput.isBlank && contentInput.isBlank)
          Validation.Result.notYetValidated
        else {
          val errors =
            summary.map(Coto.validateSummary(_)).getOrElse(Seq.empty) ++
              Coto.validateContent(content)
          Validation.Result(errors)
        }

      def scanMediaMetadata: Cmd.Batch[Msg] =
        mediaContent.map(blob =>
          Cmd.Batch(
            Geolocation.fromExif(blob).map {
              case Right(location) =>
                Msg.ExifLocationDetected(Right(location))
              case Left(t) => Msg.ExifLocationDetected(Left(t.toString))
            },
            DateTimeRange.fromExif(blob).map {
              case Right(timeRange) =>
                Msg.ExifDateTimeDetected(Right(timeRange))
              case Left(t) => Msg.ExifDateTimeDetected(Left(t.toString))
            }
          )
        ).getOrElse(Cmd.Batch())

      def readyToPost: Boolean =
        // The validation is not necessarily required
        // if `mediaContent` has some value (media-only coto).
        hasContents && !validate.failed
    }

    sealed trait Msg

    object Msg {
      case object TogglePreview extends Msg
      case class SummaryInput(summary: String) extends Msg
      case class ContentInput(content: String) extends Msg
      case class FileInput(file: dom.Blob) extends Msg
      case class ExifLocationDetected(
          result: Either[String, Option[Geolocation]]
      ) extends Msg
      case class ExifDateTimeDetected(
          result: Either[String, Option[DateTimeRange]]
      ) extends Msg
      case object DeleteMediaContent extends Msg
      case object DeleteDateTimeRange extends Msg
      case object DeleteGeolocation extends Msg
      case object UseMediaDateTime extends Msg
      case object UseMediaGeolocation extends Msg
    }

    def update(msg: Msg, model: Model)(implicit
        context: Context
    ): (Model, Geomap, Cmd[Msg]) = {
      val default = (model, context.geomap, Cmd.none)
      msg match {
        case Msg.TogglePreview =>
          default.copy(_1 = model.modify(_.inPreview).using(!_))

        case Msg.SummaryInput(summary) =>
          default.copy(_1 = model.copy(summaryInput = summary))

        case Msg.ContentInput(content) =>
          default.copy(_1 = model.copy(contentInput = content))

        case Msg.FileInput(file) =>
          model.copy(mediaContent = Some(file), mediaLocation = None).pipe(
            model => default.copy(_1 = model, _3 = model.scanMediaMetadata)
          )

        case Msg.ExifLocationDetected(Right(location)) =>
          default.copy(
            _1 = model.copy(mediaLocation = location),
            _2 = (location, context.geomap.focusedLocation) match {
              case (Some(location), None) => context.geomap.focus(location)
              case _                      => context.geomap
            }
          )

        case Msg.ExifLocationDetected(Left(error)) => {
          println(s"Location detection error: ${error}")
          default
        }

        case Msg.ExifDateTimeDetected(Right(dateTime)) =>
          default.copy(
            _1 = model.copy(
              mediaDateTime = dateTime,
              dateTimeRange = model.dateTimeRange.orElse(dateTime)
            )
          )

        case Msg.ExifDateTimeDetected(Left(error)) => {
          println(s"DateTime detection error: ${error}")
          default
        }

        case Msg.DeleteMediaContent =>
          default.copy(
            _1 = model.copy(
              mediaContent = None,
              mediaLocation = None,
              mediaDateTime = None
            ),
            _2 = context.geomap.unfocus
          )

        case Msg.DeleteDateTimeRange =>
          default.copy(_1 = model.copy(dateTimeRange = None))

        case Msg.DeleteGeolocation =>
          default.copy(_2 = context.geomap.unfocus)

        case Msg.UseMediaDateTime =>
          default.copy(_1 = model.copy(dateTimeRange = model.mediaDateTime))

        case Msg.UseMediaGeolocation =>
          default.copy(_2 = model.mediaLocation match {
            case Some(location) => context.geomap.focus(location)
            case None           => context.geomap
          })
      }
    }

    def sectionEditorOrPreview(
        model: CotoForm.Model,
        onCtrlEnter: () => Unit,
        onFocus: Option[() => Unit] = None
    )(implicit dispatch: Msg => Unit): ReactElement =
      if (model.inPreview)
        sectionPreview(model)
      else
        CotoForm.sectionEditor(
          model = model,
          onFocus = onFocus,
          onCtrlEnter = onCtrlEnter
        )(dispatch)

    def sectionEditor(
        model: CotoForm.Model,
        onCtrlEnter: () => Unit,
        onFocus: Option[() => Unit] = None
    )(implicit dispatch: Msg => Unit): ReactElement =
      section(className := "coto-editor")(
        input(
          className := "summary",
          `type` := "text",
          placeholder := "Summary (optional)",
          value := model.summaryInput,
          onChange := (e => dispatch(Msg.SummaryInput(e.target.value))),
          onKeyDown := (e =>
            if (model.readyToPost && detectCtrlEnter(e)) {
              onCtrlEnter()
            }
          )
        ),
        textarea(
          placeholder := "Write your coto in Markdown",
          value := model.contentInput,
          slinky.web.html.onFocus := onFocus,
          onChange := (e => dispatch(Msg.ContentInput(e.target.value))),
          onKeyDown := (e =>
            if (model.readyToPost && detectCtrlEnter(e)) {
              onCtrlEnter()
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
      )

    def sectionPreview(model: CotoForm.Model): ReactElement =
      section(className := "coto-preview")(
        ScrollArea()(
          model.summary.map(section(className := "summary")(_)),
          div(className := "content")(
            ViewCoto.sectionTextContent(Some(model.content))
          )
        )
      )

    def buttonPreview(
        model: CotoForm.Model
    )(implicit dispatch: Msg => Unit): ReactElement =
      button(
        className := "preview contrast outline",
        disabled := !model.validate.validated,
        onClick := (_ => dispatch(Msg.TogglePreview))
      )(
        if (model.inPreview)
          "Edit"
        else
          "Preview"
      )

    def sectionMediaPreview(
        model: Model
    )(implicit dispatch: Msg => Unit): Option[ReactElement] =
      model.mediaContent match {
        case Some(mediaContent) => {
          val url = dom.URL.createObjectURL(mediaContent)
          Some(
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
          )
        }

        case None => None
      }

    def sectionValidationError(model: Model): ReactElement =
      Validation.sectionValidationError(model.validate)
  }

  object CotonomaForm {
    case class Model(
        nameInput: String = "",
        imeActive: Boolean = false,
        validation: Validation.Result = Validation.Result.notYetValidated,
        error: Option[String] = None
    ) extends Form {
      def hasContents: Boolean = !nameInput.isBlank

      def name: String = nameInput.trim

      def validate(nodeId: Id[Node]): (Model, Cmd.One[Msg]) = {
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

      def readyToPost: Boolean =
        hasContents && validation.validated
    }

    sealed trait Msg

    object Msg {
      case class CotonomaNameInput(name: String) extends Msg
      case object ImeCompositionStart extends Msg
      case object ImeCompositionEnd extends Msg
      case class CotonomaByName(
          name: String,
          result: Either[ErrorJson, Cotonoma]
      ) extends Msg
    }

    def update(msg: Msg, model: Model)(implicit
        context: Context
    ): (Model, Cmd[Msg]) =
      (msg, context.domain.currentCotonoma) match {
        case (Msg.CotonomaNameInput(name), Some(cotonoma)) =>
          model.copy(nameInput = name)
            .validate(cotonoma.nodeId)
            .modify(_._2).using(validation =>
              if (model.imeActive)
                Cmd.none // validation should not be invoked when IME is active
              else
                validation
            )

        case (Msg.ImeCompositionStart, _) =>
          (model.copy(imeActive = true), Cmd.none)

        case (Msg.ImeCompositionEnd, currentCotonoma) =>
          model.copy(imeActive = false).pipe(model =>
            currentCotonoma match {
              case Some(cotonoma) => model.validate(cotonoma.nodeId)
              case None           => (model, Cmd.none)
            }
          )

        case (Msg.CotonomaByName(name, Right(cotonoma)), _) =>
          if (cotonoma.name == model.name)
            (
              model.modify(_.validation).setTo(
                Validation.Error(
                  "cotonoma-already-exists",
                  s"The cotonoma \"${cotonoma.name}\" already exists in this node.",
                  Map("name" -> cotonoma.name, "id" -> cotonoma.id.uuid)
                ).toResult
              ),
              Cmd.none
            )
          else
            (model, Cmd.none)

        case (Msg.CotonomaByName(name, Left(error)), _) =>
          if (name == model.name && error.code == "not-found")
            (model.copy(validation = Validation.Result.validated), Cmd.none)
          else
            (
              model.copy(error =
                Some(s"Validation system error: ${error.default_message}")
              ),
              Cmd.none
            )

        case _ => (model, Cmd.none)
      }

    def inputCotonomaName(
        model: Model,
        onFocus: () => Unit,
        onBlur: () => Unit,
        onCtrlEnter: () => Unit
    )(implicit dispatch: Msg => Unit): ReactElement =
      input(
        className := "cotonoma-name",
        `type` := "text",
        placeholder := "New cotonoma name",
        value := model.nameInput,
        Validation.ariaInvalid(model.validation),
        slinky.web.html.onFocus := onFocus,
        slinky.web.html.onBlur := onBlur,
        onChange := (e => dispatch(Msg.CotonomaNameInput(e.target.value))),
        onCompositionStart := (_ => dispatch(Msg.ImeCompositionStart)),
        onCompositionEnd := (_ => dispatch(Msg.ImeCompositionEnd)),
        onKeyDown := (e =>
          if (model.readyToPost && detectCtrlEnter(e)) {
            onCtrlEnter()
          }
        )
      )
  }

  private def liAttributeDateTimeRange(
      dateTimeRange: Option[DateTimeRange],
      mediaDateTime: Option[DateTimeRange]
  )(implicit
      context: Context,
      dispatch: CotoForm.Msg => Unit
  ): Option[ReactElement] =
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
            _ => dispatch(CotoForm.Msg.UseMediaDateTime)
          )
        },
        Option.when(dateTimeRange.isDefined) {
          divAttributeDelete(_ => dispatch(CotoForm.Msg.DeleteDateTimeRange))
        }
      )
    }

  def ulAttributes(
      dateTimeRange: Option[DateTimeRange],
      mediaDateTime: Option[DateTimeRange],
      location: Option[Geolocation],
      mediaLocation: Option[Geolocation]
  )(implicit
      context: Context,
      dispatch: CotoForm.Msg => Unit
  ): Option[ReactElement] =
    Seq(
      liAttributeDateTimeRange(dateTimeRange, mediaDateTime),
      liAttributeGeolocation(location, mediaLocation)
    ).flatten match {
      case Seq() => None
      case attributes =>
        Some(ul(className := "attributes")(attributes: _*))
    }

  private def liAttributeGeolocation(
      location: Option[Geolocation],
      mediaLocation: Option[Geolocation]
  )(implicit dispatch: CotoForm.Msg => Unit): Option[ReactElement] =
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
            _ => dispatch(CotoForm.Msg.UseMediaGeolocation)
          )
        },
        Option.when(location.isDefined) {
          divAttributeDelete(_ => dispatch(CotoForm.Msg.DeleteGeolocation))
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
}
