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
final class Scorer(repo: String, demos: Vector[Concrete], sum: Boolean):
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

  private def asked(c: Concrete, i: Int): String =
    s"Universe: ${c.universe}.\nTerms: ${c.dictionary.map(_._2).mkString("; ")}.\nSentence: ${c.premisses(i)}."

  private val shown: Vector[Json] = Vector(message("system", system)) ++ demos.flatMap(d =>
    d.goldPremisses.toVector.flatMap(_.zipWithIndex.flatMap((p, i) =>
      Vector(message("user", asked(d, i)), message("assistant", InWords.of(d, p))))))

  // the state fed with a context but its last token: a longer context feeds the rest only
  private var kept: Option[(Array[Int], Model.State)] = None

  /** the log-probability of each option's tokens after the context: their sum, or their mean */
  private def score(context: Array[Int], options: Vector[Array[Int]]): Vector[Double] =
    val (head, last) = (context.init, context.last)
    val s = new Scope
    try
      def feed(st: Model.State, ids: Array[Int]): Unit =
        if ids.nonEmpty then model.logits(ints(ids, 1, ids.length)(using s), st)(using s).eval()
      val st = kept match
        case Some((ids, st)) if head.startsWith(ids) => feed(st, head.drop(ids.length)); st
        case other =>
          other.foreach(_._2.close())
          val st = model.newState(); feed(st, head); st
      kept = Some(head -> st)
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
        if sum then total else total / o.length
      }
    finally s.close()

  val weigher: Reading.Weigher = (c: Concrete, i: Int, options: Vector[Premiss]) =>
    val context = chat.prompt(shown :+ message("user", asked(c, i)), Nil, Map("enable_thinking" -> false))
    score(context, options.map(o => chat.tokenizer.encode(InWords.of(c, o))))
