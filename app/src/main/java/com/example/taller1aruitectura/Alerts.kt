package com.example.taller1aruitectura

import android.content.Context
import android.view.View
import com.google.android.material.snackbar.Snackbar

class Alerts(private val context: Context) {

    fun indefiniteSnackbar(parentView: View, message: String){
        val snackbar = Snackbar.make(parentView, message, Snackbar.LENGTH_INDEFINITE)
        snackbar.setAction(R.string.cerrar){snackbar.dismiss()}
        snackbar.show()
    }
}