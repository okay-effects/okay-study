package okay.study.reader

import okay.study.{Book, Carroll, Model, Reading, Sample}

import java.nio.file.{Files, Path, StandardOpenOption}

/** THE SMALL NEUROSYMBOLIC MODEL MEASURED on Carroll's concrete soriteses (§ 9): a small language model reads
 * each premiss by choosing among its readings said in words, the symbolic model (models/carroll-sorites.json)
 * concludes. The first DEMOS exercises are shown to the reader in its prompt; the rest are measured.
 *   READER (mlx-community/Qwen3-0.6B-4bit), ADAPTER (a trained one's file), DEMOS (3), FROM, ONLY (all),
 *   SCORE (mean | sum | pmi), TOP (4) */
object Bench:
  private def env(name: String, default: String): String = sys.env.getOrElse(name, default)

  def main(args: Array[String]): Unit =
    val book = Book(Files.readString(Path.of("books/carroll-symbolic-logic.txt")))
    val model = Model.parse(Files.readString(Path.of("models/carroll-sorites.json"))).fold(sys.error, identity)
    val all = Carroll.concreteSoriteses(book)
    val demos = all.take(env("DEMOS", "3").toInt)
    // FROM: the first exercise measured is FROM + 1 — one measured set for readers shown different numbers
    val asked = all.drop(env("FROM", demos.size.toString).toInt.max(demos.size)).take(env("ONLY", "1000").toInt)
    val repo = env("READER", "mlx-community/Qwen3-0.6B-4bit")
    val scorer = Scorer(repo, demos, env("SCORE", "mean"), sys.env.get("ADAPTER"))
    val top = env("TOP", "4").toInt
    println(s"$repo${sys.env.get("ADAPTER").fold("")(" with " + _)}, ${demos.size} exercises shown, " +
      s"${asked.size} measured, score ${env("SCORE", "mean")}, top $top")
    var premisses, premissesRight, nullitiesRight, aloneRight, togetherRight, togetherWrong, unread, inTop = 0
    var strictRight, strictWrong, strictUnread = 0
    val budget = env("BUDGET", "20000").toInt
    val kinds = scala.collection.mutable.LinkedHashMap.empty[String, Int]
    val t0 = System.nanoTime()
    for c <- asked do
      val gold = c.goldPremisses.get
      // DUMP: every option's weight, in `Premiss.options`' order — a reading tried again with no model
      val weigher: Reading.Weigher = (c, i, options) =>
        val w = scorer.weigher(c, i, options)
        sys.env.get("DUMP").foreach(f => Files.writeString(Path.of(f),
          "{\"number\":" + c.number + ",\"premiss\":" + i + ",\"weights\":[" + w.mkString(",") + "]}\n",
          StandardOpenOption.CREATE, StandardOpenOption.APPEND))
        w
      val ranked = Reading.ranked(c, weigher)
      val alone = Reading.alone(ranked)
      val right = alone.zip(gold).count(_ == _)
      premisses += gold.size; premissesRight += right
      nullitiesRight += alone.zip(gold).count((a, g) => a.nullity == g.nullity)
      // the anatomy of a misreading: which terms it took, and what it made of them
      for ((a, g), i) <- alone.zip(gold).zipWithIndex if a.nullity != g.nullity do
        val kind =
          if a.letters == g.letters then "the two terms right, a sign wrong"
          else if (a.letters & g.letters).nonEmpty then "one term wrong"
          else "both terms wrong"
        kinds(kind) = kinds.getOrElse(kind, 0) + 1
        println(s"    ${c.number}.${i + 1} [$kind] ${c.premisses(i)} | meant ${Sample.subscript(g)} read ${Sample.subscript(a)}")
      inTop += ranked.zip(gold).count((r, g) => r.take(top).exists(_._1 == g))
      val aloneOk = Carroll.concluded(model, c, alone) == Right(true)
      if aloneOk then aloneRight += 1
      val together = Reading.together(model, c, ranked, top)
      val outcome = together match
        case None => unread += 1; "not read"
        case Some(read) if Carroll.concluded(model, c, read) == Right(true) => togetherRight += 1; "right"
        case Some(_) => togetherWrong += 1; "WRONG"
      val strict = Reading.together(model, c, ranked, top, budget, Reading.strict) match
        case None => strictUnread += 1; "not read"
        case Some(read) if Carroll.concluded(model, c, read) == Right(true) => strictRight += 1; "right"
        case Some(_) => strictWrong += 1; "WRONG"
      println(f"${c.number}%2d: premisses $right of ${gold.size}; alone ${if aloneOk then "right" else "wrong"}; strict $strict; " +
        s"together $outcome")
    val n = asked.size
    println(s"premisses read right alone: $premissesRight of $premisses (as nullities: $nullitiesRight); " +
      s"the book's reading in the top $top: $inTop")
    println(s"misread as nullities: ${kinds.toVector.sortBy(-_._2).map((k, v) => s"$v $k").mkString("; ")}")
    println(s"conclusions, the reader alone: $aloneRight of $n right")
    println(s"conclusions, with the symbolic model: $togetherRight right, $togetherWrong wrong, $unread not read, of $n")
    println(s"conclusions, with the sorites' shape asked too: $strictRight right, $strictWrong wrong, " +
      s"$strictUnread not read, of $n")
    println(f"${(System.nanoTime() - t0) / 1e9 / math.max(1, n)}%.1f s an exercise")
