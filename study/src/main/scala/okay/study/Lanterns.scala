package okay.study

import okay.effects.json.Json

import java.nio.file.{Files, Path}
import scala.util.Random

/** A BOOK NO MODEL HAS READ: "the calculus of lanterns", made up here (2026-11-19) so that a teacher's theory
 * of it can only come from its lesson. Things tend one another, some are kindled, some veiled; warmth passes
 * along tending but not THROUGH a veiled thing; the problems ask what is dark and where the rifts are. The
 * reference model below gives the answers; the teacher never sees it — only `books/lanterns/lesson.txt` and the
 * exercises shown. `main` writes the book: the lesson with two worked examples, and forty exercises. */
object Lanterns:
  val reference: Model = Model.parse(
    """{"sentences": [
      |  {"pattern": "{X:[a-z]} tends {Y:[a-z]}", "facts": ["tends({X}, {Y})"]},
      |  {"pattern": "{X:[a-z]} is kindled", "facts": ["kindled({X})"]},
      |  {"pattern": "{X:[a-z]} is veiled", "facts": ["veiled({X})"]}],
      | "terms": [{"pattern": "{T:[a-z]}", "facts": ["thing({T})"]}],
      | "rules": ["reaches(X, Y) :- tends(X, Y).",
      |           "reaches(X, Y) :- tends(X, Z), not veiled(Z), reaches(Z, Y).",
      |           "warm(X) :- kindled(X).",
      |           "warm(Y) :- kindled(K), reaches(K, Y).",
      |           "dark(X) :- thing(X), not warm(X).",
      |           "rift(X, Y) :- tends(X, Y), warm(X), dark(Y)."],
      | "answers": [{"pattern": "{X:[a-z]}°", "goal": "dark(X)", "unordered": false},
      |             {"pattern": "{X:[a-z]}|{Y:[a-z]}", "goal": "rift(X, Y)", "unordered": false}]}""".stripMargin)
    .fold(sys.error, identity)

  private def answer(sentences: Vector[String]): String =
    Solve.run(reference, sentences).fold(sys.error, _.map(_._2).sorted.mkString(" + "))

  /** a problem: five to seven things, tending among them, one or two kindled, one or two veiled; at least one
   * dark thing and, three times in four, a rift */
  def problem(number: Int, rnd: Random): Exercise =
    val things = rnd.shuffle("pqrstuvw".map(_.toString).toVector).take(5 + rnd.nextInt(3))
    def pick(n: Int) = rnd.shuffle(things).take(n)
    val pairs = for a <- things; b <- things if a != b yield a -> b
    val sentences = rnd.shuffle(
      rnd.shuffle(pairs).take(things.size + rnd.nextInt(3)).map((a, b) => s"$a tends $b") ++
        pick(1 + rnd.nextInt(2)).map(t => s"$t is kindled") ++ pick(1 + rnd.nextInt(2)).map(t => s"$t is veiled"))
    val said = answer(sentences)
    if said.contains("°") && (said.contains("|") || rnd.nextInt(4) == 0) then Exercise(number, sentences, said)
    else problem(number, rnd)

  private def worked(e: Exercise): String =
    s"${e.sentences.map("    " + _ + ";").mkString("\n")}\n  Answer: ${e.answer}."

  def lesson(examples: Vector[Exercise]): String =
    s"""THE CALCULUS OF LANTERNS

§ 1. Things and what is said of them.

A Problem speaks of a few Things, each named by a small letter. Three kinds of sentence are used, and no others:

    "p tends q"      — the Thing p tends the Thing q. Tending has a direction: that p tends q does not say that
                       q tends p.
    "p is kindled"   — the Thing p is kindled.
    "p is veiled"    — the Thing p is veiled.

§ 2. Warmth.

A kindled Thing is warm.

Warmth passes along tending. If a warm Thing tends another, the other is warmed by it — with one exception, which
is the whole of this Calculus: a VEILED Thing, though it may itself be warm, passes no warmth on, unless it is
itself kindled. Thus warmth starts at the kindled Things and travels from each Thing to the Things it tends, and
from those onwards, stopping at every veiled Thing it comes to: the veiled Thing is warmed, and what it tends gets
nothing from it. (A Thing that is both kindled and veiled does warm what it tends: its veil stops only warmth that
came to it from elsewhere, and it is a source in its own right.)

A Thing may be warmed along one way though another way to it is stopped; one way is enough.

§ 3. Darkness.

A Thing that is not warm is DARK. Every Thing the Problem names is either warm or dark, and never both. The
darkness of a Thing p is written  p°.

§ 4. Rifts.

Where a warm Thing tends a dark one, there is a RIFT between them. The rift from the warm Thing p to the dark
Thing q is written  p|q,  the warm Thing first.

§ 5. The Answer of a Problem.

The Answer of a Problem is the list of all its dark Things and all its rifts, joined by " + ", nothing else and
nothing left out. Warm Things are not listed.

§ 6. Examples worked.

(1)
${worked(examples(0))}

(2)
${worked(examples(1))}
"""

  def main(args: Array[String]): Unit =
    val rnd = Random(20261119)
    val exercises = (1 to 42).toVector.map(problem(_, rnd))
    val dir = Path.of("books/lanterns")
    Files.createDirectories(dir)
    Files.writeString(dir.resolve("lesson.txt"), lesson(exercises.take(2)))
    Files.writeString(dir.resolve("exercises.jsonl"), exercises.drop(2).zipWithIndex.map((e, i) =>
      Json.obj("number" -> Json.num(i + 1), "sentences" -> Json.Arr(e.sentences.map(Json.Str(_))),
        "answer" -> Json.Str(e.answer)).render).mkString("\n") + "\n")
    println(s"${exercises.size - 2} exercises; e.g. ${exercises(2).sentences.mkString("; ")} → ${exercises(2).answer}")
