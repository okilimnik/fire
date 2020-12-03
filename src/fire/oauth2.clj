(ns fire.oauth2
  (:require [clojure.string :as str]
            [environ.core :refer [env]]
            [fire.interop :refer [date decode encode]]
            [httpurr.client :as http]
            [cemerick.url :as url-helper]
            #?(:cljs [oops.core :refer [oget]])
            #?(:cljs ["firebase-admin" :as admin])
            #?(:clj [httpurr.client.aleph :refer [client]]
               :cljs [httpurr.client.node :refer [client]]))
  (:import  #?(:clj [java.security KeyFactory Signature])
            #?(:clj [java.security.spec PKCS8EncodedKeySpec])
            #?(:clj [java.util Base64 Base64$Decoder Base64$Encoder])))

#?(:clj (set! *warn-on-reflection* 1))

(defn- now []
  (quot (inst-ms (date.)) 1000))

(defn- clean-env-var [env-var]
  (-> env-var (name) (str) (str/lower-case) (str/replace "_" "-") (str/replace "." "-") (keyword)))

(defn str->private-key [keystr']
  #?(:clj (let [^Base64$Decoder b64decoder (. Base64 getDecoder)
                ^KeyFactory kf (KeyFactory/getInstance "RSA")
                ^String keystr (-> keystr' (str/replace "\n" "") (str/replace "-----BEGIN PRIVATE KEY-----" "") (str/replace "-----END PRIVATE KEY-----" ""))]
            (->> keystr
                 (.decode b64decoder)
                 (PKCS8EncodedKeySpec.)
                 (.generatePrivate kf)))))

(defn sign [claims' priv-key]
  #?(:clj (let [^Base64$Encoder b64encoder (. Base64 	getUrlEncoder)
                ^Signature sig (Signature/getInstance "SHA256withRSA")
                strip (fn [s] (str/replace s "=" ""))
                encode' (fn [b] (strip (.encodeToString b64encoder (.getBytes ^String b "UTF-8"))))
                rencode' (fn [b] (strip (.encodeToString b64encoder ^"[B" b)))
                header "{\"alg\":\"RS256\"}"
                claims (encode claims')
                jwtbody (str (encode' header) "." (encode' claims))]
            (.initSign sig priv-key)
            (.update sig (.getBytes ^String jwtbody "UTF-8"))
            (str jwtbody "." (rencode' (.sign sig))))))

(defn get-token [env-var]
  (let [claims {:iss (:client_email auth) :scope scopes :aud aud :iat t :exp (+ t 3599)}
        auth (-> env-var clean-env-var env decode)]
    #?(:clj (if-not (:private_key auth)
              nil
              (let [scopes "https://www.googleapis.com/auth/firebase.database https://www.googleapis.com/auth/userinfo.email"
                    aud "https://oauth2.googleapis.com/token"
                    private-key (-> auth :private_key str->private-key)
                    token (sign claims private-key)
                    body (str "grant_type=" (url-helper/url "urn:ietf:params:oauth:grant-type:jwt-bearer") "&assertion=" token "&access_type=offline")
                    res' (http/send! client {:url aud
                                             :headers {"Content-Type" "application/x-www-form-urlencoded"}
                                             :body body
                                             :method :post})
                    res (-> res' :body decode)]
                (when (= (:status res') 200)
                  nil
                  {:token (:access_token res)
                   :expiry (+ (now) (:expires_in res) -5)
                   :project-id (:project_id auth)
                   :type (:type auth)})))
       :cljc (do
               (.initializeApp admin)
               (-> admin
                   (.auth)
                   (.createCustomToken (random-uuid) (clj->js claims))
                   (.then (fn [res]
                            {:token (oget res :access_token)
                             :expiry (+ (now) (oget res :expires_in) -5)
                             :project-id (:project_id auth)
                             :type (:type auth)})))))))
