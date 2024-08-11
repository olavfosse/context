# no.olavfosse/context
no.olavfosse/context is a Clojure library providing three transducers
`pretext`, `postext` and `context`. These transducers let you filter
based on a predicate in such a way that the surounding context is
kept.

`pretext` keeps trailing context, `postext` keeps leading context and
`context` keeps both trailing and leading context.

![Illustration](./illustration.png)

All three functions support the same two arities:

Span arity: `(context pred n)`

This forwards the contextualized matches in the form of vector
spans. A span is a sequence of adjacent items to be forwarded.

```clj
no.olavfosse.context> (into [] (context 2 (partial = 1)) [1 0 0 0 0 0 1 0 1 1 0])
[[1 0 0] [0 0 1 0 1 1 0]]
```
