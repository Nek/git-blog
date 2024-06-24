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

(require '[org.httpkit.server :as srv]
         '[babashka.process :refer [shell]]
         '[clojure.edn :as edn]
         '[clojure.java.browse :as browse]
         '[ruuter.core :as ruuter]
         '[clojure.string :as str]
         '[hiccup2.core :as h]
         '[hiccup.util :refer [raw-string]]
         '[markdown.core :refer [md-to-html-string]]
         '[gemtext.core :as gemtext])

(import '[java.net URLDecoder])

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


(def message-comps-by-date (->> messages
                                (map-indexed message-comp)
                                ;; (map #(paginate 10 %))
                                (group-by :sortable-date)
                                (into (sorted-map))))

(def message-dates (-> message-comps-by-date
                       (keys)
                       (reverse)))

(defn date-comp [date page] {:kind :date :date date :page page})

(defn render-date [{:keys [date]}]
  [:h2 date])

(defn render-message [message]
  (let [{:keys [time body sortable-date]} message
        id (str "#" sortable-date "-" time)]
    [:section {:class "message" :id id} 
     [:h3 [:a {:href id} time]]
     (map #(vec ( concat [ (first %) {:class "gemini"}] (rest %))) (gemtext/to-hiccup (gemtext/parse body)))]))

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

(def content (map render-comp comps))

(def css (slurp "styles.css" :encoding "UTF-8"))

(def markup [:html {:lang "en-US"}
             [:head
              [:meta {:charset "UTF-8"}]
              [:title "Log"]
              [:style (raw-string css)]]
             [:body (into [:main] content)]])

(defn index [] (list
                "<!DOCTYPE html>" 
                (h/html markup)))

;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Handlers
;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn render [handler & [status]]
  {:status (or status 200)
   :body (handler)})

(defn app-index [_]
  (render index))

(def port 3001)

;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Routes
;;;;;;;;;;;;;;;;;;;;;;;;;;

(def routes [{:path     "/"
              :method   :get
              :response app-index}
             #_{:path     "/todos/edit/:id"
              :method   :get
              :response edit-item}
            ])

;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Server
;;;;;;;;;;;;;;;;;;;;;;;;;;

(when (= *file* (System/getProperty "babashka.file"))
  (let [url (str "http://localhost:" port "/")]
    (srv/run-server #(ruuter/route routes %) {:port port})
    (println "serving" url)
    (browse/browse-url url)
    @(promise)))
