package okay.study

import scala.util.Random

/** SORITESES MADE FROM THE FORMAL SIDE: a chain of implications over the terms, each said as one of the forms a
 * premiss takes, in any order — the problems the symbolic model solves, sampled; the reader's lessons start here */
object Sample:
  /** the letters the book names terms by */
  val letters: Vector[String] = "abcdehklmnr".map(_.toString).toVector

  /** a sorites over these letters: as many premisses as letters less one, every letter used */
  def sorites(over: Vector[String], rnd: Random): Vector[Premiss] =
    val chain = rnd.shuffle(over).map(l => if rnd.nextBoolean() then l else l + "'")
    val premisses = chain.zip(chain.drop(1)).map { (from, to) => // "every `from` is `to`"
      rnd.nextInt(3) match
        case 0 => Premiss(true, from, to)
        case 1 => Premiss(true, Premiss.neg(to), Premiss.neg(from))
        case _ => Premiss(false, from, Premiss.neg(to)).normal
    }
    rnd.shuffle(premisses)

  /** an exercise of our own: a universe, terms (named by letters in their alphabetical order, as the book does),
   * and a sampled sorites over them; the premisses' words are still to be said */
  def exercise(number: Int, universe: String, terms: Vector[String], rnd: Random): Concrete =
    val dictionary = letters.zip(terms.sortBy(_.toLowerCase))
    val read = sorites(dictionary.map(_._1), rnd)
    Concrete(number, Vector.fill(read.size)(""), universe, dictionary, read.map(subscript), "", "")

  /** the book's subscript form of a reading */
  def subscript(p: Premiss): String =
    if p.all then s"${p.x}_{1}${Premiss.neg(p.y)}_{0}" else s"${p.x}${p.y}_{0}"
