package okay.study

/** the reference reading of the CUKR card's rules answers UDSC's own cases as UDSC does */
class CukrSuite extends munit.FunSuite:
  for (what, e) <- Cukr.official do
    test(s"official question $what"):
      assertEquals(Verdict.of(Cukr.reference, e), Verdict.Right_(e.answer.split(" \\+ ").toVector.sorted),
        e.sentences.mkString("; "))
