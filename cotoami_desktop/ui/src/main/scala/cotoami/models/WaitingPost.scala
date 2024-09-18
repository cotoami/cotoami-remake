package cotoami.models

import java.time.Instant
import com.softwaremill.quicklens._

import cotoami.models.Cotonoma
import cotoami.backend.CotoContent

case class WaitingPost(
    postId: String,
    content: Option[String],
    summary: Option[String],
    mediaContent: Option[(String, String)],
    geolocation: Option[Geolocation],
    isCotonoma: Boolean,
    postedIn: Cotonoma,
    error: Option[String] = None
) extends CotoContent

object WaitingPost {
  def newPostId(): String =
    Instant.now().toEpochMilli().toString

  def newCoto(
      postId: String,
      content: String,
      summary: Option[String],
      mediaContent: Option[(String, String)],
      geolocation: Option[Geolocation],
      postedIn: Cotonoma
  ): WaitingPost =
    WaitingPost(
      postId,
      Some(content),
      summary,
      mediaContent,
      geolocation,
      false,
      postedIn
    )

  def newCotonoma(
      postId: String,
      name: String,
      geolocation: Option[Geolocation],
      postedIn: Cotonoma
  ): WaitingPost =
    WaitingPost(postId, None, Some(name), None, geolocation, true, postedIn)
}

case class WaitingPosts(posts: Seq[WaitingPost] = Seq.empty) {
  def isEmpty: Boolean = this.posts.isEmpty

  def add(post: WaitingPost): WaitingPosts =
    this.modify(_.posts).using(post +: _)

  def addCoto(
      postId: String,
      content: String,
      summary: Option[String],
      mediaContent: Option[(String, String)],
      geolocation: Option[Geolocation],
      postedIn: Cotonoma
  ): WaitingPosts =
    this.add(
      WaitingPost.newCoto(
        postId,
        content,
        summary,
        mediaContent,
        geolocation,
        postedIn
      )
    )

  def addCotonoma(
      postId: String,
      name: String,
      geolocation: Option[Geolocation],
      postedIn: Cotonoma
  ): WaitingPosts =
    this.add(WaitingPost.newCotonoma(postId, name, geolocation, postedIn))

  def setError(postId: String, error: String): WaitingPosts =
    this.modify(_.posts.eachWhere(_.postId == postId).error).setTo(
      Some(error)
    )

  def remove(postId: String): WaitingPosts =
    this.modify(_.posts).using(_.filterNot(_.postId == postId))
}
