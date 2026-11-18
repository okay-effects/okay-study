package okay.study

import okay.llm.{HttpBackend, Llm}
import okay.scala3.*

import java.nio.file.{Files, Path}

/** Carroll's soriteses studied by a teacher behind a server (okay-mlx's gateway): the first `TRAIN` consistent
 * exercises of § 8 are shown with their answers, the rest are held out and only measured.
 *   TEACHER_URL (http://127.0.0.1:8090/v1), TEACHER_MODEL, TRAIN (10), ROUNDS (6 a stage),
 *   OUT (runs/carroll-sorites) */
object Main:
  private def env(name: String, default: String): String = sys.env.getOrElse(name, default)

  def main(args: Array[String]): Unit =
    val book = Book(Files.readString(Path.of("books/carroll-symbolic-logic.txt")))
    val exercises = Carroll.abstractSoriteses(book).filterNot(e => Carroll.misprinted.contains(e.number))
    val (train, held) = exercises.splitAt(env("TRAIN", "10").toInt)
    val out = Path.of(env("OUT", "runs/carroll-sorites"))
    Files.createDirectories(out)
    val teacher = HttpBackend(env("TEACHER_URL", "http://127.0.0.1:8090/v1"),
      env("TEACHER_MODEL", "mlx-community/Qwen3.6-35B-A3B-4bit"))
    val result = run(Llm.on(teacher)(Study.write[Llm](Carroll.soritesLesson(book), train, env("ROUNDS", "6").toInt,
      maxTokens = env("MAX_TOKENS", "16000").toInt)))
    result.rounds.zipWithIndex.foreach { (round, i) =>
      Files.writeString(out.resolve(s"round-${i + 1}-${round.stage}.reply.txt"), round.reply)
      Files.writeString(out.resolve(s"round-${i + 1}-${round.stage}.told.txt"), round.told)
      println(s"round ${i + 1}, ${round.stage}: " +
        round.passed.fold("unreadable")(n => s"$n of ${round.of}") +
        (if round.reply.isEmpty then " (empty reply)" else ""))
    }
    (result.model, result.score) match
      case (Some(model), Some(shown)) =>
        Files.writeString(out.resolve("model.json"), model.json)
        val heldOut = Score.of(model, held)
        println(s"the best model: shown ${shown.said}, HELD OUT ${heldOut.said}")
        heldOut.verdicts.foreach {
          case (_, Verdict.Right_(_)) => ()
          case (e, v) => println(s"  held out ${e.number}: $v")
        }
      case _ => println("no model was written")
