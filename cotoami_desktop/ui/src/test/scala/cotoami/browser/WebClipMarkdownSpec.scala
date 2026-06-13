package cotoami.browser

import org.scalatest.funsuite.AnyFunSuite

class WebClipMarkdownSpec extends AnyFunSuite {
  test("converts common HTML fragments to Markdown with a source link") {
    val markdown = WebClipMarkdown.fromSelection(
      """<h2>Heading</h2><p>Hello <strong>world</strong>.</p><ul><li>One</li><li>Two</li></ul>""",
      "Heading\nHello world.\nOne\nTwo",
      WebClipMarkdown.Source(Some("Example Page"), "https://example.com/page")
    )

    assert(markdown.contains("## Heading"))
    assert(markdown.contains("Hello **world**."))
    assert(markdown.contains("- One"))
    assert(markdown.endsWith("[Example Page](https://example.com/page)"))
  }

  test("falls back to selected text when converted HTML is blank") {
    val markdown = WebClipMarkdown.fromSelection(
      "",
      "Selected text",
      WebClipMarkdown.Source(Some("Example"), "https://example.com/")
    )

    assert(markdown == "Selected text\n\n[Example](https://example.com/)")
  }

  test("escapes source link titles") {
    val link = WebClipMarkdown.sourceLink(
      WebClipMarkdown.Source(
        Some("A [tricky] title\nwith newline"),
        "https://example.com/a path/(paren)"
      )
    )

    assert(
      link == "[A \\[tricky\\] title with newline](https://example.com/a%20path/%28paren%29)"
    )
  }

  test("escapes source URLs with balanced parentheses") {
    val link = WebClipMarkdown.sourceLink(
      WebClipMarkdown.Source(
        Some("William Adams (samurai) - Wikipedia"),
        "https://en.wikipedia.org/wiki/William_Adams_(samurai)"
      )
    )

    assert(
      link == "[William Adams (samurai) - Wikipedia](https://en.wikipedia.org/wiki/William_Adams_%28samurai%29)"
    )
  }

  test("converts relative image URLs to absolute URLs") {
    val markdown = WebClipMarkdown.fromSelection(
      """<p><img alt="Logo" src="../images/logo.png"></p>""",
      "",
      WebClipMarkdown.Source(
        Some("Example"),
        "https://example.com/docs/page/index.html"
      )
    )

    assert(markdown.contains("![Logo](https://example.com/docs/images/logo.png)"))
  }

  test("converts relative link URLs to absolute URLs") {
    val markdown = WebClipMarkdown.fromSelection(
      """<p>Read <a href="/docs/intro?from=clip">the intro</a>.</p>""",
      "Read the intro.",
      WebClipMarkdown.Source(
        Some("Example"),
        "https://example.com/docs/page/index.html"
      )
    )

    assert(
      markdown.contains("[the intro](https://example.com/docs/intro?from=clip)")
    )
  }
}
