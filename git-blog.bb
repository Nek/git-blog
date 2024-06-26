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
(deps/add-deps '{:deps {com.omarpolo/gemtext {:mvn/version "0.1.8"}
                        org.babashka/json {:mvn/version "0.1.6"}}})

(require
 '[babashka.process :refer [process]]
 '[babashka.json :refer [read-str]]
 '[clojure.string :as str]
 '[hiccup2.core :as h]
 '[gemtext.core :as gemtext])

(import 'java.time.format.DateTimeFormatter
        'java.time.LocalDateTime)


(defn message-comp [ndx message] (let [in-formatter (DateTimeFormatter/ofPattern "EEE MMM d HH:mm:ss yyyy Z")
                                       out-formatter (DateTimeFormatter/ofPattern "yyyyMMdd','dd MMMM yyyy','HH:mm:ss','ss")
                                       date-in (LocalDateTime/parse (:date message) in-formatter)
                                       date-out (.format date-in out-formatter)
                                       body (:message message)
                                       [sortable-date readable-date time seconds] (str/split date-out #",")]
                                   (into message {:kind :message :body body :ndx ndx :sortable-date sortable-date :readable-date readable-date :time time :seconds seconds})))

(def input-repo (nth *command-line-args* 0 "."))
(def output-folder (nth *command-line-args* 1 "."))
(def css (nth *command-line-args* 2 "styles.css"))

(def messages 
  (let [json-str (-> (process {:dir input-repo} "git log") (process {:out :string} "jc --git-log") deref :out)]
    (read-str json-str)))

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
        id (str "#" sortable-date "-" time)
        rendered-body (gemtext/to-hiccup (gemtext/parse body))]
    (into [:section {:class "message" :id id}
           [:h3 [:a {:href id} time]]]
          (vec rendered-body))))

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

(def log (into [:main {:id "log"}] content))

(def about [:main {:id "about"} (gemtext/to-hiccup (gemtext/parse (slurp (str input-repo "/about.txt") :encoding "UTF-8")))])

(defn index [content title] [:html {:lang "en-US"}
                             [:head
                              [:meta {:charset "UTF-8"}]
                              [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
                              [:title title]
                              #_[:link {:rel "icon" :type "image/png" :href "favicon.png"}]
                              [:link {:rel "stylesheet" :href css :type "text/css"}]
                             [:body [:nav [:a {:href "index.html"} "log"] " . " [:a {:href "about.html"} "about"]] content]]])

(spit (str output-folder "/index.html") (str "<!DOCTYPE html>" (h/html (index log "log.dudnik.dev/"))))
(spit (str output-folder "/about.html") (str "<!DOCTYPE html>" (h/html (index about "log.dudnik.dev/about"))))