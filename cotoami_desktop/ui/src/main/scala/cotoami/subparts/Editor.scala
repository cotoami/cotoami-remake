package cotoami.subparts

import scala.util.chaining._
import org.scalajs.dom
import com.softwaremill.quicklens._

import fui.Cmd
import cotoami.Context
import cotoami.utils.Validation
import cotoami.models.{Coto, Cotonoma, DateTimeRange, Geolocation, Id, Node}
import cotoami.backend.{CotonomaBackend, ErrorJson}
import cotoami.subparts.SectionGeomap.{Model => Geomap}

object Editor {
  sealed trait Form {
    def hasContents: Boolean
    def readyToPost: Boolean
  }

  object CotoForm {
    // A top-level heading as the first line will be used as a summary.
    // cf. https://spec.commonmark.org/0.31.2/#atx-headings
    val SummaryPrefix = "# "

    case class Model(
        textContent: String = "",
        mediaContent: Option[dom.Blob] = None,
        mediaLocation: Option[Geolocation] = None,
        mediaDateTime: Option[DateTimeRange] = None,
        dateTimeRange: Option[DateTimeRange] = None,
        error: Option[String] = None
    ) extends Form {
      def hasContents: Boolean =
        !textContent.isBlank || mediaContent.isDefined

      def summary: Option[String] =
        if (hasSummary)
          Some(firstLine.stripPrefix(SummaryPrefix).trim)
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

      def readyToPost: Boolean =
        hasContents && (validate.validated || mediaContent.isDefined)

      private def hasSummary: Boolean =
        textContent.startsWith(CotoForm.SummaryPrefix)

      private def firstLine = textContent.linesIterator.next()
    }

    sealed trait Msg

    object Msg {
      case class TextContentInput(content: String) extends Msg
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
        case Msg.TextContentInput(content) =>
          default.copy(_1 = model.copy(textContent = content))

        case Msg.FileInput(file) =>
          default.copy(
            _1 = model.copy(mediaContent = Some(file), mediaLocation = None),
            _3 = Cmd.Batch(
              Geolocation.fromExif(file).map {
                case Right(location) =>
                  Msg.ExifLocationDetected(Right(location))
                case Left(t) => Msg.ExifLocationDetected(Left(t.toString))
              },
              DateTimeRange.fromExif(file).map {
                case Right(timeRange) =>
                  Msg.ExifDateTimeDetected(Right(timeRange))
                case Left(t) => Msg.ExifDateTimeDetected(Left(t.toString))
              }
            )
          )

        case Msg.ExifLocationDetected(Right(location)) =>
          default.copy(
            _1 = model.copy(mediaLocation = location),
            _2 = location.map(context.geomap.focus)
              .getOrElse(context.geomap.unfocus)
          )

        case Msg.ExifLocationDetected(Left(error)) =>
          default.copy(
            _1 = model.copy(error = Some(s"Location detection error: ${error}"))
          )

        case Msg.ExifDateTimeDetected(Right(dateTime)) =>
          default.copy(
            _1 = model.copy(
              dateTimeRange = dateTime,
              mediaDateTime = dateTime
            )
          )

        case Msg.ExifDateTimeDetected(Left(error)) =>
          default.copy(_1 =
            model.copy(error = Some(s"DateTime detection error: ${error}"))
          )

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
      }
  }
}
