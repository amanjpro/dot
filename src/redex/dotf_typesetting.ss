#lang racket
(require redex)
(require "dotf.ss")
(require (only-in mzlib/struct copy-struct))
(require (only-in slideshow/pict text))
(require (only-in racket/match match))

(define (with-dot-writers thunk)
  (define (combine e a)
    ;; Buils the same element as a but with content e
    (copy-struct lw a [lw-e e]))
  (define (add a e)
    (build-lw e
              (+ (lw-line a) (lw-line-span a)) 0
              (+ (lw-column a) (lw-column-span a)) 0))
  (define (then e a)
    (build-lw e
              (lw-line a) 0
              (lw-column a) 0))
  (define (collapse a b)
    ;; Build a zero-width element that takes the same columns
    ;; as a through b
    (build-lw ""
              (lw-line a) (+ (- (lw-line b) (lw-line a))
                             (lw-line-span b))
              (lw-column a) (+ (- (lw-column b) (lw-column a))
                               (lw-column-span b))))
  (define (subtext txt)
    ;; Creates a text element as a subscript
    (text txt `(subscript . ,(default-style)) (default-font-size)))
  (define (remove-parens a)
    ;; Remove exactly one outer parentheses
    (let* ([lws (lw-e a)]
           [oparen (first lws)]
           [cparen (last lws)]
           (meat (rest (drop-right lws 1))))
      (combine
       (cons
        (collapse oparen oparen)
        (append
         meat
         (list (collapse cparen cparen)))) a)))
  (define (pretty-binding a)
    (match (lw-e a)
      [(list oparen l v cparen)
       (combine
        (list
         (collapse oparen oparen)
         l (then "=" v) v
         (collapse cparen cparen)) a)]
      [(list oparen m x e cparen)
       (combine
        (list
         (collapse oparen oparen)
         m (then "(" x) x (then ")=" e) e
         (collapse cparen cparen)) a)]))
  (define (pretty-constructor a)
    (match (lw-e a)
      [(list oparen ty bs ... cparen)
       (combine
        (list*
         (collapse oparen oparen)
         ty (add ty "(")
         (append
          (map pretty-binding bs)
          (list (then ")" cparen) (collapse cparen cparen)))) a)]))
  (with-atomic-rewriter 'Top "⊤"
  (with-atomic-rewriter 'Bottom "⊥"
  (with-compound-rewriters
   (['val
     (λ (lws)
       (list
        (collapse (first lws) (list-ref lws 0))
        (combine "val" (list-ref lws 1))
        (list-ref lws 2) ; x
        (list-ref lws 3) ; =
        (list-ref lws 4) ; new
        (pretty-constructor (list-ref lws 5)) ; c
        (combine ";" (list-ref lws 6)) ; in
        (list-ref lws 7) ; e
        (collapse (list-ref lws 8) (last lws))
       ))]
    ['sel
     (λ (lws)
       (list
        (collapse (first lws) (list-ref lws 1))
        (list-ref lws 2)
        "."
        (list-ref lws 3)
        (collapse (list-ref lws 4) (last lws))))]
    ['label-value
     (λ (lws)
       (list
        (collapse (first lws) (list-ref lws 1))
        (list-ref lws 2)
        (collapse (list-ref lws 3) (last lws))))]
    ['label-method
     (λ (lws)
       (list
        (collapse (first lws) (list-ref lws 1))
        (list-ref lws 2)
        (collapse (list-ref lws 3) (last lws))))]
    ['label-abstract-type
     (λ (lws)
       (list
        (collapse (first lws) (list-ref lws 1))
        (list-ref lws 2)
        (subtext "a")
        (collapse (list-ref lws 3) (last lws))))]
    ['label-class
     (λ (lws)
       (list
        (collapse (first lws) (list-ref lws 1))
        (list-ref lws 2)
        (subtext "c")
        (collapse (list-ref lws 3) (last lws))))]
    [':
     (λ (lws)
       (list
        (collapse (first lws) (list-ref lws 1))
        (list-ref lws 2)
        ":"
        (list-ref lws 3)
        (collapse (list-ref lws 4) (last lws))))]
    ['refinement
     (λ (lws)
       (define (helper lws first?)
         (if (null? (cdr lws))
             (list (combine " }" (last lws)))
             (append
              (if first? (list (car lws)) (list "," (car lws)))
              (helper (cdr lws) #f))))
       (list*
         (collapse (first lws) (list-ref lws 1))
         (list-ref lws 2)
         " { "
         (list-ref lws 3)
         " ⇒ "
         (helper (list-tail lws 4) #t)))])
   (thunk)))))

(with-dot-writers (lambda () (render-term dot
(val u = new ((refinement Top self (: (label-value l) Top))
              [(label-value l) u]) in
(sel u (label-value l)))
)))

(with-dot-writers (lambda () (render-term dot
(val v = new ((refinement Top z (: (label-abstract-type L) Bottom (refinement Top z (: (label-abstract-type A) Bottom Top) (: (label-abstract-type B) Bottom (sel z (label-abstract-type A))))))) in
(app (as (arrow (refinement Top z (: (label-abstract-type L) Bottom (refinement Top z (: (label-abstract-type A) Bottom Top) (: (label-abstract-type B) Bottom (sel z (label-abstract-type A)))))) Top)
         (fun (x (refinement Top z (: (label-abstract-type L) Bottom (refinement Top z (: (label-abstract-type A) Bottom Top) (: (label-abstract-type B) Bottom Top))))) Top
              (val z = new ((refinement Top z (: (label-method l)
                                               (intersection
                                                (sel x (label-abstract-type L))
                                                (refinement Top z (: (label-abstract-type A) Bottom (sel z (label-abstract-type B))) (: (label-abstract-type B) Bottom Top)))
                                               Top))
                          ((label-method l) y (as Top (fun (a (sel y (label-abstract-type A))) Top a)))) in
              (cast Top z))))
     v)))
))