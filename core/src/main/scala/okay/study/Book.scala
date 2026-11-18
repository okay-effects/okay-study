package okay.study

/** A BOOK AS TEXT: its parts cut by the lines that mark them, its numbered exercises and answers. Only cutting —
 * what a part means is the reader's and the teacher's */
final case class Book(text: String):
  val lines: Vector[String] = text.linesIterator.toVector

  /** the lines after the first one containing `from`, up to the next one containing `to` */
  def between(from: String, to: String): Vector[String] =
    val start = lines.indexWhere(_.contains(from))
    require(start >= 0, s"no line with: $from")
    val end = lines.indexWhere(_.contains(to), start + 1)
    lines.slice(start + 1, if end < 0 then lines.size else end)

object Book:
  private val Item = """^\s*(\d+)\.\s+(\S.*)$""".r
  private val Head = """^\s+(\d+)\.\s*$""".r

  /** numbered items `N. text`, a text's further lines joined to it; only numbers in order count as items */
  def items(lines: Vector[String]): Vector[(Int, String)] =
    lines.foldLeft(Vector.empty[(Int, String)])((acc, line) =>
      line match
        case Item(n, text) if n.toInt == acc.lastOption.fold(1)(_._1 + 1) => acc :+ (n.toInt -> text.trim)
        case _ if acc.nonEmpty && line.trim.nonEmpty => acc.init :+ (acc.last._1 -> s"${acc.last._2} ${line.trim}")
        case _ => acc)

  /** sets of numbered sentences, each under a heading that is its number alone (`  3.`) */
  def sets(lines: Vector[String]): Vector[(Int, Vector[String])] =
    val heads = lines.indices.filter(i => Head.matches(lines(i)))
    heads.zip(heads.drop(1) :+ lines.size).map { (from, to) =>
      val Head(n) = lines(from): @unchecked
      n.toInt -> items(lines.slice(from + 1, to)).map(_._2)
    }.toVector

/** Lewis Carroll, "Symbolic Logic" (1896; Project Gutenberg #28696): where its exercises are */
object Carroll:
  private def clean(s: String): String = s.trim.stripSuffix(";").stripSuffix(".").trim

  private def body(lines: Vector[String]): Vector[String] =
    val notes = lines.indexWhere(_.contains("[N.B. In each Example"))
    (if notes < 0 then lines else lines.take(notes)).filterNot(_.trim.matches("pg[-0-9a-z]+"))

  /** Book VIII, § 8: sets of abstract propositions as premisses for soriteses, with the answers */
  def abstractSoriteses(book: Book): Vector[Exercise] =
    val answers = Book.items(body(book.between("AN8", "AN9"))).toMap
    Book.sets(body(book.between("EX8", "EX9"))).map((n, sentences) =>
      Exercise(n, sentences.map(clean), clean(answers(n))))

  /** § 8's exercises this text prints inconsistently with the book's own rules (a `c` and an `e` confused, it
   * seems): left as printed, kept out of every measure, each with the reason */
  val misprinted: Map[Int, String] = Map(
    13 -> "the answer names e, an attribute two premisses hold: a conclusion has retinends only (c and b here)",
    14 -> "the answer names e, an attribute two premisses hold: a conclusion has retinends only (d and c here)",
    26 -> "every attribute is in two premisses and the answer names c, which is in none",
    27 -> "c is in three premisses: an attribute is in two (an eliminand) or one (a retinend)")
