#!/usr/bin/env bb

#_(def str->gmi-examples
    {"# header-1\n"                [[:header-1 "header-1"]]
     "## header-2\n"               [[:header-2 "header-2"]]
     "### header-3\n"              [[:header-3 "header-3"]]
     "=> something else\n"         [[:link "something" "else"]]
     "=> /foo\n"                   [[:link "/foo" ""]]
     "= >/foo\n"                   [[:text "= >/foo"]]
     "```descr\ncode\ncode\n```\n" [[:pre "descr" "code\ncode\n"]]
     "*foo\n* item\n"              [[:text "*foo"] [:item "item"]]
     "* item\n* item 2\n"          [[:item "item"] [:item "item 2"]]})

(require '[babashka.deps :as deps])
(deps/add-deps '{:deps {org.clojars.askonomm/ruuter {:mvn/version "1.3.4"}
                        markdown-clj/markdown-clj {:mvn/version "1.10.7"}
                        com.omarpolo/gemtext {:mvn/version "0.1.8"}}})

(require
 '[babashka.process :refer [shell]]
 '[clojure.edn :as edn]
 '[clojure.string :as str]
 '[hiccup2.core :as h]
 '[hiccup.util :refer [raw-string]]
 '[gemtext.core :as gemtext])

(import 'java.time.format.DateTimeFormatter
        'java.time.LocalDateTime)


(defn message-comp [ndx message] (let [in-formatter (DateTimeFormatter/ofPattern "EEE MMM d HH:mm:ss yyyy Z")
                                       out-formatter (DateTimeFormatter/ofPattern "yyyyMMdd','dd MMMM yyyy','HH:mm:ss','ss")
                                       date-in (LocalDateTime/parse (:date message) in-formatter)
                                       date-out (.format date-in out-formatter)
                                       [sortable-date readable-date time seconds] (str/split date-out #",")]
                                   (into message {:kind :message :ndx ndx :sortable-date sortable-date :readable-date readable-date :time time :seconds seconds})))

(def git-log-command "git log --pretty=format:'{%n:commit \"%H\"%n  :author \"%aN <%aE>\"%n  :date \"%ad\"%n  :body \"%B\"}'")

(def work-dir (nth *command-line-args* 0 "."))
(def output (nth *command-line-args* 1 "index.html"))
(def css-path (str/join "/" (concat (butlast (str/split *file* #"/")) [(nth *command-line-args* 2 "styles.css") ])))

(def messages (edn/read-string (str "[" (-> (shell {:out :string :dir work-dir} git-log-command) :out) "]")))


(def message-comps-by-date (->> messages
                                (map-indexed message-comp)
                                (group-by :sortable-date)
                                (into (sorted-map))))

(def message-dates (-> message-comps-by-date
                       (keys)
                       (reverse)))

(defn date-comp [date page sortable-date] {:kind :date :date date :page page :sortable-date sortable-date})

(defn render-date [{:keys [date sortable-date]}]
  [:h2 [:a {:href (str "#" sortable-date)} date]])

(defn render-message [message]
  (let [{:keys [time body sortable-date]} message
        id (str "#" sortable-date "-" time)]
    [:section {:class "message" :id id}
     [:h3 [:a {:href id} time]]
     (map #(vec (concat [(first %) {:class "gemini"}] (rest %))) (gemtext/to-hiccup (gemtext/parse body)))]))

(def comps (reduce (fn [acc date]
                     (let [message-comps (get message-comps-by-date date)
                           {:keys [readable-date page sortable-date]} (first message-comps)
                           comps (into [(date-comp readable-date page sortable-date)] message-comps)]
                       (into acc comps)))
                   []
                   message-dates))

(defmulti render-comp :kind)
(defmethod render-comp :message [message-comp] (render-message message-comp))
(defmethod render-comp :date [date-comp] (render-date date-comp))

(def content (map render-comp comps))

(def css (slurp css-path :encoding "UTF-8"))

(def main (into [:main] content))

(def markup [:html {:lang "en-US"}
             [:head
              [:meta {:charset "UTF-8"}]
              [:title "Log"]
              [:style (raw-string css)]]
             [:body main]])

(spit output (str "<!DOCTYPE html>" (h/html markup)))
