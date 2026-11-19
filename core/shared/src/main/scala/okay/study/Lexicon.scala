package okay.study

import okay.effects.json.Json

/** THE WORDS OF A DICTIONARY'S TERMS, as data: for each term, words a sentence may use for what HAS the attribute
 * (`same`) and for what has NOT (`opposite`: an antonym, the complement's name within the universe — "Gentiles" for
 * not "Jews"). Extracted from a model, kept as a table: read, corrected, removed; matched exactly, no model */
final case class Lexicon(words: Map[String, Lexicon.Entry]):
  /** how many of a reading's two terms the sentence gives evidence for, with the sign the reading takes them by */
  def evidence(c: Concrete, sentence: String, p: Premiss): Int =
    val said = Lexicon.stems(sentence)
    Vector(p.x, p.y).count { term =>
      val letter = term.take(1)
      val entry = words.getOrElse(letter, Lexicon.Entry(Vector.empty, Vector.empty))
      if term.endsWith("'") then entry.opposite.exists(Lexicon.within(_, said))
      else (entry.same :+ c.dictionary.toMap.getOrElse(letter, "")).exists(Lexicon.within(_, said))
    }

object Lexicon:
  final case class Entry(same: Vector[String], opposite: Vector[String])

  private val light = Set("that", "this", "with", "from", "have", "having", "being", "which", "what", "those",
    "these", "their", "them", "they", "will", "would", "than", "ever", "never", "always", "very", "some", "mine")

  /** a text's content words, by their first five letters (a rough stem: "boarders" and "boarder" are one) */
  def stems(text: String): Set[String] =
    text.toLowerCase.split("[^a-z]+").toVector.filter(w => w.length > 3 && !light(w)).map(_.take(5)).toSet

  /** a phrase is in a sentence when every content word of it is */
  def within(phrase: String, said: Set[String]): Boolean =
    val need = stems(phrase)
    need.nonEmpty && need.subsetOf(said)

  def json(number: Int, l: Lexicon): String = Json.obj("number" -> Json.num(number),
    "words" -> Json.Obj(l.words.toVector.sortBy(_._1).map((k, e) => k -> Json.obj(
      "same" -> Json.Arr(e.same.map(Json.Str(_))), "opposite" -> Json.Arr(e.opposite.map(Json.Str(_))))))).render

  def parse(line: String): (Int, Lexicon) =
    val j = Json.fromText(line)
    def texts(x: Json) = x.list.collect { case Json.Str(s) => s }
    j("number").int -> Lexicon(j("words").record.map((k, e) => k -> Entry(texts(e("same")), texts(e("opposite")))).toMap)
