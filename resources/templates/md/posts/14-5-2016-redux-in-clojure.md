{:title "Redux reducers in Clojure"
 :layout :post
 :tags  ["Clojure" "React" "Redux"]}


[Redux](http://redux.js.org/) is a Javascript library with an
interesting approach to doing client side state management. It is
inspired by the [Flux architecture](https://facebook.github.io/flux/docs/overview.html) and
is most often used in combination with
[React](https://facebook.github.io/react/). Redux takes a very
functional approach to state management which could potentially be interesting to
many Clojure programmers.

At the highest level Redux works like this:

![Redux](/nil-recur/img/redux01.png)

There is a single (often huge) state object which the UI uses for
rendering. The UI emits actions that are fed into the reducer which
together with the current state object produces the next state for the
UI to render.

We know that in React the user interface is modelled as a render
tree. It also often makes sense for the redux state object to be
represented as some kind of tree. A more complete picture of the
relation between Redux and React might look like this:

![Redux](/nil-recur/img/redux02.png)

Note in particular that the shape of the state tree does not have to
correspond to the shape of the render tree. It is often wise to try to
keep the state tree as normalized as possible to avoid
duplication. The render tree on the other hand will have a shape
similar to whatever the visual design dictates.

In redux there is a single function (the reducer in the image above,
sometimes called the "root" reducer for reasons that will be explained
later in the post) whose job is to create new states based on all
the possible actions the user interface emits. This might easily
become a maintenance nightmare if not handled with care. Redux comes
bundled with a function, called `combineReducers` that helps you split
up the root reducer in interesting ways. The rest of this post
examines this function from a Clojure point of view.

Let's start with an example. Imagine we are building some kind of
email client. Our email client will manage contacts, the inbox and the
emails that are sent. We will have functions for adding and removing contacts:

```clojure
(defn add-contact
  [contacts contact]
  (assoc contacts (:id contact) contact))

(defn remove-contact
  [contacts {:keys [id]}]
  (dissoc contacts id))

(-> {}
    (add-contact {:id 101 :email "vastra@example.com"})
    (add-contact {:id 102 :email "flint@example.com"})
    (add-contact {:id 103 :email "strax@example.com"})
    (remove-contact {:id 102}))

;;=> {101 {:id 101, :email "vastra@example.com"},
;;    103 {:id 103, :email "strax@example.com"}}
```

Note that both add-contact and remove-contact could be used as
reducing functions where the `contacts` argument would be the
accumulator and second `contact` argument would be the next element to
be ingested.

```clojure
(reduce add-contact
        {}
        [{:id 101 :email "vastra@example.com"}
         {:id 102 :email "flint@example.com"}
         {:id 103 :email "strax@example.com"}])

;;=> {101 {:id 101, :email "vastra@example.com"},
;;    102 {:id 102, :email "flint@example.com"},
;;    103 {:id 103, :email "strax@example.com"}}
```

Let's create a single function `contacts-reducer` that combines
`add-contact` and `remove-contact` into a single reducing function.

```clojure
(defn contacts-reducer
  [contacts {:keys [type payload]}]
  (condp = type
    :add-contact (add-contact contacts payload)
    :remove-contact (remove-contact contacts payload)))

(reduce contacts-reducer
        {}
        [{:type :add-contact
          :payload {:id 101 :email "vastra@example.com"}}
         {:type :add-contact
          :payload {:id 102 :email "flint@example.com"}}
         {:type :remove-contact
          :payload {:id 101}}
         {:type :add-contact
          :payload {:id 103 :email "strax@example.com"}}])
;;=> {102 {:id 102, :email "flint@example.com"},
;;    103 {:id 103, :email "strax@example.com"}}
```

Note that we use a type tag to be able to figure out which function to
call. A map like

```clojure
{:type :add-contact
 :payload {:id 102 :email "flint@example.com"}}
```

is called an "action" in Redux. The only requirement is the presence
of the type tag. Otherwise the object can have any shape and keys. In
this post I will always use the `:payload` key which will hold the
data for the reducing functions.

Redux also adds a few requirements to the reducing functions themselves:

* If the action type is unknown, return the state unchanged.
* If the state is `undefined`, return the initial state for this reducer.

The `undefined` part doesn't translate well to Clojure. Instead we'll
take some inspiration from
[transducers](http://clojure.org/reference/transducers) (an eerily
similar concept to Redux reducers) where the initial state is obtained
by calling the reducing function without arguments.

Let's rewrite `contacts-reducer` to follow these simple rules:

```clojure
(defn contacts-reducer
  ([] {})
  ([contacts {:keys [type payload]}]
   (condp = type
     :add-contact (add-contact contacts payload)
     :remove-contact (remove-contact contacts payload)
     contacts)))

(contacts-reducer)
;;=> {}

(contacts-reducer {:will "not change"} {:type :unknown-action})
;;=> {:will "not change"}
```

To illustrate the point of Redux's `combineReducers` function we'll
need a few other reducer functions as well. For managing the inbox we
have:

```clojure
(defn add-to-inbox [inbox email]
  (conj inbox email))

(defn remove-from-inbox [inbox email]
  (disj inbox email))

(defn inbox-reducer
  ([] #{})
  ([inbox {:keys [type payload]}]
   (condp = type
     :add-to-inbox (add-to-inbox inbox payload)
     :remove-from-inbox (remove-from-inbox inbox payload)
     inbox)))
```

The inbox is just a set of email messages. We have also defined an
`inbox-reducer` that's able to add and remove emails from the inbox:

```clojure
(let [initial-state (inbox-reducer)
      actions [{:type :add-to-inbox
                :payload {:from "vastra@example.com"
		          :title "Hello"
			  :body "..."}}
               {:type :add-to-inbox
                :payload {:from "flint@example.com"
		          :title "Lunch?"
			  :body "..."}}
               {:type :remove-from-inbox
                :payload {:from "vastra@example.com"
		          :title "Hello"
			  :body "..."}}]]
  (reduce inbox-reducer
          initial-state
          actions))

;;=> #{{:from "flint@example.com", :title "Lunch?", :body "..."}}
```

The final reducer we'll use is the one that manages the "sent"
emails. We'll just handle a single action type for this reducer so
we'll simply inline the implementation:

```clojure
(defn sent-reducer
  ([] [])
  ([state action]
   (if (= (:type action) :email-sent)
     (conj state (:payload action))
     state)))

(let [initial-state (sent-reducer)
      actions [{:type :email-sent
                :payload {:to "strax@example.com"
		          :title "Hi!"
			  :body "..."}}
               {:type :email-sent
                :payload {:to "vastra@example.com"
		          :title "Hello"
			  :body "..."}}]]
  (reduce sent-reducer
          initial-state
          actions))

;;=> [{:to "strax@example.com", :title "Hi!", :body "..."}
;;    {:to "vastra@example.com", :title "Hello", :body "..."}]
```

The Redux `combineReducer` is a higher order function that takes a map
where the keys corresponds to keys in the state tree and the values
are the reducers that manages that part of the state. The function
returns a reducing function which is important for composability.

If we think about the state that our application will manage it could
look something like

```clojure
{:contacts {101 { .. first contact ..}
            102 { .. another contact ..}
	    ;; etc
	    199 { .. last contact ..}}
 :emails {:inbox #{ .. set of emails in the inbox .. }
          :sent [ .. list of sent emails ]}}
```

With `combine-reducers` we'll be able to create a (root) reducer that
maintains the structure above and is built using the reducers we've
been defining previously. Let's define the `root-reducer` using (the
as of yet to be implemented) `combine-reducers`:

```clojure
(def root-reducer
  (combine-reducers {:contacts contacts-reducer
                     :emails (combine-reducers {:inbox inbox-reducer
                                                :sent sent-reducer})}))
```

The `root-reducer` follows the same contract as the rest of the reducers:

```clojure
(root-reducer)
;;=> {:contacts {}, :emails {:inbox #{}, :sent []}}


(root-reducer (root-reducer) {:type :unknown-action})
;;=> {:contacts {}, :emails {:inbox #{}, :sent []}}
```

Note how the initial value is built using both the map structure as
well as the "leaf" reducers initial values.

We can now use the `root-reducer` with all the actions we've defined

```clojure
(let [initial-state (root-reducer)
      actions [{:type :email-sent
                :payload {:to "strax@example.com"
		          :title "Hi!"
			  :body "..."}}
               {:type :add-contact
                :payload {:id 101 :email "vastra@example.com"}}
               {:type :add-to-inbox
                :payload {:from "strax@example.com"
		          :title "Reply: Hi!"
			  :body "..."}}
               {:type :add-contact
                :payload {:id 102 :email "flint@example.com"}}
               {:type :remove-contact
                :payload {:id 101}}
               {:type :add-contact
                :payload {:id 103 :email "strax@example.com"}}]]
  (reduce root-reducer
          initial-state
          actions))

;;=> {:contacts {102 {:id 102, :email "flint@example.com"},
;;               103 {:id 103, :email "strax@example.com"}},
;;    :emails {:inbox #{{:from "strax@example.com",
;;                       :title "Reply: Hi!",
;;                       :body "..."}},
;;             :sent [{:to "strax@example.com",
;;                     :title "Hi!",
;;                     :body "..."}]}}
```

The implementation of `combine-reducer` looks like follows:

```clojure
(defn combine-reducers [reducer-map]
  (fn
    ([]
     (reduce (fn [acc [key reducing-fn]]
               (assoc acc key (reducing-fn)))
             {}
             reducer-map))
    ([state action]
     (reduce (fn [acc [key reducing-fn]]
               (update acc key reducing-fn action))
             state
             reducer-map))))
```

Note that the argument lists are the same as for all the other
reducers. We build the initial value by reducing over the
`reducer-map` and call the corresponding `reducer-fn` with no
arguments to obtain that part of the initial value. The two argument
version is also a reduction over the `reducer-map`. In this case we
update the value under the key by calling what amounts to
`(reducing-fn (get acc key) action)`. Note also that the top reduction
uses the `state` as the initial value.

I found the `combine-reducers` function really interesting to study
and reimplement in Clojure. I have not seen a similar function used in
a Clojure context before.

I also find it amazing how essential functional programming has become
to client side Javascript in 2016 with the rise of React and Redux
(and others). I hope I've been able to show how functional programming
ideas are at the very heart of how Redux works.
