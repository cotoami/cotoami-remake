<p align="center"><img src="docs/images/logo.png" alt="Cotoami Remake" height="250px"></p>

Cotoami is a standalone, cross-platform note-taking application designed to help you organize scattered information into meaningful knowledge—entirely on your own device, with full offline support.

Cotoami structures your input as “Cotos” (units of information) and connects them using “Itos” (links between Cotos). By building these connections, you transform isolated notes into an interconnected web of knowledge.

One unique feature is the built-in world map, allowing you to associate any information with geographic locations—even when offline, since the map is fully included within the app (powered by [OpenStreetMap](https://openstreetmap.org) and [Protomaps](https://github.com/protomaps/basemaps)).

While Cotoami Remake is designed for private, offline use, it also offers a powerful collaboration option: you can connect to other users’ databases online and cooperatively edit knowledge as a team.

> Cotoami Remake is a complete reimagining of the original [Cotoami](https://github.com/cotoami/cotoami) web application, rebuilt from the ground up as a standalone desktop app for individual users who want a simple, private note-taking experience. While it retains the core concept of its predecessor, Cotoami Remake introduces entirely new features—such as integration with a built-in map and the ability to connect databases across users—making it a fundamentally different application.

[main screeshot]

## Basic Usage

In Cotoami, you can casually post anything you want to remember—just like sending a chat message or writing a microblog. Each Coto you post is saved to your timeline.

[screeshot 1]

> You can write your Cotos using Markdown. Cotoami supports [GitHub Flavored Markdown](https://github.github.com/gfm/), allowing for familiar formatting options like tables, task lists, and more. In addition, code blocks support syntax highlighting for a wide range of programming languages.

If you find certain Cotos especially important, you can “pin” them to the Stock area for quick access.

[screeshot 2]

By linking related Cotos together with Itos, you can build a hierarchical structure of content, starting from any pinned Coto.

[screeshot 3]

Cotos in the Stock area can be freely reordered within the same level, letting you arrange your key information just the way you like.

[screeshot 4]

## Core Concept

![](docs/images/core-concept.png)

In Cotoami, each individual post—or unit of information—is called a **Coto** (the Japanese word for “thing”). There is also a special type of Coto called a **Cotonoma** (Coto-no-ma means “a space of Cotos”). From an organizational perspective, a Cotonoma acts like a folder or category; from a communication perspective, it can be thought of as a chat room.

Each Cotonoma maintains its own timeline, where Cotos posted within it are collected. Since a Cotonoma is itself a kind of Coto, you can also link it to other Cotos or Cotonoma using **Itos** (the Japanese word for “thread”). Among these links, those starting from a Cotonoma are called **Pins**.

In summary, a Cotonoma is a container for two types of information:

* The “flow” of Cotos collected in its timeline
* The “stock” of Cotos pinned and organized hierarchically via Pins

This dual structure lets you both capture the stream of new information and organize key items for easy reference.

## The Knowledge Growth Cycle

One of the most important features of Cotoami is the ability to “promote” a Coto into a Cotonoma. This makes it possible to organically grow new Cotonomas as your knowledge develops, directly from existing information. Think of Cotonomas as entries in the catalog of your knowledge base.

[Insert screenshot of the feature here]

The following diagram was created when I first came up with the idea for Cotoami’s structure:

![](docs/images/growth-cycle.png)

As illustrated above, the typical knowledge growth cycle in Cotoami works like this:

1. **Post Cotos to a Cotonoma**: Start by posting Cotos relevant to a certain Cotonoma.
2. **Connect Cotos with Itos**: As you add more Cotos, you may find relationships between them. Use Itos to build structures and connections.
3. **Promote a Coto to a Cotonoma**: When a particular Coto becomes central—often because it has many connections (Itos)—you can promote it to a Cotonoma. This allows you to dig deeper into that topic, giving it its own timeline and structure.
4. **Repeat the Process**: Inside the new Cotonoma, you can start the cycle again from step 1.

Not every Cotonoma needs to be created through this cycle—you can also create a new Cotonoma as a category right from the start if you already have something in mind. But if you only do that, your list of Cotonomas will be limited to what you already know or expect. By following the cycle above, you’ll discover new and unexpected Cotonomas as your knowledge grows. This process of “discovery” is what the knowledge growth cycle is all about.

### Repost

[screeshot]

By default, each Coto belongs to the Cotonoma in which it was originally posted. But what if you realize that a Coto is also relevant to another Cotonoma? One way is to connect it with an Ito from that Cotonoma, indicating a direct relationship. However, if the connection isn’t strong enough to justify an Ito, you can use the Repost feature to post the Coto again on the timeline of a different Cotonoma. This allows a single Coto to belong to multiple Cotonomas through reposting. The Repost function becomes even more important when working with the networked database environment, which will be described later.

## Database Networking

<p><img src="docs/images/distributed-graph.png" alt="Distributed coto graph" height="600px"></p>
