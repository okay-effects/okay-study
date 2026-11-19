package okay.study

import okay.effects.json.{Json, JsonText}

import scala.util.matching.Regex

/** A PATTERN: words with slots — `{X}` (any text, the shortest) or `{X:regex}` (the slot's own class, no `}` in
 * it). A slot's name begins with a capital, so `_{1}` is plain text. Spaces match any run of spaces */
final case class Pattern(text: String):
  private val parts: Vector[Either[String, (String, String)]] =
    val out = Vector.newBuilder[Either[String, (String, String)]]
    var at = 0
    for m <- Pattern.Slot.findAllMatchIn(text) do
      if m.start > at then out += Left(text.substring(at, m.start))
      out += Right(m.group(1) -> Option(m.group(2)).getOrElse(".+?"))
      at = m.end
    if at < text.length then out += Left(text.substring(at))
    out.result()

  val slots: Vector[String] = parts.collect { case Right((name, _)) => name }.distinct

  private val regex: Regex =
    val seen = scala.collection.mutable.Set.empty[String]
    parts.map {
      case Left(words) => words.split("\\s+", -1).map(Regex.quote).mkString("\\s+")
      case Right((name, cls)) => if seen.add(name) then s"(?<$name>$cls)" else s"\\k<$name>"
    }.mkString("^\\s*", "", "\\s*$").r

  /** the slots' values, when the whole text is of this pattern */
  def read(s: String): Option[Map[String, String]] =
    regex.findFirstMatchIn(s).map(m => slots.map(n => n -> m.group(n)).toMap)

  /** the pattern said with these values */
  def say(values: Map[String, String]): String =
    parts.map { case Left(words) => words; case Right((name, _)) => values.getOrElse(name, s"{$name}") }.mkString

object Pattern:
  private val Slot = """\{([A-Z][A-Za-z0-9]*)(?::([^}]*))?\}""".r

/** A FORM: a pattern and the facts a text of it states — `pred(arg, …)`, each argument words with the pattern's
 * slots (`{X}`, `{X}'`) and `{#}`, the number of the sentence in its problem */
final case class Form(pattern: Pattern, facts: Vector[Form.Template])

object Form:
  final case class Template(pred: String, args: Vector[String]):
    def fill(values: Map[String, String]): (String, Vector[String]) =
      pred -> args.map(a => values.foldLeft(a)((s, kv) => s.replace(s"{${kv._1}}", kv._2)))

  private val Call = """^\s*([a-z][A-Za-z0-9_]*)\s*\((.*)\)\s*$""".r

  def template(text: String): Either[String, Template] = text match
    case Call(pred, args) => Right(Template(pred, args.split(",").toVector.map(_.trim)))
    case _ => Left(s"not a fact `pred(arg, …)`: $text")

/** AN ANSWER'S FORM: the pattern an answer is said by, and the goal whose solutions are the answers — the goal's
 * variables are the pattern's slots. `unordered`: the arguments' order says nothing (`ac_{0}` is `ca_{0}`) */
final case class Saying(pattern: Pattern, goal: String, unordered: Boolean)

/** THE SYMBOLIC MODEL OF A KIND OF PROBLEM, all of it data: the canonical sentences a problem is stated in
 * (`sentences`), what each term in them adds (`terms`: every slot's value read by the first form it fits), the
 * theory (`rules`: Datalog), and how answers are said (`answers`) */
final case class Model(sentences: Vector[Form], terms: Vector[Form], rules: String, answers: Vector[Saying]):
  def json: String = Json.pretty(Json.obj(
    "sentences" -> Json.Arr(sentences.map(Model.formJson)), "terms" -> Json.Arr(terms.map(Model.formJson)),
    "rules" -> Json.Arr(rules.linesIterator.map(Json.Str(_)).toVector),
    "answers" -> Json.Arr(answers.map(a => Json.obj("pattern" -> Json.Str(a.pattern.text),
      "goal" -> Json.Str(a.goal), "unordered" -> Json.Bool(a.unordered))))))

object Model:
  private def formJson(f: Form): Json = Json.obj("pattern" -> Json.Str(f.pattern.text),
    "facts" -> Json.Arr(f.facts.map(t => Json.Str(s"${t.pred}(${t.args.mkString(", ")})"))))

  private def all[A](xs: Vector[Either[String, A]]): Either[String, Vector[A]] =
    xs.collectFirst { case Left(e) => e }.toLeft(xs.collect { case Right(a) => a })

  private def form(j: Json): Either[String, Form] =
    for
      pattern <- j.field("pattern").collect { case Json.Str(s) => s }.toRight(s"a form without a pattern: ${j.render}")
      facts <- all(j.field("facts").map(_.list).getOrElse(Vector.empty).map {
        case Json.Str(s) => Form.template(s)
        case other => Left(s"a fact is text: ${other.render}")
      })
    yield Form(Pattern(pattern), facts)

  /** `rules` as one text or a list of lines */
  private def text(j: Json): Either[String, String] = j match
    case Json.Str(s) => Right(s)
    case Json.Arr(lines) =>
      all(lines.map { case Json.Str(s) => Right(s); case o => Left(s"a rule is text: ${o.render}") })
        .map(_.mkString("\n"))
    case other => Left(s"rules are text: ${other.render}")

  def parse(source: String): Either[String, Model] =
    for
      j <- JsonText.parse(source).left.map(e => s"not JSON: $e")
      _ <- j match { case _: Json.Obj => Right(()); case _ => Left("the model is a JSON object") }
      sentences <- all(j.field("sentences").map(_.list).getOrElse(Vector.empty).map(form))
      terms <- all(j.field("terms").map(_.list).getOrElse(Vector.empty).map(form))
      rules <- j.field("rules").map(text).getOrElse(Right(""))
      answers <- all(j.field("answers").map(_.list).getOrElse(Vector.empty).map(a =>
        for
          pattern <- a.field("pattern").collect { case Json.Str(s) => s }.toRight("an answer without a pattern")
          goal <- a.field("goal").collect { case Json.Str(s) => s }.toRight("an answer without a goal")
        yield Saying(Pattern(pattern), goal, a.field("unordered").contains(Json.Bool(true)))))
      _ <- if sentences.isEmpty then Left("no sentences") else Right(())
      _ <- if answers.isEmpty then Left("no answers") else Right(())
    yield Model(sentences, terms, rules, answers)
