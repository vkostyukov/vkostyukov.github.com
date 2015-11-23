---
layout: post
title:  "Finagle Your Fibonacci Calculation"
date:   2014-02-01 10:00:00
categories: scala, finagle
disqus: true
---

[Finagle][1] is an RPC library for JVM that allows you to develop service-based applications in a protocol-agnostic way. Formally, the Finagle library provides both asynchronous runtime via [futures][2] and protocol-independence via [codecs][3]. In this post I will try to build a Finagle-powered distributed [Fibonacci Numbers][4] calculator that scales up to thousands of nodes.

### Topology Design

Lets start with the requirements. At the first glance, we might want to see our system both [fault-tolerant][6] and [scalable][7]. These are typical requirements for any kind of distributed system. And the good news here, Finagle provides a corresponding set of building blocks and mechanisms (such as load balancing, retrying, monitoring, etc.) that allows the developer easily write a _reusable_ scalable and fault-tolerant code without a particulate knowledge about concrete protocols.

Anyway, such things like scalability should be done at some different level then framework or library level. The systems should be scalable by design not by any fancy tool. Thus, we must remember it at any stage of application life-cycle.

In order to design the scalable system we have to understand the problem we're trying to solve. The classic Fibonacci calculation algorithm builds a recursive tree with height `O(n)` and branching factor `2`. Thus, the most natural and suitable [service topology][8] we can use here is a hierarchical one. The hierarchical or tree-based topology satisfy both scalability and fault-tolerance requirements. So, the distributed Fibonacci calculator might be viewed as following

![High-Level Design]({{ site.url }}/assets/images/fibonacci-design.png)

