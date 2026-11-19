//> using scala 3.3.8
//> using repository https://okay-effects.github.io/okay-simple/maven
//> using dep io.github.okay-effects::okay-effects-json-value:0.1.1
//> using javaOpt -Xmx6g

// WORDS' RELATIONS READ FROM THE WEIGHTS ALONE (no text run through the model): for a word's token, the MLP
// neurons whose write vector points most at it in the vocabulary's space (its row of the output matrix, through
// the final norm's gain), and for each of them the tokens it PROMOTES and the tokens it SUPPRESSES — the question
// being whether a word's complement is among what its neurons suppress. Beside it, the plainest reading: the
// tokens nearest to the word by the embedding's cosine.
//   scala-cli run scripts/weights-lens.scala -- <model.safetensors> <vocab.json> word [word...]
import okay.effects.json.Json

import java.io.RandomAccessFile
import java.nio.{ByteBuffer, ByteOrder}
import java.nio.channels.FileChannel
import java.nio.file.{Files, Path}

final class SafeTensors(path: String):
  private val file = RandomAccessFile(path, "r")
  private val channel = file.getChannel
  private val headerSize =
    val b = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN); channel.read(b, 0); b.flip(); b.getLong
  private val header =
    val b = ByteBuffer.allocate(headerSize.toInt); channel.read(b, 8); Json.fromText(String(b.array, "UTF-8"))
  def names: Vector[String] = header.record.map(_._1).filter(_ != "__metadata__")
  def shape(name: String): Vector[Int] = header(name)("shape").list.map(_.int)
  /** a tensor as floats, rows first (BF16 or F32 as stored) */
  def floats(name: String): Array[Float] =
    val t = header(name)
    val Vector(from, to) = t("data_offsets").list.map(_.long): @unchecked
    val out = new Array[Float](shape(name).product)
    val dtype = t("dtype").string
    var at = 8 + headerSize + from; var k = 0
    val chunk = 64 << 20
    while at < 8 + headerSize + to do
      val n = math.min(chunk.toLong, 8 + headerSize + to - at).toInt
      val b = channel.map(FileChannel.MapMode.READ_ONLY, at, n).order(ByteOrder.LITTLE_ENDIAN)
      dtype match
        case "BF16" => while b.remaining >= 2 do { out(k) = java.lang.Float.intBitsToFloat((b.getShort & 0xffff) << 16); k += 1 }
        case "F32" => while b.remaining >= 4 do { out(k) = b.getFloat; k += 1 }
        case other => sys.error(s"$name: dtype $other")
      at += n
    out

@main def lens(model: String, vocabFile: String, words: String*): Unit =
  val st = SafeTensors(model)
  val vocab = Json.fromText(Files.readString(Path.of(vocabFile))).record.map((k, v) => k -> v.int)
  val byId = vocab.map(_.swap).toMap
  def shown(id: Int): String = byId.getOrElse(id, s"<$id>").replace("Ġ", "·").replace("Ċ", "\\n")
  val embedName = st.names.find(_.endsWith("embed_tokens.weight")).get
  val Vector(v, d) = st.shape(embedName): @unchecked
  println(s"$embedName: $v tokens × $d")
  val embed = st.floats(embedName)
  val gain = st.floats(st.names.find(n => n.endsWith("norm.weight") && !n.contains("layers")).get)
  val layers = st.names.filter(_.endsWith("mlp.down_proj.weight")).sortBy(_.split('.')(2).toInt)
  val Vector(_, neurons) = st.shape(layers.head): @unchecked
  println(s"${layers.size} layers × $neurons neurons")
  val norms = Array.tabulate(v)(t => math.sqrt((0 until d).map(i => embed(t * d + i).toDouble * embed(t * d + i)).sum))
  // only whole words: tokens that begin a word and are letters
  val wordy = (0 until v).filter(t => byId.get(t).exists(s => s.startsWith("Ġ") && s.length > 3 && s.drop(1).forall(_.isLetter)))

  /** every token's logit for a direction of the stream, through the final norm's gain */
  def project(direction: Array[Float]): Array[Double] =
    val g = Array.tabulate(d)(i => direction(i).toDouble * gain(i))
    Array.tabulate(v) { t => var s = 0.0; var i = 0; val at = t * d; while i < d do { s += embed(at + i) * g(i); i += 1 }; s }

  for word <- words do
    vocab.toMap.get("Ġ" + word) match
      case None => println(s"\n== $word: not one token")
      case Some(t) =>
        println(s"\n== $word (token $t)")
        val near = wordy.filter(_ != t).map(u =>
          u -> (0 until d).map(i => embed(t * d + i).toDouble * embed(u * d + i)).sum / (norms(t) * norms(u)))
          .sortBy(-_._2).take(14)
        println("  nearest by the embedding: " + near.map((u, c) => f"${shown(u)} $c%.2f").mkString(", "))
        // the neurons whose write vector, through the gain, points most at this token (by cosine)
        val target = Array.tabulate(d)(i => embed(t * d + i).toDouble * gain(i))
        val scored = layers.zipWithIndex.flatMap { (name, l) =>
          val w = st.floats(name) // [d, neurons], rows first
          (0 until neurons).map { j =>
            var dot = 0.0; var nn = 0.0; var i = 0
            while i < d do { val x = w(i * neurons + j); dot += x * target(i); nn += x.toDouble * x; i += 1 }
            (l, j, dot / math.sqrt(nn + 1e-12))
          }.sortBy(-_._3).take(3)
        }.sortBy(-_._3).take(6)
        for (l, j, score) <- scored do
          val w = st.floats(layers(l))
          val logits = project(Array.tabulate(d)(i => w(i * neurons + j)))
          val order = wordy.sortBy(u => -logits(u))
          println(f"  layer $l neuron $j (toward the word $score%.2f)")
          println("    promotes:   " + order.take(14).map(shown).mkString(" "))
          println("    suppresses: " + order.takeRight(14).reverse.map(shown).mkString(" "))
