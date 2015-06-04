(ns merikens-2ch-browser.interop)

(defn get-parent-file [f] (.getParentFile f))
(defn get-path        [f] (.getPath f))
(defn is-directory    [f] (.isDirectory f))
(defn get-time        [f] (.getTime f))
(defn java-file-exists [f] (.exists f))
(defn java-format-timestamp [format timestamp] (.format format timestamp))
(defn java-sanitize         [sanitizer code]   (.sanitize sanitizer code))
(defn java-get-jdbc-url     [data-source]      (.getJdbcUrl data-source))