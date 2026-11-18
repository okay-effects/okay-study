# AGENTS.md — okay-study

A textbook read into a neurosymbolic model: a theory for an exact engine and a reader for its problems, each
judged by the book's own exercises and answers. Read `specs/study.md` first; the theory behind it is okay-desk's
`specs/theory.md` and `specs/guarantees.md`. Scala 3, lines at most 120 characters, English.

- `core/` (`okay.study`): `Model` (patterns → facts, Datalog rules, answer forms; JSON in and out), `Solve`
  (a problem's sentences → facts → okay-datalog → answers; the book's answer read by the same forms), `Verdict`,
  `Score`, `Book` (cutting a text into numbered exercises and answers), `Carroll` (the first book's layout and
  its misprints). No language model here — this is what runs on a phone.
- `study/`: `Study.write` — a program of okay-llm's `Llm` alone: the teacher writes a model, the exercises shown
  judge it, the report goes back; `Main` runs it against the gateway (`TEACHER_URL`, default :8090).
- `books/`: the texts, as published (public domain or open licence only), never edited — an erratum is data in
  the code with its reason.
- The rule: nothing a model writes is trusted; it serves by the exercises it answers as the book does. A measure
  is taken on exercises never shown. A negative result is written down with its reason.
- Tests: `sbt test` (no model needed). A run with the teacher shares the Mac's GPU: look at what else runs first.
- Plans: `SPRINT.md` (what is being done, each with its check); results go to `specs/study.md` §4.
- Commits: plain messages, no attribution trailers.
