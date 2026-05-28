package com.antgskds.calendarassistant.core.instantcode

import java.util.regex.Pattern

/*
 * 部分取件码规则参考自 parcel 项目 (https://github.com/shareven/parcel)。
 * 原项目基于 MIT 许可证开源，版权所有 (c) 2025 shareven。
 */
object InstantCodePatterns {
    val smsCodePattern: Pattern = Pattern.compile(
        """(?i)(取件码为|提货号为|取货码为|提货码为|取件码（|提货号（|取货码（|提货码（|取件码『|提货号『|取货码『|提货码『|取件码【|提货号【|取货码【|提货码【|取件码\(|提货号\(|取货码\(|提货码\(|取件码\[|提货号\[|取货码\[|提货码\[|取件码|提货号|取货码|提货码|凭|快递|京东|天猫|中通|顺丰|韵达|德邦|菜鸟|拼多多|EMS|闪送|美团|饿了么|盒马|叮咚买菜|UU跑腿|签收码|签收编号|操作码|提货编码|收货编码|签收编码|取件編號|提貨號碼|運單碼|快遞碼|快件碼|包裹碼|貨品碼)\s*[A-Za-z0-9\s-]{2,}(?:[，,、][A-Za-z0-9\s-]{2,})*"""
    )

    val lockerPattern: Pattern = Pattern.compile(
        """(?i)([0-9]+)号(?:柜|快递柜|丰巢柜|蜂巢柜|熊猫柜|兔喜快递柜)"""
    )

    val addressPattern: Pattern = Pattern.compile(
        """(?i)(地址|收货地址|送货地址|位于|放至|已到达|到达|已到|送达|已放入|已存放至|已存放|放入)[\s\S]*?([\w\s-]+?(?:门牌|驿站|快递点|门面|柜|,|，|。|$))"""
    )

    val quickCodePattern: Pattern = Pattern.compile(
        """(?i)(请用|请凭|凭)\s*([A-Za-z0-9-]{3,12})"""
    )

    val strictPickupCodePattern: Pattern = Pattern.compile(
        """(?i)(取件码|提货号|取货码|提货码|签收码|签收编号|提货编码|收货编码|签收编码)\s*[:：]?\s*\*?([A-Za-z0-9-]{3,12})"""
    )

    val directCodePatterns: List<Pair<InstantCodeType, Pattern>> = listOf(
        InstantCodeType.PICKUP to Pattern.compile(
            """(?i)(取件码|取货码|提货码|提货号|取件编号|取件編號|签收码|签收编号|收货码|提货编码)\s*(?:为|是|:|：|=|#|＃)?\s*\*?([A-Za-z0-9][A-Za-z0-9\s-]{1,18}[A-Za-z0-9])"""
        ),
        InstantCodeType.FOOD to Pattern.compile(
            """(?i)(取餐码|取餐号|餐码|餐号|自提码|自取码|到店取码|取餐编号|外卖码)\s*(?:为|是|:|：|=|#|＃)?\s*\*?([A-Za-z0-9][A-Za-z0-9\s-]{1,18}[A-Za-z0-9])"""
        ),
        InstantCodeType.TICKET to Pattern.compile(
            """(?i)(取票码|取票号|票码|票号|电子票码|兑票码|入场码|入园码|检票码|观影码|凭证码|验票码)\s*(?:为|是|:|：|=|#|＃)?\s*\*?([A-Za-z0-9][A-Za-z0-9\s-]{1,18}[A-Za-z0-9])"""
        ),
        InstantCodeType.SENDER to Pattern.compile(
            """(?i)(寄件码|寄件号|寄件编号|寄货码|退货码|退件码|揽收码|上门取件码)\s*(?:为|是|:|：|=|#|＃)?\s*\*?([A-Za-z0-9][A-Za-z0-9\s-]{1,18}[A-Za-z0-9])"""
        )
    )

