package com.antgskds.calendarassistant.ui.contract

data class DonateUiState(val hapticEnabled: Boolean)

enum class DonateQrCode { ALIPAY, WECHAT_PAY }

sealed interface DonateUiAction {
    data object MarkDonated : DonateUiAction
    data class SaveQrCode(val code: DonateQrCode) : DonateUiAction
}
