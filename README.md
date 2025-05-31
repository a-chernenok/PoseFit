# PoseFit - Android Application
CHERNENOK OLEKSANDR

Το PoseFit είναι μια καινοτόμος εφαρμογή Android που χρησιμοποιεί τεχνολογία Pose Estimation (TensorFlow Lite) για την παρακολούθηση και αξιολόγηση της ορθότητας των ασκήσεων φυσικής αγωγής σε πραγματικό χρόνο, παρέχοντας άμεση ανατροφοδότηση στους χρήστες.

---

### **2. Τεχνολογίες Χρησιμοποιούμενες**

* **Kotlin**
* **Android Studio**
* **Jetpack Compose** (για το UI)
* **TensorFlow Lite** (για Pose Estimation)

---

### **3. Προαπαιτούμενα (Prerequisites)**

Για να εκτελέσετε την εφαρμογή 'PoseFit' τοπικά, βεβαιωθείτε ότι έχετε εγκατεστημένα τα ακόλουθα:

* **Android Studio:** Meerkat | 2024.1.1 Patch 1 ή νεότερη.
    * Android SDK Platform: API Level 34 (Android 14.0 - U).
    * Java Development Kit (JDK): JetBrains Runtime 21.0.6.
    * Gradle: Έκδοση 8.11.1.
    * **Επιτάχυνση Υλικού:** Ενεργοποιημένη εικονικοποίηση (Intel HAXM ή Windows Hypervisor Platform) στο BIOS/UEFI και στα Windows Features.

---

### **4. Οδηγίες Εγκατάστασης (Installation)**

Ακολουθήστε τα παρακάτω βήματα για να εγκαταστήσετε και να ρυθμίσετε την Android εφαρμογή:

1.  **Λήψη Κώδικα:**
    Ανοίξτε ένα terminal και εκτελέστε:
    ```bash
    git clone [https://github.com/a-chernenok/PoseFit.git](https://github.com/a-chernenok/PoseFit.git)
    ```
2.  **Άνοιγμα Project στο Android Studio:**
    * Ανοίξτε το Android Studio.
    * Επιλέξτε `File > Open`, πλοηγηθείτε στον φάκελο `PoseFit` (που κατεβάσατε στο βήμα 4.1) και επιλέξτε τον. Αφήστε το Android Studio να ολοκληρώσει τον συγχρονισμό του Gradle project.
3.  **Ρυθμίσεις Backend API URL:**
    * Η διεύθυνση του backend API ορίζεται στο `object Config` μέσα στο αρχείο `MainActivity.kt` το BASE_URL, το οποίο θα το αλλάξερε και θα βάλετε την τοπική σας διεύθυνση.
    * **Διαδρομή Αρχείου:** `app/src/main/java/com.your_package_name/MainActivity.kt` (Αντικαταστήστε το `com.your_package_name` με το πραγματικό package name του project σας).
    * Βεβαιωθείτε ότι το `BASE_URL` έχει οριστεί ως `http://*Η IP*/Posefit_backend/` (πχ. `http://192.168.1.204/posefit_backend/"`).

---

### **5. Οδηγίες Εκτέλεσης (How to Run)**

1.  **Εκκινήστε το Backend:** Βεβαιωθείτε ότι το XAMPP backend (Apache και MySQL) είναι σε λειτουργία, όπως περιγράφεται στο `README` του `Posefit_backend`.
2.  **Εκτέλεση της Εφαρμογής στο Android Studio:**
    * Στο Android Studio, επιλέξτε την επιθυμητή συσκευή (Android Emulator ή Φυσική Συσκευή) από το drop-down μενού στη γραμμή εργαλείων.
    * Πατήστε το πράσινο κουμπί `Run` για να ξεκινήσετε και να εγκαταστήσετε την εφαρμογή.

3.  **Λειτουργία της Εφαρμογής:**
    * Η εφαρμογή είναι άμεσα λειτουργική χωρίς login/εγγραφή.
    * Επιλέξτε την άσκηση από το μενού της εφαρμογής.
    * **Παραχωρήστε άδεια πρόσβασης στην κάμερα** όταν ζητηθεί.
    * Εκτελέστε την άσκηση μπροστά στην κάμερα για να λάβετε ανατροφοδότηση σε πραγματικό χρόνο από τη λειτουργία Pose Estimation.

---