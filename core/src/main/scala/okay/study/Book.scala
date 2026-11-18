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

  /** the lesson for soriteses: Book VI (the method of subscripts) and Book VII (soriteses), as printed */
  def soritesLesson(book: Book): String =
    val from = book.lines.indexWhere(_.trim == "BOOK VI.")
    val to = book.lines.indexWhere(_.trim == "BOOK VIII.", from)
    require(from >= 0 && to > from, "Book VI to Book VIII not found")
    book.lines.slice(from, to).filterNot(_.trim.matches("pg[-0-9a-z]+")).mkString("\n")

  private val Formula = """[a-z]'?(?:_\{1\})?[a-z]'?_\{0\}""".r

  /** italics' marks and the text's no-break spaces away, spaces single */
  private def plain(s: String): String = s.replace("_", "").replace(' ', ' ').replaceAll("\\s+", " ").trim

  /** blocks under headings that are a number alone, as `=12.=` or ` 12.` */
  private def blocks(lines: Vector[String], head: scala.util.matching.Regex): Vector[(Int, Vector[String])] =
    val at = lines.indices.filter(i => head.matches(lines(i)))
    at.zip(at.drop(1) :+ lines.size).map((from, to) =>
      head.findFirstMatchIn(lines(from)).get.group(1).toInt -> lines.slice(from + 1, to)).toVector

  /** Book VIII, § 9: sixty concrete soriteses — the premisses and the dictionary (EX9), the English answer (AN9),
   * and from the solutions (SL9) each premiss in subscript form and the conclusion */
  def concreteSoriteses(book: Book): Vector[Concrete] =
    val page = (l: String) => l.trim.matches("pg[-0-9a-z]+")
    val answers = Book.items(book.between("AN9", "CHAPTER III.").filterNot(page)).toMap
    val solutions = blocks(book.between("SL9", "pg164").filterNot(page), """^\s*(\d+)\.\s*$""".r).toMap
    blocks(book.between("EX9", "CHAPTER II.").filterNot(page), """^\s*=(\d+)\.=\s*$""".r).map { (n, lines) =>
      val text = lines.mkString("\n")
      val (stated, glossary) = text.splitAt(text.indexOf("Univ."))
      val premisses = """(?s)\(\d+\)\s+(.*?)(?=\n\s*\(\d+\)|\z)""".r.findAllMatchIn(stated)
        .map(m => plain(m.group(1)).stripSuffix(";").stripSuffix(".").trim).toVector
      val universe = "\"([^\"]*)\"".r.findFirstMatchIn(glossary).map(_.group(1)).getOrElse("things")
      // a term ends at the next `x = ` (the text misses a `;` once, in exercise 47)
      val dictionary = """(?s)\b([a-z]) = (.*?)(?=;?\s+[a-z] = |\z)""".r.findAllMatchIn(plain(glossary))
        .map(m => m.group(1) -> m.group(2).trim.stripSuffix(".").trim).toVector
      val solved = solutions.getOrElse(n, Vector.empty).mkString("\n")
      val gold = Formula.findAllIn(solved).take(premisses.size).toVector
      val conclusion = Formula.findAllIn(solved.drop(solved.lastIndexOf('¶') + 1).takeWhile(_ != '\n')).mkString(" + ")
      Concrete(n, premisses, universe.stripSuffix("."), dictionary, gold, conclusion, clean(answers.getOrElse(n, "")))
    }

  /** § 9's exercises whose printed solution the symbolic model does not confirm; kept out of every measure */
  val concreteMisprinted: Map[Int, String] = Map.empty

  /** whether these readings of an exercise's premisses give the book's conclusion — as a nullity: the book prints
   * the existence of one retinend where its own rule (§ 8) gives both, so existence is not compared here */
  def concluded(model: Model, c: Concrete, read: Vector[Premiss]): Either[String, Boolean] =
    val nullities = (said: Vector[String]) => said.flatMap(Premiss.ofSubscript).map(_.nullity).toSet
    Solve.run(model, read.map(_.sentence)).map(found =>
      found.nonEmpty && nullities(found.map(_._2)) == nullities(c.conclusion.split("\\s+\\+\\s+").toVector))
