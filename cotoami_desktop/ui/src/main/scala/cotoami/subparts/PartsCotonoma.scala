package cotoami.subparts

import slinky.core.facade.{Fragment, ReactElement}

import cotoami.Context
import cotoami.models.Cotonoma

object PartsCotonoma {

  def cotonomaLabel(
      cotonoma: Cotonoma
  )(using context: Context): ReactElement =
    Fragment(
      context.repo.nodes.get(cotonoma.nodeId).map(PartsNode.imgNode(_)),
      cotonoma.name
    )
}
