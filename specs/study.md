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

## 7. Knowledge looked up (planned, SPRINT 6)

The symbolic side knows what it lacks: a goal not reached names the missing fact, and that hole — said in words —
is the query. The reader's part is the one it already has: among a page's sentences, choose the one that states
the fact, and its canonical form. The guarantee changes and must be said to the person: an answer from the book
alone is right because the theory passed the book's exercises; an answer with a looked-up fact FOLLOWS from its
sources, quoted and addressed, and is right if they are. Rules are never looked up. A page cannot instruct a
reader that only chooses among forms the engine offered.

### 4.2 The teacher writes the whole model at once — NEGATIVE (2026-11-18)

Qwen3.6-35B-A3B (4-bit) through the gateway, Books VI–VII as printed (44 KB), 10 exercises of § 8 shown, 16 held
out, 6 rounds, thinking on, 12,000 tokens a round, 10 minutes: **shown 0 of 10, held out 0 of 16**. What it wrote:
- the slot class `[a-z]'` where the format's own example says `[a-z]'?` — no sentence without a prime is read;
- rules that are not Datalog: terms inside terms (`combine(sub(X, 0, M, 0), …)`), a `+` in a head, the book's
  three Figures copied as shapes with no way from a problem's facts to them;
- the answers of the shown problems written as "goals" (`"goal": "a_1 b_0 + b_1 a_0"`) — a table, not a theory.
And the harness's own faults: three rounds of six came back empty (the thinking took the whole budget), an empty
reply was put into the conversation, and the round after it repeated an earlier writing word for word.
The reason, as far as one run tells: three different things are asked in one answer — a notation (patterns), a
theory (rules) and a dialect (this Datalog) — and the only signal back is the last one's failure. Next (§5.1):
the stages apart, each with its own check — the words first (every sentence read, every printed answer read and
said back the same), then the rules over fixed words with the facts of a problem shown.

### 4.3 The stages apart, the same teacher — the words pass, the rules do not run (2026-11-18)

Same teacher and lesson, 10 shown, 16 held out, 6 rounds a stage, 8 minutes.
- **Words: passed at the second writing** (33 checks of 33) — but mostly by copying the format's own examples (the
  term forms word for word), and with ONE goal for both kinds of answer (`a_{1}b_{0}` and `ab_{0}` → the same
  `conclusion(X, Y)`): the words' judge let through a notation in which "all" and "no" answers are one claim.
  Fixed after this run: two answer forms may not share a goal.
- **Rules: 0 of 10 shown, 0 of 16 held out.** Five writings, none RAN: one syntax error, then four times the same
  kind of unsafe rule (`partial(X, Y) :- partial(U, V), no(N, V, U), comp(V, V).` — the head's variables in no
  positive literal), each "corrected" into another unsafe rule. The theory was never reached: the report never got
  past the dialect. One reply of six was empty again.
What one run tells: this teacher (35B-A3B, 4-bit, ~3B active) does not write a recursive, stratified Datalog
theory of this size, with the dialect explained and the engine's own error told back. The format is not the
obstacle (§4.1: twenty rules do it). Next: the same protocol with a stronger teacher, to tell the method's limit
from this model's.

### 4.4 A stronger teacher, the same protocol — POSITIVE: 16 of 16 held out, at the first writing (2026-11-18)

The same two tasks (`Judge`, by files), with the format's examples made of ANOTHER kind of problem — no hint of
this book's notation or theory, the "exactly once" idiom removed (commit before this one). The teacher: a fresh
agent of the model this repository is developed with (Claude), told to read the task's one file and nothing else.
- **Words: passed at the first writing.** `no`, `all`, `some` forms; the letter and its prime related both ways
  by the term forms; two answer forms, two goals, the nullity unordered.
- **Rules: 12 rules, first writing — shown 10 of 10, HELD OUT 16 of 16.** Kept as `models/carroll-sorites.json`,
  under a test (`CarrollSuite`: 26 of 26). Shorter than the hand-written reference (20 rules): a premiss as a
  nullity both ways, a retinend as an attribute whose negation never occurs, the chain through negations.

So the method holds — a lesson's prose and ten answered exercises are enough to write a theory that answers the
book's other exercises, in two stages each with its judge — and §4.2–4.3 measured the LOCAL teacher's limit, not
the method's. To hold against it:
- one run, one book, one kind of problem, and the smallest theory one could ask for;
- Carroll's method is in any large model's training: this is not yet "a theory learned from an unseen book" —
  that needs a text the teacher cannot have seen (a made-up calculus with its own exercises);
- the agent had tools; it reported, and its usage shows, two calls — the two files it was given; this is not
  enforced by the protocol.

## 5.1 What follows

1. **Which teacher is enough**: the same two tasks to models between the 35B-A3B and this one — the smallest
   that writes the rules decides whether build time can stay on the Mac.
2. **An unseen book**: a small invented calculus, its lesson and exercises written by us, to take the training's
   memory out of the measure.
3. Then SPRINT 2 (the reader's ceiling on § 9).
