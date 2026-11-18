# okay-study

A textbook read into a neurosymbolic model: a symbolic model that solves the book's problems exactly — all of it
data, run by [okay-datalog](https://github.com/okay-effects/okay-datalog) — and a reader for those problems, each
judged by the book's own exercises and answers. See `specs/study.md`.

The first book is Lewis Carroll's "Symbolic Logic" (1896, public domain, Project Gutenberg #28696, in `books/`).

    sbt test                      # the laws, no language model
    sbt "study/runMain okay.study.Main"   # a teacher behind okay-mlx's gateway writes the model of § 8
