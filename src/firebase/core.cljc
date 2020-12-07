(ns firebase.core
  (:refer-clojure :exclude [read])
  (:require #?(:clj [clojure.core.async :as async]
               :cljs [cljs.core.async :as async])
            #?(:cljs [oops.core :refer [ocall]])))

(defn write!
  "Creates or destructively replaces data in a Firebase database at a given path"
  [db path data & [options]]
  #?(:clj (let [ref (.getReference db path)]
            (if (:async options)
              (.setValueAsync ref data #(async/put! (:async options) %))
              (.setValue ref data)))
     :cljs (let [ref (ocall db :ref path)]
             (ocall ref :set data #(do
                                     (async/put! (:async options) %))))))

(defn update!
  "Updates data in a Firebase database at a given path via destructively merging."
  [db path data & [options]]
  #?(:clj (let [ref (.getReference db path)]
            (if (:async options)
              (.updateChildrenAsync ref data #(async/put! (:async options) %))
              (.updateChildren ref data)))
     :cljs (let [ref (ocall db :ref path)]
             (ocall ref :update (clj->js data) #(do

                                                  (async/put! (:async options) (or % [])))))))

(defn push!
  "Appends data to a list in a Firebase db at a given path."
  [db path data & [options]]
  #?(:clj (let [ref (.push (.getReference db path))]
            (if (:async options)
              (.setValueAsync ref data #(async/put! (:async options) %))
              (.setValue ref data)))
     :cljs (let [ref (ocall db :ref path)]
             (ocall ref :push data #(do
                                      (async/put! (:async options) %)))))

  (defn delete!
    "Deletes data from Firebase database at a given path"
    [db path & [options]]
    #?(:clj (let [ref (.getReference db path)]
              (if (:async options)
                (.removeValueAsync ref #(async/put! (:async options) %))
                (.removeValue ref)))
       :cljs (let [ref (ocall db :ref path)]
               (ocall ref :remove #(async/put! (:async options) %))))))

(defn read
  "Retrieves data from Firebase database at a given path"
  [db path & [options]]
  #?(:clj (let [ref (.getReference db path)]
            (.addListenerForSingleValueEvent ref (reify ValueEventListener
                                                   (onDataChange [_ snapshot]
                                                     (async/put! (:async options) snapshot))
                                                   (onCancelled [_ error]
                                                     (async/put! (:async options) error)))))
     :cljs (let [ref (ocall db :ref path)]
             (ocall ref :once "value" #(let [val (js->clj (or (ocall % :val) []) :keywordize-keys true)]
                                  
                                         (async/put! (:async options) val))))))