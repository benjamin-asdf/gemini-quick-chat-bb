(ns gemini-chat
  (:require
   [babashka.curl :as curl]
   [cheshire.core :as json]
   [clojure.java.process :as p]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(def api-key
  (memoize
   (fn []
     (str/trim
      (p/exec
       "script/gemini_api_key.sh")))))

(defn generate-content
  "Sends a request to the Gemini API to generate content based on the given text."
  [api-key text]
  (let
    [response
       (curl/post
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"
        {:body (json/encode {:contents
                             [{:parts [{:text text}]}]})
         :headers {"Content-Type" "application/json"}
         :query-params {:key api-key}})]
    response))

(comment
  (generate-content (api-key) "say foo"))

(defn read-resp [r]
  (-> r :body (json/decode keyword)))

(defn resp->text
  [resp]
  (->> resp
       :candidates
       first
       :content
       :parts
       first
       :text))

(comment
  (do
    (defn resp->text [resp]
      (->> resp :candidates first :content :parts first :text))
    (resp->text (read-resp resp))))

(defn chat
  [{:keys [input file]}]
  (-> (generate-content (api-key) input)
      read-resp
      resp->text
      println))

#_(defn stream-chat
  [{:keys [input file]}]
  (with-open
    [wrt (io/writer (io/make-writer))]
    (let
        [url
         "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:streamGenerateContent"
         api-key (api-key)
         input (or input (slurp (io/file file)))
         body (json/encode-stream
               {:contents [{:parts [{:text input}]}]})
         headers {"Content-Type" "application/json"}
         query-params {:alt "sse" :key api-key}
         response (curl/post url
                             {:as :stream
                              :body body
                              :headers headers
                              :query-params query-params})]
        (with-open [rdr (io/reader (:body response))]
          (doseq [line (line-seq rdr)]
            (when (str/starts-with? line "data: ")
              (try (let [data (subs line 5) ; Remove "data: "
                                        ; prefix
                         json-data (json/decode data keyword)]
                     (when-let [text (-> json-data
                                         :candidates
                                         first
                                         :content
                                         :parts
                                         first
                                         :text)]
                       (print text)
                       (flush)))        ; Flush the output stream
                   (catch Exception e
                     (println "Error parsing JSON:"
                              (.getMessage e))))))))))


(defn stream-chat
  [{:keys [input file]}]
  (let [os (java.io.ByteArrayOutputStream.)]
    (let
      [url
         "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:streamGenerateContent"
       api-key (api-key)
       input (or input (slurp (io/file file)))
       _ (with-open [w (io/writer os)]
           (json/encode-stream
             {:contents [{:parts [{:text input}]}]}
             w))
       is (java.io.ByteArrayInputStream. (.toByteArray os))
       headers {"Content-Type" "application/json"}
       query-params {:alt "sse" :key api-key}
       response (curl/post url
                           {:as :stream
                            :headers headers
                            :in-stream is
                            :query-params query-params})]
      (with-open [rdr (io/reader (:body response))]
        (doseq [line (line-seq rdr)]
          (when (str/starts-with? line "data: ")
            (try (let [data (subs line 5)
                       json-data (json/decode data keyword)]
                   (when-let [text (-> json-data
                                       :candidates
                                       first
                                       :content
                                       :parts
                                       first
                                       :text)]
                     (print text)
                     (flush)))
                 (catch Exception e
                   (println "Error parsing JSON:"
                            (.getMessage e))))))))))






(comment
  (stream-chat {:input "Write a short poem about clojure"
                :file nil})


  (stream-chat {:file "/home/benj/repos/gemini-chat/src/gemini_chat.clj"})
  *1)
