package okay.study

import scala.collection.mutable

/** THE READING OF A CONCRETE SORITES: each premiss's options weighed by a WEIGHER (the one learned thing — a
 * language model, or an oracle in tests), then decided either by the weigher alone or TOGETHER with the symbolic
 * model: of the combinations of readings, the best-weighed one the exact side accepts */
object Reading:
  /** an exercise, the number of a premiss in it, its options → a weight for each (greater: likelier) */
  type Weigher = (Concrete, Int, Vector[Premiss]) => Vector[Double]

  /** each premiss's options, the likeliest first */
  def ranked(c: Concrete, weigh: Weigher): Vector[Vector[(Premiss, Double)]] =
    val options = Premiss.options(c.letters)
    c.premisses.indices.toVector.map(i => options.zip(weigh(c, i, options)).sortBy(-_._2))

  /** the weigher alone: each premiss's likeliest reading */
  def alone(ranked: Vector[Vector[(Premiss, Double)]]): Vector[Premiss] = ranked.map(_.head._1)

  /** what the exact side asks of a whole reading: every term of the dictionary is used (the account: nothing
   * given is left out), and the symbolic model draws a conclusion from it */
  def accepted(model: Model, c: Concrete, read: Vector[Premiss]): Boolean =
    read.flatMap(_.letters).toSet == c.letters.toSet && Solve.run(model, read.map(_.sentence)).exists(_.nonEmpty)

  /** the lesson's own shape of a sorites, asked too: every term in two premisses, but two terms (the retinends) in
   * one — a reading that uses a term three times, or leaves three terms single, is no sorites */
  def strict(model: Model, c: Concrete, read: Vector[Premiss]): Boolean =
    val uses = read.flatMap(_.letters.toVector).groupBy(identity).view.mapValues(_.size).toMap
    uses.values.forall(_ <= 2) && uses.values.count(_ == 1) == 2 && accepted(model, c, read)

  /** the weigher and the symbolic model together: combinations of each premiss's `top` readings, by their summed
   * weight, the first one accepted; none within `budget` combinations: not read (to be asked) */
  def together(model: Model, c: Concrete, ranked: Vector[Vector[(Premiss, Double)]], top: Int = 4,
      budget: Int = 2000, accept: (Model, Concrete, Vector[Premiss]) => Boolean = accepted): Option[Vector[Premiss]] =
    val lists = ranked.map(_.take(top))
    def weight(at: Vector[Int]): Double = at.indices.map(i => lists(i)(at(i))._2).sum
    val queue = mutable.PriorityQueue.empty[(Double, Vector[Int])](Ordering.by(_._1))
    val seen = mutable.Set.empty[Vector[Int]]
    val start = Vector.fill(lists.size)(0)
    queue.enqueue(weight(start) -> start); seen += start
    var tried = 0
    var found = Option.empty[Vector[Premiss]]
    while found.isEmpty && queue.nonEmpty && tried < budget do
      val (_, at) = queue.dequeue()
      tried += 1
      val read = at.indices.toVector.map(i => lists(i)(at(i))._1)
      if accept(model, c, read) then found = Some(read)
      else
        for i <- at.indices if at(i) + 1 < lists(i).size do
          val next = at.updated(i, at(i) + 1)
          if seen.add(next) then queue.enqueue(weight(next) -> next)
    found
