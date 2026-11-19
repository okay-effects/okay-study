package okay.study

import java.nio.file.{Files, Path}
import scala.util.Random

/** the sampled soriteses are problems of the symbolic model: each is accepted, and reads back from its own form */
class SampleSuite extends munit.FunSuite:
  private val model = Model.parse(Files.readString(Path.of("models/carroll-sorites.json"))).fold(e => fail(e), identity)

  test("a thousand sampled soriteses: every term used, a conclusion drawn, the subscript form read back"):
    val rnd = Random(7)
    for k <- 1 to 1000 do
      val terms = Vector("red", "round", "sweet", "heavy", "old", "tall", "kind").take(4 + rnd.nextInt(4))
      val c = Sample.exercise(k, "things", terms, rnd)
      val read = c.goldPremisses.getOrElse(fail(s"$k: ${c.gold}"))
      assertEquals(read.size, terms.size - 1)
      assert(Reading.accepted(model, c, read), s"$k: ${c.gold}")
      assert(read.forall(Premiss.options(c.letters).contains), s"$k: ${c.gold}")
