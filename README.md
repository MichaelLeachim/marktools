# (WIP) Marktools

A set of useful tools for markdown in Clojure programming language

## Usage

FIXME

### Preprocessing

At this step should a transformation happen of things
that can be transformed. 

The transformation happens as follows:

* The input is read **line by line**
* The chain of **reducers** is applied to the given line
* Then, the input transforms into a HTML string via render. 

#### Existing reducers

* **toc extractor** will extract table of contents
* **Headings incrementer** will increment headings
* **Header extractor** will extract commented out header
* **Raw text extractor** will extract raw text from data (skipping HTML)
* **Line counter** will count amount of lines

#### Writing your own reducer

At this point it is important to understand that 
most of the reducers here are too broad, meaning
they are mostly useless for a specific use case. 

On the other hand, almost every single use case will 
require, a specific reducer. 

Let's look at a simple, heading incrementing reducer. 


```clojure

(defns reducer:heading-incrementer
  "This will increment the size of the headings
   This affects line generation"
  [(reducer:heading-incrementer {} "# Hello world")
   => [{} "## Hello world"]
   (reducer:heading-incrementer {} "## Hello world")
   => [{} "### Hello world"]]
  [app-state  line]
  [app-state
   (clojure.string/replace line #"^#+" #(str  %1 "#"))])
```

It takes the obligatory two parameters. The `application state` and
the `line`. The default application state is as following:

```clojure

(def empty-state
  {:lines-count 0 ;; lines count
   :lines [] ;; lines of processed markdown
   :toc []   ;; table of content
   :raw-text [] ;; text without HTML 
   :preview []  ;; text before <preview> tag
   :disclaimer [] ;; comment text at the top of the item
   })
   
```






##### XML/HTML transformer

Having an ability to ad hoc define HTML like transformation 
is nice. For this case, there is a function in this 
namespace:

{{html-replace}}










##### 


## License

Copyright © 2018 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
