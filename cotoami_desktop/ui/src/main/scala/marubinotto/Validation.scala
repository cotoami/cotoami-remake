package marubinotto

import java.net.{URI, URISyntaxException}
import com.softwaremill.quicklens._

import slinky.core.AttrPair
import slinky.core.facade.ReactElement
import slinky.web.html._

object Validation {
  case class Error(
      code: String,
      defaultMessage: String,
      params: Map[String, String] = Map.empty
  ) {
    def toResult: Result = Validation.Result(this)
  }

  case class Result(errors: Option[Seq[Validation.Error]]) {
    def notYetValidated: Boolean = errors.isEmpty

    def validated: Boolean =
      errors.map(_.isEmpty).getOrElse(false)

    def failed: Boolean = firstError.isDefined

    def firstError: Option[Validation.Error] =
      errors.flatMap(_.headOption)

    def addError(error: Validation.Error): Result =
      addErrors(Seq(error))

    def addErrors(errors: Seq[Validation.Error]): Result =
      this.modify(_.errors).using(
        _ match {
          case Some(existing) => Some(existing ++ errors)
          case None           => Some(errors)
        }
      )
  }

  object Result {
    def apply(error: Validation.Error): Result =
      Result(Some(Seq(error)))

    def apply(errors: Seq[Validation.Error]): Result =
      Result(Some(errors))

    lazy val notYetValidated: Result = Result(None)

    lazy val validated: Result = Result(Seq.empty)
  }

  def nonBlank(name: String, value: String): Option[Error] =
    if (value.isBlank())
      Some(
        Error(
          "blank",
          s"${name.capitalize} must not be blank.",
          Map("name" -> name)
        )
      )
    else
      None

  def length(name: String, value: String, min: Int, max: Int): Option[Error] =
    if (value.length() < min)
      Some(
        Error(
          "length-min",
          s"${name.capitalize} must be longer than ${min}.",
          Map("name" -> name, "min" -> min.toString())
        )
      )
    else if (value.length() > max)
      Some(
        Error(
          "length-max",
          s"${name.capitalize} must be shorter than ${max}.",
          Map("name" -> name, "max" -> max.toString())
        )
      )
    else
      None

  def url(name: String, value: String): Either[Error, URI] =
    try {
      Right(new URI(value))
    } catch {
      case e: URISyntaxException =>
        Left(
          Error(
            "invaid-url",
            s"${name.capitalize} must be in valid URL format."
          )
        )
      case e: Throwable => Left(Error("system-error", e.toString()))
    }

  val HttpSchemes = Seq("http", "https")

  def httpUrl(name: String, value: String): Option[Error] =
    url(name, value) match {
      case Right(url) =>
        if (HttpSchemes.contains(url.getScheme()))
          None
        else
          Some(
            Error(
              "non-http-url",
              s"${name.capitalize} must be an HTTP or HTTPS URL."
            )
          )
      case Left(e) => Some(e)
    }

  private val UuidRegex =
    "^[0-9a-fA-F]{8}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{12}$".r

  def uuid(name: String, value: String): Option[Error] =
    UuidRegex.findFirstIn(value) match {
      case Some(_) => None
      case None =>
        Some(
          Error(
            "non-uuid",
            s"${name.capitalize} must be a valid UUID."
          )
        )
    }

  def ariaInvalid(result: Validation.Result): AttrPair[input.tagType] = {
    (aria - "invalid") :=
      (if (result.notYetValidated)
         ""
       else if (result.validated)
         "false"
       else
         "true")
  }

  def sectionValidationError(result: Validation.Result): ReactElement =
    result.firstError.map(e =>
      section(className := "validation-error")(
        e.defaultMessage
      )
    )
}
