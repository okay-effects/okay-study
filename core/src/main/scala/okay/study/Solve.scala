package okay.study

import okay.datalog.{Atom, Eval, Fact, Parse, Term}

/** an answer as the engine holds it: the goal's predicate and its arguments (sorted, where their order says
 * nothing) */
final case class Claim(pred: String, args: Vector[String])

/** A PROBLEM SOLVED BY A MODEL, exactly: its sentences read by the model's forms into facts, the theory run
 * (okay-datalog), the answers the goals give. Nothing here is learned */
object Solve:
  /** the facts a problem's sentences state; Left: a sentence no form reads — it is never skipped */
  def facts(m: Model, sentences: Vector[String]): Either[String, Vector[Fact]] =
    val out = Vector.newBuilder[Fact]
    val unread = sentences.zipWithIndex.flatMap { (s, i) =>
      m.sentences.iterator.flatMap(f => f.pattern.read(s).map(f -> _)).nextOption() match
        case None => Some(s)
        case Some((form, values)) =>
          val numbered = values + ("#" -> (i + 1).toString)
          form.facts.foreach(t => out += fact(t.fill(numbered)))
          for v <- values.values; term <- m.terms.iterator.flatMap(f => f.pattern.read(v).map(f -> _)).nextOption() do
            term._1.facts.foreach(t => out += fact(t.fill(term._2)))
          None
    }
    if unread.nonEmpty then Left(s"no form reads: ${unread.mkString(" | ")}") else Right(out.result().distinct)

  private def fact(f: (String, Vector[String])): Fact = Fact(f._1, f._2)

  private def claim(s: Saying, pred: String, args: Vector[String]): Claim =
    Claim(pred, if s.unordered then args.sorted else args)

  private def goal(s: Saying): Either[String, Atom] = Parse.atom(s.goal).left.map(e => s"the goal ${s.goal}: $e")

  /** the model's answers to a problem, as claims and as said */
  def run(m: Model, sentences: Vector[String]): Either[String, Vector[(Claim, String)]] =
    for
      stated <- facts(m, sentences)
      program <- Parse.program(m.rules).left.map(e => s"the rules: $e")
      result <- Eval.run(program, stated).left.map(e => s"the rules: $e")
      found <- m.answers.foldLeft[Either[String, Vector[(Claim, String)]]](Right(Vector.empty))((acc, s) =>
        for
          sofar <- acc
          g <- goal(s)
        yield sofar ++ result.query(g).map(values =>
          claim(s, g.pred, g.args.map { case Term.Var(n) => values(n); case Term.Const(c) => c }) ->
            s.pattern.say(values)))
    yield found.distinctBy(_._1)

  /** an answer as the book prints it (`a + b`: several), read by the model's own answer forms */
  def read(m: Model, answer: String): Either[String, Set[Claim]] =
    val parts = answer.split("\\s+\\+\\s+").toVector.map(_.trim).filter(_.nonEmpty)
    parts.foldLeft[Either[String, Set[Claim]]](Right(Set.empty))((acc, part) =>
      for
        sofar <- acc
        read <- m.answers.iterator.flatMap(s => s.pattern.read(part).map(s -> _)).nextOption()
          .toRight(s"no answer form reads: $part")
        g <- goal(read._1)
        args <- g.args.foldLeft[Either[String, Vector[String]]](Right(Vector.empty))((as, t) => as.flatMap(v =>
          t match
            case Term.Var(n) => read._2.get(n).map(v :+ _).toRight(s"the goal's $n is no slot of ${read._1.pattern.text}")
            case Term.Const(c) => Right(v :+ c)))
      yield sofar + claim(read._1, g.pred, args))

/** a problem with the book's answer, and what a model made of it */
final case class Exercise(number: Int, sentences: Vector[String], answer: String)

enum Verdict:
  /** the model's answers are the book's */
  case Right_(said: Vector[String])
  case Wrong(said: Vector[String], expected: String)
  /** the model could not state the problem, read the book's answer, or run */
  case Failed(why: String)

object Verdict:
  def of(m: Model, e: Exercise): Verdict =
    (for
      expected <- Solve.read(m, e.answer)
      got <- Solve.run(m, e.sentences)
    yield
      if got.map(_._1).toSet == expected then Verdict.Right_(got.map(_._2))
      else Verdict.Wrong(got.map(_._2), e.answer)).fold(Verdict.Failed(_), identity)

/** a model against a set of exercises */
final case class Score(verdicts: Vector[(Exercise, Verdict)]):
  def right: Int = verdicts.count(_._2.isInstanceOf[Verdict.Right_])
  def total: Int = verdicts.size
  def said: String = s"$right of $total"

object Score:
  def of(m: Model, exercises: Vector[Exercise]): Score = Score(exercises.map(e => e -> Verdict.of(m, e)))
