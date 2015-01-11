{:title "A note on pre and post conditions"
 :layout :post
 :tags  ["Design by Contract" "Pre & post conditions" "Clojure" "test.check"]}


David Nolen recently published two interesting blog posts ([here](http://swannodette.github.io/2015/01/10/faster-validation-through-immutability/) and  [here](http://swannodette.github.io/2015/01/09/life-with-dynamic-typing/)) about the usefulness of pre and post conditions. I have recently started to use quite a lot of pre/post conditions (and other assertions and invariants) in my own code and found that they really help a lot during development. This is just a short note about how one of the examples David gave can be extended to check more known properties of the function under test.

Let's use the following distance function as the running example:

```
(defn dist [p0 p1]
  (let [a (- (:x p0) (:x p1))
        b (- (:y p0) (:y p1))]
    (js/Math.sqrt (+ (* a a)
                     (* b b)))))
```

David showed how to add **pre conditions** to this function in order to do runtime type checking:

```
(defn dist [p0 p1]
  {:pre [(point? p0) (point? p1)]}
  ...)
```

This is really useful and will catch a lot bugs during development but pre and post conditions are not limited to only type checking. This example is especially illuminating since distance functions are [well studied in mathematics](http://en.wikipedia.org/wiki/Metric_%28mathematics%29):

* `(dist p0 p1)` = `0` if and only if `(= p0 p1)`
* `(dist p0 p1)` > `0` if and only if `(not= p0 p1)`
* `(dist p0 p1)` = `(dist p1 p0)` for all points `p0` and `p1`
* `(dist p0 p1)` + `(dist p1 p2)` >= `(dist p0 p2)` for all points `p0`, `p1` and `p2`.

It should be quite obvious that checking the types of the input arguments has nothing to do with whether the above properties hold or not. The first two properties however lend themselves well as a post condition:

```
(defn dist [p0 p1]
  {:pre [(point? p0) (point? p1)]
   :post [(if (= p0 p1)
            (zero? %)
            (pos? %))]}
  ...)
```

Note that `%` is bound to the functions return value. The expression `(if (= p0 p1) (zero? %) (pos? %))` completely captures the first two properties of our metric function and nicely ties the input arguments to the output without having to do the actual calculation (which could introduce its own set of bugs).

The last two properties doesn't lend themselves well to pre / post conditions and are therefor better handled by external testing tools. One obvious choice in this particular case is to use [test.check](https://github.com/clojure/test.check):

```
(def commutativity
  (prop/for-all [p0 (gen-point)
                 p1 (gen-point)]
    (almost= (dist p0 p1) (dist p1 p0))))

(def triangle-inequality
  (prop/for-all [p0 (gen-point)
                 p1 (gen-point)
                 p2 (gen-point)]
    (>= (+ (dist p0 p1) (dist p1 p2))
        (dist p0 p2))))
```

All tools that help us create reliable software should be carefully evaluated and used when appropriate. I'm happy to have put pre/post in my own toolbelt.
