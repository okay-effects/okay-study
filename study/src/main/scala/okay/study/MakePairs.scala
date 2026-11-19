package okay.study

import okay.llm.{HttpBackend, Llm}
import okay.scala3.*

import java.nio.file.{Files, Path, StandardOpenOption}
import scala.util.Random

/** the reader's lessons made with the teacher behind the gateway, a theme at a time, appended to OUT (a theme
 * already there is not made again). The book's exercises 1–SHOWN are the style shown; no sentence of the book's
 * other exercises may come back as a lesson.
 *   THEMES (150), EACH (2 soriteses a theme), SHOWN (10), OUT (runs/pairs/lessons.jsonl), SEED (1) */
object MakePairs:
  private def env(name: String, default: String): String = sys.env.getOrElse(name, default)

  def main(args: Array[String]): Unit =
    val book = Book(Files.readString(Path.of("books/carroll-symbolic-logic.txt")))
    val (shown, measured) = Carroll.concreteSoriteses(book).splitAt(env("SHOWN", "10").toInt)
    val theBooks = measured.flatMap(_.premisses).map(_.toLowerCase.filter(_.isLetter)).toSet
    val out = Path.of(env("OUT", "runs/pairs/lessons.jsonl"))
    Files.createDirectories(out.getParent)
    val themesFile = out.resolveSibling("themes.txt")
    val teacher = HttpBackend(env("TEACHER_URL", "http://127.0.0.1:8090/v1"),
      env("TEACHER_MODEL", "mlx-community/Qwen3.6-35B-A3B-4bit"))
    def teach[A](p: A ! Llm): A = run(Llm.on(teacher)(p))
    val themes =
      if Files.exists(themesFile) then Files.readAllLines(themesFile).toArray(Array.empty[String]).toVector
      else
        val made = teach(Pairs.themes[Llm](env("THEMES", "150").toInt))
        Files.writeString(themesFile, made.mkString("\n"))
        made
    val done = if Files.exists(out) then Files.readAllLines(out).size else 0
    val doneThemes = out.resolveSibling("done.txt")
    val finished = if Files.exists(doneThemes) then Files.readAllLines(doneThemes).toArray(Array.empty[String]).toSet
      else Set.empty[String]
    println(s"${themes.size} themes, ${finished.size} done, $done lessons so far")
    val rnd = Random(env("SEED", "1").toLong)
    var kept, all = 0
    for (theme, i) <- themes.zipWithIndex if !finished(theme) do
      val lessons = teach(Pairs.lessons[Llm](theme, i + 1, env("EACH", "2").toInt, shown, rnd))
        .filterNot(_.exercise.premisses.exists(s => theBooks(s.toLowerCase.filter(_.isLetter))))
      lessons.foreach(l => Files.writeString(out, Pairs.json(l) + "\n", StandardOpenOption.CREATE,
        StandardOpenOption.APPEND))
      Files.writeString(doneThemes, theme + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND)
      kept += lessons.map(_.pairs.size).sum; all += lessons.map(_.read.size).sum
      println(s"${i + 1}/${themes.size} $theme: ${lessons.size} soriteses; read back $kept of $all premisses so far")
