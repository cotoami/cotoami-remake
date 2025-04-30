package cotoami.subparts

import scala.util.chaining._
import scala.scalajs.js
import org.scalajs.dom
import com.softwaremill.quicklens._

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._
import slinky.web.SyntheticMouseEvent

import marubinotto.fui.{Browser, Cmd}
import marubinotto.Validation
import marubinotto.components.{
  materialSymbol,
  toolButton,
  ScrollArea,
  SplitPane
}

import cotoami.Context
import cotoami.models.{Coto, Cotonoma, DateTimeRange, Geolocation, Id, Node}
import cotoami.backend.{CotonomaBackend, ErrorJson}
import cotoami.subparts.SectionGeomap.{Model => Geomap}

object EditorCoto {
  sealed trait Form {
    def hasContents: Boolean
    def hasValidContents: Boolean
  }

  /////////////////////////////////////////////////////////////////////////////
  // CotoForm
  /////////////////////////////////////////////////////////////////////////////

  object CotoForm {
    case class Model(
        isCotonoma: Boolean = false,
        inPreview: Boolean = false,
        summaryInput: String = "",
        contentInput: String = "",
        dateTimeRange: Option[DateTimeRange] = None,
        geolocation: Option[Geolocation] = None,
        mediaBlob: Option[dom.Blob] = None,
        encodingMedia: Boolean = false,
        mediaBase64: Option[(String, String)] = None,
        mediaDateTime: Option[DateTimeRange] = None,
        mediaLocation: Option[Geolocation] = None
    ) extends Form {
      def hasContents: Boolean =
        !summaryInput.isBlank || !contentInput.isBlank || mediaBase64.isDefined

      def summary: Option[String] =
        Option.when(!summaryInput.isBlank())(summaryInput.trim)

      def content: String = contentInput.trim

      def validate: Validation.Result =
        if (summaryInput.isBlank && contentInput.isBlank)
          Validation.Result.notYetValidated
        else {
          val errors =
            summary.map(Coto.validateSummary).getOrElse(Seq.empty) ++
              Coto.validateContent(content, isCotonoma)
          Validation.Result(errors)
        }

      def scanMediaMetadata: Cmd.Batch[Msg] =
        mediaBlob.map(blob =>
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

      def encodeMedia: (Model, Cmd.One[Msg]) =
        mediaBlob.map { blob =>
          (
            copy(encodingMedia = true),
            Browser.encodeAsBase64(blob, true).map {
              case Right(base64) =>
                Msg.MediaContentEncoded(Right((base64, blob.`type`)))
              case Left(e) =>
                Msg.MediaContentEncoded(
                  Left("Media content encoding error.")
                )
            }
          )
        }.getOrElse((this, Cmd.none))

      def hasValidContents: Boolean =
        // validate.validated is not necessarily required for a media-only coto.
        hasContents && !validate.failed

      def isMediaDateTimeNotUsed: Boolean =
        mediaDateTime.isDefined && dateTimeRange != mediaDateTime

      def isMediaLocationNotUsed: Boolean =
        mediaLocation.isDefined && geolocation != mediaLocation
      def isGeomapLocationNotUsed(map: Geomap): Boolean =
        map.focusedLocation.isDefined && geolocation != map.focusedLocation
    }

    object Model {
      def forUpdate(original: Coto): Model =
        CotoForm.Model(
          isCotonoma = original.isCotonoma,
          summaryInput = original.summary.getOrElse(""),
          contentInput = original.content.getOrElse(""),
          dateTimeRange = original.dateTimeRange,
          geolocation = original.geolocation,
          mediaBlob = original.mediaBlob.map(_._1),
          // The content of `mediaBase64` will be used only if the media content has been
          // changed, so let's set dummy data here to avoid the cost of base64-encoding
          // `mediaBlob` and just to denote that the coto has some media content
          // (cf. `CotoForm.Model.hasContents`).
          mediaBase64 = original.mediaBlob.map { case (_, t) => ("", t) }
        )
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
      case class MediaContentEncoded(result: Either[String, (String, String)])
          extends Msg
      case object DeleteMediaContent extends Msg
      case object DeleteDateTimeRange extends Msg
      case object DeleteGeolocation extends Msg
      case object UseMediaDateTime extends Msg
      case object UseGeomapLocation extends Msg
      case object UseMediaLocation extends Msg
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
          model
            .modify(_.mediaBlob).setTo(Some(file))
            .modify(_.mediaLocation).setTo(None)
            .encodeMedia
            .pipe { case (model, encodeMedia) =>
              default.copy(
                _1 = model,
                _3 = model.scanMediaMetadata :+ encodeMedia
              )
            }

        case Msg.ExifLocationDetected(Right(location)) =>
          default.copy(
            _1 = model.copy(
              mediaLocation = location,
              geolocation = model.geolocation.orElse(location)
            ),
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

        case Msg.MediaContentEncoded(Right(mediaBase64)) =>
          default.copy(
            _1 = model.copy(
              encodingMedia = false,
              mediaBase64 = Some(mediaBase64)
            )
          )

        case Msg.MediaContentEncoded(Left(e)) => {
          println(e)
          update(Msg.DeleteMediaContent, model)
        }

        case Msg.DeleteMediaContent =>
          default.copy(
            _1 = model.copy(
              mediaBlob = None,
              encodingMedia = false,
              mediaBase64 = None,
              mediaLocation = None,
              mediaDateTime = None
            ),
            _2 = context.geomap.unfocus
          )

        case Msg.DeleteDateTimeRange =>
          default.copy(_1 = model.copy(dateTimeRange = None))

        case Msg.DeleteGeolocation =>
          default.copy(_1 = model.copy(geolocation = None))

        case Msg.UseMediaDateTime =>
          default.copy(_1 = model.copy(dateTimeRange = model.mediaDateTime))

        case Msg.UseGeomapLocation =>
          default.copy(_1 =
            model.copy(geolocation = context.geomap.focusedLocation)
          )

        case Msg.UseMediaLocation =>
          default.copy(
            _1 = model.copy(geolocation = model.mediaLocation),
            _2 = model.mediaLocation match {
              case Some(location) => context.geomap.focus(location)
              case None           => context.geomap
            }
          )
      }
    }

    def apply(
        form: Model,
        onCtrlEnter: Option[() => Unit] = None,
        onFocus: Option[() => Unit] = None,
        vertical: Boolean = false
    )(implicit
        context: Context,
        dispatch: Msg => Unit
    ): ReactElement = {
      val editor = Fragment(
        sectionEditorOrPreview(form, onCtrlEnter, onFocus),
        ulAttributes(form),
        sectionValidationError(form)
      )
      div(className := "coto-form")(
        sectionMediaPreview(form) match {
          case Some(mediaPreview) =>
            SplitPane(
              vertical = vertical,
              initialPrimarySize = 300,
              primary = SplitPane.Primary.Props()(mediaPreview),
              secondary = SplitPane.Secondary.Props()(editor)
            )
          case None => editor
        }
      )
    }

    def sectionEditorOrPreview(
        form: Model,
        onCtrlEnter: Option[() => Unit] = None,
        onFocus: Option[() => Unit] = None,
        enableImageInput: Boolean = true
    )(implicit context: Context, dispatch: Msg => Unit): ReactElement =
      if (form.inPreview)
        sectionPreview(form)
      else
        sectionEditor(form, onCtrlEnter, onFocus, enableImageInput)

    def sectionEditor(
        form: Model,
        onCtrlEnter: Option[() => Unit] = None,
        onFocus: Option[() => Unit] = None,
        enableImageInput: Boolean = true
    )(implicit context: Context, dispatch: Msg => Unit): ReactElement =
      section(className := "coto-editor fill")(
        Option.when(!form.isCotonoma) {
          input(
            className := "summary",
            `type` := "text",
            placeholder := context.i18n.text.EditorCoto_placeholder_summary,
            value := form.summaryInput,
            onChange := (e => dispatch(Msg.SummaryInput(e.target.value))),
            onKeyDown := (e =>
              if (form.hasValidContents && detectCtrlEnter(e)) {
                onCtrlEnter.map(_())
              }
            )
          )
        },
        textarea(
          placeholder := (if (form.isCotonoma)
                            context.i18n.text.EditorCoto_placeholder_cotonomaContent
                          else context.i18n.text.EditorCoto_placeholder_coto),
          value := form.contentInput,
          slinky.web.html.onFocus := onFocus,
          onChange := (e => dispatch(Msg.ContentInput(e.target.value))),
          onKeyDown := (e =>
            if (form.hasValidContents && detectCtrlEnter(e)) {
              onCtrlEnter.map(_())
            }
          )
        ),
        Option.when(enableImageInput) {
          div(className := "input-image")(
            InputFile(
              accept = js.Dictionary("image/*" -> js.Array[String]()),
              message = context.i18n.text.EditorCoto_inputImage,
              onSelect = file => dispatch(Msg.FileInput(file))
            )
          )
        }
      )

    def sectionPreview(form: Model): ReactElement =
      section(className := "coto-preview fill")(
        ScrollArea()(
          Option.when(!form.isCotonoma) {
            form.summary.map(section(className := "summary")(_))
          },
          div(className := "content")(
            PartsCoto.sectionTextContent(Some(form.content))
          )
        )
      )

    def buttonPreview(
        form: Model
    )(implicit dispatch: Msg => Unit): ReactElement =
      button(
        className := "preview contrast outline",
        disabled := !form.validate.validated,
        onClick := (_ => dispatch(Msg.TogglePreview))
      )(
        if (form.inPreview)
          "Edit"
        else
          "Preview"
      )

    def sectionMediaPreview(
        form: Model,
        enableDelete: Boolean = true
    )(implicit dispatch: Msg => Unit): Option[ReactElement] =
      form.mediaBlob.map { blob =>
        val url = dom.URL.createObjectURL(blob)
        section(className := "media-preview fill")(
          div(className := "media-content")(
            img(
              src := url,
              onLoad := (_ => dom.URL.revokeObjectURL(url))
            ),
            Option.when(enableDelete) {
              toolButton(
                symbol = "close",
                tip = Some("Delete"),
                classes = "delete",
                disabled = form.encodingMedia,
                onClick = _ => dispatch(Msg.DeleteMediaContent)
              )
            }
          )
        )
      }

    def sectionValidationError(form: Model): ReactElement =
      Validation.sectionValidationError(form.validate)

    def ulAttributes(
        form: Model
    )(implicit
        context: Context,
        dispatch: CotoForm.Msg => Unit
    ): Option[ReactElement] =
      Seq(
        liAttributeDateTimeRange(form),
        liAttributeGeolocation(form)
      ).flatten match {
        case Seq() => None
        case attributes =>
          Some(ul(className := "attributes")(attributes: _*))
      }

    private def liAttributeDateTimeRange(form: Model)(implicit
        context: Context,
        dispatch: CotoForm.Msg => Unit
    ): Option[ReactElement] =
      Option.when(
        form.dateTimeRange.isDefined || form.mediaDateTime.isDefined
      ) {
        li(className := "attribute time-range")(
          div(className := "attribute-name")(
            materialSymbol("calendar_month"),
            context.i18n.text.EditorCoto_date
          ),
          div(className := "attribute-value")(
            form.dateTimeRange.map(range =>
              context.time.formatDateTime(range.start)
            )
          ),
          div(className := "from-buttons")(
            Option.when(form.isMediaDateTimeNotUsed) {
              toolButton(
                classes = "from-image",
                symbol = "image",
                tip = Some("From Image"),
                tipPlacement = "left",
                onClick = (_ => dispatch(CotoForm.Msg.UseMediaDateTime))
              )
            }
          ),
          Option.when(form.dateTimeRange.isDefined) {
            divAttributeDelete(_ => dispatch(CotoForm.Msg.DeleteDateTimeRange))
          }
        )
      }

    private def liAttributeGeolocation(
        form: Model
    )(implicit
        context: Context,
        dispatch: CotoForm.Msg => Unit
    ): Option[ReactElement] =
      Option.when(
        form.geolocation.isDefined || form.mediaLocation.isDefined || context.geomap.focusedLocation.isDefined
      ) {
        li(className := "attribute geolocation")(
          div(className := "attribute-name")(
            materialSymbol("location_on"),
            context.i18n.text.EditorCoto_location
          ),
          div(className := "attribute-value")(
            form.geolocation.map(location =>
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
          div(className := "from-buttons")(
            Option.when(form.isGeomapLocationNotUsed(context.geomap)) {
              toolButton(
                classes = "from-map",
                symbol = "public",
                tip = Some("From Map"),
                tipPlacement = "left",
                onClick = (_ => dispatch(CotoForm.Msg.UseGeomapLocation))
              )
            },
            Option.when(form.isMediaLocationNotUsed) {
              toolButton(
                classes = "from-image",
                symbol = "image",
                tip = Some("From Image"),
                tipPlacement = "left",
                onClick = (_ => dispatch(CotoForm.Msg.UseMediaLocation))
              )
            }
          ),
          Option.when(form.geolocation.isDefined) {
            divAttributeDelete(_ => dispatch(CotoForm.Msg.DeleteGeolocation))
          }
        )
      }

    private def divAttributeDelete(
        onClick: SyntheticMouseEvent[_] => Unit
    )(implicit context: Context): ReactElement =
      div(className := "attribute-delete")(
        toolButton(
          symbol = "close",
          tip = Some(context.i18n.text.Delete),
          tipPlacement = "left",
          classes = "delete",
          onClick = onClick
        )
      )
  }

  /////////////////////////////////////////////////////////////////////////////
  // CotonomaForm
  /////////////////////////////////////////////////////////////////////////////

  object CotonomaForm {
    case class Model(
        originalName: Option[String] = None,
        nameInput: String = "",
        imeActive: Boolean = false,
        validation: Validation.Result = Validation.Result.notYetValidated,
        error: Option[String] = None
    ) extends Form {
      def hasContents: Boolean = !nameInput.isBlank

      def name: String = nameInput.trim

      def isNew: Boolean = originalName.isEmpty

      def edited: Boolean = originalName match {
        case Some(original) => name != original
        case None           => !name.isEmpty()
      }

      def validate(nodeId: Id[Node]): (Model, Cmd.One[Msg]) = {
        val (validation, cmd) =
          if (edited)
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
          else
            (Validation.Result.notYetValidated, Cmd.none)

        (copy(validation = validation), cmd)
      }

      def hasValidContents: Boolean =
        hasContents && (Some(name) == originalName || validation.validated)

      // CotonomaForm doesn't support media input
      val mediaDateTime: Option[DateTimeRange] = None
      val mediaLocation: Option[Geolocation] = None
    }

    object Model {
      def forUpdate(originalName: String): Model = Model(
        originalName = Some(originalName),
        nameInput = originalName
      )

      def withDefault(
          defaultName: String,
          nodeId: Id[Node]
      ): (Model, Cmd[Msg]) =
        Model(originalName = None, nameInput = defaultName)
          .validate(nodeId)
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
      (msg, context.repo.currentCotonoma) match {
        case (Msg.CotonomaNameInput(name), Some(cotonoma)) =>
          model.copy(nameInput = name)
            .validate(cotonoma.nodeId)
            .modify(_._2).using(validation =>
              if (model.imeActive)
                Cmd.none // validation should not be invoked when IME is active
              else
                validation.debounce("CotonomaForm.validation", 200)
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
                  context.i18n.text.EditorCoto_cotonomaAlreadyExists(
                    cotonoma.name
                  ),
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

    def inputName(
        model: Model,
        onFocus: Option[() => Unit] = None,
        onBlur: Option[() => Unit] = None,
        onCtrlEnter: Option[() => Unit] = None
    )(implicit context: Context, dispatch: Msg => Unit): ReactElement =
      input(
        className := "cotonoma-name",
        `type` := "text",
        placeholder := (if (model.isNew)
                          context.i18n.text.EditorCoto_placeholder_newCotonomaName
                        else
                          context.i18n.text.EditorCoto_placeholder_cotonomaName),
        value := model.nameInput,
        Validation.ariaInvalid(model.validation),
        slinky.web.html.onFocus := onFocus,
        slinky.web.html.onBlur := onBlur,
        onChange := (e => dispatch(Msg.CotonomaNameInput(e.target.value))),
        onCompositionStart := (_ => dispatch(Msg.ImeCompositionStart)),
        onCompositionEnd := (_ => dispatch(Msg.ImeCompositionEnd)),
        onKeyDown := (e =>
          if (model.hasValidContents && detectCtrlEnter(e)) {
            onCtrlEnter.map(_())
          }
        )
      )
  }
}
