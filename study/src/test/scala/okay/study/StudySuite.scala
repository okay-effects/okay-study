package okay.study

import okay.llm.Llm
import okay.scala3.*

import java.nio.file.{Files, Path}

/** the study's program with no model: a teacher that is wrong first and right after being told */
class StudySuite extends munit.FunSuite:
  private def text(path: String) = Files.readString(Path.of(path))
  private val exercises = Carroll.abstractSoriteses(Book(text("books/carroll-symbolic-logic.txt"))).take(5)
  private val right = text("core/src/test/resources/carroll-sorites.reference.json")
  /** the reference with existence forgotten: every answer of `All` is lost */
  private val wrong = right.replace("\"exists(X) :- every(P, X, Y).\",", "\"exists(X) :- every(P, X, Y), nullity(P, X, Y).\",")

  private def study(answers: Vector[String], rounds: Int): Study.Result =
    // the fake may ask for one answer more than once: an answer per distinct message, in the order first seen
    val seen = scala.collection.mutable.LinkedHashMap.empty[String, Int]
    val teacher = (m: String) => s"Here it is.\n```json\n${answers(seen.getOrElseUpdate(m, seen.size))}\n```"
    run(Llm.fake(teacher)(Study.write[Llm](lesson = "the lesson", exercises, rounds, thinking = false)))

  test("a model right at once ends the study"):
    val result = study(Vector(right), rounds = 3)
    assertEquals(result.rounds.size, 1)
    assertEquals(result.best.map(_._2.said), Some("5 of 5"))

  test("a wrong model is told what failed, and the next writing is judged again"):
    val result = study(Vector(wrong, right), rounds = 3)
    assertEquals(result.rounds.map(_.score.map(_.right)), Vector(Some(2), Some(5)))
    assertEquals(result.best.map(_._2.said), Some("5 of 5"))

  test("the rounds are bounded, and the best writing is kept"):
    val result = study(Vector(wrong, "not a model", wrong), rounds = 3)
    assertEquals(result.rounds.size, 3)
    assert(result.rounds(1).model.isLeft)
    assertEquals(result.best.map(_._2.said), Some("2 of 5"))

  test("the report names each failure with the book's answer"):
    val model = Model.parse(wrong).fold(e => fail(e), identity)
    val report = Study.report(Score.of(model, exercises))
    assert(report.contains("Problem 1: WRONG"), report)
    assert(report.contains("The book's answer: a_{1}b_{0} + b_{1}a_{0}"), report)
