package okay.study

import okay.effects.json.Json

import java.nio.file.{Files, Path}

/** THE STUDY BY FILES, for a teacher that is not behind the gateway: the same tasks and the same judges as
 * `Study.write`, one step a call. TRAIN (10) exercises of Carroll's § 8 are shown.
 *   task-words <out>                  the words' task
 *   words <reply> <words.json>        the reply judged; passed: the words kept
 *   task-rules <words.json> <out>     the rules' task over those words
 *   rules <words.json> <reply> <model.json>   the reply judged on the shown; the held out measured apart */
object Judge:
  def main(args: Array[String]): Unit =
    val book = Book(Files.readString(Path.of("books/carroll-symbolic-logic.txt")))
    val exercises = Carroll.abstractSoriteses(book).filterNot(e => Carroll.misprinted.contains(e.number))
    val (train, held) = exercises.splitAt(sys.env.getOrElse("TRAIN", "10").toInt)
    val lesson = Carroll.soritesLesson(book)
    def read(path: String) = Files.readString(Path.of(path))
    def write(path: String, text: String) = { Files.writeString(Path.of(path), text); () }
    def words(path: String) = Model.parse(read(path)).fold(e => sys.error(e), identity)
    args.toList match
      case List("task-words", out) => write(out, Study.wordsTask(lesson, train))
      case List("words", reply, out) =>
        Study.json(read(reply)).flatMap(j => Model.parse(j.render)) match
          case Left(why) => println(s"TOLD: Your answer could not be read: $why")
          case Right(m) =>
            val faults = Study.wordsFaults(m, train)
            if faults.isEmpty then { write(out, m.json); println("PASSED: the words are kept") }
            else println(s"TOLD:\n${faults.mkString("\n")}")
      case List("task-rules", w, out) => write(out, Study.rulesTask(lesson, words(w), train))
      case List("rules", w, reply, out) =>
        val rules = Study.json(read(reply)).flatMap(_.field("rules") match
          case Some(Json.Arr(lines)) => Right(lines.collect { case Json.Str(s) => s }.mkString("\n"))
          case Some(Json.Str(s)) => Right(s)
          case _ => Left("no \"rules\" in the object"))
        rules match
          case Left(why) => println(s"TOLD: Your answer could not be read: $why")
          case Right(text) =>
            val model = words(w).copy(rules = text)
            val shown = Score.of(model, train)
            write(out, model.json)
            println(if shown.right == shown.total then s"PASSED: shown ${shown.said}"
              else s"TOLD:\n${Study.report(shown)}")
            val heldOut = Score.of(model, held)
            println(s"NOT TOLD — held out ${heldOut.said}")
            heldOut.verdicts.foreach {
              case (_, Verdict.Right_(_)) => ()
              case (e, v) => println(s"  held out ${e.number}: $v")
            }
      case _ => println("task-words <out> | words <reply> <words.json> | task-rules <words.json> <out> | " +
        "rules <words.json> <reply> <model.json>")
