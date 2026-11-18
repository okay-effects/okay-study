package okay.study

import okay.effects.json.Json
import okay.llm.{Ask, Llm, Sampling}
import okay.scala3.*

/** THE STUDY: a teacher (the effect `Llm`) reads a lesson of a book and writes the symbolic model of its
 * problems; the book's worked exercises judge it; what failed is told back, and it writes again. The teacher is
 * never trusted: a model serves by the exercises it answers as the book does, and only so */
object Study:
  /** one writing: what the teacher said, the model read from it (or why not), its score on the exercises shown */
  final case class Round(reply: String, model: Either[String, Model], score: Option[Score])

  final case class Result(rounds: Vector[Round]):
    /** the best model written: by the exercises right, the earliest of equals */
    def best: Option[(Model, Score)] =
      rounds.collect { case Round(_, Right(m), Some(s)) => (m, s) }.maxByOption(_._2.right)

  val format: String =
    """You write a MODEL: a JSON object that lets an exact Datalog engine solve a book's problems. A problem is a
      |list of sentences; the model reads each sentence into facts, the rules derive the answers, and the answers
      |are said in the book's notation.
      |
      |{
      |  "sentences": [{"pattern": "...", "facts": ["pred(arg, ...)", ...]}, ...],
      |  "terms":     [{"pattern": "...", "facts": ["pred(arg, ...)", ...]}, ...],
      |  "rules":     ["head(X, Y) :- body(X, Z), other(Z, Y).", ...],
      |  "answers":   [{"pattern": "...", "goal": "pred(X, Y)", "unordered": false}, ...]
      |}
      |
      |PATTERNS. A pattern is literal text with slots. `{X}` is a slot (a name beginning with a capital letter)
      |matching any text; `{X:regex}` gives the slot its own regular expression (no `}` inside), e.g.
      |`{X:[a-z]'?}` for a letter with an optional prime. A pattern must match the WHOLE sentence. The first
      |sentence form that matches is used; a sentence that no form matches is an error.
      |FACTS. Each fact template is `pred(arg, ...)`; an argument is text in which `{X}` is replaced by the slot's
      |value, so `{X}'` builds the value with a prime added. `{#}` is the number of the sentence in its problem
      |(1, 2, ...) — use it when the rules must tell the sentences apart or count where a thing occurs.
      |TERMS. Every slot value of a matched sentence is also read by the first "terms" form that matches it, and
      |that form's facts are added. This is the only way to create constants the sentences do not contain (the
      |engine cannot build new constants): e.g. to relate a letter to its primed form both ways.
      |RULES. Datalog: `head :- literal, literal.` Variables begin with a capital letter. A literal is `p(X, Y)`,
      |a negation `not p(X, Y)`, or a comparison `X != Y`, `X = Y`, `X < Y`. Negation must be stratified (no
      |recursion through `not`), and every variable of a head, a negation or a comparison must occur in a positive
      |literal of the same rule. No arithmetic, no aggregates, no function symbols. Do not write constants in
      |rules unless they are plain lowercase words. Comment lines begin with `%`.
      |ANSWERS. Each answer form has a goal `pred(X, Y)` whose variables are exactly the slots of its pattern;
      |every solution of the goal is said by the pattern. The answers of a problem are ALL solutions of ALL goals,
      |and they must be exactly the book's answer — nothing missing, nothing more. The book's own answers are
      |read by the same patterns (`a + b` in the book means two answers). `"unordered": true` says the order of
      |the goal's arguments does not matter when answers are compared. The first answer form that matches is used.
      |
      |A small model of another kind of problem, to show the format only:
      |{
      |  "sentences": [{"pattern": "{X:[A-Z][a-z]+} is a parent of {Y:[A-Z][a-z]+}", "facts": ["parent({X}, {Y})"]}],
      |  "terms": [],
      |  "rules": ["anc(X, Y) :- parent(X, Y).", "anc(X, Z) :- parent(X, Y), anc(Y, Z).",
      |            "grand(X, Z) :- parent(X, Y), parent(Y, Z)."],
      |  "answers": [{"pattern": "{X:[A-Z][a-z]+} is a grandparent of {Y:[A-Z][a-z]+}", "goal": "grand(X, Y)",
      |               "unordered": false}]
      |}
      |
      |Answer with the model alone, as one JSON object in a ```json block.""".stripMargin

  private def shown(e: Exercise): String =
    s"Problem ${e.number}:\n${e.sentences.map("  " + _).mkString("\n")}\nThe book's answer: ${e.answer}"

  def task(lesson: String, examples: Vector[Exercise]): String =
    s"""$format
       |
       |THE LESSON, from the book:
       |
       |$lesson
       |
       |THE PROBLEMS, with the book's answers:
       |
       |${examples.map(shown).mkString("\n\n")}
       |
       |Write the model that answers every one of these problems as the book does, by the lesson's own method —
       |a general theory, not a table of these problems.""".stripMargin

  /** what the exercises said about a model, told to the teacher */
  def report(score: Score): String =
    val lines = score.verdicts.map {
      case (e, Verdict.Right_(_)) => s"Problem ${e.number}: right."
      case (e, Verdict.Wrong(said, expected)) =>
        s"Problem ${e.number}: WRONG. Your model answers: ${if said.isEmpty then "(nothing)" else said.mkString(" + ")}. " +
          s"The book's answer: $expected."
      case (e, Verdict.Failed(why)) => s"Problem ${e.number}: FAILED. $why"
    }
    s"""Your model answers ${score.said} problems as the book does.
       |
       |${lines.mkString("\n")}
       |
       |Find what is wrong in the theory and write the whole corrected model, as one JSON object in a ```json
       |block.""".stripMargin

  /** the JSON object in a reply: the last fenced block, else from the first `{` to the last `}` */
  def model(reply: String): Either[String, Model] =
    val fenced = "(?s)```(?:json)?\\s*(\\{.*?\\})\\s*```".r.findAllMatchIn(reply).map(_.group(1)).toVector.lastOption
    val braces = for a <- Some(reply.indexOf('{')) if a >= 0; b = reply.lastIndexOf('}') if b > a
      yield reply.substring(a, b + 1)
    fenced.orElse(braces).toRight("no JSON object in the answer").flatMap(Model.parse)

  private def message(role: String, text: String): Json = Json.obj("role" -> Json.Str(role), "content" -> Json.Str(text))

  /** the model written for a lesson, over at most `rounds` writings; it ends early when every example is right */
  def write[R[_]: Has[Llm]](lesson: String, examples: Vector[Exercise], rounds: Int, maxTokens: Int = 6000,
      thinking: Boolean = true): Result ! R =
    loop[(Vector[Json], Vector[Round]), Result, R]((Vector(message("user", task(lesson, examples))), Vector.empty)) {
      (messages, done) =>
        Llm.complete(Ask(messages, maxTokens = maxTokens, thinking = thinking,
          sampling = Sampling(temperature = Some(0.0)))).map { finished =>
          val reply = finished.reply.content
          val written = model(reply)
          val score = written.toOption.map(Score.of(_, examples))
          val all = done :+ Round(reply, written, score)
          if score.exists(s => s.right == s.total) || all.size >= rounds then Right(Result(all))
          else
            val told = score.map(report).getOrElse(
              s"The model could not be read: ${written.left.getOrElse("")}. Write the whole model again, as one JSON " +
                "object in a ```json block.")
            Left((messages :+ message("assistant", reply) :+ message("user", told), all))
        }
    }
