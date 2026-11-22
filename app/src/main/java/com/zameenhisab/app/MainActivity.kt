package com.zameenhisab.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
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
