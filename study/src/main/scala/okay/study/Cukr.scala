package okay.study

import okay.effects.json.Json

import java.nio.file.{Files, Path}
import scala.util.Random

/** THE FIRST LEGAL BOOK: the CUKR residence card (Poland, the Office for Foreigners' materials in `books/cukr/`,
 * fetched 2026-11-19). A case is a few canonical sentences about an applicant; the answer is `eligible` or the
 * reasons the application fails. The reference model is MY reading of the information sheet and of answers 6–20
 * and 29 of the official questions; `official` are UDSC's own cases, stated by hand, with what UDSC answers —
 * the check of that reading. `main` writes the lesson (the sheet and answers 1–10, as published) and the
 * exercises. Information, not legal advice; the law behind it changes. */
object Cukr:
  val reference: Model = Model.parse(
    """{"sentences": [
      |  {"pattern": "the applicant came to Poland from Ukraine after 23 February 2022", "facts": ["came(yes)"]},
      |  {"pattern": "the applicant is a child born in Poland after 23 February 2022", "facts": ["born(yes)"]},
      |  {"pattern": "PESEL UKR is active on the day of the application", "facts": ["pesel_today(yes)"]},
      |  {"pattern": "PESEL UKR is not active on the day of the application", "facts": ["pesel_today(no)"]},
      |  {"pattern": "PESEL UKR was active on 4 June 2025", "facts": ["pesel_june(yes)"]},
      |  {"pattern": "PESEL UKR was not active on 4 June 2025", "facts": ["pesel_june(no)"]},
      |  {"pattern": "the UKR status has lasted {N:[0-9]+} days without a break", "facts": ["days({N})"]},
      |  {"pattern": "the application is made on {D:[0-9]+}", "facts": ["applied({D})"]},
      |  {"pattern": "the mother holds a CUKR card", "facts": ["mother_card(yes)"]},
      |  {"pattern": "the mother does not hold a CUKR card", "facts": ["mother_card(no)"]},
      |  {"pattern": "the registers hold all the required data", "facts": ["registers(yes)"]},
      |  {"pattern": "the registers lack some required data", "facts": ["registers(no)"]}],
      | "terms": [],
      | "rules": ["refused(no_pesel_today) :- pesel_today(no).",
      |           "refused(no_pesel_june_2025) :- came(yes), pesel_june(no).",
      |           "refused(under_365_days) :- came(yes), days(N), N < 365.",
      |           "refused(mother_without_card) :- born(yes), mother_card(no).",
      |           "refused(too_early) :- applied(D), D < 20260504.",
      |           "refused(too_late) :- applied(D), D > 20270304.",
      |           "refused(registers_incomplete) :- registers(no).",
      |           "blocked(yes) :- refused(R).",
      |           "verdict(R) :- refused(R).",
      |           "verdict(eligible) :- applied(D), not blocked(yes)."],
      | "answers": [{"pattern": "{V:[a-z0-9_]+}", "goal": "verdict(V)", "unordered": false}]}""".stripMargin)
    .fold(sys.error, identity)

  private def answer(sentences: Vector[String]): String =
    Solve.run(reference, sentences).fold(sys.error, _.map(_._2).sorted.mkString(" + "))

  private val came = "the applicant came to Poland from Ukraine after 23 February 2022"
  private val born = "the applicant is a child born in Poland after 23 February 2022"
  private def pesel(on: Boolean) = s"PESEL UKR is ${if on then "" else "not "}active on the day of the application"
  private def june(on: Boolean) = s"PESEL UKR was ${if on then "" else "not "}active on 4 June 2025"
  private def days(n: Int) = s"the UKR status has lasted $n days without a break"
  private def made(d: Int) = s"the application is made on $d"
  private def mother(card: Boolean) = if card then "the mother holds a CUKR card" else "the mother does not hold a CUKR card"
  private def registers(full: Boolean) =
    if full then "the registers hold all the required data" else "the registers lack some required data"

  /** UDSC's own cases (the number of the official question), as I state them, and what UDSC answers */
  val official: Vector[(String, Exercise)] = Vector(
    "11: with PESEL UKR, when may I apply — from 4 May 2026" ->
      Exercise(1, Vector(came, pesel(true), june(true), days(900), made(20260503), registers(true)), "too_early"),
    "11: … and on that day" ->
      Exercise(2, Vector(came, pesel(true), june(true), days(900), made(20260504), registers(true)), "eligible"),
    "12: half a year, a leave, the status again — the periods do not add up" ->
      Exercise(3, Vector(came, pesel(true), june(true), days(183), made(20260601), registers(true)), "under_365_days"),
    "14: a temporary residence permit in 2025 — the PESEL UKR was lost" ->
      Exercise(4, Vector(came, pesel(false), june(true), days(900), made(20260601), registers(true)), "no_pesel_today"),
    "15: back in Poland only at the end of 2025 — not active on 4 June 2025" ->
      Exercise(5, Vector(came, pesel(true), june(false), days(400), made(20270101), registers(true)),
        "no_pesel_june_2025"),
    "16: a child born in Poland, the mother holds the card" ->
      Exercise(6, Vector(born, pesel(true), mother(true), made(20260601), registers(true)), "eligible"),
    "17: a child born in Poland, the mother without the card" ->
      Exercise(7, Vector(born, pesel(true), mother(false), made(20260601), registers(true)), "mother_without_card"),
    "17–19: a child who came from Ukraine, whatever the parents hold" ->
      Exercise(8, Vector(came, pesel(true), june(true), days(700), mother(false), made(20260601), registers(true)),
        "eligible"),
    "29: the last day is 4 March 2027" ->
      Exercise(9, Vector(came, pesel(true), june(true), days(900), made(20270305), registers(true)), "too_late"),
    "8: data missing from the registers — MOS blocks the application" ->
      Exercise(10, Vector(came, pesel(true), june(true), days(900), made(20260601), registers(false)),
        "registers_incomplete"))

  /** a case sampled: mostly a person who came, sometimes a child born in Poland; the thresholds' both sides */
  def sampled(number: Int, rnd: Random): Exercise =
    def one[A](xs: A*): A = xs(rnd.nextInt(xs.size))
    def mostly: Boolean = rnd.nextInt(5) > 0
    val date = one(20260301, 20260503, 20260504, 20260505, 20260815, 20261119, 20270304, 20270305, 20270601)
    val sentences =
      if rnd.nextInt(5) > 0 then
        Vector(came, pesel(mostly), june(mostly), days(one(30, 183, 200, 364, 365, 366, 400, 900)), made(date),
          registers(mostly))
      else Vector(born, pesel(mostly), mother(rnd.nextBoolean()), made(date), registers(mostly))
    Exercise(number, rnd.shuffle(sentences), answer(sentences))

  /** the lesson: the information sheet and the official answers 1–10, as published (page footers taken off) */
  def lesson: String =
    def clean(file: String): Vector[String] = Files.readString(Path.of("books/cukr", file)).linesIterator.toVector
      .filterNot(l => l.contains("Taborowa") || l.contains("02-699") || l.trim == "rodo" || l.contains('\f'))
    val faq = clean("faq-pl.txt")
    val upTo = faq.indexWhere(_.trim.startsWith("11. Jestem obywatelem Ukrainy"))
    (clean("info-pl.txt") ++ Vector("", "PYTANIA I ODPOWIEDZI (1–10)", "") ++ faq.take(upTo)).mkString("\n")

  def main(args: Array[String]): Unit =
    val rnd = Random(20261119)
    val exercises = (1 to 60).toVector.map(sampled(_, rnd))
    val dir = Path.of("books/cukr")
    Files.writeString(dir.resolve("lesson.txt"), lesson)
    Files.writeString(dir.resolve("exercises.jsonl"), exercises.map(e =>
      Json.obj("number" -> Json.num(e.number), "sentences" -> Json.Arr(e.sentences.map(Json.Str(_))),
        "answer" -> Json.Str(e.answer)).render).mkString("\n") + "\n")
    val kinds = exercises.groupBy(_.answer).view.mapValues(_.size).toVector.sortBy(-_._2)
    println(s"${exercises.size} exercises; answers: ${kinds.take(8).map((a, n) => s"$n $a").mkString("; ")}")
