(require 'server)
(setq server-use-tcp t server-socket-dir "~/.emacs.d/server")
(setq server-host "127.0.0.1")
(setq server-port "1024")
(setq server-auth-key (server-generate-key))
(unless (server-running-p) (server-start))
(setq server-auth-endcoded-key (replace-regexp-in-string (regexp-quote "\\") "\\\\" server-auth-key nil 'literal))
(setq server-auth-endcoded-key (replace-regexp-in-string (regexp-quote "\"") "\\\"" server-auth-endcoded-key nil 'literal))
(setq server-auth-key "")



(set 'src-anal-host "127.0.0.1")
(set 'src-anal-port "27017")
(set 'src-anal-db (getenv "USER"))
(set 'src-anal-con-str (concat src-anal-host ":" src-anal-port "/" src-anal-db))



(set 'source-code-browser-config-endpoint "http://localhost:8080/sourcecodebrowser/config")

(defun get-server-config () (concat "{\"host\": \"" server-host "\", \"port\": \"" server-port "\", \"authorizationKey\": \"" server-auth-endcoded-key "\", \"srcDbConnStr\": \"" src-anal-con-str "\"}"))

(let ((url-request-method "POST") (url-request-extra-headers '(("Content-Type" . "application/json"))) (url-request-data (get-server-config))) (url-retrieve-synchronously source-code-browser-config-endpoint))



(set 'source-code-browser-endpoint "http://localhost:8080/sourcecodebrowser/target")

(defun get-json-request-url (file-name file-position) (concat "{\"file\": \"" file-name "\", \"position\": " (number-to-string file-position) "}"))

(defun get-json-request-url-at-here () (get-json-request-url buffer-file-name (point)) )

(defun browse-source-code-at-here () (let ((url-request-method "POST") (url-request-extra-headers '(("Content-Type" . "application/json"))) (url-request-data (get-json-request-url-at-here))) (url-retrieve-synchronously source-code-browser-endpoint)))

(defun browse-source-code-at-here-cmd () (interactive) (browse-source-code-at-here) (message ""))

(global-set-key [?\M-\.] 'browse-source-code-at-here-cmd)


(set 'PWD (getenv "PWD"))
(setenv "SRC_PARSER_WS" PWD)
