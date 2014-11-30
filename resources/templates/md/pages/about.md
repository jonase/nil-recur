{:title "About"
 :layout :page
 :page-index 0
 :navbar? true}

You can contact the author of this blog via email simply by running
the following in your Clojure REPL

```
(decode-rot13 (decode-base64 "d2JhbmYucmF5aGFxQHR6bnZ5LnBieg=="))
```

where the functions `decode-base64` and `decode-rot13` are left as an
exercise for the reader. Alternatively, you can ping me on twitter at
[@jonasenlund](https://twitter.com/jonasenlund). The name of the blog
derives from an [EP by Porcupine Tree](http://en.wikipedia.org/wiki/Nil_Recurring).