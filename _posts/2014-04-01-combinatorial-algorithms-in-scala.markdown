---
layout: post
title:  "Combinatorial Algorithms in Scala"
date:   2014-04-01 10:00:00
categories: scala, algorithms
---

[Combinatorics][1] is a branch of mathematics that mostly focuses on problems of counting the structures of given size and kind. The most famous and well-known examples of such problems might be often asked as job interview questions. This blog post presents four generational problems ([combinations][2], [subsets][3], [permutations][4] and [variations][5]) along with their _purely functional_ implementations in Scala.

#### Implicit Classes
Scala's [_implicit classes_][6] provide simple and composable way of extending the API of third-party classes. For example, the following implicit class extends default `Int` class within a new method `times(fn: Unit => Unit): Unit` that executes given function `fn` n-times.

{% highlight scala %}
object IntOps {
  implicit class ExtendedInt(n: Int) {
    def times(fn: Unit => Unit): Unit =
      (0 until n).foreach(fn)
  }
}  
{% endhighlight %}

This gives us a very neat usage way. All one need to do is import an implicit class into the current namespace and let the magic happened.

{% highlight scala %}
import IntOps._
5.times {
  println("Hello, World!")
}
{% endhighlight %}

We'll use this approach in order to extend Scala's `List` with four new methods that implement our combinatorial algorithms. The only one restriction we have to satisfy here: new functions' names shouldn't conflict with an existent API. Thus, we'll use a prefix `x` (from _eXtended_) for new functions. The following listing represents a skeleton class we're going to implement.

{% highlight scala %}
object CombinatorialOps {
  implicit class CombinatorialList[A](l: List[A]) {

    def xcombinations(n: Int): List[List[A]] = ???
    def xsubsets: List[List[A]] = ???
    def xvariations(n: Int): List[List[A]] = ???
    def xpermutations: List[List[A]] = ???

  }
}
{% endhighlight %}

This tiny class might be used as follows (in the exact way as `IntOps` was used below).

{% highlight scala %}
import CombinatorialOps._
val c = List(1, 2, 3).xcombinations(2)
{% endhighlight %}

#### Optimistic Programming
_Optimistic Programming_ is an implementation technique of recursive programs when it's believed that a recursive function works as expected on a smaller input (on a sub-problem) in order to use its result for solving the full-size problem. In other words, the body of recursive function may be implemented in terms of following ideas: (a) when called recursively it gives the right answer for any sub-problem, but (b) some additional work should be done in order to merge these sub-problem solutions into the single solution of the entire problem. Doesn't that sound _optimistic_? The recursive function is pretended to be correctly implemented _before_ its body is actually being written.

An optimistic programming lie between [Divide and Conquer][8] and [Dynamic Programming][9] techniques. Rather then focussing on how sub-problems are being splited (whether or not the sub-problems overlap) an optimistic programming focuses on the nature of recursive programs and provides a simple tool making the programming of complex problems much easier.

We'll use an optimistic programming for solving combinatorial problems in a functional setting.

#### Combinations
Imagine you're given a standard deck of fifty-two cards and asked to select any two of them. Those pair of cards you'll select is called a _combination_ (i.e., a _2-combination_). And there are 1326 such 2-card combinations that may be possibly selected from a standard card deck. More formally, a [binomial coefficient][10] defines the number of _k-combinations_ from a set of `n` distinct elements.

The combination elements' order _doesn't_ matter. So, `[a, b]` and `[b, a]` are the same combinations.

{% highlight scala %}
scala> List("a", "b", "c").xcombinations(2)
res1: List[List[String]] = List(List(a, b),
                                List(a, c),
                                List(b, c))
{% endhighlight %}

