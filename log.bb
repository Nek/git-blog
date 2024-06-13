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
                                 [sortable-date readable-date time seconds] (str/split date-out #",")]
                             (into message {:sortable-date sortable-date :readable-date readable-date :time time :seconds seconds})))

(def git-log-command "git log --pretty=format:'{%n:commit \"%H\"%n  :author \"%aN <%aE>\"%n  :date \"%ad\"%n  :body \"%B\"}'")

(def work-dir (or (first *command-line-args*) "."))

(def messages (edn/read-string (str "[" (-> (shell {:out :string :dir work-dir} git-log-command) :out) "]")))

(def messages-by-date (->> (map parse-date messages) 
                           (group-by :sortable-date)
                           (into (sorted-map))))

(def dates (-> messages-by-date
               (keys)
               (reverse)))

(defn render-date [date]
  [:h2 date])

(defn render-message [message]
  (let [{:keys [time body]} message] 
    [:section {:class "message"}
     [:h3 time]
     (raw-string (md-to-html-string body))]))

(def content (reduce (fn [acc date]
                       (let [messages (get messages-by-date date)
                             rendered-messages (map render-message messages)
                             rendered-date (render-date (:readable-date (first messages)))
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

