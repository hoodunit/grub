(ns grub.test.unit.diff
  (:require [grub.diff :as diff]
            [clojure.test :refer :all]))


(def empty-diff {:grubs {:deleted #{} :updated nil}
                 :recipes {:deleted #{} :updated nil}})

(deftest diff-empty-states
  (let [empty-state {:grubs {} :recipes {}}]
    (is (= empty-diff 
           (diff/diff-states empty-state empty-state)))))

(deftest diff-equal-states
  (is (= empty-diff 
         (diff/diff-states {:grubs {"id" {:text "asdf" :completed false}} :recipes {}}
                           {:grubs {"id" {:text "asdf" :completed false}} :recipes {}}))))

(deftest diff-added-grub
  (is (= {:grubs {:deleted #{} 
                  :updated {"id" {:completed false, :text "asdf"}}}
          :recipes {:deleted #{} :updated nil}}
         (diff/diff-states {:grubs {} :recipes {}}
                           {:grubs {"id" {:text "asdf" :completed false}} :recipes {}}))))

(deftest diff-deleted-grub
  (is (= {:grubs {:deleted #{"id"} 
                  :updated nil}
          :recipes {:deleted #{} :updated nil}}
         (diff/diff-states {:grubs {"id" {:text "asdf" :completed false}} :recipes {}}
                           {:grubs {} :recipes {}}))))

(deftest diff-edited-grub
  (is (= {:grubs {:deleted #{} 
                  :updated {"id" {:text "asdf2"}}}
          :recipes {:deleted #{} :updated nil}}
         (diff/diff-states {:grubs {"id" {:text "asdf" :completed false}} :recipes {}}
                           {:grubs {"id" {:text "asdf2" :completed false}} :recipes {}}))))

(deftest diff-completed-grub
  (is (= {:grubs {:deleted #{} 
                  :updated {"id" {:completed true}}}
          :recipes {:deleted #{} :updated nil}}
         (diff/diff-states {:grubs {"id" {:text "asdf" :completed false}} :recipes {}}
                           {:grubs {"id" {:text "asdf" :completed true}} :recipes {}}))))

(deftest diff-added-recipe
  (is (= {:grubs {:deleted #{} 
                  :updated nil}
          :recipes {:deleted #{} :updated {"id" {:name "Blue Cheese Soup"
                                                 :grubs "Some grubs"}}}}
         (diff/diff-states {:grubs {} :recipes {}}
                           {:grubs {} :recipes {"id" {:name "Blue Cheese Soup"
                                                 :grubs "Some grubs"}}}))))

(deftest diff-edited-recipe
  (is (= {:grubs {:deleted #{} 
                  :updated nil}
          :recipes {:deleted #{} :updated {"id" {:name "Bleu Cheese Soup" }}}}
         (diff/diff-states {:grubs {} :recipes {"id" {:name "Blue Cheese Soup"
                                                 :grubs "Some grubs"}}}
                           {:grubs {} :recipes {"id" {:name "Bleu Cheese Soup"
                                                 :grubs "Some grubs"}}}))))

(deftest diff-deleted-recipe
  (is (= {:grubs {:deleted #{} :updated nil}
          :recipes {:deleted #{"id"} :updated nil}}
         (diff/diff-states {:grubs {} :recipes {"id" {:name "Blue Cheese Soup"
                                                      :grubs "Some grubs"}}}
                           {:grubs {} :recipes {}}))))

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

(deftest diff-many-changes
  (is (= expected-diff (diff/diff-states before-state after-state))))

(deftest patch-returns-original-state
  (is 
   (let [diff (diff/diff-states before-state after-state)]
     (= after-state (diff/patch-state before-state diff)))))
