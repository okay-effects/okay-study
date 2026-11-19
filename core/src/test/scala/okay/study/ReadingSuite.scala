package okay.study

import java.nio.file.{Files, Path}

/** the reading's search with weighers made by hand: an oracle, and one that is wrong about a premiss */
class ReadingSuite extends munit.FunSuite:
  private def text(path: String) = Files.readString(Path.of(path))
  private val all = Carroll.concreteSoriteses(Book(text("books/carroll-symbolic-logic.txt")))
  private val model = Model.parse(text("models/carroll-sorites.json")).fold(e => fail(e), identity)

  /** the gold reading weighs 1, others 0; for premiss `spoil` of exercise 1 a wrong reading weighs 1.5 */
  private def weigher(spoil: Option[Int]): Reading.Weigher = (c, i, options) =>
    val gold = c.goldPremisses.get(i)
    val wrong = Premiss(false, "b", "d") // "No b are d": it leaves the term a out of exercise 1
    options.map(o => if o == gold then 1.0 else if spoil.contains(i) && o == wrong then 1.5 else 0.0)

  test("an oracle's reading alone is the book's, for every exercise, and gives the book's conclusion"):
    for c <- all do
      val read = Reading.alone(Reading.ranked(c, weigher(None)))
      assertEquals(read, c.goldPremisses.get, s"exercise ${c.number}")
      assertEquals(Carroll.concluded(model, c, read), Right(true), s"exercise ${c.number}")

  test("a premiss misread alone is put right together with the symbolic model"):
    val c = all.head
    val ranked = Reading.ranked(c, weigher(Some(1)))
    assertNotEquals(Reading.alone(ranked), c.goldPremisses.get)
    assertEquals(Carroll.concluded(model, c, Reading.alone(ranked)), Right(false))
    assertEquals(Reading.together(model, c, ranked), c.goldPremisses)

  test("the book's own readings have the sorites' shape, all sixty"):
    val not = all.filterNot(c => Reading.strict(model, c, c.goldPremisses.get)).map(_.number)
    assertEquals(not, Vector.empty[Int])
