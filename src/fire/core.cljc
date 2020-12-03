(ns fire.core
  (:require #?(:clj [clojure.core.async :as async]
               :cljs [cljs.core.async :as async])
            [httpurr.client :as http]
            #?(:cljs [oops.core :refer [oget]])
            #?(:clj [httpurr.client.aleph :refer [client]]
               :cljs [httpurr.client.node :refer [client]])
            [cemerick.url :as url-helper]
            [fire.auth :as fire-auth]
            [fire.interop :refer [date exception throwable decode encode]])
  (:refer-clojure :exclude [read]))

#?(:clj (set! *warn-on-reflection* 1))

(def firebase-root "firebaseio.com")
(def http-type {:get    "GET"
                :post   "POST"
                :put    "PUT"
                :patch  "PATCH"
                :delete "DELETE"})
(defn thrower [res]
  (when (instance? throwable res) (throw res))
  res)

(defn recursive-merge
  "Recursively merge hash maps."
  [a b]
  (if (and (map? a) (map? b))
    (merge-with recursive-merge a b)
    (if (map? a) a b)))

(defn db-base-url 
  "Returns a proper Firebase base url given a database name"
  [db-name]
  (str "https://" db-name "." firebase-root))

(defn db-url 
  "Returns a proper Firebase url given a database name and path"
  [db-name path]
  (let [url (try (str (url-helper/url db-name)) (catch exception _ nil))]
    (if (nil? url)
      (str (db-base-url db-name) path ".json")
      (str url path ".json"))))

(defn request 
  "Request method used by other functions."
  [method db-name path data & [auth options]]
  (let [res-ch (async/chan 1)]
    (try
      (let [now (quot (inst-ms (date.)) 1000)
            token (when auth
                    (if (< now (:expiry auth))
                      (:token auth)
                      (-> auth :env fire-auth/create-token :token)))
            request-options (reduce
                             recursive-merge [{:query-params {:pretty-print true}}
                                              {:headers {"X-HTTP-Method-Override" (method http-type)}}
                                              {:keepalive 600000}
                                              (when auth {:headers {"Authorization" (str "Bearer " token)}})
                                              (when (not (nil? data)) {:body (encode data)})
                                              (dissoc options :async)])
            url (db-url db-name path)
            params   (merge {:url url
                             :method :post}
                            request-options)
            on-result (fn [res error]
                        (if error
                          (do
                            (async/put! res-ch error)
                            (async/close! res-ch))
                          (if (nil? res)
                            (async/close! res-ch)
                            (async/put! res-ch res))))]
        #?(:clj (http/send! client params
                            (fn [response]
                              (let [res (-> response :body decode)
                                    error (:error response)]
                                (on-result res error))))
           :cljs (-> (http/send! client params
                                 (.then (fn [response]
                                          (let [res (-> response (oget :body) decode)
                                                error (oget response :error)]
                                            (on-result res error))))
                                 (.catch (fn [e]
                                           (on-result nil e))))))
      (catch exception e 
        (async/put! res-ch e)
        (async/close! res-ch)))
      res-ch))  

(defn write! 
  "Creates or destructively replaces data in a Firebase database at a given path"
  [db-name path data auth & [options]]
  (let [res (request :put db-name path data auth options)]
    (if (:async (merge {} options auth))
      res
      #?(:clj (-> res async/<!! thrower)
         :cljs res))))

(defn update!
  "Updates data in a Firebase database at a given path via destructively merging."
  [db-name path data auth & [options]]
  (let [res (request :patch db-name path data auth options)]
    (if (:async (merge {} options auth))
      res
      #?(:clj (-> res async/<!! thrower)
         :cljs res))))

(defn push!
  "Appends data to a list in a Firebase db at a given path."
  [db-name path data auth & [options]]
  (let [res (request :post db-name path data auth options)]
    (if (:async (merge {} options auth))
      res
       #?(:clj (-> res async/<!! thrower)
          :cljs res))))

(defn delete! 
  "Deletes data from Firebase database at a given path"
  [db-name path auth & [options]]
  (let [res (request :delete db-name path nil auth options)]
    (if (:async (merge {} options auth))
      res
      #?(:clj (-> res async/<!! thrower)
         :cljs res))))

(defn escape 
  "Surround all strings in query with quotes"
  [query]
  (apply merge (for [[k v] query]  {k (if (string? v) (str "\"" v "\"") v)})))

(defn read
  "Retrieves data from Firebase database at a given path"
  [db-name path auth & [options]]
  (let [res (request :get db-name path nil auth (merge {:query-params (or (escape (:query options)) {})} 
                                                       (dissoc options :query)))]
    (if (:async (merge {} options auth))
      res
      #?(:clj (-> res async/<!! thrower)
         :cljs res))))