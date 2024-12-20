package cotoami.subparts

import org.scalajs.dom
import com.softwaremill.quicklens._

import fui.Cmd
import cotoami.utils.Validation
import cotoami.models.{Coto, Cotonoma, DateTimeRange, Geolocation, Id, Node}
import cotoami.backend.{CotonomaBackend, ErrorJson}

object Editor {
  sealed trait Form

  object CotoForm {
    // A top-level heading as the first line will be used as a summary.
    // cf. https://spec.commonmark.org/0.31.2/#atx-headings
    val SummaryPrefix = "# "

    case class Model(
        textContent: String = "",
        mediaContent: Option[dom.Blob] = None,
        mediaLocation: Option[Geolocation] = None,
        mediaDateTime: Option[DateTimeRange] = None,
        dateTimeRange: Option[DateTimeRange] = None
    ) extends Form {
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

      private def hasSummary: Boolean =
        textContent.startsWith(CotoForm.SummaryPrefix)

      private def firstLine = textContent.linesIterator.next()
    }
  }

  object CotonomaForm {
    case class Model(
        nameInput: String = "",
        validation: Validation.Result = Validation.Result.notYetValidated,
        error: Option[String] = None
    ) extends Form {
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
    }

    sealed trait Msg

    object Msg {
      case class CotonomaByName(
          name: String,
          result: Either[ErrorJson, Cotonoma]
      ) extends Msg
    }

    def update(msg: Msg, model: Model): Model =
      msg match {
        case Msg.CotonomaByName(name, Right(cotonoma)) =>
          if (cotonoma.name == model.name)
            model.modify(_.validation).setTo(
              Validation.Error(
                "cotonoma-already-exists",
                s"The cotonoma \"${cotonoma.name}\" already exists in this node.",
                Map("name" -> cotonoma.name, "id" -> cotonoma.id.uuid)
              ).toResult
            )
          else
            model

        case Msg.CotonomaByName(name, Left(error)) =>
          if (name == model.name && error.code == "not-found")
            model.copy(validation = Validation.Result.validated)
          else
            model.copy(error =
              Some(s"Validation system error: ${error.default_message}")
            )

      }
  }
}
