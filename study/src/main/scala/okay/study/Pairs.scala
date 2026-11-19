package okay.study

import okay.effects.json.Json
import okay.llm.{Ask, Llm, Sampling}
import okay.scala3.*

import scala.util.Random

/** THE READER'S LESSONS, MADE FROM THE FORMAL SIDE: a sorites sampled over a dictionary the teacher invents, each
 * premiss SAID by the teacher in an ordinary sentence, and kept only if the teacher, asked apart and shown the
 * sentence alone, READS it back to the same nullity. The meaning is known by construction; the teacher gives only
 * the wording, and the read-back refuses a wording that says something else */
object Pairs:
  /** a lesson: an exercise of ours with its premisses said, and each one's reading where it was read back to the
   * sampled nullity — the read-back's own form (`All` or `No`, as the sentence says it), the lesson's label */
  final case class Lesson(exercise: Concrete, read: Vector[Option[Premiss]]):
    def pairs: Vector[(Int, Premiss)] = read.zipWithIndex.collect { case (Some(p), i) => i -> p }

  private def message(role: String, text: String): Json = Json.obj("role" -> Json.Str(role), "content" -> Json.Str(text))

  private def ask[R[_]: Has[Llm]](text: String, temperature: Double, maxTokens: Int): Json ! R =
    Llm.complete(Ask(Vector(message("user", text)), maxTokens = maxTokens, thinking = false,
      sampling = Sampling(temperature = Some(temperature)))).map(f => Study.json(f.reply.content).getOrElse(Json.Null))

  private def texts(j: Json, field: String): Vector[String] =
    j.field(field).collect { case Json.Arr(xs) => xs.collect { case Json.Str(s) => s.trim } }.getOrElse(Vector.empty)

  private def glossary(c: Concrete): String =
    s"Universe: ${c.universe}.\nTerms: ${c.dictionary.map(_._2).mkString("; ")}."

  /** the book's exercises shown as the style: a premiss and its standard form */
  private def style(shown: Vector[Concrete]): String = shown.map(c =>
    s"${glossary(c)}\n" + c.goldPremisses.get.zip(c.premisses).map((p, s) =>
      s"  standard: ${InWords.of(c, p)}\n  ordinary: $s.").mkString("\n")).mkString("\n\n")

  def themes[R[_]: Has[Llm]](n: Int): Vector[String] ! R =
    ask(s"List $n different, concrete themes of everyday life, work, nature, school, trade, travel and home " +
      "(two to four words each, no two alike). Answer with one JSON object: {\"themes\": [\"...\", ...]}", 1.0, 4000)
      .map(texts(_, "themes").distinct)

  /** a universe and terms for a theme, as the book's dictionaries are: a class of things, and attributes each of
   * which fits "<universe> that are <term>" */
  def dictionary[R[_]: Has[Llm]](theme: String, terms: Int, shown: Vector[Concrete]): Option[(String, Vector[String])] ! R =
    ask(s"""A logic puzzle needs a dictionary: a UNIVERSE (a plural noun phrase, a class of things or persons) and
           |$terms TERMS — attributes of its members, each a short phrase that fits the frame
           |"<universe> that are <term>" (an adjective, a participle phrase, or a plural noun). The terms must be
           |independent of each other (none the opposite or a kind of another) and none may contain "not".
           |Examples of such dictionaries:
           |${shown.map(glossary).mkString("\n")}
           |
           |Invent one for the theme: $theme. Answer with one JSON object:
           |{"universe": "...", "terms": ["...", ...]}""".stripMargin, 0.9, 500).map { j =>
      val found = texts(j, "terms").map(_.stripSuffix(".")).filter(t => t.nonEmpty && !t.toLowerCase.contains("not ")).distinct
      j.field("universe").collect { case Json.Str(u) if u.trim.nonEmpty && found.size >= 4 => u.trim.stripSuffix(".") -> found }
    }

  /** each premiss of a sampled exercise said in an ordinary sentence */
  def said[R[_]: Has[Llm]](c: Concrete, shown: Vector[Concrete]): Vector[String] ! R =
    val forms = c.goldPremisses.get.map(InWords.of(c, _))
    ask(s"""Sentences in a STANDARD form are to be said as an author of logic puzzles would say them: in
           |ordinary, natural English, each a single sentence, in varied wording — "No one ... unless ...",
           |"None but ...", "All ..., that ..., are ...", "Nobody who ... ever ...", "Whatever ...", a plain
           |statement. The meaning must stay exactly the same: the same two terms, nothing added, nothing left out.
           |"not <term>" may be said by an opposite or a negation. Do not use the words "both" or "that are".
           |
           |Examples (from a book):
           |${style(shown)}
           |
           |Now these, in order:
           |${glossary(c)}
           |${forms.zipWithIndex.map((f, i) => s"  ${i + 1}. $f").mkString("\n")}
           |
           |Answer with one JSON object: {"sentences": ["...", ...]} — ${forms.size} sentences, in the same order.""".stripMargin,
      0.9, 800).map(j => texts(j, "sentences").map(_.stripSuffix(".").trim))

  /** each sentence read back, alone: the standard form the teacher says it means */
  def readBack[R[_]: Has[Llm]](c: Concrete, sentences: Vector[String], shown: Vector[Concrete]): Vector[Option[Premiss]] ! R =
    val options = Premiss.options(c.letters)
    val bySaying = options.map(o => InWords.of(c, o).toLowerCase -> o).toMap
    ask(s"""Each sentence is to be rewritten in STANDARD form, using only the given terms. A standard form is
           |either "All U that are X are Y." or "No U are both X and Y.", where U is the universe and X and Y are
           |terms exactly as given, or terms with "not" before them.
           |
           |Examples (from a book):
           |${style(shown)}
           |
           |Now these, in order:
           |${glossary(c)}
           |${sentences.zipWithIndex.map((s, i) => s"  ${i + 1}. $s.").mkString("\n")}
           |
           |Answer with one JSON object: {"standard": ["...", ...]} — ${sentences.size} sentences, in the same order.""".stripMargin,
      0.0, 900).map(j => texts(j, "standard").map(s => bySaying.get(s.toLowerCase)).padTo(sentences.size, None))

  /** the lessons of a theme: its dictionary, `each` soriteses sampled over parts of it, said, read back */
  def lessons[R[_]: Has[Llm]](theme: String, number: Int, each: Int, shown: Vector[Concrete], rnd: Random): Vector[Lesson] ! R =
    dictionary(theme, 9, shown).flatMap {
      case None => pure(Vector.empty)
      case Some((universe, terms)) =>
        (0 until each).foldLeft(pure[Vector[Lesson], R](Vector.empty)) { (acc, k) =>
          val size = 4 + rnd.nextInt((terms.size - 3).min(4)) // four to seven terms: three to six premisses
          val sampled = Sample.exercise(number * 10 + k, universe, rnd.shuffle(terms).take(size), rnd)
          for
            sofar <- acc
            sentences <- said(sampled, shown)
            lesson <-
              if sentences.size != sampled.gold.size then pure(Option.empty[Lesson])
              else
                val exercise = sampled.copy(premisses = sentences)
                readBack(exercise, sentences, shown).map(read => Some(Lesson(exercise,
                  read.zip(exercise.goldPremisses.get).map((r, g) => r.filter(_.nullity == g.nullity)))))
          yield sofar ++ lesson
        }
    }

  /** a lesson as a line of JSON, and back */
  def json(l: Lesson): String = Json.obj("number" -> Json.num(l.exercise.number),
    "universe" -> Json.Str(l.exercise.universe),
    "terms" -> Json.Arr(l.exercise.dictionary.map((k, v) => Json.arr(Json.Str(k), Json.Str(v)))),
    "premisses" -> Json.Arr(l.exercise.premisses.map(Json.Str(_))),
    "gold" -> Json.Arr(l.exercise.gold.map(Json.Str(_))),
    "read" -> Json.Arr(l.read.map(_.fold[Json](Json.Null)(p => Json.Str(Sample.subscript(p)))))).render

  def parse(line: String): Lesson =
    val j = Json.fromText(line)
    Lesson(Concrete(j("number").int, j("premisses").list.map(_.string), j("universe").string,
      j("terms").list.map(t => t.list(0).string -> t.list(1).string), j("gold").list.map(_.string), "", ""),
      j("read").list.map { case Json.Str(s) => Premiss.ofSubscript(s); case _ => None })
