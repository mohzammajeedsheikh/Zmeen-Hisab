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

class MainActivity : Activity() {

    private lateinit var web: WebView
    private var printView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        }

        @JavascriptInterface
        fun load(k: String): String = sp.getString(k, "") ?: ""

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
