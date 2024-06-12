#!/usr/bin/env bb

(ns log
  (:require [babashka.process :refer [shell]]
            [clojure.edn :as edn]
            [hiccup2.core :as h]
            [clojure.string :as str]))

(import 'java.time.format.DateTimeFormatter
        'java.time.LocalDateTime)

#_(def date-str "Sun Jan 7 22:19:25 2024 +0100")

(defn parse-date [message] (let [in-formatter (DateTimeFormatter/ofPattern "EEE MMM d HH:mm:ss yyyy Z")
                                 out-formatter (DateTimeFormatter/ofPattern "dd MMMM yyyy','HH:mm','ss")
                                 date-in (LocalDateTime/parse (:date message) in-formatter)
                                 date-out (.format date-in out-formatter)
                                 [day-date time seconds] (str/split date-out #",")]
                             (into message {:day-date day-date :time time :seconds seconds})))

(def messages (edn/read-string (-> (shell {:out :string} (str "./log2edn.sh " (or (first *command-line-args*) "."))) :out)))

(def enriched-messages (map parse-date messages))

(println enriched-messages)



(def markup [:html [:head [:title "Log"]]
             [:body 
             [:div {:class "messages"} (for [message enriched-messages]
                   [:div {:class "message"}
                    [:div {:class "date"} (:day-date message)]
                    [:div {:class "time"} (:time message)]
                    #_[:div {:class "seconds"} (:seconds message)]
                    [:div {:class "author"} (:author message)]
                    [:div {:class "message"} (:message message)]])]]])

(spit "index.html" (str
                     "<!DOCTYPE html>" (h/html markup)))

