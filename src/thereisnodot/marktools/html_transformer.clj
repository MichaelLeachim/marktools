;; @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
;; @ Copyright (c) Michael Leahcim                                                      @
;; @ You can find additional information regarding licensing of this work in LICENSE.md @
;; @ You must not remove this notice, or any other, from this software.                 @
;; @ All rights reserved.                                                               @
;; @@@@@@ At 2018-10-22 21:33 <thereisnodotcollective@gmail.com> @@@@@@@@@@@@@@@@@@@@@@@@
(ns
    ^{:doc "Preprocessing pipeline. HTML transformer"
      :author "Michael Leahcim"}
    thereisnodot.marktools.html-transformer
  (:require
   [net.cgrand.enlive-html :as enlive]
   [thereisnodot.akronim.core :refer [defns]]))

(defn- drop-html-from-enlive-emit
  [enlive-emit]
  (cond
    (< (count enlive-emit) 4) enlive-emit
    (=  (nth enlive-emit 4) "body")
    (drop 6 (take (-  (count enlive-emit) 6) enlive-emit))
    :else
    enlive-emit))


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

(defn- processor-video-transform
  "Take video tag: <video title='View Finder of cropper laptop'>cropper_laptop_2.mp4</video> and turn it into HTML valid video tag"
  [app-state line]
  [app-state
   (html-transformer
    :video
    (fn [{{title :title} :attrs [path] :content}]
      [:div.video-view.mik-flush-center
       [:video.mik-cut-bottom {:controls true}
        [:source {:src "#" :type "video/mp4"}]
        "You browser does not support the video tag"]
       [:h5.mik-flush-center.mik-cut-top  title]])
    line)])

