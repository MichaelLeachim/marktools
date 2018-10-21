;; @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
;; @ Copyright (c) Michael Leahcim                                                      @
;; @ You can find additional information regarding licensing of this work in LICENSE.md @
;; @ You must not remove this notice, or any other, from this software.                 @
;; @ All rights reserved.                                                               @
;; @@@@@@ At 2018-10-21 22:21 <thereisnodotcollective@gmail.com> @@@@@@@@@@@@@@@@@@@@@@@@

;; This is a markdown preprocessor engine. It takes markdown as input and extracts
;; all sorts of useful information from it.

(ns thereisnodot.marktools.preprocessor
  (:require [net.cgrand.enlive-html :as enlive]
            [wireframe.routes :as routes]
            [wireframe.markdown.render :as mr]))

(defn line-seq->text
  [lineseq]
  (clojure.string/join "\n" lineseq))

(defn line-seq->html
  [lineseq]
  (mr/text->html (line-seq->text lineseq)))

(def ^:private LINK-WRAP "::INTERLINK-TITLE::")
(defn link-replace
  [input-text replacement-fn]
  (clojure.string/replace
   input-text
   (re-pattern (str LINK-WRAP "(.*?)" LINK-WRAP))
   (fn [[_ var]]
     (replacement-fn var))))

(defn link-wrap
  [input]
  (str LINK-WRAP input LINK-WRAP))


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

(defn- html-string->raw-text
  "Will take HTML as input. Will output raw text as a result.
   Will cut out HTML tags with their content"
  [html-string]
  (->>
    html-string java.io.StringReader. enlive/html-resource  first :content first :content
    (filter string?)
    (clojure.string/join " ")))


(defn- drop-html-from-enlive-emit
  [enlive-emit]
  (cond
    (< (count enlive-emit) 4) enlive-emit
    (=  (nth enlive-emit 4) "body")
    (drop 6 (take (-  (count enlive-emit) 6) enlive-emit))
    :else
    enlive-emit))


(defn- html-transformer
  [tag-key transform-fn line]
  (let [should-continue? (.contains line (str "<" (name  tag-key)))]
    (if-not should-continue?
      line
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
        (apply str $)))))

(defn- processor-video-transform
  "Take video tag: <video title='View Finder of cropper laptop'>cropper_laptop_2.mp4</video> and turn it into HTML valid video tag"
  [app-state line]
  [app-state
   (html-transformer
    :video
    (fn [{{title :title} :attrs [path] :content}]
      [:div.video-view.mik-flush-center
       [:video.mik-cut-bottom {:controls true}
        [:source {:src (routes/path-for :media :path path) :type "video/mp4"}]
        "You browser does not support the video tag"]
       [:h5.mik-flush-center.mik-cut-top  title]])
    line)])

(defn- processor-path-for-transform
  "Transforms <path-for>:blog :tag :reframe :page 0</path-for> into /blog/reframe/0 or smth. like this" 
  [app-state line]
  [app-state
   (html-transformer
    :path-for
    (fn [{[data-edn] :content}]
      (apply routes/path-for (read-string (str "[" data-edn "]")))) line)])

(defn- processor-image-transform
  [app-state line]
  [app-state
   (html-transformer
    :img-local
    (fn [item]
      (let [{{src :src width :width} :attrs} item]
        (->
         item
         (assoc-in
          [:attrs :src]
          (cond
            (and width src)
            (routes/path-for :media-width :path src :width width)
            src
            (routes/path-for :media :path src)))
         (assoc :tag :img)))) line)])

(comment
  (processor-image-transform
   {}
   "<img-local src=\"avatar.jpg\" width=200></img>"))


(defn- processor-interlink-transform-and-gather
  "Take this: <interlink>post.md</ineterlink>
   and turn it into <a href='/article/post_page.md'>post.md<a>
   it will also gather any outstanding links"  
  [app-state line]
  (let [link-gathering (atom [])
        transformed-line
        (html-transformer
         :interlink
         (fn [{[article] :content} ]
           (do
             (swap! link-gathering conj article)
             [:a.interlink {:href (routes/path-for :article :title article)}
              (link-wrap article)])) line)]
    [(update  app-state :links-to concat @link-gathering)
     transformed-line]))

