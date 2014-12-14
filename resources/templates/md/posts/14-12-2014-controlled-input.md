{:title "Controlled and Uncontrolled input"
 :layout :post
 :tags  ["Om" "requestAnimationFrame" "React" "Reagent"]}

[React](http://facebook.github.io/react/) tries as much as possible to
be *declarative*: The body of the `render` method is a description of
the component *at any point in time*. Some input elements pose an
interesting challange to this problem:

```
<input type="text" value={this.props.name} onChange=... />
```

Given that we specify that the value of the input is
`this.props.value`, and props are treated immutably by react, what
should happen if we type something into the input box? If we change
the content of the input we break the declarative model of React
because the specified value does not match the actual content.

In React parlance, there is a difference between [controlled and uncontrolled components](http://facebook.github.io/react/docs/forms.html#controlled-components). A controlled component will always contain
the value specified in the `value` attribute. Controlled components
are preferred because they preserve the overall declarative nature of
React. The `onChange` callback must somehow cause a re-render so that
the value property can be updated:

```
var NameInput = React.createClass({
  render: function() {
    return (
      <input type="text"
             value={this.props.name.toUpperCase()}
             onChange={this.handleChange}/>
    );
  },

  handleChange: function(evt) {
    this.props.nameChange(evt.target.value);
  }
});

var SomeForm = React.createClass({
  getInitialState: function() {
    return {name: "",
            age: ""};
  },

  render: function() {
    return (
      <ul>
       <li>Name: <NameInput name={this.state.name}
                            nameChange={this.handleNameChange}/></li>
       <li>Age: ...</li>
      </ul>
    );
  },

  handleNameChange: function(name) {
    this.setState({name: name});
  }
});
```

Note that the `NameInput` component is completely stateless. At
any point in time, the component will display the value of
`this.props.name.toUpperCase()`. It is up to the `onChange` callback
to decide what to do if the user types something into the box. There
is no default behaviour like *"add the typed letter at cursor position"*.

React also has a concept of uncontrolled components where instead of
specifying `value` you use `defaultValue` and this value will be used
for the initial render and after that the textbox behaves like a
standard html input element. Note that uncontrolled components breaks
the declarativeness. The words *"initial render"* and *"after"* should
make it clear that the render function no longer specifies how the
component should render at any point in time.

### Input components in Om

While working with input elements in Om I was surprised to realize that
my components did not behave like controlled components does in
React. After some digging I
realized that the behaviour seemed to be intentional, since [Om wraps React input components in its own stateful versions](https://github.com/swannodette/om/blob/master/src/om/dom.cljs#L7-L36). The reason for this is
that Om does rendering asynchrounusly via `requestAnimationFrame` and
it turns out that React does not fully support this mode of rendering.

As an example, consider the following application:

```
(def app-state (atom {:text "foo"}))

(defn my-input [app]
  (reify
    om/IRender
    (render [this]
      (dom/input
        #js {:type "text"
             :value (.toUpperCase (:text app))
             :onChange #(if (< (count app) 10)
                          (om/transact! app
                                        :text
                                        (constantly (-> % .-target .-value))))}))))

(om/root
  my-input
  app-state
  {:target (. js/document (getElementById "app"))})
```

If you try to run this application you will notice two interesting things

1. The input text is kept in upper case. However, if you try to add a character to the middle of the text the cursor will jump to the last position. If there is no transformation done (i.e., `:value (:text app)` instead of `:value (.toUpperCase (:text app))`) the cursor will stay at the correct position.

2. The app-state is updated only if the text is less that 10 characters long. It is still possible to type more characters into the input box and in that case *the local state of the component differs from the application state*.

### Input components in Reagent

Reagent also does asynchrounous rendering and as such should be battling
with the same issues, and indeed [it does](https://github.com/reagent-project/reagent/blob/master/src/reagent/impl/template.cljs#L82-L140). I have not yet been able to completely understand this implementation but it seems like an attempt to keep the semantics of Reacts controlled components even in the case of asynchronous rendering.

It seems like this is not an easy task. For example, if you try this short reagent code snippet (which is very similar to the Om example above)

```
(defn my-input [on-change text]
  [:div [:input {:type "text"
                 :value (.toUpperCase text)
                 :on-change #(if (< (count text) 10)
                               (on-change (-> % .-target .-value)))}]])

(def text (reagent/atom "foo"))

(defn main-page []
  [my-input #(reset! text %) @text])

(defn init! []
  (reagent/render-component [main-page] (.getElementById js/document "app")))
```

1. You will notice the same behaviour as in the Om example: If you type a character in the middle of the text the cursor jumps to the last position.

2. The state of the `text` atom and the rendered text in the component seems to be kept in sync since it is not possible to type more than 10 characters into the input box.

### Conclusions

It is unfortunate that asynchronous rendering does not work properly
in React. However, this is hardly the fault of React, since they are pretty clear in
their communication with the community that rendering with `requestAnimationFrame` is simply [not supported](https://groups.google.com/forum/#!msg/reactjs/LkTihnf6Ey8/FgFvvf33GckJ)

> Let me repeat myself: rAF batching is not supported. It is not a priority for us because it does not solve any real problems that I know of and makes things much harder to reason about.

It is interesting that the various Clojurescript React wrappers choose async rendering as their model when the underlying library
that they rely on does not fully support it.