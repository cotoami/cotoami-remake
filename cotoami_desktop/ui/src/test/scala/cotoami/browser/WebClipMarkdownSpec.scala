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
        "https://example.com/a path/paren)"
      )
    )

    assert(
      link == "[A \\[tricky\\] title with newline](https://example.com/a%20path/paren%29)"
    )
  }
}