It's time to use the power of optimistic programming for solving the problem of generating _k-combinations_. An optimistic programming guarantees that a recursive function being called on a _sub-problem_ produces a correct answer. A sub-problem of generating k-combinations is generating (k-1)-combinations. The only one question's left: how to solve an entire problem then? This is when the things become interesting. Obviously, there is should be _an extra element_ in the set, which being added to a (k-1)-combination upgrades it to a _full-size_ k-combination. A set's _extra_ element is nothing different from a _regular_ set's element. And set `S` itself is a _recursive object_, which without one element is still a set `S'` and may be processed recursively. Thus, the final solution contains both `S'`'s k-combinations and `S'`'s (k-1)-combinations with an extra element appended.

There are also two corner cases that we have to handle separately. There is nothing to do when `k > n` (combination's size is greater then an entire set's size). And there is no further grouping required if it's a generation of 1-combinations.

{% highlight scala %}
/**
 * Generates the combinations of this list with given length 'n'. The order
 * doesn't matter.
 *
 * The total number of k-combinations on n-length set might be calculated
 * as follows:
 *
 *                  C_k,n = n!/k!(n - k)!
 *
 * Time - O(C_k,n)
 * Space - O(C_k,n)
 */
def xcombinations(n: Int): List[List[A]] =
  if (n > xsize) Nil
  else l match {
    case _ :: _ if n == 1 =>
      l.map(List(_))
    case hd :: tl =>
      tl.xcombinations(n - 1).map(hd :: _) ::: tl.xcombinations(n)
    case _ => Nil
  }
{% endhighlight %}


#### Subsets
A set's k-combination may also be referenced as a _subset_. The other combinatorial problem is generating all the subsets (all k-combinations, where `k = 1..n`) of a given set.

{% highlight scala %}
scala> List("a", "b", "c").xsubsets
res1: List[List[String]] = List(List(a, b, c),
                                List(a, b),
                                List(a, c),
                                List(b, c),
                                List(a),
                                List(b),
                                List(c))
{% endhighlight %}

The implementation is straightforward - combinations of all the possible sizes should be merged together. That may be done by List's `foldLeft` operation.

{% highlight scala %}
/**
 * Generates all the subsets of this list. The order doesn't matter.
 *
 * The total number of subsets might be obtained from variations formula:
 *
 *                  S_n = sum(i=1..n) {C_i,n} = 2 ** n
 *
 * Time - O(S_n)
 * Space - O(S_n)
 */
def xsubsets: List[List[A]] =
  (2 to xsize).foldLeft(l.xcombinations(1))((a, i) => l.xcombinations(i) ::: a)
{% endhighlight %}

There are `2^n` subset of an n-size set. It's choice of two: every set's element is either taken or not into the particular subset.

#### Variations
Unlike combinations, the order of elements inside a variation _does_ matter. Thus, tuples `[a, b]` and `[b, a]` are different _variations_ (i.e., _2-variations_). In general, variations are denoted as _partial permutations_ or _k-permutations_, where `0 < k <= n`.

{% highlight scala %}
scala> List("a", "b", "c").xvariations(2)
res1: List[List[String]] = List(List(b, a),
                                List(a, b),
                                List(c, a),
                                List(a, c),
                                List(c, b),
                                List(b, c))
{% endhighlight %}

The number of _k-permutations_ of `n` is the following product: `n * (n-1) * ... * (n-k+1)`. That's a bit different from a _binomial coefficient_ (the number of _k-combinations_ of `n`): there is no `k!` in a denominator, since it counts _all_ the possible k-permutations rather then treating them equal.

The same ideas of an _optimistic programming_ may be used in generating the variations (k-permutations) of a given set. The corner cases are the same: there's nothing to do with `k > n` or `k = 1`. Just like in combinations, these two cases should be handled separately. More interesting is the regular case: upgrading a recursively generated (k-1)-permutation to a _full-size_ one. It's no longer a problem of getting _an extra element_ from a set, as well as the _upgrading_ itself is no longer a merging.

Since the order does matter, an extra element should be _inserted_ into the every possible place of a permutation rather then just being merged with it. So, instead of 1-by-1 mapping between unfinished and finished combination it comes to 1-by-k mapping for permutations: there are `k` places in (k-1)-permutation where an extra element may be inserted.

Ultimately, by analogy with k-combinations, k-permutations of `S` contain all the k-permutations of `S'`, where `S'` is a without-out-element version of `S`.

{% highlight scala %}
/**
 * Generates the variations of this list with given length 'n'. The order
 * does matter.
 *
 * The total number of variations might be calculated as follows:
 *
 *                   V_k,n = n!/(n - k)!
 *
 * Time - O(V_k,n)
 * Space - O(V_k,n)
 */
def xvariations(n: Int): List[List[A]] = {
  def mixmany(x: A, ll: List[List[A]]): List[List[A]] = ll match {
    case hd :: tl => foldone(x, hd) ::: mixmany(x, tl)
    case _ => Nil
  }

  def foldone(x: A, ll: List[A]): List[List[A]] =
    (1 to ll.length).foldLeft(List(x :: ll))((a, i) => (mixone(i, x, ll)) :: a)

  def mixone(i: Int, x: A, ll: List[A]): List[A] =
    ll.slice(0, i) ::: (x :: ll.slice(i, ll.length))

  if (n > xsize) Nil
  else l match {
    case _ :: _ if n == 1 => l.map(List(_))
    case hd :: tl => mixmany(hd, tl.xvariations(n - 1)) ::: tl.xvariations(n)
    case _ => Nil
  }
}
{% endhighlight %}


#### Permutations
_Permutations_ are just set's size variations or k-permutations with `k = n`. A permutation may also be viewed as a result of a set's _shuffle_ operation. In other words, every iteration of a shuffling the deck of cards process gives a new permutation. Permutations are counted by a product: `n * (n-1) * ... 1`, which is `n!`.

{% highlight scala %}
scala> List("a", "b", "c").xpermutations
res1: List[List[String]] = List(List(c, b, a),
                                List(c, a, b),
                                List(a, c, b),
                                List(b, c, a),
                                List(b, a, c),
                                List(a, b, c))
{% endhighlight %}

The implementation of a purely-functional algorithm of generating the permutations is quite simple in terms of variations.

{% highlight scala %}
/**
 * Generates all permutations of this list. The order does matter.
 *
 * The total number of permutations might be calculated as follows:
 *
 *                 P_n = V_n,n = n!
 *
 * Time - O(n!)
 * Space - O(n!)
 */
def xpermutations: List[List[A]] = xvariations(xsize)
{% endhighlight %}


#### Further Improvements
The full version of `CombinatorialOps` class might be found [at GitHub][7]. In order to reduce the memory footprint a bit of lazinesses may be involved by (a) replacing the output data type `List[List[A]]` with `Iterable[List[A]]` and (b) generating each piece of data _on-demand_.

[1]: http://en.wikipedia.org/wiki/Combinatorics
[2]: http://en.wikipedia.org/wiki/Combination
[3]: http://en.wikipedia.org/wiki/Subset
[4]: http://en.wikipedia.org/wiki/Permutations
[5]: http://en.wikipedia.org/wiki/Combination
[6]: http://docs.scala-lang.org/sips/completed/implicit-classes.html
[7]: https://gist.github.com/vkostyukov/9015987
[8]: http://en.wikipedia.org/wiki/Divide_and_conquer_algorithms
[9]: http://en.wikipedia.org/wiki/Dynamic_programming
[10]: http://en.wikipedia.org/wiki/Binomial_coefficient
