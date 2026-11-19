package okay.study

import java.nio.file.{Files, Path}

/** Carroll's sixty concrete soriteses as data, and the book's own solutions checked by the symbolic model */
class ConcreteSuite extends munit.FunSuite:
  private def text(path: String) = Files.readString(Path.of(path))
  private val all = Carroll.concreteSoriteses(Book(text("books/carroll-symbolic-logic.txt")))
  private val model = Model.parse(text("models/carroll-sorites.json")).fold(e => fail(e), identity)

  test("sixty exercises, the first as the book prints it"):
    assertEquals(all.map(_.number), (1 to 60).toVector)
    val first = all.head
    assertEquals(first.premisses, Vector("Babies are illogical", "Nobody is despised who can manage a crocodile",
      "Illogical persons are despised"))
    assertEquals(first.universe, "persons")
    assertEquals(first.dictionary, Vector("a" -> "able to manage a crocodile", "b" -> "babies", "c" -> "despised",
      "d" -> "logical"))
    assertEquals(first.gold, Vector("b_{1}d_{0}", "ac_{0}", "d'_{1}c'_{0}"))
    assertEquals(first.conclusion, "b_{1}a_{0}")
    assertEquals(first.answer, "Babies cannot manage crocodiles")

  test("a reading is said in the exercise's words"):
    val first = all.head
    assertEquals(first.goldPremisses.get.map(InWords.of(first, _)), Vector(
      "All persons that are babies are not logical.",
      "No persons are both able to manage a crocodile and despised.",
      "All persons that are not logical are despised."))

  test("the options over four letters: 24 nullities and 48 of `All`, the gold among them"):
    val options = Premiss.options(all.head.letters)
    assertEquals(options.count(!_.all), 24)
    assertEquals(options.count(_.all), 48)
    assert(all.head.goldPremisses.get.forall(options.contains))

  test("the book's solutions, run by the symbolic model, give the book's conclusions"):
    val report = all.map { c =>
      c.goldPremisses match
        case None => c.number -> s"the solution's premisses are not read: ${c.gold.size} of ${c.premisses.size}"
        case Some(read) => c.number -> Carroll.concluded(model, c, read).fold(identity, ok =>
          if ok then "right" else "another conclusion")
    }
    val missed = report.filter(_._2 != "right")
    assertEquals(missed.map(_._1), Carroll.concreteMisprinted.keys.toVector.sorted, missed.mkString("\n"))
