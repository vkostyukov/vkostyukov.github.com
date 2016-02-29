---
layout: post
title:  "How Fast is Finch?"
date:   2016-02-29 9:00:00
categories: scala, fp, finch
disqus: true
---

Turns out I've never mentioned anything about [Finch][finch], a library I'm working on most of my
free time, in my personal blog. So I decided to finally fix that and write a small note on what I
think about Finch's performance as an HTTP library/server. To be more precise, I want to comment
[the most recent results of the TechEmpower benchmark][te-results] and perhaps, give some
insights on why Finch is ranked so high there.

A couple of days ago, results from the most recent run of the TechEmpower benchmark were published.
While I was expecting Finch to perform well there, I didn't expect it to be the second fastest HTTP
library written in Scala.

<blockquote align="center" class="twitter-tweet" data-lang="en"><p lang="en" dir="ltr">Impressive results by <a href="https://twitter.com/hashtag/Finch?src=hash">#Finch</a> (now <a href="https://twitter.com/hashtag/Scala?src=hash">#Scala</a> 2nd fastest HTTP library) running <a href="https://twitter.com/techempower">@techempower</a> benchmark (430k QPS peak): <a href="https://t.co/YeBMnJeQ5W">pic.twitter.com/YeBMnJeQ5W</a></p>&mdash; Vladimir Kostyukov (@vkostyukov) <a href="https://twitter.com/vkostyukov/status/703374308056309760">February 27, 2016</a></blockquote>
<script async src="//platform.twitter.com/widgets.js" charset="utf-8"></script>

With that said, I'll go ahead and answer my own question in the title of this post, "How Fast is
Finch?". Looking at the chart, it's obvious that <strong>Finch is fast enough</strong> to perform
really well on 99.99% of your business problems. At least, in comparison with other Scala libraries.

The most interesting part of this discussion is trying to understand why it performs so well? Why
that insane level of indirections, Finch involves on top of Finagle services, doesn't add much
overhead? The quick answer would be: Finch owns most of its high performance to the _fast_ and
_battle-proven_ libraries it depends on (Finagle, Circe, Cats and Shapeless). The secret recipe is
quite simple - take <strong>fast components</strong> (no matter functional or imperative) and glue
them together using the <strong>rock-solid (pure) functional abstractions</strong> that are easy to
test and reason about.

Thus, Finch is fast because ...

#### Finagle is fast

Finch was designed with one goal in mind: provide an easy to use API (i.e., combinators API) on top
one that's easy to implement (Finagle services). Obviously, it should involve some overhead on top of
bare metal Finagle. And it does: by our latest measurements it adds 10% of allocations and 5% of
running time on top of Finagle, which is not so dramatic and, I'd say, pretty good for a pre 1.0
library.

When it comes to [Finagle][finagle], there is no doubt in its performance. The Finagle team
at Twitter (which I'm luckily a part of) puts a lot of effort to make sure that Finagle's
performance is constantly improving. There is a number of micro-benchmarks we run on each commit to
critical components. There are integration tests we write using the internal framework called Integ
to load test different Finagle topologies. Finally, there is [https://twitter.com][tw] that stress
tests Finagle 24/7 doing millions of queries per second (DC-wise).

Finagle itself is built by the same principles Finch is. It reuses the industry's best practices and
runs its IO layer on [Netty][netty], which is well-know as the best thing that happened to JVM in
years. [Netty is everywhere][adopters]: I'd be surprised if Netty code doesn't handle at least 10%
of your everyday traffic. You send at tweet - Finagle and Netty take care of it. You upload your
photos to iCloud, talk to Siri - [Netty handles it][netty-at-apple]. This list is almost limitless,
I'm not sure I know any JVM shop around that doesn't use Netty in some way.

#### Circe is fast

While Finch is designed to be agnostic to a concrete serialization library, there is one that plays
really nicely with Finch. [Circe][circe] is relatively new JSON library, started as fork of
[Argonaut][argonaut], but ended up as a completely standalone and mature project. It promotes
type-full programming and provides compile-time mechanisms for deriving JSON codecs for sealed
traits and case classes.

Even though Circe is young, it's already one of [the fastest Scala JSON libraries][circe-perf]
around, which is quite mind-blowing given how nice, thoughtful and boilerplate-less its API is. Part
of the Circe's great performance comes from the library it uses to parse JSON strings into JSON
ASTs. This library is called [Jawn][jawn] and it's one of the fastest (if not the fastest) ways to
parse JSON on JVM.

#### Shapeless is fast

[Shapeless][shapeless] is a generic programming library used by many Scala projects (including
[Circe][circe], [Spray][spray], [scodec][scodec], etc) to implement generic API (e.g., abstract over tuple arity) in a boilerplate-less manner.

While it might seem like Shapeless does a lot of work and does add a lot of overhead, it's not
really the case. Most of the Shapeless-related work happens at compile time and does not affect
program running time. It shouldn't be a surprise that Shapeless-powered code does increase compilation
time, but it almost never increase running time. Finch benchmarks of the derived vs. custom written
endpoints only conform that - the performance is literally the same.

#### Finch is fast

The bottom line is - Finch is in a good company. There is a team or a person, behind every single
library Finch uses, dedicated to its future and performance. I'm confident in those people and I'm
confident in libraries they maintain. Finch takes a lot from the OSS community and tries to pay it
back with a good performance.

The fact that Finch performs so well gives me a hope that the abstractions we've chosen in the
beginning are not completely broken performance-wise. And this makes me confident in Finch's future
performance. We haven't stopped yet. In fact, we haven't started yet and the actual performance work
is only planned as a post 1.0 activity.

[finch]: https://github.com/finagle/finch
[finagle]: https://github.com/twitter/finagle
[te-results]: https://www.techempower.com/benchmarks/#section=data-r12&hw=peak&test=json&l=6bk
[tw]: https://twitter.com
[netty]: http://netty.io/
[adopters]: http://netty.io/wiki/adopters.html
[netty-at-apple]: https://speakerdeck.com/normanmaurer/connectivity
[circe]: https://github.com/travisbrown/circe
[argonaut]: http://argonaut.io/
[circe-perf]: https://github.com/travisbrown/circe#performance
[jawn]: https://github.com/non/jawn
[shapeless]: https://github.com/milessabin/shapeless
[spray]: http://spray.io/
[scodec]: https://github.com/scodec/scodec
