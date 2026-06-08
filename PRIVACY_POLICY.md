# Politique de Confidentialité — Application "Vide-Poche"

*Dernière mise à jour : 8 juin 2026*

La présente politique de confidentialité a pour but de vous informer sur la manière dont l'application mobile **Vide-Poche** (ci-après « l'Application »), identifiée sous le package `com.aistudio.videpoche.rvkmzp`, traite vos données personnelles.

Nous attachons une importance primordiale au respect de votre vie privée. C'est pourquoi l'Application a été conçue selon un paradigme **offline-first et local par défaut**.

---

## 1. Responsable du traitement
L'Application est un outil de gestion personnelle local. Aucun serveur externe n’est utilisé pour stocker vos données. Toutes les opérations de traitement de l'information s’effectuent directement sur votre appareil Android.

---

## 2. Collecte et traitement des données
L'Application ne collecte, ne transmet, ne vend ni ne partage **aucune donnée personnelle, directe ou indirecte**, à des tiers ou à des serveurs tiers.

### a. Stockage Local des Données (Room SQLite)
* **Quoi :** Les titres des tickets de caisse, les dates d'achat, les durées de garantie, les codes-barres reconstitués, les catégories, ainsi que les notes personnelles que vous saisissez.
* **Où :** Ces informations sont enregistrées localement au sein d'une base de données sécurisée sécurisée (SQLite / Room) stockée uniquement dans l'espace de stockage privé de l'Application sur votre smartphone.
* **Accès :** Aucune entité tierce n'y a accès.

### b. Photos et Documents (Stockage Interne)
* **Quoi :** Les clichés de vos tickets de caisse pris via l'appareil photo de l’Application.
* **Où :** Ces images sont copiées et enregistrées de manière sécurisée dans le stockage interne privé de l'Application (`context.filesDir`).
* **Durée de conservation :** Ces fichiers sont stockés localement et sont définitivement supprimés physique de votre appareil dès lors que vous supprimez le ticket correspondant au sein de l’Application ou que vous désinstallez l’Application.

### c. Reconnaissance Optique de Caractères (OCR) locale
* **Quoi :** L'analyse automatique du texte présent sur vos tickets de caisse (magasin, montant, date, code-barres).
* **Comment :** Ce traitement utilise le SDK Google ML Kit (Text Recognition) de manière **100 % locale et hors ligne** sur votre téléphone. Aucune image ni aucun texte extrait n'est envoyé sur internet ou à Google pour ce traitement.

---

## 3. Autorisations requises (Permissions Android)
Pour fonctionner de manière optimale, l'Application demande l'accès à certaines fonctionnalités de votre appareil :

* **Appareil photo (Camera) :** Indispensable pour capturer les photos de vos tickets de caisse directement dans l'Application.
* **Notification (Notifications Push) :** Utilisé pour configurer des alertes locales afin de vous prévenir de l'expiration imminente de vos garanties d'achats. Ces notifications sont générées localement par le système Android, sans passer par un serveur de push externe.

---

## 4. Partage et transfert des données
Aucune donnée collectée par l'Application n'est transmise d'aucune façon. Vos données restent sur votre appareil.

---

## 5. Sécurité
Vos données sont protégées par les mécanismes de cloisonnement natifs du système d'exploitation Android (bac à sable). Nous vous recommandons de sécuriser l'accès à votre appareil (verrouillage par schéma, code ou empreinte) pour préserver la confidentialité physique de vos tickets.

---

## 6. Vos Droits (RGPD)
Puisque toutes vos données sont stockées localement sur votre appareil mobile :
* Vous disposez d'un droit d'accès, de rectification et de limitation absolu en modifiant directement vos tickets dans l'Application.
* Vous pouvez exercer votre **droit à l’effacement (droit à l'oubli)** à tout moment en supprimant manuellement vos tickets au sein de l’Application, ou simplement en supprimant / désinstallant l'Application de votre téléphone. Cela détruira instantanément et définitivement l’intégralité de la base de données SQLite locale et toutes les photos rattachées.

---

## 7. Contact
Pour toute question relative à cette politique de confidentialité ou au fonctionnement de l'appareil, vous pouvez contacter l'éditeur à l'adresse e-mail suivante :
* **E-mail :** lorahalima9@gmail.com
