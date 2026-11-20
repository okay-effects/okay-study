# okay-study

A book studied into a model you can check. From a textbook's lesson and its exercises with answers, a language
model (the *teacher*) writes a **symbolic model** of the book's problems — as data: the sentences a problem is
stated in, Datalog rules, the notation of answers — and the book's own exercises judge it. Nothing the teacher
writes is trusted: a model serves by the exercises it answers as the book does, and it is measured on exercises it
was never shown. The model then runs by an exact engine ([okay-datalog]) on the JVM and in a browser, with no
language model at all.

```scala
resolvers ++= Seq("okay-study" at "https://okay-effects.github.io/okay-study/maven",
  "okay-datalog" at "https://okay-effects.github.io/okay-datalog/maven",
  "okay-simple" at "https://okay-effects.github.io/okay-simple/maven")
libraryDependencies += "io.github.okay-effects" %%% "okay-study" % "0.1.0"          // the model and its run: JVM, Scala.js
libraryDependencies += "io.github.okay-effects" %% "okay-study-teacher" % "0.1.0"   // the study with a teacher: JVM
```

```scala
import okay.study.*

val model = Model.parse(json).toOption.get                      // sentences, terms, rules, answers
Solve.run(model, Vector("No c are d", "All a are d", "All b are c"))   // Right(… "a_{1}b_{0}", "b_{1}a_{0}")
Verdict.of(model, Exercise(1, sentences, "a_{1}b_{0} + b_{1}a_{0}"))   // Right_ | Wrong | Failed
```

## What is here

- **`core`** (`okay-study`, JVM and Scala.js): `Model` (JSON in and out), `Solve` (sentences → facts → rules →
  answers; a sentence no form reads is an error, never skipped), `Verdict` / `Score` against a book's answers,
  `Book` (a text cut into numbered exercises and answers); and for problems told in ordinary words: `Reading`
  (a reader's options weighed, alone or together with the symbolic model), `Lexicon`, `Sample`.
- **`study`** (`okay-study-teacher`): the study as a program of [okay-llm]'s effect `Llm` — the words first (every
  sentence read, every printed answer said back), then the rules over them, each stage told what failed
  (`Study.write`); `Judge` runs the same two stages by files, for any teacher and any book
  (`BOOK=<dir>` with `lesson.txt` and `exercises.jsonl`).
- **`reader`** (not published; Apple's MLX through [mlx-scala]): a small language model as a reader that only
  chooses among options said in words, its training on lessons made from the formal side, the benches.

## What was measured (`specs/study.md`)

- Lewis Carroll's *Symbolic Logic* (1896): a teacher's 12 rules, written from Books VI–VII and ten answered
  exercises, answer the 16 held-out abstract soriteses as the book does; four of the book's thirty are misprints
  the model exposed. A local 35B model did not write rules that run; a stronger teacher did, at the first writing.
- An official legal text (the first application, [okay-migrant](https://okay-effects.github.io/okay-migrant/)):
  the teacher's models agree with a second reading made apart on thousands of cases and answer the office's own
  questions.
- The reader is the weak half: an untrained 0.6B model reads Carroll's concrete soriteses to 0 conclusions of 50,
  trained for two minutes on lessons made from the formal side to 21; what remains is knowledge of words
  (a term said by its opposite), which the exact side cannot supply.

Negative results are written down with their reasons. `sbt test` needs no model.

[okay-datalog]: https://github.com/okay-effects/okay-datalog
[okay-llm]: https://github.com/okay-effects/okay-llm
[mlx-scala]: https://github.com/sergey-scherbina/mlx-scala
