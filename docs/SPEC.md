Peux-tu coder une application native android qui visualise les données du logiciel tellico
avec comme fonctions:
- Import de fichiers .tc (XML compressé par ZIP)
- Lire les metadonnées du fichier pour en déduire le schéma des objets gérés avec leurs attributs (application générique sur des objets de collection qui peuvent être des CDs, DVDs, BDs, Livres, Instruments de musique, Bouteille de vin, ...)
- Présentation sous forme de liste d'objets avec affichage d'une fiche de détail de l'objet en cliquant dessus.
- Possibilité de geler une colonne dans l'affichage en faisant défiler les autres, de la retailler. Idem pour une ligne.
- Gestion du paging ou scrolling pour parcourir les grands fichiers de données
- Recherche dans les collections avec filtre par champ  et fuzzy searching
- Support des images (Tellico peut en contenir)
- Une version optimisée (MVVM + repository + persistence locale (SQLite et Room)) qui supporte des fichiers tellico allant jusqu'à 10000 articles sans problème et une UI type airtable
- Pipeline type linux pour Android: XML+ZIP Tellico → Objet → Room DB → Room DB → PagingSource → ViewModel cache → Search Engine (ranking) → Filtered dataset → Compose Lazy Grid UI
- Synchronisation avec PC des fichiers tellico .tc
- l'interface doit comporter un panneau de configuration par fichier .tc où l'on puisse choisir les champs à afficher et ceux à cacher.
- Projet mature, complet, fonctionnel et avec les meilleures pratiques de codage propre, maintenable par un humain, et avec UX soignée.
- Inclure la documentation technique (design, choix des composants et documenter le code à chaque niveau)
- L'interface doit être multilingue avec des fichiers po que l'on peut traduire par transiflex.
- Livré sous forme de fichier tar.gz avec tous les éléments de build inclus et aussi paquet f-Droid prêt à l'installation.

Contexte:
- Débutant en développement android et kotlin, peu de connaissances en MVC, PWA et developpement dit moderne.
- Background système et réseau en environnement Linux, et développement en C/X11/Perl/shell/bases de python, notion d'objet, depuis 1993
