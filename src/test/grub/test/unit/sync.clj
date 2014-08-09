(ns grub.test.unit.sync
  (:require [grub.sync :as sync]
            [clojure.test :refer :all]))

(def server-state
  {:grubs 
   {"grub-same" {:id "grub-same", 
                 :completed false, 
                 :grub "3 garlic cloves"}
    "grub-completed" {:id "grub-completed", 
                      :completed false, 
                      :grub "2 tomatoes"}
    "grub-updated" {:id "grub-updated", 
                    :completed false, 
                    :grub "BBQ sauce"}
    "grub-deleted" {:id "grub-deleted"
                    :completed true
                    :grub "diapers"}}
   :recipes 
   {"recipe-same" {:id "recipe-same"
                   :grubs "3 T. butter\n1 yellow onion\n1 1/2 dl red pepper\n1 dl apple\n3 garlic cloves\n1 t. curry\n3 dl water\n2-2 1/2 T. wheat flour\n1 kasvisliemikuutio\n200 g blue cheese\n2 dl apple juice\n2 dl milk\n1 t. basil\n1 package take-and-bake french bread"
                   :name "Blue Cheese Soup"}
    "recipe-updated" {:id "recipe-updated"
                      :grubs "450 g lean stew beef (lapa/naudan etuselkä), cut into 1-inch cubes\n2 T. vegetable oil\n5 dl water\n2 lihaliemikuutios\n350 ml burgundy (or another red wine)\n1 garlic clove\n1 bay leaf (laakerinlehti)\n1/2 t. basil\n3 carrots\n1 yellow onion\n4 potatoes\n1 cup celery\n2 tablespoons of cornstarch (maissijauho/maizena)"
                      :name "Beef Stew"}
    "recipe-deleted" {:id "recipe-deleted"
                      :grubs "8 slices rye bread\n400 g chicken breast\nBBQ sauce\nketchup\nmustard\nbutter\n1 package rocket\n4 tomatoes\n2 red onions\n1 bottle Coca Cola"
                      :name "Chickenburgers"}}})

(def client-state
  {:grubs 
   {"grub-same" {:id "grub-same", 
                 :completed false, 
                 :grub "3 garlic cloves"}
    "grub-completed" {:id "grub-completed", 
                      :completed true, 
                      :grub "2 tomatoes"}
    "grub-updated" {:id "grub-updated", 
                    :completed false, 
                    :grub "Ketchup"}
    "grub-added" {:id "grub-added"
                  :completed false
                  :grub "Toothpaste"}}
   :recipes 
   {"recipe-same" {:id "recipe-same"
                   :grubs "3 T. butter\n1 yellow onion\n1 1/2 dl red pepper\n1 dl apple\n3 garlic cloves\n1 t. curry\n3 dl water\n2-2 1/2 T. wheat flour\n1 kasvisliemikuutio\n200 g blue cheese\n2 dl apple juice\n2 dl milk\n1 t. basil\n1 package take-and-bake french bread"
                   :name "Blue Cheese Soup"}
    "recipe-updated" {:id "recipe-updated"
                      :grubs "300 g lean stew beef (lapa/naudan etuselkä), cut into 1-inch cubes\n2 T. vegetable oil\n5 dl water\n2 lihaliemikuutios\n400 ml burgundy (or another red wine)\n1 garlic clove\n1 bay leaf (laakerinlehti)\n1/2 t. basil\n2 carrots\n1 yellow onion\n4 potatoes\n1 cup celery\n2 tablespoons of cornstarch (maissijauho/maizena)"
                      :name "Beef Stew"}
    "recipe-added" {:id "recipe-added"
                    :grubs "400 g ground beef\nhamburger buns\n2 red onions\n4 tomatoes\ncheddar cheese\nketchup\nmustard\npickles\nfresh basil\n1 bottle Coca Cola"
                    :name "Burgers"}}})

(def expected-diff
  {:recipes
   {:deleted #{"recipe-deleted"},
    :updated
    {"recipe-added"
     {:name "Burgers",
      :id "recipe-added",
      :grubs
      "400 g ground beef\nhamburger buns\n2 red onions\n4 tomatoes\ncheddar cheese\nketchup\nmustard\npickles\nfresh basil\n1 bottle Coca Cola"},
     "recipe-updated"
     {:grubs
      "300 g lean stew beef (lapa/naudan etuselkä), cut into 1-inch cubes\n2 T. vegetable oil\n5 dl water\n2 lihaliemikuutios\n400 ml burgundy (or another red wine)\n1 garlic clove\n1 bay leaf (laakerinlehti)\n1/2 t. basil\n2 carrots\n1 yellow onion\n4 potatoes\n1 cup celery\n2 tablespoons of cornstarch (maissijauho/maizena)"}}},
   :grubs
   {:deleted #{"grub-deleted"},
    :updated
    {"grub-completed" {:completed true},
     "grub-updated" {:grub "Ketchup"},
     "grub-added"
     {:completed false, :grub "Toothpaste", :id "grub-added"}}}})

(deftest diffing
  (is (= expected-diff (sync/diff-states server-state client-state))))

(deftest patching
  (is 
   (let [diff (sync/diff-states server-state client-state)]
     (= client-state (sync/patch-state server-state diff)))))
