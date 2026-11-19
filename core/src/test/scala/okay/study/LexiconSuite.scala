package okay.study

/** a lexicon's exact match: the sentence's own words against a term's words, with the reading's sign */
class LexiconSuite extends munit.FunSuite:
  private val c = Concrete(14, Vector("No Gentiles have hooked noses"), "persons",
    Vector("a" -> "good hands at a bargain", "b" -> "hook-nosed", "c" -> "Jews", "d" -> "making money"),
    Vector("bc'_{0}"), "", "")
  private val lexicon = Lexicon(Map("c" -> Lexicon.Entry(Vector("Hebrews"), Vector("Gentiles", "non-Jews")),
    "b" -> Lexicon.Entry(Vector("hooked noses"), Vector("straight-nosed"))))

  test("the sentence's Gentiles is evidence for not-Jews, and none for Jews"):
    val s = c.premisses.head
    assertEquals(lexicon.evidence(c, s, Premiss(false, "b", "c'")), 2) // hooked noses; Gentiles
    assertEquals(lexicon.evidence(c, s, Premiss(false, "b", "c")), 1) // hooked noses only
    assertEquals(lexicon.evidence(c, s, Premiss(false, "a'", "b")), 1) // the reader's misreading
    assertEquals(lexicon.evidence(c, s, Premiss(false, "a", "d")), 0)

  test("a lexicon is the same written and read back"):
    assertEquals(Lexicon.parse(Lexicon.json(14, lexicon)), 14 -> lexicon)
