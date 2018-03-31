# valentine

A simple Clojure pattern validation/manipulation library.

- Pattern syntax built directly from literals and predicates
- Strict or permissive collection type validation
- Powerful structure manipulation with variable binding

## Usage

### Validating and matching structures

#### conforms?

The heart of Valentine is the `conforms?` function. It takes a Clojure structure and a pattern and returns true if the structure matches the pattern.

```
(conforms? [1 2] [1 2])
; ==> true

(conforms? '(1 2) [1 2])
; ==> false

(conforms? [1 2] [odd? even?])
; ==> true

(conforms? [1 "A" {:a "B"}] [odd? string? {keyword? "B"}])
; ==> true
```

As you can see, primitives match themselves. Functions match if they return a truthy value. Collections only match collections of the same type.

Valentine also provides the following functions to be used in patterns:

 - `coll` matches any type of collection:
 ```
 (conforms? [1 2] (coll 1 2))
 ; ==> true

 (conforms? '(1 2) (coll 1 2))
 ; ==> true
 ```

 - `coll-of` takes a single argument, and matches any collection that contains only elements that are matched by that argument:
 ```
 (conforms? [1 2 3 4] (coll-of integer?))
 ; ==> true

 (conforms? #{nil false} (coll-of not))
 ; ==> true
 ```

 - `vec-of`, `list-of`, and `set-of` behave like `coll-of` but are strict on collection type

 - `mset` matches like a set literal, but allows you to supply duplicate matching items:
 ```
 (conforms? #{1 2 4 "Sheep"} (mset even? even? string? 1))
 ; ==> true
 ```

 - `literal` always checks for direct equality, so it allows you to match functions:
```
 (conforms? [2 even?] [even? (literal even?)])
 ; ==> true
```

The `matches` function returns a list of all objects in a structure that are matched by a pattern:

```
(matches [1 [2 3 4] [[5 6] [7 8] 9]] even?)
; ==> (2 4 6 8)

(matches [1 [2 3 4] [[5 6] [7 8] 9]] [integer? some? some?])
; ==> ([1 [2 3 4] [[5 6] [7 8] 9]] [2 3 4])
```

### Binding and Structure manipulation

#### with

The `with` function allows you to bind parts of your matched structure to names that you can then reference within the body of `with`. To bind a value, wrap its matcher and the bound name with `as`.

```
(with {:first-name "Bob" :last-name "Ross"}
      {:first-name (as string? fname) :last-name (as string? lname)}
      (str fname " " lname))
; ==> "Bob Ross"

(with [even? 3] [(as fn? f) (as some? arg)]
      (println "Function:" f)
      (println "Argument:" arg)
      (f arg))
; ==> Function: #function[clojure.core/even?]
; ==> Argument: 3
; ==> false
```

If the structure does not match the pattern, `with` returns nil:
```
(with [1 2 false] [(as odd? odd) (as even? even) true?]
      (+ even odd))
; ==> nil
```

#### replace-with

`replace-with` allows you to any matches in a structure. It supports bindings with `as`, which is a powerful way to manipulate data.

```
(replace-with [1 2 [3 4 [5] 6] 7 [[8]]] even? "even")
; ==> [1 "even" [3 "even" [5] "even"] 7 [["even"]]]

(replace-with {:a "foo" :b {:c "bar" :d "baz"}} (as string? s) (clojure.string/capitalize s))
; ==> {:a "Foo", :b {:c "Bar", :d "Baz"}}

(replace-with ["+" ["/" ["*" 3 8] 3] ["-" 14 ["/" 6 3]]] ["/" (as some? num) (as some? denom)] ["/" denom num])
; ==> ["+" ["/" 3 ["*" 3 8]] ["-" 14 ["/" 3 6]]]
```

#### update-with

`update-with` is like `replace-with`, but rather than a replacement value, takes a function that is applied to the matching structure. It also supports bindings with `as`.

```
(update-with {:a [1 2 3] :b "foo" :c [4 5 6]} (vec-of #(> % 2)) #(reduce + %))
; ==> {:a [1 2 3], :b "foo", :c 15}
```

## Cheatsheet

##### conforms?
```
(conforms? structure matcher)
"Returns true if structure is matched by matcher, false otherwise."
```
##### matches
```
(matches structure matcher)
"Returns a list of all elements in structure that are matched by matcher."
```
##### with
```
(with structure matcher & body)
"If structure is matched by matcher, executes body with bindings provided by calls to
`as` in matcher. Otherwise returns nil."
```
##### replace-with
```
(replace-with structure matcher replacement)
"Recursively replaces any elements in structure that are matched by matcher with
replacement. Replacement may reference bindings provided by calls to `as` in matcher."
```
##### update-with
```
(update-with structure matcher f)
"Returns structure, updated by applying f to any elements that are matched by matcher.
f may reference bindings provided by calls to `as` in matcher."
```

## License

Copyright © 2018 Zachary Kuepfer

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
