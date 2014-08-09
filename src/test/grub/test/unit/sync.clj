(ns grub.test.unit.sync
  (:require [grub.sync :as sync]
            [clojure.test :refer :all]))

(def server-state
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

(def client-state
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
   {:deleted #{"recipe-deleted"}
    :updated
    {"recipe-added"
     {:name "Burgers"
      :grubs
      "400 g ground beef\nhamburger buns\n2 red onions\n4 tomatoes\ncheddar cheese\nketchup\nmustard\npickles\nfresh basil\n1 bottle Coca Cola"}
     "recipe-updated"
     {:grubs
      "300 g lean stew beef (lapa/naudan etuselkä), cut into 1-inch cubes\n2 T. vegetable oil\n5 dl water\n2 lihaliemikuutios\n400 ml burgundy (or another red wine)\n1 garlic clove\n1 bay leaf (laakerinlehti)\n1/2 t. basil\n2 carrots\n1 yellow onion\n4 potatoes\n1 cup celery\n2 tablespoons of cornstarch (maissijauho/maizena)"}}}
   :grubs
   {:deleted #{"grub-deleted"}
    :updated
    {"grub-completed" {:completed true}
     "grub-updated" {:text "Ketchup"}
     "grub-added"
     {:completed false :text "Toothpaste"}}}})

(deftest diffing
  (is (= expected-diff (sync/diff-states server-state client-state))))

(deftest patching
  (is 
   (let [diff (sync/diff-states server-state client-state)]
     (= client-state (sync/patch-state server-state diff)))))
