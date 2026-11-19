package okay.study

/** the reference reading of the CUKR card's rules answers UDSC's own cases as UDSC does */
class CukrSuite extends munit.FunSuite:
  for (what, e) <- Cukr.official do
    test(s"official question $what"):
      assertEquals(Verdict.of(Cukr.reference, e), Verdict.Right_(e.answer.split(" \\+ ").toVector.sorted),
        e.sentences.mkString("; "))

/** the model a teacher wrote from the official lesson (models/cukr.json) against UDSC's cases and against the
 * reference on cases over every day and every length of status */
class CukrTeacherSuite extends munit.FunSuite:
  private val model = Model.parse(java.nio.file.Files.readString(java.nio.file.Path.of("models/cukr.json")))
    .fold(e => fail(e), identity)

  for (what, e) <- Cukr.official do
    test(s"the teacher's model, official question $what"):
      assertEquals(Verdict.of(model, e), Verdict.Right_(e.answer.split(" \\+ ").toVector.sorted),
        e.sentences.mkString("; "))

  test("the teacher's model and the reference agree on three thousand cases, any day from 2025 to 2028"):
    val rnd = scala.util.Random(3)
    val differ = (1 to 3000).flatMap { k =>
      val e = Cukr.sampled(k, rnd)
      // any day and any length, not only the thresholds' sides
      val day = f"${2025 + rnd.nextInt(4)}%04d${1 + rnd.nextInt(12)}%02d${1 + rnd.nextInt(28)}%02d"
      val sentences = e.sentences.map {
        case s if s.startsWith("the application is made on") => s"the application is made on $day"
        case s if s.startsWith("the UKR status has lasted") => s"the UKR status has lasted ${rnd.nextInt(1500)} days without a break"
        case s => s
      }
      val (a, b) = (Solve.run(Cukr.reference, sentences).map(_.map(_._2).toSet), Solve.run(model, sentences).map(_.map(_._2).toSet))
      Option.when(a != b)(s"${sentences.mkString("; ")} → reference $a, teacher $b")
    }
    assertEquals(differ.take(5).toVector, Vector.empty[String], s"${differ.size} differ")
