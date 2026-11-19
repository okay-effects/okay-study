package okay.study

import okay.effects.json.Json

import java.nio.file.{Files, Path}

/** the invented book: its exercises are the reference's, and the model a teacher wrote from the lesson alone
 * (models/lanterns.json) answers all forty as the book does */
class LanternsSuite extends munit.FunSuite:
  private val exercises = Files.readString(Path.of("books/lanterns/exercises.jsonl")).linesIterator
    .filter(_.trim.nonEmpty).map(Json.fromText)
    .map(j => Exercise(j("number").int, j("sentences").list.map(_.string), j("answer").string)).toVector

  test("the book's answers are the reference model's"):
    assertEquals(Score.of(Lanterns.reference, exercises).said, "40 of 40")

  test("the teacher's model, written from the lesson, answers all forty"):
    val model = Model.parse(Files.readString(Path.of("models/lanterns.json"))).fold(e => fail(e), identity)
    assertEquals(Score.of(model, exercises).said, "40 of 40")
    assertNotEquals(model.rules, Lanterns.reference.rules)