(comment
  (processor-interlink-transform-and-gather
   empty-state "hello world <interlink>blab.md</interlink>"))

(defn- processor-line-index
  "This will put the extracted lines into :lines field on state"
  [app-state  line]
  [(->
    app-state
    (update :lines-count  inc)
    (update :lines  conj  line))
   line])

(comment
  (processor-line-index
   empty-state "hello world <interlink>blab.md</interlink>"))


(defn- processor-headers+1
  "This will increment the size of the headings
   Changing (# Hello world) into (## Hello world)
   This is destructive"
  [app-state  line]
  [app-state
   (clojure.string/replace
    line
    #"^#+"
    #(str  %1 "#"))])

(comment
  (last
   (processor-headers+1
    empty-state "# hello world")))


(defn- processor-extract-toc
  "This will extract table of contents from the page and put it 
   into :toc field of the state
   This is non destructive"
  [app-state  line]
  (if-not (clojure.string/starts-with? line "#")
    [app-state line]
    (let [size (count (re-find #"^#+" line))
          removed (clojure.string/replace line #"^#+"  "")]
      [(update app-state :toc  conj (list size removed)) line])))

(comment
  (:toc
   (first (processor-extract-toc
           empty-state "# hello world"))))

(defn- cut-single-contiguous-region
  "Will cut single region that matches current dataset"
  [key-name skip-line? matches-fn? process-fn app-state line]
  (let [buffer (key-name app-state)]
    (cond
      (=  (last buffer) CLOSED-BUFFER)
      [app-state line]
      (matches-fn? line)
      [(update app-state key-name conj (process-fn line)) (if skip-line? SKIP-LINE line)]
      (not  (empty? buffer))
      [(update app-state key-name conj CLOSED-BUFFER) line]
      :else
      [app-state line])))

(def ^{:private true} processor-extract-mik-metadata
  "This will extract :metadata from the page 
   Now, this is true **only** regarding my files. It is not standard. 
   It is not going to work for other people. "
  (let [extractor (fn [line] (->> line
                                  (re-find #"^([A-z\_\-\s]+):(.*?)$" )
                                  (rest)
                                  (map clojure.string/trim)))]
    (fn  [app-state  line]
      (cut-single-contiguous-region
       :metadata true
       #(= (count (extractor %)) 2)
       extractor
       app-state line))))

(def ^{:private true} processor-extract-disclaimer
  "This will extract :metadata from the page 
   Now, this is true **only** regarding my files. It is not standard. 
   It is not going to work for other people. "
  (let [extractor (fn [line]
                    (->> line
                         (re-find #"^\<\!\-\-(.*?)\-\-\>$" )
                         (rest)
                         (map clojure.string/trim)))
        checker #(= (count (extractor %)) 1)
        saver  #(first (extractor %))]
    (fn  [app-state  line]
      (cut-single-contiguous-region
       :disclaimer true
       checker
       saver
       app-state line))))

(comment
  (processor-extract-disclaimer empty-state "<!-- @ Copyright (c) Michael Leachim                                                      @ -->"))
(comment
  (:metadata
   (first (processor-extract-mik-metadata
           empty-state "some: zerothing")))
  (:metadata
   (first (processor-extract-mik-metadata
           empty-state "some zerothing"))))
(defn- processor-raw-text-extractor
  [app-state line]
  []
  [(->>
    line
    (html-string->raw-text)
    (re-seq  #"\w+")
    (clojure.string/join " ")
    (update app-state :raw-text conj)) line])

(defn- processor-extract-separator
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

(comment
  (:preview
   (first (processor-extract-separator
           empty-state "some: zerothing"))))

(defn wrap-skip-line
  [process-fn [app-state line]]
  (if (= line SKIP-LINE)
    [app-state line]
    (process-fn app-state line)))

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
