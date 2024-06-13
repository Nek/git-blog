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

(defn parse-date [message] (let [in-formatter (DateTimeFormatter/ofPattern "EEE MMM d HH:mm:ss yyyy Z")
                                 out-formatter (DateTimeFormatter/ofPattern "yyyyMMdd','dd MMMM yyyy','HH:mm:ss','ss")
                                 date-in (LocalDateTime/parse (:date message) in-formatter)
                                 date-out (.format date-in out-formatter)
                                 [sort-date day-date time seconds] (str/split date-out #",")]
                             (into message {:sort-date sort-date :day-date day-date :time time :seconds seconds})))

(def messages (edn/read-string (-> (shell {:out :string} (str "./log2edn.sh " (or (first *command-line-args*) "."))) :out)))

(def enriched-messages (map parse-date messages))

(def messages-by-date (->> enriched-messages 
                           (group-by :sort-date)
                           (into (sorted-map))))

(def dates (-> messages-by-date
               (keys)
               (reverse)))

(defn render-date [date]
  [:h2 date])

(defn render-message [message]
  (let [{:keys [time body]} message] 
    (println body)
    [:section {:class "message"}
     [:h3 time]
     [:div (raw-string (md-to-html-string body))]]))

(def content (reduce (fn [acc date]
                       (let [messages (get messages-by-date date)
                             rendered-messages (map render-message messages)
                             rendered-date (render-date (:day-date (first messages)))
                             section (vec (concat [:section {:class "year"}] [rendered-date] rendered-messages))]
                         (into acc [section])))
                     [:main]
                     dates))

(def css (slurp "styles.css" :encoding "UTF-8"))

(def markup [:html {:lang "en-US"} [:head
                                    [:style (raw-string css)]
                                    [:title "Log"]]
             (into [:body] [content])])

(spit "index.html" (str
                    "<!DOCTYPE html>" (h/html markup)))

