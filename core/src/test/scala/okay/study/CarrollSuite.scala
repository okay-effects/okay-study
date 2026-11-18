package okay.study

import java.nio.file.{Files, Path}

/** THE LAW OF REACH: a model written in this format reproduces the book's own answers — Carroll's thirty
 * abstract soriteses (Book VIII, § 8), no language model anywhere */
class CarrollSuite extends munit.FunSuite:
  private def text(path: String) = Files.readString(Path.of(path))
  private val book = Book(text("books/carroll-symbolic-logic.txt"))
  private val exercises = Carroll.abstractSoriteses(book)
  private val reference = Model.parse(text("core/src/test/resources/carroll-sorites.reference.json"))

  test("the book's thirty exercises are cut out, each with its answer"):
    assertEquals(exercises.map(_.number), (1 to 30).toVector)
    assertEquals(exercises.head, Exercise(1, Vector("No c are d", "All a are d", "All b are c"),
      "a_{1}b_{0} + b_{1}a_{0}"))
    assertEquals(exercises.last.sentences.size, 10)
    assertEquals(exercises.last.answer, "k_{1}b'_{0}")

  test("the reference model answers every exercise as the book does"):
    val model = reference.fold(e => fail(e), identity)
    val score = Score.of(model, exercises)
    val missed = score.verdicts.collect { case (e, v) if !v.isInstanceOf[Verdict.Right_] => e.number }
    assertEquals(missed, Carroll.misprinted.keys.toVector.sorted)

  test("a model is the same model written and read back"):
    val model = reference.fold(e => fail(e), identity)
    assertEquals(Model.parse(model.json).map(_.json), Right(model.json))

  test("a sentence no form reads is an error, never skipped"):
    val model = reference.fold(e => fail(e), identity)
    assert(Solve.run(model, Vector("No c are d", "Some a are d")).left.exists(_.contains("Some a are d")))
