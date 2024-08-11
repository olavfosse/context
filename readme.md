# no.olavfosse/context
no.olavfosse/context is a Clojure library providing three transducers
`pretext`, `postext` and `context`. These transducers filter their input
items based on a predicate `pred` such that the items for which `(pred x)` is true
are forwarded as well as the `n` items trailing or leading `x`.

Specifically `pretext` forwards trailing context, `postext` forwards leading context and
`context` forwards both trailing and leading context.

![Illustration](./illustration.png)

All three functions support the same two arities:

**Span arity:** `(context pred n)`

This forwards the contextualized matches in the form of vector
spans. Each span is a sequence of adjacent items to be forwarded. Note that if the context of two or more matches overlap, they will be delivered in the same span.

```clj
> (into [] (context 2 (partial = 1)) [1 0 0 0 0 0 1 0 1 1 0])
[[1 0 0] [0 0 1 0 1 1 0]]
```
