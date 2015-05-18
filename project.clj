; This file is part of Meriken's 2ch Browser.
;
; Meriken's 2ch Browser is free software: you can redistribute it and/or modify
; it under the terms of the GNU General Public License as published by
; the Free Software Foundation, either version 3 of the License, or
; (at your option) any later version.
;
; Meriken's 2ch Browser is distributed in the hope that it will be useful,
; but WITHOUT ANY WARRANTY; without even the implied warranty of
; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
; GNU General Public License for more details.
;
; You should have received a copy of the GNU General Public License
; along with Meriken's 2ch Browser.  If not, see <http://www.gnu.org/licenses/>.



(defproject
  merikens-2ch-browser
  "0.1.24"
  :description
  "Meriken's 2ch Browser is a dedicated browser for 2ch.net, 2ch.sc, open2ch.net and other 2ch-compatible online forums."
  :ring
  {:handler merikens-2ch-browser.handler/app,
   :init merikens-2ch-browser.core/init,
   :destroy merikens-2ch-browser.core/destroy
   :uberwar-name "ROOT.war"} ; Added by Meriken
  :resources-path "resources" ; Added by Meriken
  :cljsbuild
  {:builds
   [{:source-paths ["src-cljs"],
     :compiler
     {:optimizations :advanced,
      :output-to "resources/public/js/clojurescript.js",
      :externs ["externs/jquery.js"],
      :pretty-print false},
     :jar true}]}
  :plugins
  [[lein-ring "0.8.11"]
   [lein-environ "0.5.0"]
   [lein-cljsbuild "1.0.5"]
   [cider/cider-nrepl "0.8.1"]]
  :url
  "http://example.com/FIXME"
  :profiles
  {:uberjar {:aot :all},
   :production
   {:ring
    {:open-browser? true, :stacktraces? false, :auto-reload? false}},
   :dev
   {:dependencies
    [[ring-mock "0.1.5"]
     [ring/ring-devel "1.3.0"]
     [pjstadig/humane-test-output "0.6.0"]],
    :injections
    [(require 'pjstadig.humane-test-output)
     (pjstadig.humane-test-output/activate!)],
    :env {:dev true}}}
  :dependencies
  [[org.clojure/clojure "1.6.0"]
   [org.clojure/clojurescript "0.0-3211"]

   [log4j "1.2.17" :exclusions [javax.mail/mail javax.jms/jms com.sun.jdmk/jmxtools com.sun.jmx/jmxri]]

   [ring/ring-core "1.3.1"]
   [ring/ring-jetty-adapter "1.3.1"]
   [http-kit "2.1.16"]
   [org.immutant/web "2.x.incremental.373"] ; Added by Meriken
   [noir-exception "0.2.2"]

   ; Databases
   [org.clojure/java.jdbc "0.3.6"]
   [clojure.jdbc/clojure.jdbc-c3p0 "0.3.1"]
   [korma "0.4.0"] ; Added by Meriken
   [com.h2database/h2 "1.4.187"] ; Added by Meriken
   [org.hsqldb/hsqldb "2.3.2"] ; Added by Meriken
   [org.postgresql/postgresql "9.3-1101-jdbc3"] ; Added by Meriken
   [mysql/mysql-connector-java "5.1.25"] ; Added by Meriken

   [org.clojure/data.fressian "0.2.0"]
   [cljs-ajax "0.2.6"]
   [hipo "0.3.0"]
   [jayq "2.5.4"]
   [com.taoensso/timbre "3.2.1"]
   [environ "0.5.0"]

   [com.googlecode.owasp-java-html-sanitizer/owasp-java-html-sanitizer "r239"] ; Added by Meriken
   [clj-http "0.9.2"] ; Added by Meriken
   [clj-time "0.7.0"] ; Added by Meriken
   ; [pandect "0.3.4"] ; Added by Meriken
   [pandect "0.5.1"]
   ; [org.clojars.mikejs/ring-gzip-middleware "0.1.0-SNAPSHOT"] ; Added by Meriken
   [rm-hull/ring-gzip-middleware "0.1.7"] ; Added by Meriken
   ; [amalloy/ring-gzip-middleware "0.1.3"] ; Added by Meriken
   [org.apache.commons/commons-lang3 "3.0"] ; Added by Meriken
   [com.taoensso/tower "2.0.2"]
   ; [ragtime "0.3.6"]
   [lib-noir "0.8.4"]
   [com.twelvemonkeys.imageio/imageio-core "3.0-rc5"] ; Added by Meriken. BSD
   [com.twelvemonkeys.imageio/imageio-jpeg "3.0-rc5"] ; Added by Meriken. BSD
   [com.climate/claypoole "0.3"] ; Added by Meriken. Apache 2.0
   [com.taoensso/nippy "2.6.3"] ; Added by Meriken. Eclipse
   [org.clojure/data.codec "0.1.0"] ; Added by Meriken. Eclipse
   ; [criterium "0.4.3"] ; Added by Meriken. Eclipse
   [org.jsoup/jsoup "1.8.1"] ; Added by Meriken.
   [org.clojure/math.numeric-tower "0.0.4"] ; Added by Meriken.
   [garden "1.2.5"] ; Added by Meriken.
   [ring/ring-json "0.3.1"] ; Added by Meriken.
   [cheshire "5.4.0"] ; Added by Meriken.
   [clj-json "0.5.3"] ; Added by Meriken.
   ]
  :repositories [["Immutant 2.x incremental builds"
                  "http://downloads.immutant.org/incremental/"]]
:repl-options
  {:init-ns merikens-2ch-browser.core}
  :min-lein-version "2.0.0"
  :main merikens-2ch-browser.core ; Added by Meriken
  :uberjar-name "merikens-2ch-browser.jar" ; Added by Meriken
  :jvm-opts ["-server"
             "-Xmx12g"
             "-XX:MaxPermSize=256m"
             "-XX:+UseG1GC"
             "-XX:MaxGCPauseMillis=1000"
             "-Dfile.encoding=utf-8"])
