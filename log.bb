#!/usr/bin/env bb

 (ns log
   (:require [babashka.process :refer [shell]]
             [clojure.edn :as edn]
             [hiccup.util :refer [raw-string]]
             [hiccup2.core :as h]
             [clojure.string :as str]
             [markdown.core :refer [md-to-html-string]]))

(import 'java.time.format.DateTimeFormatter
        'java.time.LocalDateTime)

(defn paginate [per-page message-comp] (into message-comp {:page (int (/ (:ndx message-comp) per-page))}))

(defn message-comp [ndx message] (let [in-formatter (DateTimeFormatter/ofPattern "EEE MMM d HH:mm:ss yyyy Z")
                                       out-formatter (DateTimeFormatter/ofPattern "yyyyMMdd','dd MMMM yyyy','HH:mm:ss','ss")
                                       date-in (LocalDateTime/parse (:date message) in-formatter)
                                       date-out (.format date-in out-formatter)
                                       [sortable-date readable-date time seconds] (str/split date-out #",")]
                                   (into message {:kind :message :ndx ndx :sortable-date sortable-date :readable-date readable-date :time time :seconds seconds})))

(def git-log-command "git log --pretty=format:'{%n:commit \"%H\"%n  :author \"%aN <%aE>\"%n  :date \"%ad\"%n  :body \"%B\"}'")

(def work-dir (or (first *command-line-args*) "."))

(def messages (edn/read-string (str "[" (-> (shell {:out :string :dir work-dir} git-log-command) :out) "]")))

;; (println (map-indexed (fn [ndx message] (into message {:page (int (/ ndx 10))})) messages))


;; (defn paginate [per-page] (fn [message ndx] (if (= (% ndx per-page))))

(def message-comps-by-date (->> messages
                                (map-indexed message-comp)
                                (map #(paginate 10 %))
                                (group-by :sortable-date)
                                (into (sorted-map))))

(def message-dates (-> message-comps-by-date
                       (keys)
                       (reverse)))

(defn date-comp [date page] {:kind :date :date date :page page})

(defn render-date [{:keys [date]}]
  [:h2 date])

(defn render-message [message]
  (let [{:keys [time body]} message]
    [:section {:class "message"}
     [:h3 time]
     (raw-string (md-to-html-string body))]))

(def comps (reduce (fn [acc date]
                     (let [message-comps (get message-comps-by-date date)
                           {:keys [readable-date page]} (first message-comps)
                           comps (into [(date-comp readable-date page)] message-comps)]
                       (into acc comps)))
                   []
                   message-dates))

(def pages (partition-by :page comps))

(defn get-page [page] (let [max-page (dec (count pages))]
                       (nth  pages (max 0 (min max-page page)))))

(println (count pages))

(defmulti render-comp :kind)
(defmethod render-comp :message [message-comp] (render-message message-comp))
(defmethod render-comp :date [date-comp] (render-date date-comp))

(def content (map render-comp (get-page 0)))

(def css (slurp "styles.css" :encoding "UTF-8"))

(def markup [:html {:lang "en-US"}
             [:head
              [:meta {:charset "UTF-8"}]
              [:title "Log"]
              [:style (raw-string css)]]
             [:body (into [:main] content)]])

(spit "index.html" (str
                    "<!DOCTYPE html>" (h/html markup)))

