package okay.study

import okay.effects.json.{Json, JsonText}
import okay.llm.Llm
import okay.scala3.*

import java.nio.file.{Files, Path}

/** the study's program with no model: a teacher that is wrong first and right after being told, stage by stage */
class StudySuite extends munit.FunSuite:
  private def text(path: String) = Files.readString(Path.of(path))
  private val exercises = Carroll.abstractSoriteses(Book(text("books/carroll-symbolic-logic.txt"))).take(5)
  private val reference = Model.parse(text("core/src/test/resources/carroll-sorites.reference.json")).toOption.get
  private val words = reference.copy(rules = "").json
  /** the words with the prime required: a sentence without one is not read */
  private val badWords = words.replace("[a-z]'?}", "[a-z]'}")
  private def rules(text: String) = Json.obj("rules" -> Json.Str(text)).render
  private val right = rules(reference.rules)
  /** existence forgotten: every answer of `All` is lost */
  private val wrong = rules(reference.rules.replace("exists(X) :- every(P, X, Y).", "exists(X) :- every(P, X, Y), nullity(P, X, Y)."))

  /** an answer per distinct message, in the order first seen */
  private def study(answers: Vector[String], rounds: Int): Study.Result =
    val seen = scala.collection.mutable.LinkedHashMap.empty[String, Int]
    val teacher = (m: String) => s"Here it is.\n```json\n${answers(seen.getOrElseUpdate(m, seen.size))}\n```"
    run(Llm.fake(teacher)(Study.write[Llm](lesson = "the lesson", exercises, rounds, thinking = false)))

  test("the reference's words pass the words' judge; with the prime required they do not"):
    assertEquals(Study.wordsFaults(reference, exercises), Vector.empty[String])
    val faults = Study.wordsFaults(Model.parse(badWords).toOption.get, exercises)
    assert(faults.contains("no sentence form reads: No c are d"), faults)
    assert(faults.exists(_.startsWith("no answer form reads: a_{1}b_{0}")), faults)

  test("words right, rules right: two writings"):
    val result = study(Vector(words, right), rounds = 3)
    assertEquals(result.rounds.map(r => r.stage -> r.passed), Vector("words" -> Some(18), "rules" -> Some(5)))
    assertEquals(result.score.map(_.said), Some("5 of 5"))

  test("each stage is told what failed and judged again"):
    val result = study(Vector(badWords, words, wrong, right), rounds = 3)
    assertEquals(result.rounds.map(_.stage), Vector("words", "words", "rules", "rules"))
    assert(result.rounds.head.told.contains("no sentence form reads"), result.rounds.head.told)
    assert(result.rounds(2).told.contains("Problem 1: WRONG"), result.rounds(2).told)
    assertEquals(result.score.map(_.said), Some("5 of 5"))

  test("words that never pass: no rules are asked, no model"):
    val result = study(Vector(badWords, "not a model", badWords), rounds = 2)
    assertEquals(result.rounds.map(_.stage), Vector("words", "words"))
    assertEquals(result.model, None)

  test("the rounds are bounded, and the best rules are kept"):
    val result = study(Vector(words, wrong, rules("p(X) :- q(X)."), wrong), rounds = 2)
    assertEquals(result.rounds.size, 3)
    assertEquals(result.score.map(_.said), Some("2 of 5"))

  test("a model's rules read back as the same JSON"):
    assertEquals(JsonText.parse(right).map(_("rules").string), Right(reference.rules))
