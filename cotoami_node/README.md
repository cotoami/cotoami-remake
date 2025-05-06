# Cotoami Node Server

Cotoami Node Server provides a Cotoami database that is intended to be accessed remotely. Applications like Cotoami Desktop or other Cotoami Nodes connect to this Node Server via HTTP or HTTPS and incorporate its contents as part of their own databases. Once connected, any changes to the database are propagated to other nodes in real time through WebSocket or Server-Sent Events. With Cotoami Node Server, collaborative work becomes possible by sharing a common database.

<p><img src="../docs/images/distributed-graph.png" alt="Distributed coto graph" height="600px"></p>

A unique aspect of the Cotoami Node network is the separation between the roles defined at the network level (client/server) and those defined at the database level (child/parent). In terms of connection, the initiator of a connection is referred to as the *client*, and the recipient is the *server*. Meanwhile, in terms of database relationships, the node that pulls in data is called the *child*, and the one whose data is being pulled is the *parent*. These roles are independent, which allows configurations where the Server acts as a child, rather than always as a parent.

[image]

> Note: While this separation of roles is already implemented internally, the current version only supports configurations where the Server takes on the Parent role. Future versions will support role reversal to enable more flexible configurations.
