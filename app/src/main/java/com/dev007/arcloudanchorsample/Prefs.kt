package com.dev007.arcloudanchorsample;

import android.content.Context
import android.content.SharedPreferences
import androidx.collection.arraySetOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import java.util.ArrayList

class Prefs(context: Context) {
    private val APP_PREF_INT_EXAMPLE = "MyLocalArNodesList"

    private val prefs: SharedPreferences =
        context.getSharedPreferences("MySharedPref", Context.MODE_PRIVATE)

    private var nodeList = arraySetOf<String>()

    fun addNode(nodeId: String) {
        nodeList.add(nodeId)
        saveArrayList()
    }

    fun getList(): ArrayList<String> {
        return getArrayList() ?: arrayListOf<String>()
    }

    private fun saveArrayList() {
        val editor: SharedPreferences.Editor = prefs.edit()
        val gson = Gson()
        val json: String = gson.toJson(nodeList)
        editor.putString(APP_PREF_INT_EXAMPLE, json)
        editor.apply()
    }

    private fun getArrayList(): ArrayList<String>? {
        val gson = Gson()
        val json: String = prefs.getString(APP_PREF_INT_EXAMPLE, null) ?: ""
        val type: Type = object : TypeToken<ArrayList<String>?>() {}.type
        return gson.fromJson(json, type)
    }
}