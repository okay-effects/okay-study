package okay.study

import okay.effects.json.Json

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** THE EXTRACTED WORDS IN THE READING, measured with no model running: a reader's weights as its bench dumped
 * them (DUMP), each option's weight raised by LAMBDA for every term the lexicon finds evidence for in the
 * sentence; premisses as nullities and conclusions, for exercises FROM+1 on. Also: of the premisses the reader
 * misread alone, how many the lexicon alone has the meant words for.
 *   DUMP (runs/dump-trained-all.jsonl), LEXICON (runs/lexicon/lexicon.jsonl), LAMBDAS (0,2,5,10,20), FROM (10), TO (60) */
object LexiconBench:
  private def env(name: String, default: String): String = sys.env.getOrElse(name, default)

  def main(args: Array[String]): Unit =
    val all = Carroll.concreteSoriteses(Book(Files.readString(Path.of("books/carroll-symbolic-logic.txt"))))
    val model = Model.parse(Files.readString(Path.of("models/carroll-sorites.json"))).fold(sys.error, identity)
    def lines(f: String) = Files.readAllLines(Path.of(f)).asScala.toVector.filter(_.trim.nonEmpty)
    val lexicons = lines(env("LEXICON", "runs/lexicon/lexicon.jsonl")).map(Lexicon.parse).toMap
    val weights = lines(env("DUMP", "runs/dump-trained-all.jsonl")).map { l =>
      val j = Json.fromText(l); (j("number").int, j("premiss").int) -> j("weights").list.map(_.double)
    }.toMap
    val asked = all.filter(c => c.number > env("FROM", "10").toInt && c.number <= env("TO", "60").toInt)
      .filter(c => lexicons.contains(c.number) && weights.contains(c.number -> 0))
    println(s"${asked.size} exercises, ${asked.map(_.premisses.size).sum} premisses")
    for lambda <- env("LAMBDAS", "0,2,5,10,20").split(",").map(_.toDouble) do
      var right, alone, strict, strictWrong, covered, misread = 0
      for c <- asked do
        val gold = c.goldPremisses.get
        val lexicon = lexicons(c.number)
        val weigh: Reading.Weigher = (c, i, options) =>
          weights(c.number -> i).zip(options).map((w, o) => w + lambda * lexicon.evidence(c, c.premisses(i), o))
        val ranked = Reading.ranked(c, weigh)
        val read = Reading.alone(ranked)
        right += read.zip(gold).count((a, g) => a.nullity == g.nullity)
        if Carroll.concluded(model, c, read) == Right(true) then alone += 1
        Reading.together(model, c, ranked, 4, 20000, Reading.strict) match
          case Some(r) if Carroll.concluded(model, c, r) == Right(true) => strict += 1
          case Some(_) => strictWrong += 1
          case None => ()
        if lambda == 0 then
          for ((a, g), i) <- read.zip(gold).zipWithIndex if a.nullity != g.nullity do
            misread += 1
            if lexicon.evidence(c, c.premisses(i), g) == 2 then covered += 1
      println(f"lambda $lambda%4.1f: premisses as nullities $right; conclusions alone $alone, with the shape $strict " +
        s"(wrong $strictWrong)" + (if lambda == 0 then s"; of $misread misread, the lexicon has both terms' words for $covered" else ""))
