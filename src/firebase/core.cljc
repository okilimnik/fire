(ns firebase.core
  (:refer-clojure :exclude [read])
  (:require
   #?(:clj [clojure.walk :refer [stringify-keys walk]])
   #?(:clj  [clojure.core.async :as async]
      :cljs [cljs.core.async :as async])
   #?(:cljs [oops.core :refer [ocall]]))
  (:import #?@(:clj [(com.google.firebase.database ValueEventListener DatabaseReference$CompletionListener DataSnapshot)
                     (java.util HashMap)])))

(defn write!
  "Creates or destructively replaces data in a Firebase database at a given path"
  [db path data & [options]]
  #?(:clj (let [ref (.getReference db path)
                stringified (stringify-keys data)]
            (if (:async options)
              (.updateChildren ref stringified (reify DatabaseReference$CompletionListener
                                                 (onComplete [this error ref']
                                                   (async/put! (:async options) (or error [])))))
              (.updateChildrenAsync ref stringified)))
     :cljs (let [ref (ocall db :ref path)]
             (ocall ref :set data #(do
                                     (async/put! (:async options) %))))))

(defn update!
  "Updates data in a Firebase database at a given path via destructively merging."
  [db path data & [options]]
  #?(:clj (let [ref (.getReference db path)
                stringified (stringify-keys data)]
            (if (:async options)
              (.updateChildren ref stringified (reify DatabaseReference$CompletionListener
                                                 (onComplete [this error ref']
                                                   (async/put! (:async options) (or error [])))))
              (.updateChildrenAsync ref stringified)))
     :cljs (let [ref (ocall db :ref path)]
             (ocall ref :update (clj->js data) #(async/put! (:async options) (or % []))))))

(defn push!
  "Appends data to a list in a Firebase db at a given path."
  [db path data & [options]]
  #?(:clj (let [ref (.push (.getReference db path))]
            (if (:async options)
              (.setValue ref data (reify DatabaseReference$CompletionListener
                                    (onComplete [this error ref']
                                      (async/put! (:async options) (or error [])))))
              (.setValueAsync ref data)))
     :cljs (let [ref (ocall db :ref path)]
             (ocall ref :push data #(do
                                      (async/put! (:async options) %))))))

(defn delete!
  "Deletes data from Firebase database at a given path"
  [db path & [options]]
  #?(:clj (let [ref (.getReference db path)]
            (if (:async options)
              (.removeValueAsync ref #(async/put! (:async options) %))
              (.removeValue ref)))
     :cljs (let [ref (ocall db :ref path)]
             (ocall ref :remove #(async/put! (:async options) %)))))


#?(:clj (defn ->map [data]
          (walk (fn [[k v]]
                  (if (instance? HashMap v)
                    [(keyword k) (->map v)]
                    (if (vector? v)
                      [(keyword k) (mapv ->map v)]
                      [(keyword k) v])))
                identity (zipmap (.keySet data) (.values data)))))

(defn read
  "Retrieves data from Firebase database at a given path"
  [db path & [options]]
  #?(:clj (let [ref (.getReference db path)]
            (.addListenerForSingleValueEvent ref (reify ValueEventListener
                                                   (^void onDataChange [_ ^DataSnapshot snapshot]
                                                     (if-let [v (.getValue snapshot)]
                                                       (let [result (->map v)]
                                                         (async/put! (:async options) result))
                                                       (async/put! (:async options) [])))
                                                   (onCancelled [_ error]
                                                     (async/put! (:async options) error)))))
     :cljs (let [ref (ocall db :ref path)]
             (ocall ref :once "value" #(let [val (js->clj (or (ocall % :val) []) :keywordize-keys true)]

                                         (async/put! (:async options) val))))))