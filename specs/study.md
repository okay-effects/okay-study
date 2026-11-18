# okay-study: a textbook read into a neurosymbolic model

The operator's aim (2026-11-18): a system that, by reading books, creates neurosymbolic models — for a kind of
problem a book teaches, a SYMBOLIC model that solves it exactly and a small LANGUAGE model that reads its problems;
both small enough for a phone's browser. The theory it follows is okay-desk's `specs/theory.md` (reading is
refining typed holes; the text licenses the term, §12) and `specs/guarantees.md`.

## 1. The principle: the book is the judge

A textbook carries its own tests: worked examples and exercises with answers. Nothing a model writes is trusted;
a thing serves by the exercises it answers as the book does, and only so.

- **Build time, on the Mac**: a TEACHER (a large local model, the effect `Llm`) reads the lesson and writes the
  model as data; exercises shown with their answers judge each writing, and what failed is told back.
- **The measure**: exercises never shown (held out), answered as the book does.
- **Run time, on the phone**: the model's data, an exact engine (okay-datalog: JVM, Scala.js, Native), a small
  reader. No teacher.

## 2. The symbolic model, all of it data (`core`, `Model`)

    { "sentences": [pattern → facts],  the canonical sentences a problem is stated in
      "terms":     [pattern → facts],  what each term adds (the only way to new constants)
      "rules":     Datalog,            the theory
      "answers":   [pattern, goal] }   how answers are said, in the book's notation

A pattern is words with slots (`{X}`, `{X:regex}`); a sentence no form reads is an error, never skipped — the
account of the text (theory §12) at the level of sentences. `Solve.run`: sentences → facts → `Eval` → the goals'
solutions, said. `Solve.read`: the book's printed answer read by the same answer forms. `Verdict`: equal as sets
of claims. The canonical sentences are the fragment of guarantees.md §3.3: read by a deterministic parser.

**The law of reach** (`CarrollSuite`): a model in this format, written by hand, reproduces the book's answers —
the format CAN say the book's theory. Without it a teacher's failure would not tell the format's from its own.

## 3. The study (`study`, `Study.write`)

A program of `Llm` alone: the format, the lesson's text as printed, the shown exercises with answers → a model;
scored; the report (each problem right / wrong with both answers / failed with the reason) → the next writing;
at most `rounds`; the best by exercises right. Tested with a fake teacher (`StudySuite`).

## 4. The first book: Lewis Carroll, "Symbolic Logic" (1896, public domain; Gutenberg #28696)

A real textbook: definitions, a method, examples worked as models, and Book VIII — hundreds of exercises with
answers and, apart, solutions. It separates the two halves by itself:

- **§ 8, thirty ABSTRACT soriteses** ("No c are d; All a are d; All b are c" → `a_{1}b_{0} + b_{1}a_{0}`): no
  language to understand — they test the symbolic model alone.
- **§ 9, sixty CONCRETE soriteses** ("No ducks waltz; no officers ever decline to waltz; all my poultry are
  ducks", with the dictionary a = ducks, …; answers in English) and § 5, a hundred pairs: they test the reader.
- **§ 1**, propositions "to be reduced to normal form", with answers: pairs free sentence → canonical sentence,
  the reader's own exercises, from the book.

### 4.1 Results

- **Reach**: the reference model (`core/src/test/resources/carroll-sorites.reference.json`, 20 rules) answers
  26 of § 8's 30 as the book does. The other four are MISPRINTS of this text, each inconsistent with the book's own
  rules whatever the model (`Carroll.misprinted`: an answer naming an eliminated attribute, an attribute in three
  premisses, an answer naming an attribute no premiss has — a `c` and an `e` confused, it seems). They are left as
  printed and kept out of every measure. A model that reads a book finds the book's errata.

## 5. Next

1. **The teacher's model of § 8** — `Main`: 10 exercises shown, 16 held out, the 35B through the gateway.
2. **The reader's ceiling on § 9**: the teacher reads each concrete premiss, with the exercise's dictionary, into
   a canonical sentence; the symbolic model answers; the book's English answer read the same way and compared.
3. **The small reader**: the canonical sentence chosen by options (okay-frames `Space`: the quantifier, the
   subject's letter, the predicate's letter, each with or without a prime), weighed by a small model
   (`Read.Score`); pairs from the formal side — problems sampled, solved, said, rephrased — and the book's § 1.
4. **A second book of another kind**: a chapter of OpenStax College Physics (CC BY) — many laws as equations;
   it needs an exact engine for quantities (rationals, linear systems), which no library of ours has yet.
5. **The page**: a sorites typed on the phone, the conclusion and its proof (okay-datalog's proofs) shown.

## 6. Open

- How well a 35B writes a theory in Datalog from prose (item 1 measures it).
- The layout of a book (where its exercises and answers are) is given by hand per book (`Carroll`); a teacher
  could find it, checked by the count and the pairing of numbers.
- A model's notation of answers must be the book's; where a book prints one answer in several ways the measure
  is a lower bound.

## 7. Knowledge looked up (planned, SPRINT 5)

The symbolic side knows what it lacks: a goal not reached names the missing fact, and that hole — said in words —
is the query. The reader's part is the one it already has: among a page's sentences, choose the one that states
the fact, and its canonical form. The guarantee changes and must be said to the person: an answer from the book
alone is right because the theory passed the book's exercises; an answer with a looked-up fact FOLLOWS from its
sources, quoted and addressed, and is right if they are. Rules are never looked up. A page cannot instruct a
reader that only chooses among forms the engine offered.
