package cotoami.subparts

import marubinotto.react.{Fragment, ReactNode}

import cotoami.Context
import cotoami.models.Cotonoma

object PartsCotonoma {

  def cotonomaLabel(
      cotonoma: Cotonoma
  )(using context: Context): ReactNode =
    Fragment(
      context.repo.nodes.get(cotonoma.nodeId).map(PartsNode.imgNode(_)),
      cotonoma.name
    )
}
