package cotoami.browser

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{literal => jso}

import org.scalajs.dom

import marubinotto.libs.unified.Unified
import marubinotto.libs.unified.htmlToMarkdownPlugins

object WebClipMarkdown {
  case class Source(title: Option[String], url: String)

  private def processor(baseUrl: String) =
    Unified
      .unified()
      .use(htmlToMarkdownPlugins.RehypeParse, jso(fragment = true))
      .use(absolutizeUrlAttributesPlugin(baseUrl))
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
    val markdown = normalize(convertHtml(selectedHtml, source.url))
    val text = normalize(selectedText)
    val body =
      if (markdown.isBlank()) text
      else if (conversionLooksWorse(markdown, text)) text
      else markdown
    Seq(body, sourceLink(source)).filterNot(_.isBlank()).mkString("\n\n")
  }

  private def convertHtml(html: String, baseUrl: String): String =
    if (html.isBlank()) ""
    else
      try {
        processor(baseUrl).processSync(html).toString()
      } catch {
        case e: Throwable =>
          println(s"HTML to Markdown conversion failed: ${e.toString}")
          ""
      }

  private def absolutizeUrlAttributesPlugin(baseUrl: String): js.Function0[js.Function1[js.Dynamic, Unit]] =
    () =>
      (tree: js.Dynamic) => {
        def visit(node: js.Dynamic): Unit = {
          if (!js.isUndefined(node.properties))
            urlAttributesFor(node).foreach(attribute =>
              if (!js.isUndefined(node.properties.selectDynamic(attribute)))
                resolveUrl(
                  node.properties.selectDynamic(attribute).asInstanceOf[String],
                  baseUrl
                ).foreach(node.properties.updateDynamic(attribute)(_))
            )

          if (!js.isUndefined(node.children))
            node.children
              .asInstanceOf[js.Array[js.Dynamic]]
              .foreach(visit)
        }
        visit(tree)
      }

  private def urlAttributesFor(node: js.Dynamic): Seq[String] =
    if (js.isUndefined(node.tagName)) Seq.empty
    else
      node.tagName.asInstanceOf[String] match {
        case "a" | "area" | "link" => Seq("href")
        case "img" | "source" | "video" | "audio" | "track" | "iframe" |
            "embed" | "script" =>
          Seq("src")
        case "object" => Seq("data")
        case _ => Seq.empty
      }

  private def resolveUrl(url: String, baseUrl: String): Option[String] =
    Option(url)
      .map(_.trim)
      .filter(_.nonEmpty)
      .flatMap(url =>
        try {
          Some(new dom.URL(url, baseUrl).href)
        } catch {
          case _: Throwable => None
        }
      )

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
