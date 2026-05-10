package cotoami.browser

import scala.scalajs.js.Dynamic.{literal => jso}

import org.scalajs.dom

import marubinotto.libs.unified.Unified
import marubinotto.libs.unified.htmlToMarkdownPlugins

object WebClipMarkdown {
  case class Source(title: Option[String], url: String)

  private lazy val processor =
    Unified
      .unified()
      .use(htmlToMarkdownPlugins.RehypeParse, jso(fragment = true))
      .use(htmlToMarkdownPlugins.RehypeRemark)
      .use(
        htmlToMarkdownPlugins.RemarkStringify,
        jso(
          bullet = "-",
          fences = true,
          rule = "-"
        )
      )

  def fromSelection(
      selectedHtml: String,
      selectedText: String,
      source: Source
  ): String = {
    val markdown = normalize(convertHtml(selectedHtml))
    val text = normalize(selectedText)
    val body =
      if (markdown.isBlank()) text
      else if (conversionLooksWorse(markdown, text)) text
      else markdown
    Seq(body, sourceLink(source)).filterNot(_.isBlank()).mkString("\n\n")
  }

  private def convertHtml(html: String): String =
    if (html.isBlank()) ""
    else
      try {
        processor.processSync(html).toString()
      } catch {
        case e: Throwable =>
          println(s"HTML to Markdown conversion failed: ${e.toString}")
          ""
      }

  private def conversionLooksWorse(markdown: String, text: String): Boolean = {
    if (text.isBlank()) false
    else {
      val markdownPlain = markdown
        .replaceAll("""!\[[^\]]*\]\([^)]+\)""", "")
        .replaceAll("""\[([^\]]+)\]\([^)]+\)""", "$1")
        .replaceAll("""[`*_>#-]""", "")
        .replaceAll("""\s+""", " ")
        .trim
      val textPlain = text.replaceAll("""\s+""", " ").trim
      markdownPlain.isBlank() ||
      markdownPlain.length > textPlain.length * 4 ||
      duplicatedPlainText(markdownPlain, textPlain)
    }
  }

  private def duplicatedPlainText(markdownPlain: String, textPlain: String) =
    textPlain.nonEmpty &&
      markdownPlain.length >= textPlain.length * 2 &&
      markdownPlain.replace(textPlain, "").length <= markdownPlain.length / 5

  def normalize(value: String): String =
    value
      .replace("\r\n", "\n")
      .replace("\r", "\n")
      .replaceAll("""\n{3,}""", "\n\n")
      .trim

  def sourceLink(source: Source): String = {
    val label = source.title
      .map(normalize)
      .filter(_.nonEmpty)
      .getOrElse(fallbackLabel(source.url))
    s"[${escapeLinkText(label)}](${escapeUrl(source.url)})"
  }

  private def fallbackLabel(url: String): String =
    try {
      val parsed = new dom.URL(url)
      Option(parsed.host).filter(_.nonEmpty).getOrElse(url)
    } catch {
      case _: Throwable => url
    }

  def escapeLinkText(text: String): String =
    text
      .replace("\\", "\\\\")
      .replace("[", "\\[")
      .replace("]", "\\]")
      .replace("\n", " ")

  private def escapeUrl(url: String): String =
    url.replace(")", "%29").replace(" ", "%20")
}
