@file:Suppress("UNUSED_PARAMETER")
package com.tencent.mm.plugin.luckymoney.model

import org.json.JSONObject

// Synthetic shapes only: no WeChat implementation or user data.
class MatchingPaymentModel(a: Int, b: Int, c: String, d: String, e: String, f: String,
    g: String, h: String, i: String, j: String) {
    fun onGYNetEnd(code: Int, message: String, data: JSONObject) {}
}

class CallbackOnlyModel {
    fun onGYNetEnd(code: Int, message: String, data: JSONObject) {}
}

class WrongReturnModel(a: Int, b: Int, c: String, d: String, e: String, f: String,
    g: String, h: String, i: String, j: String) {
    fun onGYNetEnd(code: Int, message: String, data: JSONObject): String = ""
}