    val credentialCodePatterns: List<Pair<InstantCodeType, Pattern>> = listOf(
        InstantCodeType.PICKUP to Pattern.compile(
            """(?i)(?:凭|请凭|使用|出示)\s*([A-Za-z0-9][A-Za-z0-9\s-]{1,18}[A-Za-z0-9])\s*(?:取件|取货|提货|签收|领取包裹)"""
        ),
        InstantCodeType.FOOD to Pattern.compile(
            """(?i)(?:凭|请凭|使用|出示)\s*([A-Za-z0-9][A-Za-z0-9\s-]{1,18}[A-Za-z0-9])\s*(?:取餐|取外卖|取餐品|到店自提)"""
        ),
        InstantCodeType.TICKET to Pattern.compile(
            """(?i)(?:凭|请凭|使用|出示)\s*([A-Za-z0-9][A-Za-z0-9\s-]{1,18}[A-Za-z0-9])\s*(?:取票|兑票|入场|入园|检票|观影)"""
        ),
        InstantCodeType.SENDER to Pattern.compile(
            """(?i)(?:凭|请凭|使用|出示)\s*([A-Za-z0-9][A-Za-z0-9\s-]{1,18}[A-Za-z0-9])\s*(?:寄件|退货|退件|揽收)"""
        )
    )

    val relaxedCodePatterns: List<Pair<InstantCodeType, Pattern>> = listOf(
        InstantCodeType.PICKUP to Pattern.compile(
            """(?i)(?:快递|包裹|驿站|菜鸟|丰巢|中通|圆通|申通|韵达|顺丰|京东|德邦|极兔|EMS).{0,24}(?:码|号)\s*(?:为|是|:|：|=|#|＃)?\s*([A-Za-z0-9][A-Za-z0-9\s-]{1,18}[A-Za-z0-9])"""
        ),
        InstantCodeType.FOOD to Pattern.compile(
            """(?i)(?:美团|饿了么|外卖|餐品|肯德基|KFC|麦当劳|瑞幸|星巴克|喜茶|奈雪|蜜雪冰城).{0,24}(?:码|号)\s*(?:为|是|:|：|=|#|＃)?\s*([A-Za-z0-9][A-Za-z0-9\s-]{1,18}[A-Za-z0-9])"""
        ),
        InstantCodeType.TICKET to Pattern.compile(
            """(?i)(?:电影票|演出票|门票|电子票|猫眼|淘票票|大麦|影院|景区).{0,24}(?:码|号)\s*(?:为|是|:|：|=|#|＃)?\s*([A-Za-z0-9][A-Za-z0-9\s-]{1,18}[A-Za-z0-9])"""
        )
    )

    val ignoreKeywords = setOf(
        "验证码", "校验码", "动态码", "短信码", "登录", "注册", "支付", "付款", "转账", "银行", "信用卡",
        "借记卡", "贷款", "还款", "还款日", "账单", "消费", "余额", "理财", "证券", "股票", "基金",
        "医保", "社保", "缴费", "充值", "退款", "安全码", "授权码", "激活码", "积分", "理财产品"
    )

    val nonCodeLabels = setOf("订单号", "运单号", "物流单号", "快递单号", "交易号", "流水号", "手机号", "电话")

    val foodKeywords = setOf(
        "美团", "饿了么", "盒马", "叮咚买菜", "肯德基", "KFC", "麦当劳", "星巴克", "瑞幸", "蜜雪冰城", "喜茶", "奈雪"
    )

    val companyKeywords = mapOf(
        "顺丰" to "顺丰速运", "sf" to "顺丰速运",
        "中通" to "中通快递", "zt" to "中通快递",
        "圆通" to "圆通速递", "yt" to "圆通速递",
        "韵达" to "韵达快递", "yd" to "韵达快递",
        "申通" to "申通快递", "st" to "申通快递",
        "极兔" to "极兔速递", "jt" to "极兔速递",
        "邮政" to "中国邮政", "ems" to "EMS",
        "京东" to "京东快递", "jd" to "京东快递",
        "德邦" to "德邦快递", "dp" to "德邦快递",
        "菜鸟" to "菜鸟驿站",
        "丰巢" to "丰巢快递柜",
        "天猫" to "天猫超市",
        "拼多多" to "拼多多",
        "闪送" to "闪送",
        "UU跑腿" to "UU跑腿"
    )
}
