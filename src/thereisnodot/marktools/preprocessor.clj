;; @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
;; @ Copyright (c) Michael Leahcim                                                      @
;; @ You can find additional information regarding licensing of this work in LICENSE.md @
;; @ You must not remove this notice, or any other, from this software.                 @
;; @ All rights reserved.                                                               @
;; @@@@@@ At 2018-10-21 22:21 <thereisnodotcollective@gmail.com> @@@@@@@@@@@@@@@@@@@@@@@@

;; This is a markdown preprocessor engine. It takes markdown as input and extracts
;; all sorts of useful information from it.

(ns
    ^{:doc "Preprocessing pipeline"
      :author "Michael Leahcim"}
    thereisnodot.marktools.preprocessor
  
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


(defns line-seq->text
  "Will turn line-seq into text"
  [(line-seq->text (list "h" "e" "l" "l" "o" ))
   => "h\ne\nl\nl\no"]
  [lineseq]
  (clojure.string/join "\n" lineseq))

(defns markdown-line-seq->html
  "Will turn lineseq into HTML"
  [(:html (line-seq->html (list "# Hello" "world" "* Whatever")))
   => "<h1 id=\"hello\">Hello</h1>\n<p>world</p>\n<ul>\n<li>Whatever</li>\n</ul>"]
  [lineseq]
  (mr/text->html (line-seq->text lineseq)))

(defns cut-out-html-from-string
  "Will take HTML as input. Will output raw text as a result.
   Will cut out HTML tags with their content"
  [(cut-out-html-from-string "<div><b>Hello</b></div>world")
   => "world"]
  [html-string]
  (->>
    html-string java.io.StringReader. enlive/html-resource  first :content first :content
    (filter string?)
    (clojure.string/join " ")))


(def SKIP-LINE :skip-line)
(def CLOSED-BUFFER :closed)

(def empty-state
  {:lines-count 0
   :lines []
   :toc []
   :metadata []
   :links-to []
   :raw-text []
   :preview []
   :disclaimer []})

(comment
  (processor-extract-disclaimer empty-state "<!-- @ Copyright (c) Michael Leachim                                                      @ -->"))

(def processing-vector
  [processor-extract-disclaimer
   processor-extract-mik-metadata
   processor-raw-text-extractor
   processor-video-transform
   processor-image-transform
   processor-path-for-transform
   processor-interlink-transform-and-gather
   processor-extract-toc
   processor-extract-separator
   processor-line-index])

(def indent-headers-vector [processor-headers+1
                            processor-line-index])

(defn extracted-data->fix-up
  [app-state]
  (->
   app-state
   (update :metadata butlast)
   (update :disclaimer butlast)
   (dissoc :seen-separator?)))

(defn line-seq->extracted-data
  ([input-seq]
   (line-seq->extracted-data input-seq processing-vector))
  ([input-seq processing-vec]
   (extracted-data->fix-up
    (let [pfn (apply comp (map #(partial wrap-skip-line %) (reverse processing-vec)))]
      (reduce
       (fn [app-state line]
         (first (pfn [app-state line])))
       empty-state
       input-seq)))))

(defn file->extracted-data
  [file-path]
  (with-open [rdr (clojure.java.io/reader file-path)]
    (line-seq->extracted-data (line-seq rdr) processing-vector)))

(defn string->extracted-data
  [some-string]
  (line-seq->extracted-data (clojure.string/split some-string #"\n") processing-vector))

(defn line-seq->increment-headers
  [input-seq]
  (line-seq->extracted-data input-seq (into []  indent-headers-vector)))
