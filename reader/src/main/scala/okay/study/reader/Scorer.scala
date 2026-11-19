package okay.study.reader

import mlx.{Dtype, Scope}
import mlx.Mlx.*
import mlx.llm.{Chat, Model, Models}
import okay.effects.json.Json
import okay.study.{Concrete, InWords, Premiss, Reading}

import java.nio.file.{Files, Path}

/** THE SMALL MODEL AS A WEIGHER (mlx-scala, Apple's GPU): a premiss's options, each said in the exercise's own
 * words, weighed by the model's own probability of writing them after the premiss — nothing is generated, the
 * model only chooses. `demos`: exercises shown before, each premiss with the book's reading (no training) */
/** `mode`: "mean" or "sum" of the option's token log-probabilities; "pmi" — what the TEXT adds: the mean
 * after the premiss minus the mean after the same terms with no sentence (okay-desk theory §11.1) */
final class Scorer(repo: String, demos: Vector[Concrete], mode: String):
  private val dir = Models.snapshot(repo).orElse(Some(Path.of(repo)).filter(Files.isDirectory(_)))
    .getOrElse(sys.error(s"$repo not in the cache"))
  private val chat = Chat.load(dir)
  private val model = Model.load(dir, mapped = true)

  private val system =
    "You rewrite a sentence in standard form, using only the given terms. A standard form is either " +
      "\"All U that are X are Y.\" or \"No U are both X and Y.\", where U is the universe and X and Y are " +
      "terms, or terms with \"not\" before them."

  private def message(role: String, content: String): Json =
    Json.obj("role" -> Json.Str(role), "content" -> Json.Str(content))

  private def about(c: Concrete, sentence: String): String =
    s"Universe: ${c.universe}.\nTerms: ${c.dictionary.map(_._2).mkString("; ")}.\nSentence: $sentence"

  private def asked(c: Concrete, i: Int): String = about(c, c.premisses(i) + ".")

  private val shown: Vector[Json] = Vector(message("system", system)) ++ demos.flatMap(d =>
    d.goldPremisses.toVector.flatMap(_.zipWithIndex.flatMap((p, i) =>
      Vector(message("user", asked(d, i)), message("assistant", InWords.of(d, p))))))

  private def prompt(last: String): Array[Int] =
    chat.prompt(shown :+ message("user", last), Nil, Map("enable_thinking" -> false))

  // what every context begins with (the system's words and the exercises shown): fed once, kept by a checkpoint
  private val common: Array[Int] =
    val (a, b) = (prompt("A"), prompt("B"))
    a.take(a.indices.takeWhile(i => i < b.length && a(i) == b(i)).size)
  private val state: Model.State = model.newState()
  private val begun: Model.Checkpoint =
    val s = new Scope
    try { model.logits(ints(common, 1, common.length)(using s), state)(using s).eval(); state.checkpoint() }
    finally s.close()

  /** the log-probability of each option's tokens after the context: their sum, or their mean */
  private def score(context: Array[Int], options: Vector[Array[Int]]): Vector[Double] =
    val (head, last) = (context.init, context.last)
    val s = new Scope
    try
      def feed(st: Model.State, ids: Array[Int]): Unit =
        if ids.nonEmpty then model.logits(ints(ids, 1, ids.length)(using s), st)(using s).eval()
      require(head.startsWith(common), "a context begins with the common words")
      val st = state
      st.rewind(begun, 0): Unit
      feed(st, head.drop(common.length))
      options.map { o =>
        val at = st.checkpoint()
        val input = last +: o.init
        val lp = Scope(s) {
          val logits = astype(model.logitsAll(ints(input, 1, input.length), st), Dtype.Float32)
          val v = logits.dim(-1)
          takeAlong(softmax(reshape(logits, input.length, v), 1), ints(o, input.length, 1), 1).toFloats.take(o.length)
        }
        st.rewind(at, 0): Unit
        at.close()
        val total = lp.map(p => math.log(math.max(p, 1e-30f).toDouble)).sum
        if mode == "sum" then total else total / o.length
      }
    finally s.close()

  val weigher: Reading.Weigher = (c: Concrete, i: Int, options: Vector[Premiss]) =>
    val said = options.map(o => chat.tokenizer.encode(InWords.of(c, o)))
    val after = score(prompt(asked(c, i)), said)
    if mode != "pmi" then after
    else after.zip(score(prompt(about(c, "(not given)")), said)).map(_ - _)
