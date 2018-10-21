;; @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
;; @ Copyright (c) Michael Leahcim                                                      @
;; @ You can find additional information regarding licensing of this work in LICENSE.md @
;; @ You must not remove this notice, or any other, from this software.                 @
;; @ All rights reserved.                                                               @
;; @@@@@@ At 2018-10-21 22:30 <thereisnodotcollective@gmail.com> @@@@@@@@@@@@@@@@@@@@@@@@

(ns wireframe.markdown.render
  (:import
   [org.commonmark.parser Parser]
   [org.commonmark.ext.gfm.tables TablesExtension]
   [org.commonmark.ext.autolink AutolinkExtension]
   [org.commonmark.ext.gfm.strikethrough  StrikethroughExtension]
   [org.commonmark.ext.heading.anchor  HeadingAnchorExtension]
   [org.commonmark.ext.ins  InsExtension]
   [org.commonmark.renderer.html HtmlRenderer]))

(def ^{:dynamic true} *EXTENSIONS*
  (java.util.LinkedList.
   [(TablesExtension/create)
    (AutolinkExtension/create)
    (StrikethroughExtension/create)
    (HeadingAnchorExtension/create)
    (InsExtension/create)]))

(def ^{:private true} parser
  (->
   (Parser/builder)
   (.extensions *EXTENSIONS*)
   (.build)))

(def ^{:private true} html-renderer
  (->
   (HtmlRenderer/builder)
   (.extensions *EXTENSIONS*)
   (.build)))

(defn text->html
  [text]
  (->>
   (.parse parser text)
   (.render html-renderer)))


