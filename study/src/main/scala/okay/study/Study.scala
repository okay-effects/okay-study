package okay.study

import okay.datalog.Parse
import okay.effects.json.{Json, JsonText}
import okay.llm.{Ask, Llm, Sampling}
import okay.scala3.*

/** THE STUDY: a teacher (the effect `Llm`) reads a lesson of a book and writes the symbolic model of its
 * problems, in two stages each with its own judge — the WORDS (how a problem and an answer are written: judged
 * by reading every sentence and saying every printed answer back), then the RULES over those words (judged by the
 * book's answers). The teacher is never trusted: a writing serves by what it passes, and only so */
object Study:
  /** one writing: what the teacher said, and how many of its checks it passed (none: it could not be read) */
  final case class Round(stage: String, reply: String, passed: Option[Int], of: Int, told: String)

  final case class Result(model: Option[Model], score: Option[Score], rounds: Vector[Round])

  private val patterns: String =
    """PATTERNS. A pattern is literal text with slots. `{X}` is a slot (a name beginning with a capital letter)
      |matching any text; `{X:regex}` gives the slot its own regular expression (no `}` inside), e.g.
      |`{N:[0-9]+}` for a number; in a regex `?` makes the character before it optional. A pattern must match
      |the WHOLE text; of several forms the first that matches is used. Braces around anything but a name
      |beginning with a capital letter are literal text. Outside a slot a pattern is NOT a regular expression:
      |`?`, `*`, `;?` there are literal characters. A number or a date in a sentence is kept as it is — the rules
      |written later can compare numbers (`N < 365`), so a threshold belongs to a rule, not to a pattern.""".stripMargin

  private val wordsFormat: String =
    s"""You write the WORDS of a model: how the problems of a book and their answers are written. The rules that
       |solve them come later; now only the notation. Answer with one JSON object in a ```json block:
       |
       |{
       |  "sentences": [{"pattern": "...", "facts": ["pred(arg, ...)", ...]}, ...],
       |  "terms":     [{"pattern": "...", "facts": ["pred(arg, ...)", ...]}, ...],
       |  "answers":   [{"pattern": "...", "goal": "pred(X, Y)", "unordered": false}, ...]
       |}
       |
       |$patterns
       |SENTENCES. Each form reads one kind of sentence of a problem into facts for a Datalog engine. A fact
       |template is `pred(arg, ...)` with a lowercase predicate name; an argument is text where `{X}` is replaced
       |by the slot's value. `{#}` is the number of the sentence in its problem (1, 2, ...): put it as the first
       |argument, so that the rules can tell the sentences apart. Example, of another kind of problem:
       |  {"pattern": "{X:[A-Z][a-z]+} is taller than {Y:[A-Z][a-z]+}", "facts": ["taller({#}, {X}, {Y})"]}
       |TERMS. Every slot value of a matched sentence is also read by the first "terms" form that matches it,
       |and that form's facts are added. The engine cannot build new constants, so this is the only way to relate
       |a term to another the sentence does not contain. Example, of another kind of problem — a plural and
       |its singular, both ways, whichever of the two the sentence has:
       |  {"pattern": "{T:[a-z]+}s", "facts": ["plural({T}, {T}s)", "name({T})", "name({T}s)"]},
       |  {"pattern": "{T:[a-z]+}",  "facts": ["plural({T}, {T}s)", "name({T})", "name({T}s)"]}
       |ANSWERS. One form per KIND of answer the book prints (not per problem). Its pattern is the book's own
       |notation with slots; its goal is `pred(X, Y)` — a predicate name you choose for that kind of answer, with
       |exactly the pattern's slots as its arguments — at least one: the engine has no atoms without arguments,
       |so an answer that is a fixed word is read by a slot (`{V:[a-z_]+}` with the goal `verdict(V)`, the rules
       |deriving `verdict(the_word)`). The rules written later will derive this predicate; two
       |answer forms never share a goal's predicate (two kinds of answer are two different claims). An
       |answer the book prints as `p + q` is two answers, each read by a form alone: never put ` + ` in a pattern.
       |`"unordered": true` when the order of the two arguments says nothing.""".stripMargin

  private val rulesFormat: String =
    """You write the RULES of a model: a Datalog theory that derives a problem's answers from its facts. Answer
      |with one JSON object in a ```json block:  {"rules": ["head(X, Y) :- p(X, Z), q(Z, Y).", ...]}
      |
      |THE DIALECT, strictly:
      |- A rule is `head :- literal, literal, ... .` and ends with a period. A fact or head is `pred(A, B)`.
      |- An argument is a Variable (begins with a capital letter) or a plain lowercase constant. NOTHING else:
      |  no nested terms like `f(g(X))`, no `+`, no lists, no strings built in rules, no arithmetic.
      |- A literal is `p(X, Y)`, a negation `not p(X, Y)`, or a comparison `X != Y`, `X = Y`, `X < Y`.
      |- Every variable of a head, of a negation and of a comparison must also occur in a positive literal of the
      |  same rule. Negation must be stratified: no recursion through `not`.
      |- Recursion is allowed and is how a chain of any length is followed:
      |    reach(X, Y) :- step(X, Y).
      |    reach(X, Z) :- step(X, Y), reach(Y, Z).
      |- Comment lines begin with `%`.
      |The rules must be GENERAL: they see only the facts of one problem, of any number of sentences. Never write
      |a problem's letters or its answer into a rule. The answers of a problem are ALL solutions of the answer
      |goals — they must be exactly the book's answer, nothing missing and nothing more.""".stripMargin

  private def shown(e: Exercise): String =
    s"Problem ${e.number}:\n${e.sentences.map("  " + _).mkString("\n")}\nThe book's answer: ${e.answer}"

  private def parts(answer: String): Vector[String] =
    answer.split("\\s+\\+\\s+").toVector.map(_.trim).filter(_.nonEmpty)

  /** THE WORDS' JUDGE: every sentence read, every printed answer read and said back the same, every goal a
   * predicate of its pattern's slots. The failures, in words for the teacher */
  def wordsFaults(m: Model, examples: Vector[Exercise]): Vector[String] =
    val sentences = examples.flatMap(_.sentences).distinct
      .filter(s => !m.sentences.exists(_.pattern.read(s).isDefined)).map(s => s"no sentence form reads: $s")
    val answers = examples.flatMap(e => parts(e.answer)).distinct.flatMap { a =>
      m.answers.iterator.flatMap(s => s.pattern.read(a).map(s -> _)).nextOption() match
        case None => Some(s"no answer form reads: $a")
        case Some((s, values)) =>
          val back = s.pattern.say(values)
          if back.replaceAll("\\s+", "") == a.replaceAll("\\s+", "") then None
          else Some(s"the answer $a is said back as $back by the pattern ${s.pattern.text}")
    }
    val goals = m.answers.flatMap(s => Parse.atom(s.goal) match
      case Left(e) => Some(s"the goal `${s.goal}` is not `pred(X, Y)` (at least one argument, a slot): $e")
      case Right(atom) =>
        val vars = atom.args.collect { case okay.datalog.Term.Var(n) => n }
        if vars.size == atom.args.size && vars.toSet == s.pattern.slots.toSet then None
        else Some(s"the goal `${s.goal}` must have exactly the slots of `${s.pattern.text}` as its arguments"))
    val preds = m.sentences.flatMap(_.facts.map(_.pred)).toSet
    val clash = m.answers.flatMap(s => Parse.atom(s.goal).toOption.map(_.pred)).filter(preds)
      .map(p => s"the answer predicate `$p` is also a sentence's fact: an answer must be derived, choose another name")
    val shared = m.answers.flatMap(s => Parse.atom(s.goal).toOption.map(_.pred)).groupBy(identity)
      .collect { case (p, uses) if uses.size > 1 => s"two answer forms share the goal predicate `$p`: each kind of " +
        "answer is its own claim, give each its own predicate" }
    (sentences ++ answers ++ goals ++ clash ++ shared).distinct

  def wordsTask(lesson: String, examples: Vector[Exercise]): String =
    s"""$wordsFormat
       |
       |THE LESSON, from the book:
       |
       |$lesson
       |
       |THE PROBLEMS, with the book's answers:
       |
       |${examples.map(shown).mkString("\n\n")}
       |
       |Write the words for these problems and their answers.""".stripMargin

  private def factsShown(m: Model, e: Exercise): String =
    Solve.facts(m, e.sentences)
      .fold(identity, _.map(f => s"${f.pred}(${f.args.mkString(", ")})").mkString("  ", "\n  ", ""))

  def rulesTask(lesson: String, words: Model, examples: Vector[Exercise]): String =
    s"""$rulesFormat
       |
       |THE LESSON, from the book:
       |
       |$lesson
       |
       |THE WORDS, already fixed (you cannot change them): how sentences become facts and which predicates say
       |the answers.
       |
       |${words.copy(rules = "").json}
       |
       |For example, the facts of Problem ${examples.head.number} are:
       |${factsShown(words, examples.head)}
       |
       |THE PROBLEMS, with the book's answers:
       |
       |${examples.map(shown).mkString("\n\n")}
       |
       |Write the rules that derive, from a problem's facts, the answer predicates
       |${words.answers.map(a => s"`${a.goal}` (said as ${a.pattern.text})").mkString(", ")}
       |so that every problem is answered as the book does — by the lesson's own method.""".stripMargin

  /** what the exercises said about the rules, told to the teacher: an error every problem shares is said once */
  def report(score: Score): String =
    val failures = score.verdicts.collect { case (_, Verdict.Failed(why)) => why }
    if failures.size == score.total && failures.distinct.size == 1 then
      s"The model could not run on any problem: ${failures.head}\nWrite the whole corrected rules."
    else
      val lines = score.verdicts.map {
        case (e, Verdict.Right_(_)) => s"Problem ${e.number}: right."
        case (e, Verdict.Wrong(said, expected)) =>
          s"Problem ${e.number}: WRONG. Your rules answer: " +
            s"${if said.isEmpty then "(nothing)" else said.mkString(" + ")}. The book's answer: $expected."
        case (e, Verdict.Failed(why)) => s"Problem ${e.number}: FAILED. $why"
      }
      s"Your rules answer ${score.said} problems as the book does.\n\n${lines.mkString("\n")}\n\n" +
        "Find what is wrong in the theory and write the whole corrected rules."

  /** the JSON object in a reply: the last fenced block, else from the first `{` to the last `}` */
  def json(reply: String): Either[String, Json] =
    val fenced = "(?s)```(?:json)?\\s*(\\{.*?\\})\\s*```".r.findAllMatchIn(reply).map(_.group(1)).toVector.lastOption
    val braces = for a <- Some(reply.indexOf('{')) if a >= 0; b = reply.lastIndexOf('}') if b > a
      yield reply.substring(a, b + 1)
    fenced.orElse(braces).toRight("no JSON object in the answer")
      .flatMap(t => JsonText.parse(t).left.map(e => s"not JSON: $e"))

  private def message(role: String, text: String): Json =
    Json.obj("role" -> Json.Str(role), "content" -> Json.Str(text))

  /** A STAGE: the task asked, each writing judged — (what it made, how many checks passed, what to tell back; no
   * faults: done) — at most `rounds` times; the best writing by checks passed. An empty reply (the thinking took
   * the budget) is asked again without thinking and is not put into the conversation */
  private def stage[A, R[_]: Has[Llm]](name: String, task: String, rounds: Int, maxTokens: Int, of: Int,
      thinking: Boolean)(
      judge: Json => Either[String, (A, Int, Option[String])]): (Option[(A, Int)], Vector[Round]) ! R =
    final case class At(messages: Vector[Json], thinking: Boolean, best: Option[(A, Int)], done: Vector[Round])
    val start = At(Vector(message("user", task)), thinking, None, Vector.empty)
    loop[At, (Option[(A, Int)], Vector[Round]), R](start) { at =>
      Llm.complete(Ask(at.messages, maxTokens = maxTokens, thinking = at.thinking,
        sampling = Sampling(temperature = Some(0.0)))).map { finished =>
        val reply = finished.reply.content
        val judged = json(reply).flatMap(judge)
        val (best, passed, told) = judged match
          case Right((a, n, tell)) => (if at.best.forall(_._2 < n) then Some(a -> n) else at.best, Some(n), tell)
          case Left(why) => (at.best, None, Some(s"Your answer could not be read: $why"))
        val all = at.done :+ Round(name, reply, passed, of, told.getOrElse("all passed"))
        if told.isEmpty || all.size >= rounds then Right(best -> all)
        else if reply.trim.isEmpty then Left(At(at.messages, false, best, all))
        else Left(At(at.messages :+ message("assistant", reply) :+ message("user",
          s"${told.get}\n\nAnswer with the whole corrected JSON object in a ```json block."), at.thinking, best, all))
      }
    }

  /** the model written for a lesson: the words, then the rules over them; the score is on the examples shown */
  def write[R[_]: Has[Llm]](lesson: String, examples: Vector[Exercise], rounds: Int, maxTokens: Int = 16000,
      thinking: Boolean = true): Result ! R =
    val checks = examples.flatMap(_.sentences).distinct.size + examples.flatMap(e => parts(e.answer)).distinct.size
    stage[Model, R]("words", wordsTask(lesson, examples), rounds, maxTokens, checks, thinking) { j =>
      Model.parse(j.render).map { m =>
        val faults = wordsFaults(m, examples)
        (m, checks - faults.size, Option.when(faults.nonEmpty)(faults.mkString("\n")))
      }
    }.flatMap {
      case (Some((words, passed)), wordRounds) if passed == checks =>
        stage[(Model, Score), R]("rules", rulesTask(lesson, words, examples), rounds, maxTokens, examples.size,
            thinking) { j =>
          val text = j.field("rules") match
            case Some(Json.Arr(lines)) => Right(lines.collect { case Json.Str(s) => s }.mkString("\n"))
            case Some(Json.Str(s)) => Right(s)
            case _ => Left("no \"rules\" in the object")
          text.map { rules =>
            val model = words.copy(rules = rules)
            val score = Score.of(model, examples)
            ((model, score), score.right, Option.when(score.right < score.total)(report(score)))
          }
        }.map((best, ruleRounds) => Result(best.map(_._1._1), best.map(_._1._2), wordRounds ++ ruleRounds))
      case (_, wordRounds) => pure(Result(None, None, wordRounds))
    }
