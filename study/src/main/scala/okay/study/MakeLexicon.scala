package okay.study

import okay.effects.json.Json
import okay.llm.{Ask, HttpBackend, Llm, Sampling}
import okay.scala3.*

import java.nio.file.{Files, Path, StandardOpenOption}

/** THE WORDS OF EACH EXERCISE'S TERMS, EXTRACTED FROM A MODEL: the teacher is shown a universe and its terms —
 * never the exercise's sentences — and writes, for each term, words for what has the attribute and for what has
 * not. One line an exercise, appended to OUT (an exercise there is not asked again).
 *   OUT (runs/lexicon/lexicon.jsonl), TEACHER_URL, TEACHER_MODEL */
object MakeLexicon:
  private def env(name: String, default: String): String = sys.env.getOrElse(name, default)

  def task(c: Concrete): String =
    s"""A logic puzzle is about ${c.universe}. Its dictionary has these terms (attributes of ${c.universe}):
       |${c.dictionary.map((k, v) => s"  $k = $v").mkString("\n")}
       |
       |A sentence of the puzzle may name a term by OTHER words. For each term write:
       |- "same": up to eight other words or short phrases a sentence might use for ${c.universe} that HAVE the
       |  attribute (synonyms, the noun for them, a verb phrase saying it);
       |- "opposite": up to eight words or short phrases for ${c.universe} that do NOT have it (antonyms, and
       |  what the others are called within this universe — as "Gentiles" for those who are not "Jews", "day-boys"
       |  for boys who are not "boarders", "slim" for not "fat").
       |Single words where possible. Answer with one JSON object:
       |{"${c.dictionary.head._1}": {"same": ["...", ...], "opposite": ["...", ...]}, ...} — a key for each term's letter.""".stripMargin

  def extract[R[_]: Has[Llm]](c: Concrete): Lexicon ! R =
    Llm.complete(Ask(Vector(Json.obj("role" -> Json.Str("user"), "content" -> Json.Str(task(c)))), maxTokens = 1500,
      thinking = false, sampling = Sampling(temperature = Some(0.0)))).map { f =>
      val j = Study.json(f.reply.content).getOrElse(Json.Null)
      def texts(e: Json, field: String) =
        e.field(field).collect { case Json.Arr(xs) => xs.collect { case Json.Str(s) => s.trim } }.getOrElse(Vector.empty)
      Lexicon(c.letters.flatMap(l => j.field(l).map(e => l -> Lexicon.Entry(texts(e, "same"), texts(e, "opposite")))).toMap)
    }

  def main(args: Array[String]): Unit =
    val all = Carroll.concreteSoriteses(Book(Files.readString(Path.of("books/carroll-symbolic-logic.txt"))))
    val out = Path.of(env("OUT", "runs/lexicon/lexicon.jsonl"))
    Files.createDirectories(out.getParent)
    val done = if Files.exists(out) then Files.readAllLines(out).toArray(Array.empty[String]).toVector
      .filter(_.trim.nonEmpty).map(Lexicon.parse(_)._1).toSet else Set.empty[Int]
    val teacher = HttpBackend(env("TEACHER_URL", "http://127.0.0.1:8090/v1"),
      env("TEACHER_MODEL", "mlx-community/Qwen3.6-35B-A3B-4bit"))
    for c <- all if !done(c.number) do
      val lexicon = run(Llm.on(teacher)(extract[Llm](c)))
      Files.writeString(out, Lexicon.json(c.number, lexicon) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND)
      println(s"${c.number}: ${lexicon.words.size} of ${c.letters.size} terms; " +
        lexicon.words.toVector.sortBy(_._1).take(2).map((k, e) => s"$k not: ${e.opposite.take(3).mkString(", ")}").mkString("; "))
