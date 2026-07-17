#!/usr/bin/env bb
;; meibo — bb-native test runner (Clojure / babashka; no shell). ADR-2607062200 + ADR-2606072802.
;; Per the repo-wide rule (root CLAUDE.md §"Operational code = clj/bb"): first-party
;; tooling is clj/bb, NOT shell. New actors ship run_tests.clj, not run_tests.sh.
;;
;;   bb test
(require '[clojure.test :as t])

(def suites '[meibo.tests.test-directory
              meibo.tests.test-coverage])

(apply require suites)

(let [{:keys [fail error]} (apply t/run-tests suites)]
  (System/exit (if (zero? (+ fail error)) 0 1)))
