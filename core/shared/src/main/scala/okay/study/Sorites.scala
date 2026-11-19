package okay.study

/** A PREMISS OF A SORITES, read: `No x are y` (a nullity: the order says nothing) or `All x are y` (x exists, and
 * no x are not-y). An attribute is a letter, primed when negated */
final case class Premiss(all: Boolean, x: String, y: String):
  /** the canonical sentence the symbolic model reads */
  def sentence: String = if all then s"All $x are $y" else s"No $x are $y"
  /** equal readings are equal values: a nullity's attributes in order */
  def normal: Premiss = if !all && x > y then Premiss(false, y, x) else this
  def letters: Set[String] = Set(x.take(1), y.take(1))
  /** the two attributes that are null together: what a conclusion says apart from which of them exists */
  def nullity: Set[String] = if all then Set(x, Premiss.neg(y)) else Set(x, y)

object Premiss:
  def neg(t: String): String = if t.endsWith("'") then t.dropRight(1) else t + "'"

  private val Entity = """^([a-z]'?)_\{1\}([a-z]'?)_\{0\}$""".r
  private val Nullity = """^([a-z]'?)([a-z]'?)_\{0\}$""".r

  /** the book's subscript form: `b_{1}d_{0}` is "All b are d'", `ac_{0}` is "No a are c" */
  def ofSubscript(s: String): Option[Premiss] = s.trim match
    case Entity(x, y) => Some(Premiss(true, x, neg(y)))
    case Nullity(x, y) => Some(Premiss(false, x, y).normal)
    case _ => None

  /** every reading over these letters: two different letters, each plain or primed, as a nullity or an `All` */
  def options(letters: Vector[String]): Vector[Premiss] =
    val terms = letters.flatMap(l => Vector(l, l + "'"))
    (for x <- terms; y <- terms if x.take(1) != y.take(1); all <- Vector(true, false)
      yield Premiss(all, x, y).normal).distinct

/** a concrete sorites of the book: its premisses in words, the universe and the dictionary, and — from the
 * book's solutions — each premiss in subscript form and the conclusion */
final case class Concrete(number: Int, premisses: Vector[String], universe: String,
    dictionary: Vector[(String, String)], gold: Vector[String], conclusion: String, answer: String):
  def letters: Vector[String] = dictionary.map(_._1)
  def goldPremisses: Option[Vector[Premiss]] =
    val read = gold.flatMap(Premiss.ofSubscript)
    Option.when(read.size == gold.size && read.size == premisses.size)(read)

/** HOW A READING IS SAID in the exercise's own words: the canonical English a reader matches a premiss against */
object InWords:
  private def phrase(c: Concrete, term: String): String =
    val words = c.dictionary.toMap.getOrElse(term.take(1), term.take(1))
    if term.endsWith("'") then s"not $words" else words

  def of(c: Concrete, p: Premiss): String =
    if p.all then s"All ${c.universe} that are ${phrase(c, p.x)} are ${phrase(c, p.y)}."
    else s"No ${c.universe} are both ${phrase(c, p.x)} and ${phrase(c, p.y)}."
