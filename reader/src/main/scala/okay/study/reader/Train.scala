package okay.study.reader

import mlx.llm.{Adaptable, Chat, Lora, Model, Models}
import okay.study.{InWords, Pairs, Premiss}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.Random

/** THE SMALL READER TRAINED AS IT READS (mlx-scala's `Lora.stepChoices`): for a lesson's premiss, the options
 * said in its exercise's words — the meant one, the eleven others over the same two terms, and others at random
 * up to WIDE — and the loss the meant one's share among them. Lessons: `MakePairs`' file; one in ten exercises is
 * kept apart and only read (the share chosen right is printed).
 *   READER (mlx-community/Qwen3-0.6B-4bit), LESSONS (runs/pairs/lessons.jsonl), OUT (runs/adapters/reader.safetensors),
 *   EPOCHS (2), WIDE (24), BATCH (4), RANK (16), LR (2e-4), SEED (1),
 *   LOSS (both | listwise | generative): the listwise loss ALONE flattened a reader's scores in okay-desk
 *   (specs/frames.md §5.12), so by default each batch also takes a step of the generative loss — the meant
 *   option's own tokens after the premiss */
object Train:
  private def env(name: String, default: String): String = sys.env.getOrElse(name, default)

  def main(args: Array[String]): Unit =
    val seed = env("SEED", "1").toLong
    mlx.Mlx.randomSeed(seed)
    val rnd = Random(seed)
    val repo = env("READER", "mlx-community/Qwen3-0.6B-4bit")
    val dir = Models.snapshot(repo).getOrElse(sys.error(s"$repo not in the cache"))
    val chat = Chat.load(dir); val model = Model.load(dir, mapped = true); val tok = chat.tokenizer
    val lessons = Files.readAllLines(Path.of(env("LESSONS", "runs/pairs/lessons.jsonl"))).asScala.toVector
      .filter(_.trim.nonEmpty).map(Pairs.parse)
    val (apart, taught) = rnd.shuffle(lessons).splitAt(lessons.size / 10)
    val wide = env("WIDE", "24").toInt

    def choices(from: Vector[Pairs.Lesson]): Vector[Lora.Choice] =
      for l <- from; (i, meant) <- l.pairs yield
        val c = l.exercise
        val all = Premiss.options(c.letters)
        val near = all.filter(o => o.letters == meant.letters && o != meant)
        val far = rnd.shuffle(all.filterNot(o => o.letters == meant.letters)).take((wide - 1 - near.size).max(0))
        val options = rnd.shuffle(meant +: (near ++ far))
        val context = chat.prompt(Vector(Scorer.message("system", Scorer.system),
          Scorer.message("user", Scorer.asked(c, i))), Nil, Map("enable_thinking" -> false))
        Lora.Choice(context, options.map(o => tok.encode(InWords.of(c, o))), options.indexOf(meant))

    val train = choices(taught)
    val held = choices(apart)
    println(s"$repo: ${lessons.size} lessons, ${train.size} premisses to train on, ${held.size} kept apart, " +
      s"loss ${env("LOSS", "both")}")
    val lora = Lora(model.asInstanceOf[Adaptable], rank = env("RANK", "16").toInt,
      projs = Seq("q", "k", "v", "o", "gate", "up", "down"), lr = env("LR", "2e-4").toFloat, seed = seed)
    val batch = env("BATCH", "4").toInt
    val loss = env("LOSS", "both")
    def written(c: Lora.Choice): (Array[Int], Int) = (c.context ++ c.options(c.meant), c.context.length)
    def apartLoss(): Float =
      if held.isEmpty then 0f else held.grouped(batch).map(b => lora.evaluateChoices(b) * b.size).sum / held.size
    println(f"before: the loss apart ${apartLoss()}%.4f")
    val t0 = System.nanoTime()
    for e <- 1 to env("EPOCHS", "2").toInt do
      var sum = 0f; var k = 0; var shown = 0
      for b <- rnd.shuffle(train).grouped(batch) do
        if loss != "generative" then sum += lora.stepChoices(b) * b.size
        if loss != "listwise" then
          val g = lora.stepBatch(b.map(written))
          if loss == "generative" then sum += g * b.size
        k += b.size
        if k / 200 > shown then
          shown = k / 200
          println(f"epoch $e, $k/${train.size}: mean loss ${sum / k}%.4f (${(System.nanoTime() - t0) / 1e9}%.0f s)")
      println(f"epoch $e: mean loss ${sum / k.max(1)}%.4f; the loss apart ${apartLoss()}%.4f")
    val out = Path.of(env("OUT", "runs/adapters/reader.safetensors"))
    Files.createDirectories(out.getParent)
    lora.save(out.toString)
    println(s"saved $out")
    lora.close(); model.close()
