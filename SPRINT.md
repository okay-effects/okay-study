# SPRINT — okay-study

What is being done now, each item with its check; done items move to `specs/study.md` §4.

Done 2026-11-18 (§4.1–4.4): the format reaches the book (26 of 26); the local 35B does not write the rules (0 of
16 held out, twice); a stronger teacher does, at the first writing (16 of 16 held out; `models/carroll-sorites.json`).

Done 2026-11-19 (§8): the small neurosymbolic model for § 9 built (a reader that chooses, the symbolic model
concludes) and measured untrained — 0.6B 1–2 conclusions of 50, 1.7B 13, 4B 32; more exercises shown and pmi do
not help; the exact side's acceptance adds nothing.

0. **The reader trained** (needs the operator's word: the GPU for hours) — pairs from the formal side: soriteses
   sampled over dictionaries, each premiss said by the teacher in an ordinary sentence, kept only if the 4B (or the
   teacher) reads it back to the same form; a LoRA on the 0.6B through the options' choice (mlx-scala `Lora`,
   okay-desk's `FrameTrain` as the model). Check: § 9's 50 measured exercises (never trained on), premisses as
   nullities and conclusions, against the untrained 0.6B (1–2 of 50) and the untrained 4B (32 of 50).
1. **Which teacher is enough** — the two tasks (`Judge`) to models between the 35B-A3B and the strong one. Check:
   held out of 16 per model; the smallest that passes; whether it runs on the Mac.
2. **An unseen book** — an invented calculus with its lesson and exercises. Check: held out, with the strong
   teacher; nothing of it can be in a training set.
3. **The reader's ceiling on § 9** — the teacher reads concrete premisses into canonical sentences by the
   exercise's dictionary. Check: of 60, answered as the book's English answer says.
4. **The small reader by choices** (okay-frames `Space`) — check: § 9 against the ceiling; the phone's seconds.
5. **A second book of another kind** (quantities and equations) — the engine first, with its law of reach.
6. **The missing fact looked up** (the operator, 2026-11-18; after item 4 — it is the same reader's third use) —
   a goal the engine cannot reach names the fact it lacks; the query is said from that hole, not invented; the
   small reader chooses, among a found page's sentences, the one that states the fact and its canonical form; the
   fact enters the proof with its quotation and address. Only FACTS are looked up, never rules: a theory is
   studied beforehand, with a teacher and the book's exercises. Sources that contradict are shown, not settled.
   Check: on exercises with one premiss withheld and a page that holds it among others, the share answered as the
   book does, the share asked, the share wrong; a page with an instruction in it changes nothing. Open: long
   pages read by a small model; a fact said across several sentences; the browser needs a search API and a proxy.
