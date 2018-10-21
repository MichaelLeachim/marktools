;; @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@	 
;; @ Copyright (c) Michael Leachim                                                      @
;; @ You can find additional information regarding licensing of this work in LICENSE.md @
;; @ You must not remove this notice, or any other, from this software.                 @
;; @ All rights reserved.                                                               @
;; @@@@@@ At 2018-05-09 21:33<mklimoff222@gmail.com> @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@

(ns wireframe.markdown.parsing
  (:require
   [clj-time.format :as clj-time-format]
   [wireframe.etc.utils :as utils]))

(defn- parse-list
  [field]
  (->>
   field
   (str)
   (rest)
   (butlast)
   (apply str)
   (utils/string-split #",")
   (map (comp clojure.string/lower-case clojure.string/trim))))

(defn- parse-boolean
  [field]
  (= (clojure.string/lower-case (str field)) "true"))

(def ^{:private true} parse-time-formatter (clj-time-format/formatter "yyyy-dd-MM"))
(defn- parse-time
  [field]
  (clj-time-format/parse parse-time-formatter (str field)))
(def ^{:private true} human-readable-date-formatter (clj-time-format/formatter "yyyy-MM-dd"))

(defn prepare-metadata
  [filename metadata]
  (let [metadata
        (->>
         metadata
         (map (fn [[k v]]
                [(keyword k) v]))
         (into {}))
        date
        (if-let [date-text (:date metadata)]
          (parse-time date-text)
          (throw (Exception. (str "Article: "  filename " does not have :date field set"))))
        title (if-let [title-text (:title metadata)]
                title-text
                (throw (Exception. (str "Article: " filename " does not have :title field set"))))]
    {:title       title
     :title-slug   (utils/slugify  title)
     :date date
     :date-text
     (clj-time-format/unparse human-readable-date-formatter date)
     :pics
     (if-let [pics (:pics metadata)]
       (parse-list pics)
       [])
     :tags (if-let [tags-text (:tags metadata)]
             (into #{} (parse-list tags-text))
             [])}))

