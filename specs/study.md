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

## 8. The small neurosymbolic model: a reader that chooses, the symbolic model concludes (2026-11-19)

The whole thing for Carroll's concrete soriteses (§ 9, sixty): `Reading` (core) — each premiss's options
(`Premiss.options`: two different terms, plain or negated, as `All` or `No`; 72 over four terms) said in the
exercise's own words (`InWords`: "All persons that are babies are not logical."), weighed by a small language model
(`reader/Scorer`: the mean log-probability of the option's tokens after the premiss; nothing is generated), decided
alone (the best option of each premiss) or TOGETHER with the symbolic model (combinations of each premiss's four
best, by summed weight, the first one the exact side accepts: every term of the dictionary used, a conclusion
drawn). The data is the book's own: its solutions print every premiss in subscript form and the conclusion; the
symbolic model the teacher wrote (§4.4) confirms all sixty. A conclusion is compared as a nullity (the book prints
one retinend's existence where its rule of § 8 gives both).

### 8.1 Untrained readers, three sizes — the reader is the limit, and the exact side does not help it

Qwen3, 4-bit, no training; three exercises shown in the prompt (their premisses with the book's readings), 57
measured, 254 premisses:

| reader | premisses right alone | the book's reading in the top 4 | conclusions alone | together: right / wrong / not read | s an exercise |
|---|---|---|---|---|---|
| 0.6B | 53 (21%) | 100 (39%) | 1 of 57 | 1 / 37 / 19 | 9.6 |
| 1.7B | 115 (45%) | 164 (65%) | 18 | 18 / 39 / 0 | 19.2 |
| 4B | 174 (69%) | 225 (89%) | 28 | 28 / 29 / 0 | 40.1 |

- **Size matters steeply**: 21% → 45% → 69% of premisses. An untrained 0.6B does not read these sentences.
- **The exact side added NOTHING**: together = alone in every row, and what alone left unread or wrong became
  wrong. The acceptance (every term used, a conclusion drawn) is passed by many wrong readings — which of two
  well-formed soriteses a text means is a question of language only. The check is sound and nearly empty here.
- "Premisses right" is strict (an `All` read as its `No` counts wrong though the conclusion's nullity is the
  same): the conclusions are better than the premisses' share would give. A measure by nullity is to add.
Next: what costs no training (the weight as what the TEXT adds — PMI, theory §11.1; more exercises shown), then
the reader trained on pairs made from the formal side.

### 8.2 What costs no training — NEGATIVE: neither the weight as what the text adds, nor more exercises shown

One measured set (exercises 11–60: 50 exercises, 233 premisses), Qwen3 4-bit, untrained. "pmi": the option's
mean log-probability after the premiss minus the same with the sentence replaced by "(not given)".

| reader | weight | shown | premisses right | as nullities | in the top 4 | conclusions alone | together r / w / unread | s |
|---|---|---|---|---|---|---|---|---|
| 0.6B | mean | 3 | 50 | 64 | 95 | 2 of 50 | 2 / 33 / 15 | 10.5 |
| 0.6B | mean | 10 | 62 | 73 | 107 | 1 | 0 / 33 / 17 | 14.4 |
| 0.6B | pmi | 3 | 61 | 86 | 117 | 1 | 1 / 47 / 2 | 20.7 |
| 0.6B | pmi | 10 | 57 | 75 | 126 | 1 | 2 / 47 / 1 | 28.7 |
| 1.7B | mean | 3 | 107 | 167 | 151 | 13 | 13 / 37 / 0 | 20.3 |
| 1.7B | mean | 10 | 117 | 160 | 168 | 13 | 13 / 36 / 1 | 24.1 |
| 1.7B | pmi | 10 | 81 | 118 | 137 | 3 | 5 / 42 / 3 | 48.1 |
| 4B | mean | 10 | 161 | 210 | 211 | 32 | 32 / 18 / 0 | 47.1 |
| 4B | pmi | 10 | 140 | 184 | 192 | 13 | 13 / 36 / 1 | 94.2 |

- **More exercises shown: nothing** (0.6B 2 → 1, 1.7B 13 → 13 conclusions; a few premisses more).
- **pmi HURTS** at 1.7B (13 → 3) and 4B (32 → 13), and does nothing at 0.6B. The reason is to be found before it is
  used again: here every option is made of the SAME dictionary's words, so the prior it removes may be the very
  signal (which terms a sentence is about is read from the words it shares with them); the "no sentence" context
  was one arbitrary wording.
- **The exact side still adds nothing** (together = alone, within one or two).
- **The 4B untrained reads 90% of premisses as nullities (210 of 233) and 32 conclusions of 50**; the 0.6B, a
  quarter of the premisses and one or two conclusions. The gap is the reader's and it is large.

So a small reader has to be TRAINED for the book's sentences — which is the plan's item anyway (pairs made from
the formal side: a sorites sampled, said by a teacher in ordinary sentences, kept only if read back). Open with
it: what the exact side can give the reading beyond acceptance (it knows the conclusion's form, not which reading
was meant).

### 8.3 The reader TRAINED on lessons made from the formal side — the first point: 0 → 15 conclusions of 50

**The lessons** (`Sample`, `Pairs`, `MakePairs`): a dictionary the teacher invents for a theme (a universe and terms,
as the book's), a sorites sampled over part of it (a chain of implications, each premiss in one of its three forms;
a thousand sampled ones all accepted by the symbolic model — `SampleSuite`), each premiss SAID by the teacher in an
ordinary sentence (the book's exercises 1–10 shown as the style), then READ BACK by the teacher apart, from the
sentence alone; a pair is kept when the reading has the sampled nullity, and its label is the read-back's own form
(`All` or `No`, as the sentence says it). No sentence of the measured exercises may come back as a lesson.
First 65 themes: 129 soriteses, 444 premisses said, **206 read back** (46%; 75% on the first three themes — the
later themes are object names, their terms odd, and the teacher's sentences drift).

**The training** (`reader/Train`, mlx-scala `Lora`, rank 16, all projections, lr 2e-4, 2 epochs, batch 4): for a
premiss, 24 options said in words — the meant one, the eleven others over the same two terms, others at random —
a step of the listwise loss (`stepChoices`) and a step of the generative loss on the meant option (`stepBatch`;
the listwise loss alone flattened a reader in okay-desk, frames.md §5.12). 187 premisses trained on, 19 apart:
the loss apart 6.85 → 0.70 → 0.44. **Two minutes.**

**Measured** on § 9's exercises 11–60 (never trained on; no exercise shown in the prompt; the weight a sum):

| reader (Qwen3 0.6B, 4-bit) | premisses right of 233 | as nullities | in the top 4 | conclusions alone | together r / w / unread |
|---|---|---|---|---|---|
| untrained | 29 | 43 | 43 | 0 of 50 | 2 / 24 / 24 |
| **trained on 187 premisses** | **122** | **177** | **195** | **15** | 15 / 33 / 2 |

Beside §8.2's untrained readers with ten exercises shown: the trained 0.6B is at the untrained 1.7B (117 / 160 / 13)
and under the untrained 4B (161 / 210 / 32). So the reader's gap IS closed by training, fast, from lessons that
cost no person's work — and 187 lessons are few. What decides now is the lessons: their number, and their kind
(the read-back keeps under half; the themes drift). Still true: the exact side's acceptance adds nothing.
One seed; the 50 exercises are one book's.
