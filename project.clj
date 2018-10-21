(defproject thereisnodot/marktools "0.1.0-SNAPSHOT"
  :description "A set of useful tools for markdown in Clojure programming language"
  :url "https://github.com/michaelleachim/marktools"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [enlive "1.1.6"]                 
                 [clj-time "0.14.4"]                 
                 [thereisnodot/akronim "0.1.2"]
                 [com.atlassian.commonmark/commonmark "0.11.0"]
                 [com.atlassian.commonmark/commonmark-ext-gfm-tables "0.11.0"]
                 [com.atlassian.commonmark/commonmark-ext-autolink "0.11.0"]
                 [com.atlassian.commonmark/commonmark-ext-gfm-strikethrough "0.11.0"]
                 [com.atlassian.commonmark/commonmark-ext-heading-anchor "0.11.0"]
                 [com.atlassian.commonmark/commonmark-ext-yaml-front-matter "0.11.0"]
                 [com.atlassian.commonmark/commonmark-ext-ins "0.11.0" ]])
