package com.example.canchasdeportivas

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.example.canchasdeportivas.data.LocalCache
import com.example.canchasdeportivas.data.FirebaseRepository

class CanchasApp : Application() {
    lateinit var repository: FirebaseRepository
        private set
    override fun onCreate() {
        super.onCreate()
        val app = FirebaseApp.initializeApp(this, FirebaseOptions.Builder()
            .setApplicationId(BuildConfig.FIREBASE_APP_ID).setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setProjectId(BuildConfig.FIREBASE_PROJECT).build())
        val auth = FirebaseAuth.getInstance(app)
        val db = FirebaseFirestore.getInstance(app)
        // Evita conservar en disco información de otra cuenta al compartir el teléfono.
        db.firestoreSettings = FirebaseFirestoreSettings.Builder().setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
        if (BuildConfig.FIREBASE_EMULATORS) {
            check(BuildConfig.DEBUG) { "Los emuladores están permitidos únicamente en debug" }
            auth.useEmulator(BuildConfig.EMULATOR_HOST, 9099)
            db.useEmulator(BuildConfig.EMULATOR_HOST, 8080)
        }
        repository = FirebaseRepository(auth, db, LocalCache(this))
    }
}