In other words, we simply maps every node from the recursive tree (the algorithm's abstraction) to physical/distributed nodes. The proposed topology tree has two kind of nodes - _leaf_ nodes with label `W` (workers) and _branch_ nodes with label `F` ([fanouts][9]). The worker node is our workhorse that does all the magic, while the fanout node doesn't really perform calculation but implements _map-reduce_ approach by delegating the sub-problems to child nodes. The number of nodes in such tree is unlimited, but doesn't really make sense having workers more than a number of logical cores in your CPU. For example, a suitable configuration for a typical [Haswell][10] laptop with four logical cores looks exactly like the picture above.

### Finagle Power

The Finagle's API provides three robust building blocks: [futures][2], [filters and services][11]. All the building blocks are designed to be composable in a very neat way. Thus, keeping in mind that futures are single-element _immutable containers_ while services and filters are _just functions_, it's really simple to reason about Finagle-powered code.

Finagle is a _service-oriented_ platform. So, all the interactions between servers and clients are built around services. Servers implements their behavior via services, while clients interacts with servers via services. Finally, service is just a function that takes type `A` and returns a future of type `B`.

{% highlight scala %}
trait Service[A, B] {
  def apply(a: A): Future[B]
}
{% endhighlight %}

The `Future` type represents a placeholder for a response being sent from server. Programming with futures is an  asynchronous programming discipline that relies on transforming values rather than reasoning about sequence of events and callbacks.

The last but not least thing to discuss - Finagle's filters, which are actually [_decorators_][12] for services. Filters allow to change the behavior of services at running time as well as to change their types and get some benefits from Scala's type checker at compile time.

### Abstractions

Lets start with a cornerstone abstraction - a Fibonacci calculator that takes a `BigInt` number of Fibonacci member and returns a future of its value. It also a good idea to predefine a useful `BigInt` values in the same trait.

{% highlight scala %}
trait FibonacciCalculator {
  val Zero = BigInt(0)
  val One = BigInt(1)
  val Two = BigInt(2)

  def calculate(n: BigInt): Future[BigInt]
}
{% endhighlight %}

Now we can define a worker node implementation that uses a [for comprehension][13] for future pipelining (_sequential composition_). The straightforward implementation looks exactly like the classic recursive algorithm.

{% highlight scala %}
object LocalFibonacciCalculator extends FibonacciCalculator {
  def calculate(n: BigInt): Future[BigInt] =
    if (n.equals(Zero) || n.equals(One)) Future.value(n)
    else for { a <- calculate(n - One)
               b <- calculate(n - Two) } yield (a + b)
}
{% endhighlight %}

Thus, the fanout node implementation might be defined as following

{% highlight scala %}
class FanoutFibonacciCalculator(
  left: FibonacciCalculator,
  right: FibonacciCalculator) extends FibonacciCalculator {
  
  def calculate(n: BigInt): Future[BigInt] =
    if (n.equals(Zero) || n.equals(One)) Future.value(n)
    else {
      val seq = Seq(left.calculate(n - One), right.calculate(n - Two))
      Future.collect(seq) map { _.sum }
    }
}
{% endhighlight %}

The fanout calculator uses a _concurrent compositor_ `Future.collect()` (which takes the sequence of futures and returns the future of sequences) in order to process left and right sub-trees in parallel. The last future transformation that is performed by fanout calculator is summing up the sequence.

In our system, we will use the String-based transport layer provided by Finagle's example of [Echo Server][3], which means we need to provide a suitable _adapter_ implementation that adapts the String-based service `Service[String, String]` to `FibonacciCalculator` interface. This will allow us to use remote workers as fanout node's children.

{% highlight scala %}
class RemoteFibonacciCalculator(remote: Service[String, String]) 
    extends FibonacciCalculator {

  def calculate(n: BigInt): Future[BigInt] = 
    remote(n.toString) map { BigInt(_) }
}
{% endhighlight %}

Good news here is that `BigInt` can be converted to the `String` (and vice versa) out-of-the box, so we can easily perform the conversion in one line.

Now we're ready to setup our service that takes a Fibonacci calculator and delegates the clients' requests to it. Also, a bit of type conversions should be done here. The `FibonacciService` can be treated as an _adapter_ of `FibonacciCalculator` to `Service` interface.

{% highlight scala %}
class FibonacciService(calculator: FibonacciCalculator) 
    extends Service[String, String] {

  def apply(req: String): Future[String] =
    calculator.calculate(BigInt(req)) map { _.toString }
}
{% endhighlight %}

### Server and Client Configurations

Finally, we can define a server that handles our Fibonacci service. The launcher should allow the user to run either the worker node or fanout node by specifying the corresponding command line options. The complete implementation looks like following.

{% highlight scala %}
object FibonacciServerLauncher {
  def main(args: Array[String]): Unit = main(args.toSeq)

  def main(args: Seq[String]): Unit = args match {
    case Seq("leaf", port) =>
      val service = new FibonacciService(LocalFibonacciCalculator)
      Await.ready(FibonacciServer.serve(":" + port, service))
    case Seq("node", port, left, right) =>
      // remote services 
      val ls = FibonacciClient.newService("localhost:" + left)
      val rs = FibonacciClient.newService("localhost:" + right)

      // remote calculators
      val lc = new RemoteFibonacciCalculator(ls)
      val rc = new RemoteFibonacciCalculator(rs)

      // a fanout
      val service = new FibonacciService(new FanoutFibonacciCalculator(lc, rc))
      Await.ready(FibonacciServer.serve(":" + port, service))
  }
}
{% endhighlight %}

The client launcher looks much simpler though.

{% highlight scala %}
object FibonacciClientLauncher {
  def main(args: Array[String]): Unit = main(args.toSeq)

  def main(args: Seq[String]): Unit = args match {
    case Seq(port, req) =>
      val client = FibonacciClient.newService("localhost:" + port)
      val rep = Await.result(client(req))
      printf("Fibonacci(%s) is %s\n", req, rep)
    case _ => println("Bad arguments!")
  }
}
{% endhighlight %}

The complete source code of both client and server is available [at GitHub][14].

Now, it's time to build the topology from the first picture (the binary three with seven nodes). The following script builds the tree in a _bottom-up_ manner by launching a seven instances on the same machine.

{% highlight bash %}
$ sbt "run-main FibonacciServerLauncher leaf 2001" && \
  sbt "run-main FibonacciServerLauncher leaf 2002" && \
  sbt "run-main FibonacciServerLauncher node 2003 2002 2001" && \
  sbt "run-main FibonacciServerLauncher leaf 2004" && \
  sbt "run-main FibonacciServerLauncher leaf 2005" && \
  sbt "run-main FibonacciServerLauncher node 2006 2005 2004" && \
  sbt "run-main FibonacciServerLauncher node 2007 2006 2003"
{% endhighlight %}

From the client-side, system usage looks pretty simple. The client should interact with a root node of the topology tree. In our case, with an instance on port `2007`.

![System Usage]({{ site.url }}/assets/images/fibonacci-usage.png)

### Filters as Services' Decorators

Filters provide a natural and clean way of changing the services' behavior by chaining their requests through the number of nested filters. Thus, the same _protocol-independent_ filters can be used at both server and client sides.

Lets consider the example of the filter that simply logs services' requests to the console.

{% highlight scala %}
object LogStringFilter extends Filter[String, String, String, String] {
  def apply(req: String, srv: Service[String, String]): Future[String] = {
    println("Got a request: " + req)
    srv(req)
  }
}
{% endhighlight %}

The filter can be applied to the service by `andThen` operator. In order to make workers dump their requests we can change the launcher configuration as following.


{% highlight scala %}
val service = new FibonacciService(LocalFibonacciCalculator)
Await.ready(FibonacciServer.serve(":" + port, LogStringFilter andThen service))
{% endhighlight %}


### Is it Scalable and Fault-Tolerant?

The suggested tree-based topology might be scaled in a _bottom-up_ manner by adding new levels of fanout nodes. But, it's not that easy to configure the system with only shell commands described before. Any kind of specialized tools (like [ZooKeeper][15], which is supported by Finagle) should be used instead.

In order to make the system fault-tolerant, we can use Finagle's built-in _load balancers_ as well as customized filters that implement [retries][16] and [timeouts][17].

For example, the following client service will be balancing its requests between two nodes `localhost:2001` and `localhost:2002`:

{% highlight scala %}
val client = FibonacciClient.newService("localhost:2001,localhost:2002")
{% endhighlight %}


### Further Improvements
It might be a good idea to replace the String-based transport layer with BigInt-based one. The suitable example of corresponding pipeline configurations with BigInt decoders and encoders can be found [at Netty's example directory][5].


[1]: http://twitter.github.io/finagle/
[2]: http://twitter.github.io/finagle/guide/Futures.html
[3]: http://twitter.github.io/finagle/guide/ServerAnatomy.html
[4]: http://en.wikipedia.org/wiki/Fibonacci_number
[5]: https://github.com/netty/netty/tree/master/example/src/main/java/io/netty/example/factorial
[6]: http://en.wikipedia.org/wiki/Fault_tolerance
[7]: http://en.wikipedia.org/wiki/Scalability
[8]: http://www.openp2p.com/pub/a/p2p/2002/01/08/p2p_topologies_pt2.html
[9]: http://en.wikipedia.org/wiki/Fan-out
[10]: http://en.wikipedia.org/wiki/Haswell_(microarchitecture)
[11]: http://twitter.github.io/finagle/guide/ServicesAndFilters.html
[12]: http://en.wikipedia.org/wiki/Decorator_pattern
[13]: http://stackoverflow.com/questions/19045936/scalas-for-comprehension-with-futures
[14]: https://github.com/vkostyukov/finagle-fibonacci
[15]: http://zookeeper.apache.org
[16]: https://github.com/twitter/finagle/blob/master/finagle-core/src/main/scala/com/twitter/finagle/service/RetryingFilter.scala
[17]: https://github.com/twitter/finagle/blob/master/finagle-core/src/main/scala/com/twitter/finagle/service/TimeoutFilter.scala

