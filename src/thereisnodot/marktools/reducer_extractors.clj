;; @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
;; @ Copyright (c) Michael Leahcim                                                      @
;; @ You can find additional information regarding licensing of this work in LICENSE.md @
;; @ You must not remove this notice, or any other, from this software.                 @
;; @ All rights reserved.                                                               @
;; @@@@@@ At 2018-10-22 22:41 <thereisnodotcollective@gmail.com> @@@@@@@@@@@@@@@@@@@@@@@@
(ns
    ^{:doc "Preprocessing pipeline"
      :author "Michael Leahcim"}
    thereisnodot.marktools.reducer-extractors
  (:require [net.cgrand.enlive-html :as enlive]
            [thereisnodot.akronim.core :refer [defns]]
            [thereisnodot.marktools.render :as mr]
            [thereisnodot.marktools.html-transformer :as html-transformer]))

(defns html-replace
  "Same as string/replace, but with `:tag` html selector, `:transform-fn` and `:line` as `string` 
   `transform-fn` will accept item as a `enlive` element, and expect `hiccup` form as its output. See example"
  [(html-replace "Hello <b> text </b> world"
                 :b
                 (fn [{[content] :content} ]
                   [:b [:a {:href "#"}  content]]))
   =>  "Hello <b><a href=\"#\"> text </a></b> world"

   (html-replace "Hello <b class=\"whoever\"> bold </b> world"
                 :.whoever
                 (fn [{[content] :content}]
                   [:b.nothing  content]))
   => "Hello <b class=\"nothing\"> bold </b> world"]
  [line tag-key transform-fn]
  (as-> line $
    (java.io.StringReader. $)
    (enlive/html-resource $)
    (enlive/transform
     $
     [tag-key]
     (fn [item]
       (enlive/html (transform-fn item))))
    (enlive/emit* $)
    (drop-html-from-enlive-emit $)
    (apply str $)))

(defns reducer:line-index
  "This will put the extracted lines into :lines field on state"
  [(reducer:line-index {:lines-count 0 :lines []} "Hello world")
   => [{:lines-count 1, :lines ["Hello world"]} "Hello world"]]
  [app-state  line]
  [(->
    app-state
    (update :lines-count  inc)
    (update :lines  conj  line))
   line])

(defns reducer:heading-incrementer
  "This will increment the size of the headings
   This affects line generation"
  [(reducer:heading-incrementer {} "# Hello world")
   => [{} "## Hello world"]
   (reducer:heading-incrementer {} "## Hello world")
   => [{} "### Hello world"]]
  [app-state  line]
  [app-state
   (clojure.string/replace
    line
    #"^#+"
    #(str  %1 "#"))])

(defns reducer:toc-extraction
  "This will extract table of contents from the page and put it 
   into `:toc` field of the state
   This operation does not affect line generation"
  [(reducer:toc-extraction {} "# hello world")
   => [{:toc (list (list 1  " hello world"))} "# hello world"]]
  [app-state  line]
  (if-not (clojure.string/starts-with? line "#")
    [app-state line]
    (let [size (count (re-find #"^#+" line))
          removed (clojure.string/replace line #"^#+"  "")]
      [(update app-state :toc  conj (list size removed)) line])))

(defns reducer:raw-text-extractor
  "Will extrcat raw text from the line skipping anything inside HTML. Will populate `:raw-text` key in the `app-state` "
  [(reducer:raw-text-extractor
    {} "Hello <div class='whatever>Whoever</div>")
   =>[{:raw-text (list "Hello")} "Hello <div class='whatever>Whoever</div>"]]
  [app-state line]
  [(->>
    line
    (html-string->raw-text)
    (re-seq  #"\w+")
    (clojure.string/join " ")
    (update app-state :raw-text conj)) line])

(defn reducer:extract-separator
  "This will split the result with the separator and without the separator
   TODO: implement"
  [app-state line]
  (cond
    (:seen-separator? app-state)
    [app-state line]
    (and (not (:seen-separator? app-state)) (.contains line "<separator>"))
    [(->
      app-state
      (assoc  :seen-separator? true)
      (update :preview conj (.replace line "<separator>" "")))
     (.replace line "<separator>" "")]
    (not (:seen-separator? app-state))
    [(update app-state :preview conj line) line]))

(defn extract-commented-foreword
  [lineseq]
  (take-while
   (fn [line]
     (->> line
          (re-find #"^\<\!\-\-(.*?)\-\-\>$" )
          (rest)
          (map clojure.string/trim)))
   lineseq))
