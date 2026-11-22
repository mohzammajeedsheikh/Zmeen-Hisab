package com.zameenhisab.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.database.FirebaseDatabase
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {

    private lateinit var web: WebView
    private var printView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // keep a local copy of cloud data so it also works offline; must run before any other DB call
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (e: Exception) {
        }
        web = WebView(this)
        setContentView(web)
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.builtInZoomControls = false
        s.setSupportZoom(false)
        web.addJavascriptInterface(Bridge(), "Android")
        web.webViewClient = WebViewClient()
        web.loadUrl("file:///android_asset/index.html")
    }


    /* ---------------- Firebase: sign-in + cloud data ----------------
     * Only the keys in `synced` are ever sent to Firebase, and only while a user is signed in.
     * Guest mode = no signed-in user = nothing is written to Firebase. */
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseDatabase by lazy { FirebaseDatabase.getInstance() }
    private val synced = listOf("khata", "hist", "s")
    private var verId: String? = null
    private var resendTok: PhoneAuthProvider.ForceResendingToken? = null
    private var lastPhone: String? = null

    private fun js(code: String) {
        web.evaluateJavascript(code, null)
    }

    private fun authOk(synced: Boolean) = js("window.onAuthOk&&window.onAuthOk($synced)")

    private fun authErr(code: String) = js("window.onAuthErr&&window.onAuthErr(${JSONObject.quote(code)})")

    private fun errCode(e: Exception?, ctx: String): String = when {
        e is FirebaseNetworkException -> "network"
        e is FirebaseTooManyRequestsException -> "many"
        e is FirebaseAuthInvalidUserException -> "nouser"
        e is FirebaseAuthInvalidCredentialsException -> when (ctx) {
            "phone" -> "badphone"
            "code" -> "badcode"
            else -> "badcred"
        }
        e is FirebaseAuthException && e.errorCode == "ERROR_SESSION_EXPIRED" -> "expired"
        else -> "fail"
    }

    private fun userRef(uid: String) = db.getReference("users").child(uid)

    /** After any successful sign-in: cloud data wins for keys that exist in the cloud;
     *  keys missing in the cloud are filled from this device (e.g. work done in guest mode). */
    private fun afterSignIn() {
        val u = auth.currentUser
        if (u == null) {
            authErr("fail")
            return
        }
        val sp = getSharedPreferences("zh", Context.MODE_PRIVATE)
        userRef(u.uid).get().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val snap = task.result
                val e = sp.edit()
                for (k in synced) {
                    val remote = snap.child(k).getValue(String::class.java)
                    if (remote != null) {
                        e.putString(k, remote)
                    } else {
                        val local = sp.getString(k, "")
                        if (!local.isNullOrEmpty()) userRef(u.uid).child(k).setValue(local)
                    }
                }
                e.apply()
                authOk(true)
            } else {
                authOk(false)
            }
        }
    }

    private fun signInWith(cred: AuthCredential, ctx: String) {
        auth.signInWithCredential(cred).addOnCompleteListener { t ->
            if (t.isSuccessful) afterSignIn() else authErr(errCode(t.exception, ctx))
        }
    }

    private fun googleLogin() {
        val opt = GetSignInWithGoogleOption.Builder(getString(R.string.default_web_client_id)).build()
        val req = GetCredentialRequest.Builder().addCredentialOption(opt).build()
        CredentialManager.create(this).getCredentialAsync(
            this, req, null, ContextCompat.getMainExecutor(this),
            object : CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
                override fun onResult(result: GetCredentialResponse) {
                    val c = result.credential
                    if (c is CustomCredential && c.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        try {
                            val g = GoogleIdTokenCredential.createFrom(c.data)
                            signInWith(GoogleAuthProvider.getCredential(g.idToken, null), "google")
                        } catch (e: Exception) {
                            authErr("google")
                        }
                    } else {
                        authErr("google")
                    }
                }

                override fun onError(e: GetCredentialException) {
                    authErr(if (e is GetCredentialCancellationException) "cancel" else "google")
                }
            })
    }

    private fun phoneSend(num: String) {
        if (num != lastPhone) resendTok = null
        lastPhone = num
        val b = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(num)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(c: PhoneAuthCredential) {
                    signInWith(c, "code")
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    authErr(errCode(e, "phone"))
                }

                override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                    verId = id
                    resendTok = token
                    js("window.onCodeSent&&window.onCodeSent()")
                }
            })
        resendTok?.let { b.setForceResendingToken(it) }
        PhoneAuthProvider.verifyPhoneNumber(b.build())
    }

    private fun phoneVerify(code: String) {
        val id = verId
        if (id == null) {
            authErr("expired")
            return
        }
        signInWith(PhoneAuthProvider.getCredential(id, code), "code")
    }

    private fun emailLogin(email: String, pw: String) {
        auth.signInWithEmailAndPassword(email, pw).addOnCompleteListener { t ->
            if (t.isSuccessful) afterSignIn() else authErr(errCode(t.exception, "email"))
        }
    }

    private fun resetPw(email: String) {
        auth.sendPasswordResetEmail(email).addOnCompleteListener { t ->
            if (t.isSuccessful) js("window.onResetSent&&window.onResetSent()") else authErr(errCode(t.exception, "email"))
        }
    }

    private fun logout() {
        auth.signOut()
        verId = null
        resendTok = null
        lastPhone = null
        // keep the next person on this phone from seeing this account's data
        getSharedPreferences("zh", Context.MODE_PRIVATE).edit().remove("khata").remove("hist").apply()
        try {
            CredentialManager.create(this).clearCredentialStateAsync(
                ClearCredentialStateRequest(), null, ContextCompat.getMainExecutor(this),
                object : CredentialManagerCallback<Void?, ClearCredentialException> {
                    override fun onResult(result: Void?) {}
                    override fun onError(e: ClearCredentialException) {}
                })
        } catch (e: Exception) {
        }
    }

    private var lm: LocationManager? = null

    private val ll = object : LocationListener {
        override fun onLocationChanged(l: Location) {
            web.evaluateJavascript("window.onGps&&window.onGps(${l.latitude},${l.longitude},${l.accuracy})", null)
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private fun gpsError(code: String) {
        web.evaluateJavascript("window.onGpsErr&&window.onGpsErr('$code')", null)
    }

    private fun startLoc() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 7)
            return
        }
        val m = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        lm = m
        var any = false
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                if (m.isProviderEnabled(p)) {
                    m.requestLocationUpdates(p, 1000L, 0f, ll)
                    any = true
                }
            } catch (e: SecurityException) {
            }
        }
        if (!any) gpsError("off")
    }

    private fun stopLoc() {
        try {
            lm?.removeUpdates(ll)
        } catch (e: SecurityException) {
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 7) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) startLoc() else gpsError("perm")
        }
    }

    override fun onDestroy() {
        stopLoc()
        super.onDestroy()
    }

    override fun onBackPressed() {
        web.evaluateJavascript("window.goBack ? window.goBack() : false") { r ->
            if (r != "true") finish()
        }
    }

    inner class Bridge {
        private val sp = getSharedPreferences("zh", Context.MODE_PRIVATE)

        @JavascriptInterface
        fun save(k: String, v: String) {
            sp.edit().putString(k, v).apply()
            // cloud copy only for signed-in users; guests never touch Firebase
            val u = auth.currentUser
            if (u != null && k in synced) userRef(u.uid).child(k).setValue(v)
        }

        @JavascriptInterface
        fun load(k: String): String = sp.getString(k, "") ?: ""

        @JavascriptInterface
        fun authInfo(): String {
            val u = auth.currentUser ?: return ""
            val o = JSONObject()
            o.put("uid", u.uid)
            o.put("name", u.displayName ?: "")
            o.put("email", u.email ?: "")
            o.put("phone", u.phoneNumber ?: "")
            return o.toString()
        }

        @JavascriptInterface
        fun googleLogin() {
            runOnUiThread { this@MainActivity.googleLogin() }
        }

        @JavascriptInterface
        fun phoneSend(num: String) {
            runOnUiThread { this@MainActivity.phoneSend(num) }
        }

        @JavascriptInterface
        fun phoneVerify(code: String) {
            runOnUiThread { this@MainActivity.phoneVerify(code) }
        }

        @JavascriptInterface
        fun emailLogin(email: String, pw: String) {
            runOnUiThread { this@MainActivity.emailLogin(email, pw) }
        }

        @JavascriptInterface
        fun resetPw(email: String) {
            runOnUiThread { this@MainActivity.resetPw(email) }
        }

        @JavascriptInterface
        fun logout() {
            runOnUiThread { this@MainActivity.logout() }
        }

        @JavascriptInterface
        fun mail(subject: String) {
            runOnUiThread {
                try {
                    val i = Intent(Intent.ACTION_SENDTO)
                    i.data = Uri.parse("mailto:")
                    i.putExtra(Intent.EXTRA_EMAIL, arrayOf("creatormms720@gmail.com"))
                    i.putExtra(Intent.EXTRA_SUBJECT, subject)
                    startActivity(i)
                } catch (e: ActivityNotFoundException) {
                }
            }
        }

        @JavascriptInterface
        fun openUrl(u: String) {
            if (!(u.startsWith("https://") || u.startsWith("http://"))) return
            runOnUiThread {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
                } catch (e: ActivityNotFoundException) {
                }
            }
        }

        @JavascriptInterface
        fun startGps() {
            runOnUiThread { startLoc() }
        }

        @JavascriptInterface
        fun stopGps() {
            runOnUiThread { stopLoc() }
        }

        @JavascriptInterface
        fun share(text: String) {
            runOnUiThread {
                val i = Intent(Intent.ACTION_SEND)
                i.type = "text/plain"
                i.putExtra(Intent.EXTRA_TEXT, text)
                try {
                    val w = Intent(i)
                    w.setPackage("com.whatsapp")
                    startActivity(w)
                } catch (e: ActivityNotFoundException) {
                    startActivity(Intent.createChooser(i, "Share"))
                }
            }
        }

        @JavascriptInterface
        fun printHtml(title: String, html: String) {
            runOnUiThread {
                val w = WebView(this@MainActivity)
                printView = w
                var done = false
                w.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        if (done) return
                        done = true
                        val pm = getSystemService(Context.PRINT_SERVICE) as PrintManager
                        val attrs = PrintAttributes.Builder()
                            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                            .build()
                        pm.print(title, view.createPrintDocumentAdapter(title), attrs)
                    }
                }
                w.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        }
    }
}
