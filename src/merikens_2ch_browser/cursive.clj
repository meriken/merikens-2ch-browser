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
;
; Additional permission under GNU GPL version 3 section 7
;
; If you modify this Program, or any covered work, by linking or
; combining it with Clojure (or a modified version of that
; library), containing parts covered by the terms of EPL, the licensors
; of this Program grant you additional permission to convey the
; resulting work.{Corresponding Source for a non-source form of such
; a combination shall include the source code for the parts of clojure
; used as well as that of the covered work.}



; Extra definitions for IntelliJ and Cursive to suppress unnecessary warnings.
(ns merikens-2ch-browser.cursive
  (require [pandect.core]
           [hiccup.element]
           [hiccup.form]
           [garden.units]))



(defn get-parent-file       [f] (.getParentFile f))
(defn get-path              [f] (.getPath f))
(defn is-directory          [f] (.isDirectory f))
(defn get-time              [f] (.getTime f))
(defn java-file-exists      [f] (.exists f))
(defn java-format-timestamp [format timestamp] (.format format timestamp))
(defn java-sanitize         [sanitizer code]   (.sanitize sanitizer code))
(defn java-get-jdbc-url     [data-source]      (.getJdbcUrl data-source))
(defn java-soft-reset-all-users [data-source] (.softResetAllUsers data-source))
(defn java-pgobject-get-type  [obj] (.getType obj))
(defn java-pgobject-get-value [obj] (.getValue obj))
(defn java-message-digest-reset [m] (.reset m))
(defn java-message-digest-update [m binary-array] (.update m binary-array))
(defn java-message-digest-digest [m] (.digest m))



(defn sha512 [s] (pandect.core/sha512 s))

(defn link-to [& args] (apply hiccup.element/link-to args))
(defn form-to [& args] (apply hiccup.form/form-to args))
(defn label [& args] (apply hiccup.form/label args))
(defn text-field [& args] (apply hiccup.form/text-field args))
(defn password-field [& args] (apply hiccup.form/password-field args))
(defn hidden-field [& args] (apply hiccup.form/hidden-field args))
(defn submit-button [& args] (apply hiccup.form/submit-button args))

(defn px [& args] (apply garden.units/px args))
(defn px+ [& args] (apply garden.units/px+ args))
