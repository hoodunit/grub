(ns grub.test.unit.diff
  (:require [grub.diff :as diff]
            [midje.sweet :refer :all]))


(def empty-diff {:grubs {:- #{} :+ nil}
                 :recipes {:- #{} :+ nil}})

(fact "Diff of empty states is empty diff"
  (let [empty-state {:grubs {} :recipes {}}]
    (diff/diff-states empty-state empty-state) => empty-diff))

(fact "Diff of equal states is empty diff"
  (diff/diff-states {:grubs {"id" {:text "asdf" :completed false}} :recipes {}}
                    {:grubs {"id" {:text "asdf" :completed false}} :recipes {}})
  => empty-diff)

(fact "Diff of one added grub has one updated grub"
  (diff/diff-states {:grubs {} :recipes {}}
                    {:grubs {"id" {:text "asdf" :completed false}} :recipes {}})
  => {:grubs {:- #{} 
              :+ {"id" {:completed false, :text "asdf"}}}
      :recipes {:- #{} :+ nil}})

(fact "Diff of one removed grub has one deleted grub"
  (diff/diff-states {:grubs {"id" {:text "asdf" :completed false}} :recipes {}}
                    {:grubs {} :recipes {}})
  =>
  {:grubs {:- #{"id"} 
           :+ nil}
   :recipes {:- #{} :+ nil}})

(fact "Diff of one changed grub has updated grub"
  (diff/diff-states {:grubs {"id" {:text "asdf" :completed false}} :recipes {}}
                    {:grubs {"id" {:text "asdf2" :completed false}} :recipes {}})
  =>
  {:grubs {:- #{} 
           :+ {"id" {:text "asdf2"}}}
   :recipes {:- #{} :+ nil}})

(fact "Diff of one completed grub has updated grub"
  (diff/diff-states {:grubs {"id" {:text "asdf" :completed false}} :recipes {}}
                    {:grubs {"id" {:text "asdf" :completed true}} :recipes {}})
  => {:grubs {:- #{} 
              :+ {"id" {:completed true}}}
      :recipes {:- #{} :+ nil}})

(fact "Diff of one added recipe has updated recipe"
  (diff/diff-states {:grubs {} :recipes {}}
                    {:grubs {} :recipes {"id" {:name "Blue Cheese Soup"
                                               :grubs "Some grubs"}}})
  =>
  {:grubs {:- #{} 
           :+ nil}
   :recipes {:- #{} :+ {"id" {:name "Blue Cheese Soup"
                              :grubs "Some grubs"}}}})

(fact "Diff of one changed recipe has one updated recipe"
  (diff/diff-states {:grubs {} :recipes {"id" {:name "Blue Cheese Soup"
                                               :grubs "Some grubs"}}}
                    {:grubs {} :recipes {"id" {:name "Bleu Cheese Soup"
                                               :grubs "Some grubs"}}})
  => {:grubs {:- #{} 
              :+ nil}
      :recipes {:- #{} :+ {"id" {:name "Bleu Cheese Soup" }}}})

(fact "Diff of one removed recipe has one deleted recipe"
  (diff/diff-states {:grubs {} :recipes {"id" {:name "Blue Cheese Soup"
                                               :grubs "Some grubs"}}}
                    {:grubs {} :recipes {}})
  =>
  {:grubs {:- #{} :+ nil}
   :recipes {:- #{"id"} :+ nil}})

(def before-state
  {:grubs 
   {"grub-same" {:completed false
                 :text "3 garlic cloves"}
    "grub-completed" {:completed false
                      :text "2 tomatoes"}
    "grub-updated" {:completed false
                    :text "BBQ sauce"}
    "grub-deleted" {:completed true
                    :text "diapers"}}
   :recipes 
   {"recipe-same" {:grubs "3 T. butter\n1 yellow onion\n1 1/2 dl red pepper\n1 dl apple\n3 garlic cloves\n1 t. curry\n3 dl water\n2-2 1/2 T. wheat flour\n1 kasvisliemikuutio\n200 g blue cheese\n2 dl apple juice\n2 dl milk\n1 t. basil\n1 package take-and-bake french bread"
                   :name "Blue Cheese Soup"}
    "recipe-updated" {:grubs "450 g lean stew beef (lapa/naudan etuselkä), cut into 1-inch cubes\n2 T. vegetable oil\n5 dl water\n2 lihaliemikuutios\n350 ml burgundy (or another red wine)\n1 garlic clove\n1 bay leaf (laakerinlehti)\n1/2 t. basil\n3 carrots\n1 yellow onion\n4 potatoes\n1 cup celery\n2 tablespoons of cornstarch (maissijauho/maizena)"
                      :name "Beef Stew"}
    "recipe-deleted" {:grubs "8 slices rye bread\n400 g chicken breast\nBBQ sauce\nketchup\nmustard\nbutter\n1 package rocket\n4 tomatoes\n2 red onions\n1 bottle Coca Cola"
                      :name "Chickenburgers"}}})

(def after-state
  {:grubs 
   {"grub-same" {:completed false, 
                 :text "3 garlic cloves"}
    "grub-completed" {:completed true, 
                      :text "2 tomatoes"}
    "grub-updated" {:completed false, 
                    :text "Ketchup"}
    "grub-added" {:completed false
                  :text "Toothpaste"}}
   :recipes 
   {"recipe-same" {:grubs "3 T. butter\n1 yellow onion\n1 1/2 dl red pepper\n1 dl apple\n3 garlic cloves\n1 t. curry\n3 dl water\n2-2 1/2 T. wheat flour\n1 kasvisliemikuutio\n200 g blue cheese\n2 dl apple juice\n2 dl milk\n1 t. basil\n1 package take-and-bake french bread"
                   :name "Blue Cheese Soup"}
    "recipe-updated" {:grubs "300 g lean stew beef (lapa/naudan etuselkä), cut into 1-inch cubes\n2 T. vegetable oil\n5 dl water\n2 lihaliemikuutios\n400 ml burgundy (or another red wine)\n1 garlic clove\n1 bay leaf (laakerinlehti)\n1/2 t. basil\n2 carrots\n1 yellow onion\n4 potatoes\n1 cup celery\n2 tablespoons of cornstarch (maissijauho/maizena)"
                      :name "Beef Stew"}
    "recipe-added" {:grubs "400 g ground beef\nhamburger buns\n2 red onions\n4 tomatoes\ncheddar cheese\nketchup\nmustard\npickles\nfresh basil\n1 bottle Coca Cola"
                    :name "Burgers"}}})

(def expected-diff
  {:recipes
   {:- #{"recipe-deleted"}
    :+
    {"recipe-added"
     {:name "Burgers"
      :grubs
      "400 g ground beef\nhamburger buns\n2 red onions\n4 tomatoes\ncheddar cheese\nketchup\nmustard\npickles\nfresh basil\n1 bottle Coca Cola"}
     "recipe-updated"
     {:grubs
      "300 g lean stew beef (lapa/naudan etuselkä), cut into 1-inch cubes\n2 T. vegetable oil\n5 dl water\n2 lihaliemikuutios\n400 ml burgundy (or another red wine)\n1 garlic clove\n1 bay leaf (laakerinlehti)\n1/2 t. basil\n2 carrots\n1 yellow onion\n4 potatoes\n1 cup celery\n2 tablespoons of cornstarch (maissijauho/maizena)"}}}
   :grubs
   {:- #{"grub-deleted"}
    :+
    {"grub-completed" {:completed true}
     "grub-updated" {:text "Ketchup"}
     "grub-added"
     {:completed false :text "Toothpaste"}}}})

(fact "Diff of many changes has all changes"
  (diff/diff-states before-state after-state) => expected-diff)

(fact "Diff and patch of many changes returns original state with new tag"
  (let [diff (diff/diff-states before-state after-state)
        result (diff/patch-state before-state diff)]
    (dissoc result :tag) => after-state
    (:tag result) => #(not (nil? %))))

(fact "Diff of states with tags includes tags in diff"
  (diff/diff-states {:tag "1"} {:tag "2"}) => {:tag "2" :shadow-tag "1"})

(fact "Patch of state creates new tag by default"
  (let [result (diff/patch-state {:grubs {:a {:b1 :b2}} :tag 0} {:grubs {:+ {:a {:b1 :b3}}} :- #{}})]
    result => (contains {:grubs {:a {:b1 :b3}}})
    (:tag result) => #(not (nil? %))
    (:tag result) => #(not= % 0)))

(fact "Patch of state sets new tag to patch tag if specified"
  (diff/patch-state {:grubs {:a {:b1 :b2}} :tag 0}
                    {:grubs {:+ {:a {:b1 :b3}}} :- #{} :tag 4}
                    true)
  =>
  {:grubs {:a {:b1 :b3}} :tag 4})

(fact "Empty patch of state sets new tag to patch tag if specified"
  (diff/patch-state {:grubs {:a {:b1 :b2}} :tag 0}
                    {:shadow-tag 0 :tag 4 :grubs {:+ nil :- #{}}}
                    true)
  =>
  {:grubs {:a {:b1 :b2}} :tag 4})

(fact "Patch of empty diff returns original state"
  (diff/patch-state {:grubs {:a {:b1 :b2}} :tag 0}
                    {:grubs {:+ nil :- #{}} :tag 4})
  =>
  {:grubs {:a {:b1 :b2}} :tag 0})
