package cotoami.subparts

import org.scalajs.dom

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
        validation: Validation.Result = Validation.Result.notYetValidated
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
  }
}
